package com.org.ai.agents.skill;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Skill {
    private String name;
    private String description;
    private String content;
}
