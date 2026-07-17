package server.agents.capabilities.build.profiles;

import java.util.List;
import java.util.Map;

public record AgentSpBuildProfileCatalog(
        int schemaVersion,
        Map<Integer, SkillDefinition> skills,
        List<AgentSpBuildProfile> profiles) {

    public AgentSpBuildProfileCatalog {
        if (schemaVersion <= 0 || skills == null || skills.isEmpty() || profiles == null || profiles.isEmpty()) {
            throw new IllegalArgumentException("SP build profile catalog is required");
        }
        skills = Map.copyOf(skills);
        profiles = List.copyOf(profiles);
    }

    public record SkillDefinition(String name, int maxLevel, List<Requirement> requirements) {
        public SkillDefinition {
            if (name == null || name.isBlank() || maxLevel <= 0) {
                throw new IllegalArgumentException("valid SP skill metadata is required");
            }
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }

    public record Requirement(int skillId, int level) {
        public Requirement {
            if (skillId <= 0 || level <= 0) {
                throw new IllegalArgumentException("valid SP skill prerequisite is required");
            }
        }
    }
}
