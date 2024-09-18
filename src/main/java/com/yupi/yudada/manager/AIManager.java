package com.yupi.yudada.manager;

import com.yupi.yudada.common.ErrorCode;
import com.yupi.yudada.exception.BusinessException;
import com.zhipu.oapi.ClientV4;
import com.zhipu.oapi.Constants;
import com.zhipu.oapi.service.v4.model.*;
import io.reactivex.Flowable;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
public class AIManager {

    @Resource
    private ClientV4 client;

    private static final float STABLE_TEMPERATURE = 0.05f;

    private static final float UNSTABLE_TEMPERATURE = 0.99f;

    public String doSyncStableRequest(String systemPrompt, String userPrompt) {
        return doSyncRequest(systemPrompt, userPrompt, STABLE_TEMPERATURE);
    }

    public String doSyncUnstableRequest(String systemPrompt, String userPrompt) {
        return doSyncRequest(systemPrompt, userPrompt, UNSTABLE_TEMPERATURE);
    }

    public String doSyncRequest(String systemMessage, String userMessage, Float temperature) {
        return doRequest(systemMessage, userMessage, Boolean.FALSE, temperature);
    }

    public String doRequest(String systemPrompt, String userPrompt, Boolean stream, Float temperature) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);
        messages.add(systemMessage);
        messages.add(userMessage);

        return doRequest(messages, stream, temperature);
    }
    public String doRequest(List<ChatMessage> messages, Boolean stream, Float temperature) {
        // 调用AI接口
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(stream)
                .temperature(temperature)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();
        ModelApiResponse invokeModelApiResp = client.invokeModelApi(chatCompletionRequest);
        ChatMessage result = invokeModelApiResp.getData().getChoices().get(0).getMessage();
        return result.getContent().toString();
    }

    // 通用流式请求
    public Flowable<ModelData> doStreamRequest(List<ChatMessage> messages, Float temperature) {
        // 调用AI接口
        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
                .model(Constants.ModelChatGLM4)
                .stream(Boolean.TRUE)
                .temperature(temperature)
                .invokeMethod(Constants.invokeMethod)
                .messages(messages)
                .build();
        try {
            ModelApiResponse invokeModelApiResp = client.invokeModelApi(chatCompletionRequest);
            return invokeModelApiResp.getFlowable();
        }catch (Exception e){
            e.printStackTrace();
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, e.getMessage());
        }
    }

    public Flowable<ModelData> doStreamRequest(String systemPrompt, String userPrompt, Float temperature) {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), systemPrompt);
        ChatMessage userMessage = new ChatMessage(ChatMessageRole.USER.value(), userPrompt);
        messages.add(systemMessage);
        messages.add(userMessage);
        return doStreamRequest(messages, temperature);
    }
}
