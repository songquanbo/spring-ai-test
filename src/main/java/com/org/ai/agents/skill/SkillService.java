package com.org.ai.agents.skill;

import com.org.ai.agents.codeassistant.config.CodeAssistantProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final CodeAssistantProperties properties;
    private final Map<String, Skill> skillCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        scanSkills();
    }

    private void scanSkills() {
        Path workspacePath = Paths.get(properties.getWorkspace()).toAbsolutePath().normalize();
        Path skillsDir = workspacePath.resolve(properties.getSkillsPath()).normalize();

        if (!Files.isDirectory(skillsDir)) {
            log.warn("Skills directory not found: {}. Create it and add SKILL.md files.", skillsDir);
            return;
        }

        try (Stream<Path> stream = Files.list(skillsDir)) {
            stream.filter(Files::isDirectory)
                    .forEach(this::loadSkillFromDir);
        } catch (IOException e) {
            log.warn("Failed to list skills directory: {}", skillsDir, e);
        }

        if (skillCache.isEmpty()) {
            log.info("No skills found in {}", skillsDir);
        } else {
            log.info("Loaded {} skills from {}: {}", skillCache.size(), skillsDir, skillCache.keySet());
        }
    }

    private void loadSkillFromDir(Path dir) {
        Path skillFile = dir.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return;
        }
        try {
            String name = dir.getFileName().toString();
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String description = extractDescription(name, content);

            skillCache.put(name, Skill.builder()
                    .name(name)
                    .description(description)
                    .content(content)
                    .build());
            log.info("Loaded skill: {}", name);
        } catch (Exception e) {
            log.warn("Failed to load skill from {}", skillFile, e);
        }
    }

    public List<Skill> listSkills() {
        return List.copyOf(skillCache.values());
    }

    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skillCache.get(name));
    }

    private String extractDescription(String name, String content) {
        String firstLine = content.lines().findFirst().orElse("").trim();
        if (firstLine.startsWith("# ")) {
            int colonIdx = firstLine.indexOf(':');
            if (colonIdx > 2) {
                return firstLine.substring(colonIdx + 1).trim();
            }
        }
        return name;
    }
}
