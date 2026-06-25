package com.org.ai.web.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    @GetMapping
    public List<Map<String, String>> listAgents() {
        return List.of(
                Map.of("id", "code-assistant", "name", "代码助手", "description", "分析需求、修复Bug、重构Java代码，支持文件读写和命令执行"),
                Map.of("id", "customer-service", "name", "客服系统", "description", "自动回复客户咨询（开发中）")
        );
    }
}
