package com.org.ai.agents.codeassistant.config;

import com.org.ai.agents.codeassistant.tool.ProjectCodeTool;
import com.org.ai.agents.codeassistant.tool.WebSearchTool;
import com.org.ai.agents.skill.SkillTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
public class ToolCallbackConfig {

    @Bean
    public List<ToolCallback> functionCallbacks(ProjectCodeTool projectCodeTool, WebSearchTool webSearchTool, SkillTool skillTool) {
        ToolCallbackProvider provider = MethodToolCallbackProvider.builder()
                .toolObjects(projectCodeTool, webSearchTool, skillTool).build();
        return Arrays.asList(provider.getToolCallbacks());
    }
}
