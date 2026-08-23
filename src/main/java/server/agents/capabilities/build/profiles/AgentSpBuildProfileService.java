package server.agents.capabilities.build.profiles;

import client.Character;
import client.Skill;
import constants.game.GameConstants;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.events.AgentEventPriority;
import server.agents.progression.events.AgentProgressionEventPublisher;
import server.agents.progression.events.AgentSkillLearnedEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public final class AgentSpBuildProfileService {
    private AgentSpBuildProfileService() {
    }

    public static AgentSpBuildProfile select(AgentRuntimeEntry entry, String profileId) {
        AgentSpBuildProfile profile = AgentSpBuildProfileRepository.defaultRepository().find(profileId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown Agent SP build profile: " + profileId));
        entry.spBuildProfileState().assign(profile);
        autoAssign(entry, entry.bot(), AgentSkillGatewayRuntime.skills());
        return profile;
    }

    public static boolean autoAssign(AgentRuntimeEntry entry, Character agent) {
        return autoAssign(entry, agent, AgentSkillGatewayRuntime.skills());
    }

    /** Returns true when an independent profile owns SP allocation for this Agent. */
    public static boolean autoAssign(AgentRuntimeEntry entry, Character agent, SkillGateway skills) {
        AgentSpBuildProfile profile = entry.spBuildProfileState().profile();
        if (profile == null) {
            return false;
        }
        profile = transitionOptimalProfile(entry, agent, profile);
        if (agent == null || agent.getLevel() > profile.supportedThroughLevel()
                || !profile.supports(agent.getJob())) {
            return true;
        }

        AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();
        Map<Integer, Integer> cumulativeTargets = new LinkedHashMap<>();
        if (!profile.segments().isEmpty()) {
            for (AgentSpBuildProfile.AllocationSegment segment : profile.segments()) {
                if (segment.minimumLevel() > agent.getLevel()) {
                    break;
                }
                addTarget(profile, cumulativeTargets, segment.skillId(), segment.points());
            }
            applyTargets(entry, agent, skills, repository, profile, cumulativeTargets);
            if (allCoreTargetsMet(agent, skills, cumulativeTargets)) {
                applyDumpSkills(entry, agent, skills, repository, profile);
            }
            return true;
        }
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            if (levelPlan.level() > agent.getLevel()) {
                break;
            }
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                addTarget(profile, cumulativeTargets, allocation.skillId(), allocation.points());
            }
        }
        applyTargets(entry, agent, skills, repository, profile, cumulativeTargets);
        return true;
    }

    private static void addTarget(AgentSpBuildProfile profile,
                                  Map<Integer, Integer> targets,
                                  int skillId,
                                  int points) {
        targets.compute(skillId, (ignored, current) -> current == null
                ? profile.inheritedSkillLevels().getOrDefault(skillId, 0) + points
                : current + points);
    }

    private static AgentSpBuildProfile transitionOptimalProfile(AgentRuntimeEntry entry,
                                                                 Character agent,
                                                                 AgentSpBuildProfile current) {
        if (agent == null || agent.getJob() == null || !current.isMapleRoyalsOptimal2026()
                || current.exactJobId() == agent.getJob().getId()) {
            return current;
        }
        String nextId = AgentSpBuildDefaultCatalog.nextProfileId(
                current.profileId(), agent.getJob().getId());
        if (nextId == null) {
            return current;
        }
        AgentSpBuildProfile next = AgentSpBuildProfileRepository.defaultRepository().find(nextId)
                .orElseThrow(() -> new IllegalStateException("Missing default SP profile " + nextId));
        entry.spBuildProfileState().assign(next);
        return next;
    }

    private static void applyTargets(AgentRuntimeEntry entry,
                                     Character agent,
                                     SkillGateway skills,
                                     AgentSpBuildProfileRepository repository,
                                     AgentSpBuildProfile profile,
                                     Map<Integer, Integer> targets) {
        for (Map.Entry<Integer, Integer> target : targets.entrySet()) {
            Skill skill = skills.getSkill(target.getKey());
            AgentSpBuildProfileCatalog.SkillDefinition definition = repository.skill(target.getKey());
            if (skill == null || definition == null) {
                continue;
            }
            int book = GameConstants.getSkillBook(target.getKey() / 10000);
            int targetLevel = Math.min(target.getValue(), maxAssignableLevel(agent, skill, definition));
            while (agent.getRemainingSps()[book] > 0 && agent.getSkillLevel(skill) < targetLevel) {
                if (!requirementsMet(agent, definition, skills)) {
                    return;
                }
                learnOne(entry, agent, skill, target.getKey(), book, definition.maxLevel(), profile.profileId());
            }
        }
    }

    private static boolean allCoreTargetsMet(Character agent,
                                             SkillGateway skills,
                                             Map<Integer, Integer> targets) {
        for (Map.Entry<Integer, Integer> target : targets.entrySet()) {
            Skill skill = skills.getSkill(target.getKey());
            if (skill == null || agent.getSkillLevel(skill) < target.getValue()) {
                return false;
            }
        }
        return true;
    }

    private static void applyDumpSkills(AgentRuntimeEntry entry,
                                        Character agent,
                                        SkillGateway skills,
                                        AgentSpBuildProfileRepository repository,
                                        AgentSpBuildProfile profile) {
        for (Integer skillId : profile.dumpSkillIds()) {
            Skill skill = skills.getSkill(skillId);
            AgentSpBuildProfileCatalog.SkillDefinition definition = repository.skill(skillId);
            if (skill == null || definition == null || !requirementsMet(agent, definition, skills)) {
                continue;
            }
            int book = GameConstants.getSkillBook(skillId / 10000);
            while (agent.getRemainingSps()[book] > 0
                    && agent.getSkillLevel(skill) < maxAssignableLevel(agent, skill, definition)) {
                learnOne(entry, agent, skill, skillId, book, definition.maxLevel(), profile.profileId());
            }
        }
    }

    private static void learnOne(AgentRuntimeEntry entry,
                                 Character agent,
                                 Skill skill,
                                 int skillId,
                                 int book,
                                 int catalogMaxLevel,
                                 String profileId) {
        int currentLevel = agent.getSkillLevel(skill);
        int maxLevel = Math.min(skill.getMaxLevel(), catalogMaxLevel);
        if (currentLevel >= maxLevel) {
            return;
        }
        agent.gainSp(-1, book, false);
        agent.changeSkillLevel(skill, (byte) (currentLevel + 1),
                agent.getMasterLevel(skill), agent.getSkillExpiration(skill));
        if (agent.getId() > 0) {
            AgentProgressionEventPublisher.publish(entry, new AgentSkillLearnedEvent(
                            agent.getId(), System.currentTimeMillis(), agent.getLevel(),
                            skillId, currentLevel, currentLevel + 1,
                            agent.getRemainingSps()[book], profileId,
                            AgentProgressionEventPublisher.objectiveId(entry)),
                    AgentEventPriority.NORMAL);
        }
    }

    private static int maxAssignableLevel(
            Character agent,
            Skill skill,
            AgentSpBuildProfileCatalog.SkillDefinition definition) {
        int maxLevel = Math.min(skill.getMaxLevel(), definition.maxLevel());
        return skill.isFourthJob() ? Math.min(maxLevel, agent.getMasterLevel(skill)) : maxLevel;
    }

    private static boolean requirementsMet(Character agent,
                                           AgentSpBuildProfileCatalog.SkillDefinition definition,
                                           SkillGateway skills) {
        for (AgentSpBuildProfileCatalog.Requirement requirement : definition.requirements()) {
            Skill required = skills.getSkill(requirement.skillId());
            if (required == null || agent.getSkillLevel(required) < requirement.level()) {
                return false;
            }
        }
        return true;
    }
}
