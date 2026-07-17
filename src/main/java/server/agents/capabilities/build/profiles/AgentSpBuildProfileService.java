package server.agents.capabilities.build.profiles;

import client.Character;
import client.Skill;
import constants.game.GameConstants;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.integration.SkillGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.HashMap;
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
        if (agent == null || agent.getLevel() > profile.supportedThroughLevel()
                || !profile.supports(agent.getJob())) {
            return true;
        }

        AgentSpBuildProfileRepository repository = AgentSpBuildProfileRepository.defaultRepository();
        Map<Integer, Integer> cumulativeTargets = new HashMap<>();
        for (AgentSpBuildProfile.LevelPlan levelPlan : profile.levels()) {
            if (levelPlan.level() > agent.getLevel()) {
                break;
            }
            for (AgentSpBuildProfile.SkillPoints allocation : levelPlan.allocations()) {
                int targetLevel = cumulativeTargets.merge(
                        allocation.skillId(), allocation.points(), Integer::sum);
                Skill skill = skills.getSkill(allocation.skillId());
                if (skill == null) {
                    continue;
                }
                int book = GameConstants.getSkillBook(allocation.skillId() / 10000);
                while (agent.getRemainingSps()[book] > 0 && agent.getSkillLevel(skill) < targetLevel) {
                    if (!requirementsMet(agent, repository.skill(allocation.skillId()), skills)) {
                        return true;
                    }
                    int currentLevel = agent.getSkillLevel(skill);
                    int maxLevel = Math.min(skill.getMaxLevel(), repository.skill(allocation.skillId()).maxLevel());
                    if (currentLevel >= maxLevel) {
                        break;
                    }
                    agent.gainSp(-1, book, false);
                    agent.changeSkillLevel(skill, (byte) (currentLevel + 1),
                            agent.getMasterLevel(skill), agent.getSkillExpiration(skill));
                }
            }
        }
        return true;
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
