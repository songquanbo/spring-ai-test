package com.org.ai.agents.codeassistant.factory;

import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class PromptTemplateFactory {
    private final ResourceLoader resourceLoader;

    public String loadTemplate(String classPath) throws IOException {
        // 1. 加载模板文件
        Resource resource = resourceLoader.getResource(
                classPath
        );
        return StreamUtils.copyToString(
                new BufferedInputStream(resource.getInputStream()), StandardCharsets.UTF_8
        );
    }
}
