package com.org.ai.agents.codeassistant.permission;

import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class PermissionEvaluator {

    private final CodeAssistantProperties properties;
    private final Set<String> approvedOps = ConcurrentHashMap.newKeySet();

    public enum Result { ALLOW, ASK, DENY }

    public Result check(String toolName) {
        return check(toolName, "");
    }

    public Result check(String toolName, String arg) {
        String normalizedArg = (arg == null) ? "" : arg.trim();
        CodeAssistantProperties.PermissionConfig perm = properties.getPermission();

        if (matchesAny(perm.getDeny(), toolName, normalizedArg)) {
            return Result.DENY;
        }
        String askKey = toolName + ":" + normalizedArg;
        if (matchesAny(perm.getAsk(), toolName, normalizedArg)) {
            if (approvedOps.contains(askKey)) {
                return Result.ALLOW;
            }
            return Result.ASK;
        }
        return Result.ALLOW;
    }

    public boolean approve(String toolName, String arg) {
        approvedOps.add(toolName + ":" + ((arg == null) ? "" : arg.trim()));
        return true;
    }

    private boolean matchesAny(List<String> rules, String toolName, String arg) {
        if (rules == null) return false;
        for (String rule : rules) {
            if (rule == null || rule.isBlank()) continue;
            int colonIdx = rule.indexOf(": ");
            if (colonIdx < 0) {
                if (rule.equals(toolName)) return true;
            } else {
                String ruleTool = rule.substring(0, colonIdx).trim();
                String ruleArg = rule.substring(colonIdx + 2).trim();
                if (ruleTool.equals(toolName) && arg.contains(ruleArg)) return true;
            }
        }
        return false;
    }
}
