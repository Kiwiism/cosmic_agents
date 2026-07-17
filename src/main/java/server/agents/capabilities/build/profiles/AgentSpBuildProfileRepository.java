package server.agents.capabilities.build.profiles;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AgentSpBuildProfileRepository {
    private static final String DEFAULT_RESOURCE = "/agents/profiles/sp-build-profiles.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentSpBuildProfileRepository DEFAULT = loadResource(DEFAULT_RESOURCE);

    private final Map<Integer, AgentSpBuildProfileCatalog.SkillDefinition> skills;
    private final Map<String, AgentSpBuildProfile> profilesById;

    AgentSpBuildProfileRepository(AgentSpBuildProfileCatalog catalog) {
        skills = catalog.skills();
        Map<String, AgentSpBuildProfile> indexed = new LinkedHashMap<>();
        for (AgentSpBuildProfile profile : catalog.profiles()) {
            validate(profile);
            if (indexed.putIfAbsent(profile.profileId(), profile) != null) {
                throw new IllegalArgumentException("Duplicate SP build profile: " + profile.profileId());
            }
        }
        profilesById = Map.copyOf(indexed);
    }

    public static AgentSpBuildProfileRepository defaultRepository() {
        return DEFAULT;
    }

    public Optional<AgentSpBuildProfile> find(String profileId) {
        return Optional.ofNullable(profilesById.get(profileId));
    }

    public List<AgentSpBuildProfile> all() {
        return List.copyOf(profilesById.values());
    }

    public AgentSpBuildProfileCatalog.SkillDefinition skill(int skillId) {
        return skills.get(skillId);
    }

    private void validate(AgentSpBuildProfile profile) {
        Map<Integer, Integer> cumulative = new LinkedHashMap<>();
        int previousLevel = 0;
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            if (levelPlan.level() <= previousLevel || levelPlan.level() > profile.supportedThroughLevel()) {
                throw new IllegalArgumentException("SP profile levels must be ordered and supported");
            }
            if (previousLevel != 0 && levelPlan.level() != previousLevel + 1) {
                throw new IllegalArgumentException("SP profile levels must be contiguous");
            }
            int expectedPoints = previousLevel == 0 ? 1 : 3;
            int actualPoints = levelPlan.allocations().stream().mapToInt(AgentSpBuildProfile.SkillPoints::points).sum();
            if (actualPoints != expectedPoints) {
                throw new IllegalArgumentException("SP profile level " + levelPlan.level()
                        + " must allocate " + expectedPoints + " points");
            }
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                AgentSpBuildProfileCatalog.SkillDefinition skill = skills.get(allocation.skillId());
                if (skill == null) {
                    throw new IllegalArgumentException("Missing skill metadata for " + allocation.skillId());
                }
                for (AgentSpBuildProfileCatalog.Requirement requirement : skill.requirements()) {
                    if (cumulative.getOrDefault(requirement.skillId(), 0) < requirement.level()) {
                        throw new IllegalArgumentException("Unmet prerequisite before skill " + allocation.skillId());
                    }
                }
                int target = cumulative.merge(allocation.skillId(), allocation.points(), Integer::sum);
                if (target > skill.maxLevel()) {
                    throw new IllegalArgumentException("SP allocation exceeds WZ maximum for " + allocation.skillId());
                }
            }
            previousLevel = levelPlan.level();
        }
        if (previousLevel != profile.supportedThroughLevel()) {
            throw new IllegalArgumentException("SP profile must cover every level through its supported limit");
        }
    }

    private static AgentSpBuildProfileRepository loadResource(String resourcePath) {
        try (InputStream input = AgentSpBuildProfileRepository.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Missing Agent SP build profiles: " + resourcePath);
            }
            return new AgentSpBuildProfileRepository(MAPPER.readValue(input, AgentSpBuildProfileCatalog.class));
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Agent SP build profiles: " + resourcePath, failure);
        }
    }
}
