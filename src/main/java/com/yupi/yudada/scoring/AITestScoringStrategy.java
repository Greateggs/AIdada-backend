package com.yupi.yudada.scoring;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yupi.yudada.manager.AIManager;
import com.yupi.yudada.model.dto.question.QuestionAnswerDTO;
import com.yupi.yudada.model.dto.question.QuestionContentDTO;
import com.yupi.yudada.model.entity.App;
import com.yupi.yudada.model.entity.Question;
import com.yupi.yudada.model.entity.UserAnswer;
import com.yupi.yudada.model.vo.QuestionVO;
import com.yupi.yudada.service.QuestionService;
import com.yupi.yudada.service.ScoringResultService;
import org.bouncycastle.crypto.Digest;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ScoringStrategyConfig(appType = 1, scoringStrategy = 1)
public class AITestScoringStrategy implements ScoringStrategy {

    @Resource
    private QuestionService questionService;

    @Resource
    private AIManager aiManager;

    @Resource
    private RedissonClient redissonClient;

    // 分布式锁的key
    private static final String AI_ANSWER_LOCK = "AI_ANSWER_LOCK";

    // 本地AI评分缓存
    private final Cache<String, String> answerCacheMap = Caffeine.newBuilder().initialCapacity(1024)
            // 设置缓存有效期为5分钟
            .expireAfterAccess(5L, TimeUnit.MINUTES)
            .build();

    private static final String AI_TEST_SCORING_SYSTEM_MESSAGE = "你是一位严谨的判题专家，我会给你如下信息：\n" +
            "```\n" +
            "应用名称，\n" +
            "【【【应用描述】】】，\n" +
            "题目和用户回答的列表：格式为 [{\"title\": \"题目\",\"answer\": \"用户回答\"}]\n" +
            "```\n" +
            "\n" +
            "请你根据上述信息，按照以下步骤来对用户进行评价：\n" +
            "1. 要求：需要给出一个明确的评价结果，包括评价名称（尽量简短）和评价描述（尽量详细，大于 200 字）\n" +
            "2. 严格按照下面的 json 格式输出评价名称和评价描述\n" +
            "```\n" +
            "{\"resultName\": \"评价名称\", \"resultDesc\": \"评价描述\"}\n" +
            "```\n" +
            "3. 返回格式必须为 JSON 对象";

    private String getAiTestScoringUserMessage(App app, List<QuestionContentDTO> questionContentDTOList, List<String> choices) {
        StringBuilder userMessage = new StringBuilder();
        userMessage.append(app.getAppName()).append("\n");
        userMessage.append(app.getAppDesc()).append("\n");
        List<QuestionAnswerDTO> questionAnswerDTOList = new ArrayList<>();
        for (int i = 0; i < questionContentDTOList.size(); i++) {
            QuestionAnswerDTO questionAnswerDTO = new QuestionAnswerDTO();
            questionAnswerDTO.setTitle(questionContentDTOList.get(i).getTitle());
            questionAnswerDTO.setUserAnswer(choices.get(i));
            questionAnswerDTOList.add(questionAnswerDTO);
        }
        userMessage.append(JSONUtil.toJsonStr(questionAnswerDTOList));
        return userMessage.toString();
    }


    @Override
    public UserAnswer doScore(List<String> choices, App app) {
        Long appId = app.getId();
        //
        String str = JSONUtil.toJsonStr(choices);
        String key = buildCacheKey(appId, str);
        String answerJson = answerCacheMap.getIfPresent(key);
        if (StrUtil.isNotBlank(answerJson)) {
            // 构造返回答案
            UserAnswer userAnswer = JSONUtil.toBean(answerJson, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(str);
            return userAnswer;
        }

        // 定义锁
        RLock lock = redissonClient.getLock(AI_ANSWER_LOCK+key);
        try {
            // 竞争锁
            boolean res = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if (!res) {
                return null;
            }

            Question question = questionService.getOne(
                    Wrappers.lambdaQuery(Question.class).eq(Question::getAppId, appId)
            );
            QuestionVO questionVO = QuestionVO.objToVo(question);
            List<QuestionContentDTO> questionContentDTOList = questionVO.getQuestionContent();

            // 调用AI
            String userMessage = getAiTestScoringUserMessage(app, questionContentDTOList ,choices);
            String result = aiManager.doSyncStableRequest(AI_TEST_SCORING_SYSTEM_MESSAGE, userMessage);

            // 解析AI结果
            int start = result.indexOf("{") ;
            int end = result.lastIndexOf("}") ;
            String json = result.substring(start,end+1);

            // 缓存结果
            answerCacheMap.put(key, json);

            // 构造返回答案
            UserAnswer userAnswer = JSONUtil.toBean(json, UserAnswer.class);
            userAnswer.setAppId(appId);
            userAnswer.setAppType(app.getAppType());
            userAnswer.setScoringStrategy(app.getScoringStrategy());
            userAnswer.setChoices(JSONUtil.toJsonStr(choices));

            return userAnswer;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (lock != null && lock.isLocked())
                if (lock.isHeldByCurrentThread())
                    lock.unlock();
        }
    }

    public String buildCacheKey(Long appId, String choices) {
        return appId + ":" + DigestUtil.md5Hex(choices) ;
    }
}
