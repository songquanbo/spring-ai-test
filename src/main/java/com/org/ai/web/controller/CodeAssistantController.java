package com.org.ai.web.controller;

import com.org.ai.agents.codeassistant.service.CodeAssistantAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/code-assistant")
@RequiredArgsConstructor
public class CodeAssistantController {

    private final CodeAssistantAgentService codeAssistantAgent;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam String sessionId,
            @RequestParam String userQuery) {
        return codeAssistantAgent.streamChat(sessionId, userQuery);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStreamPost(
            @RequestBody Map<String, String> body) {
        String sessionId = body.getOrDefault("sessionId", "default");
        String userQuery = body.get("userQuery");
        return codeAssistantAgent.streamChat(sessionId, userQuery);
    }
}
