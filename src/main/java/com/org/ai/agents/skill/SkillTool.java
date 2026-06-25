package com.org.ai.agents.skill;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SkillTool {

    private final SkillService skillService;

    @Tool(description = "列出所有可用技能，每个技能包含名称和描述。用于了解当前可加载的技能。")
    public List<Skill> listSkills() {
        return skillService.listSkills();
    }

    @Tool(description = "加载指定技能的内容。调用后按技能中的指令执行任务。技能名称通过 listSkills 获取。")
    public String loadSkill(String name) {
        return skillService.getSkill(name)
                .map(Skill::getContent)
                .orElse("错误：未找到技能 '" + name + "'，请先通过 listSkills 查看可用的技能列表。");
    }
}
