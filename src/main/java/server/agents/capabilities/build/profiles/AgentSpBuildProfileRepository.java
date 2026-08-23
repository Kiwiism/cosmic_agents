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
    private static final String MAPLEROYALS_2026_RESOURCE =
            "/agents/profiles/mapleroyals-optimal-2026-sp-build-profiles.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final AgentSpBuildProfileRepository DEFAULT = loadResources(
            DEFAULT_RESOURCE, MAPLEROYALS_2026_RESOURCE);

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

    private AgentSpBuildProfileRepository(List<AgentSpBuildProfileCatalog> catalogs) {
        Map<Integer, AgentSpBuildProfileCatalog.SkillDefinition> mergedSkills = new LinkedHashMap<>();
        Map<String, AgentSpBuildProfile> indexed = new LinkedHashMap<>();
        for (AgentSpBuildProfileCatalog catalog : catalogs) {
            for (Map.Entry<Integer, AgentSpBuildProfileCatalog.SkillDefinition> skill
                    : catalog.skills().entrySet()) {
                AgentSpBuildProfileCatalog.SkillDefinition previous =
                        mergedSkills.putIfAbsent(skill.getKey(), skill.getValue());
                if (previous != null && !previous.equals(skill.getValue())) {
                    throw new IllegalArgumentException("Conflicting SP skill metadata: " + skill.getKey());
                }
            }
        }
        skills = Map.copyOf(mergedSkills);
        for (AgentSpBuildProfileCatalog catalog : catalogs) {
            for (AgentSpBuildProfile profile : catalog.profiles()) {
                validate(profile);
                if (indexed.putIfAbsent(profile.profileId(), profile) != null) {
                    throw new IllegalArgumentException("Duplicate SP build profile: " + profile.profileId());
                }
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
        for (Map.Entry<Integer, Integer> inherited : profile.inheritedSkillLevels().entrySet()) {
            AgentSpBuildProfileCatalog.SkillDefinition skill = skills.get(inherited.getKey());
            if (skill == null || inherited.getValue() == null || inherited.getValue() <= 0
                    || inherited.getValue() > skill.maxLevel()) {
                throw new IllegalArgumentException("Invalid inherited SP baseline for "
                        + inherited.getKey());
            }
            cumulative.put(inherited.getKey(), inherited.getValue());
        }
        if (!profile.segments().isEmpty()) {
            validateOrderedProfile(profile, cumulative);
            return;
        }
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

    private void validateOrderedProfile(AgentSpBuildProfile profile,
                                        Map<Integer, Integer> cumulative) {
        int previousMinimumLevel = profile.startingLevel();
        int allocated = 0;
        for (AgentSpBuildProfile.AllocationSegment segment : profile.segments()) {
            if (segment.minimumLevel() < previousMinimumLevel
                    || segment.minimumLevel() > profile.supportedThroughLevel()) {
                throw new IllegalArgumentException("SP segments must have ordered supported levels");
            }
            AgentSpBuildProfileCatalog.SkillDefinition skill = skills.get(segment.skillId());
            if (skill == null) {
                throw new IllegalArgumentException("Missing skill metadata for " + segment.skillId());
            }
            for (AgentSpBuildProfileCatalog.Requirement requirement : skill.requirements()) {
                if (cumulative.getOrDefault(requirement.skillId(), 0) < requirement.level()) {
                    throw new IllegalArgumentException("Unmet prerequisite before skill " + segment.skillId());
                }
            }
            int target = cumulative.merge(segment.skillId(), segment.points(), Integer::sum);
            if (target > skill.maxLevel()) {
                throw new IllegalArgumentException("SP allocation exceeds WZ maximum for " + segment.skillId());
            }
            previousMinimumLevel = segment.minimumLevel();
            allocated += segment.points();
        }
        int available = profile.entrySp()
                + (profile.supportedThroughLevel() - profile.startingLevel()) * 3;
        if (allocated > available) {
            throw new IllegalArgumentException("SP profile allocates more points than its level range provides");
        }
        int dumpCapacity = 0;
        java.util.Set<Integer> uniqueDumpSkills = new java.util.HashSet<>();
        for (Integer dumpSkillId : profile.dumpSkillIds()) {
            AgentSpBuildProfileCatalog.SkillDefinition skill = skills.get(dumpSkillId);
            if (skill == null || !uniqueDumpSkills.add(dumpSkillId)) {
                throw new IllegalArgumentException("Invalid or duplicate dump skill " + dumpSkillId);
            }
            for (AgentSpBuildProfileCatalog.Requirement requirement : skill.requirements()) {
                if (cumulative.getOrDefault(requirement.skillId(), 0) < requirement.level()) {
                    throw new IllegalArgumentException("Unmet prerequisite before dump skill " + dumpSkillId);
                }
            }
            dumpCapacity += Math.max(0, skill.maxLevel() - cumulative.getOrDefault(dumpSkillId, 0));
        }
        if (allocated + dumpCapacity < available) {
            throw new IllegalArgumentException("SP profile cannot spend its supported level budget");
        }
    }

    private static AgentSpBuildProfileRepository loadResources(String... resourcePaths) {
        try {
            List<AgentSpBuildProfileCatalog> catalogs = new java.util.ArrayList<>();
            for (String resourcePath : resourcePaths) {
                try (InputStream input = AgentSpBuildProfileRepository.class.getResourceAsStream(resourcePath)) {
                    if (input == null) {
                        throw new IllegalStateException("Missing Agent SP build profiles: " + resourcePath);
                    }
                    catalogs.add(MAPPER.readValue(input, AgentSpBuildProfileCatalog.class));
                }
            }
            return new AgentSpBuildProfileRepository(catalogs);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load Agent SP build profiles", failure);
        }
    }
}
