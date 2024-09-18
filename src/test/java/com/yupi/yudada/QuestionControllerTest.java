package com.yupi.yudada;

import com.yupi.yudada.controller.QuestionController;
import com.yupi.yudada.model.dto.question.AiGenerateQuestionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class QuestionControllerTest {
    @Resource
    private QuestionController questionController;

    @Test
    public void aiGenerateQuestionSSE() throws InterruptedException {
        // 构造请求参数
        AiGenerateQuestionRequest request = new AiGenerateQuestionRequest();
        request.setAppId(3L);
        request.setQuestionNumber(5);
        request.setOptionNumber(2);

        // 模拟普通用户
        questionController.aiGenerateQuestionSSE(request, false);
        questionController.aiGenerateQuestionSSE(request, false);

        // 模拟Vip
        questionController.aiGenerateQuestionSSE(request, true);

        Thread.sleep(1000000L);
    }
}
