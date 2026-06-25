package com.org.ai.agents.codeassistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "agent.code-assistant")
public class CodeAssistantProperties {
    private int maxIterations;
    private boolean writeEnabled;
    private String workspace;
    private List<String> fileWhitelistDirs = List.of();
    private String systemPromptPath;
    private String systemPrompt;
    private long maxFileSizeBytes = 10 * 1024 * 1024;
    private int maxSearchDepth = 5;
    private long commandTimeout = 30;
    private String skillsPath = "skills";
    private PermissionConfig permission = new PermissionConfig();

    @Data
    public static class PermissionConfig {
        private List<String> deny = List.of();
        private List<String> ask = List.of();
    }
}
