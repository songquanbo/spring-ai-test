package com.org.ai.web.controller;

import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
@RequiredArgsConstructor
public class WorkspaceController {

    private final CodeAssistantProperties properties;

    @PostMapping
    public Map<String, String> setWorkspace(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path != null && !path.isBlank()) {
            properties.setWorkspace(path);
        }
        return Map.of("workspace", properties.getWorkspace());
    }

    @GetMapping
    public Map<String, String> getWorkspace() {
        return Map.of("workspace", properties.getWorkspace());
    }
}
