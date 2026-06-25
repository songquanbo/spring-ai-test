package com.org.ai.agents.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

@Slf4j
public abstract class BaseAgent {

    protected final ChatClient chatClient;

    protected BaseAgent(ChatModel chatModel, ChatMemory chatMemory,
                        List<ToolCallback> toolCallbacks, String systemPrompt) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }

    protected Flux<String> streamChat(String sessionId, String userMessage) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        return chatClient.prompt()
                .messages(new UserMessage(userMessage))
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .doOnNext(chunk -> log.debug("AI chunk: {}", chunk))
                .doOnComplete(() -> log.info("Stream completed for session: {}", sessionId))
                .doOnError(error -> log.error("Stream error for session: {}", sessionId, error));
    }
}
