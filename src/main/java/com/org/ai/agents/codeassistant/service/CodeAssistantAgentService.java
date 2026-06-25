package com.org.ai.agents.codeassistant.service;

import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import com.org.ai.agents.codeassistant.factory.PromptTemplateFactory;
import com.org.ai.agents.core.BaseAgent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CodeAssistantAgentService extends BaseAgent {

    public CodeAssistantAgentService(ChatModel chatModel, ChatMemory chatMemory,
                                     List<ToolCallback> toolCallbacks,
                                     CodeAssistantProperties properties,
                                     PromptTemplateFactory promptTemplateFactory) {
        super(chatModel, chatMemory, toolCallbacks,
                loadSystemPrompt(properties, promptTemplateFactory));
    }

    private static String loadSystemPrompt(CodeAssistantProperties properties,
                                            PromptTemplateFactory factory) {
        try {
            String template = factory.loadTemplate(properties.getSystemPromptPath());
            StringSubstitutor substitutor = new StringSubstitutor(Map.of(
                    "workspace", properties.getWorkspace(),
                    "maxIterations", properties.getMaxIterations(),
                    "writeEnabled", properties.isWriteEnabled(),
                    "allowedDirs", String.join(", ", properties.getFileWhitelistDirs())
            ));
            return substitutor.replace(template);
        } catch (IOException e) {
            log.error("Failed to load system prompt from {}", properties.getSystemPromptPath(), e);
            return "";
        }
    }

    public Flux<String> streamChat(String sessionId, String userMessage) {
        return super.streamChat(sessionId, userMessage);
    }
}
