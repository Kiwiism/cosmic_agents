package server.agents.capabilities.navigation;

import client.Character;
import client.Skill;
import client.SkillFactory;
import constants.skills.BlazeWizard;
import constants.skills.Cleric;
import constants.skills.Evan;
import constants.skills.FPWizard;
import constants.skills.Hermit;
import constants.skills.ILWizard;
import constants.skills.NightWalker;
import server.agents.capabilities.movement.AgentMovementSkillConfig;
import server.agents.capabilities.movement.AgentMovementSkillStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.FieldLimit;

/**
 * Eligibility policy for navigation-level movement skills.
 *
 * <p>The graph describes possible skill movement. This policy decides whether
 * one Agent may see or execute a specific skill edge.</p>
 */
public final class AgentMovementSkillPolicy {
    private static final int[] TELEPORT_SKILL_IDS = {
            FPWizard.TELEPORT,
            ILWizard.TELEPORT,
            Cleric.TELEPORT,
            BlazeWizard.TELEPORT,
            Evan.TELEPORT
    };
    private static final int[] FLASH_JUMP_SKILL_IDS = {
            Hermit.FLASH_JUMP,
            NightWalker.FLASH_JUMP
    };

    private AgentMovementSkillPolicy() {
    }

    public static boolean isSkillEdge(AgentNavigationGraph.Edge edge) {
        return edge != null && (edge.type == AgentNavigationGraph.EdgeType.TELEPORT
                || edge.type == AgentNavigationGraph.EdgeType.FLASH_JUMP);
    }

    public static boolean canUseActivePath(Character agent, AgentNavigationGraph.Edge edge) {
        return switch (edge.type) {
            case TELEPORT -> AgentMovementSkillConfig.TELEPORT_MODE.active()
                    && baseEligibility(agent, edge)
                    && affordableWithReserve(agent, edge);
            case FLASH_JUMP -> AgentMovementSkillConfig.FLASH_JUMP_MODE.active()
                    && baseEligibility(agent, edge)
                    && affordableWithReserve(agent, edge);
            default -> true;
        };
    }

    public static boolean canUseShadowPath(Character agent, AgentNavigationGraph.Edge edge) {
        return !isSkillEdge(edge) || (mode(edge.type).visibleToShadowRouting()
                && baseEligibility(agent, edge));
    }

    public static boolean canUseAnyActiveMovementSkill(Character agent) {
        return (AgentMovementSkillConfig.TELEPORT_MODE.active()
                && activeEligibility(agent, AgentNavigationGraph.EdgeType.TELEPORT))
                || (AgentMovementSkillConfig.FLASH_JUMP_MODE.active()
                && activeEligibility(agent, AgentNavigationGraph.EdgeType.FLASH_JUMP));
    }

    public static boolean canUseAnyShadowMovementSkill(Character agent) {
        return (AgentMovementSkillConfig.TELEPORT_MODE.visibleToShadowRouting()
                && baseEligibility(agent, AgentNavigationGraph.EdgeType.TELEPORT))
                || (AgentMovementSkillConfig.FLASH_JUMP_MODE.visibleToShadowRouting()
                && baseEligibility(agent, AgentNavigationGraph.EdgeType.FLASH_JUMP));
    }

    public static boolean shouldCompareShadowMovementSkill(Character agent) {
        return (AgentMovementSkillConfig.TELEPORT_MODE.shadowOnly()
                && baseEligibility(agent, AgentNavigationGraph.EdgeType.TELEPORT))
                || (AgentMovementSkillConfig.FLASH_JUMP_MODE.shadowOnly()
                && baseEligibility(agent, AgentNavigationGraph.EdgeType.FLASH_JUMP));
    }

    public static boolean canExecute(AgentRuntimeEntry entry,
                                     Character agent,
                                     AgentNavigationGraph.Edge edge,
                                     long nowMs) {
        return entry != null
                && canUseActivePath(agent, edge)
                && AgentMovementSkillStateRuntime.castReady(entry, nowMs);
    }

    public static int skillId(Character agent, AgentNavigationGraph.EdgeType edgeType) {
        int[] ids = switch (edgeType) {
            case TELEPORT -> TELEPORT_SKILL_IDS;
            case FLASH_JUMP -> FLASH_JUMP_SKILL_IDS;
            default -> new int[0];
        };
        if (agent == null) {
            return 0;
        }
        for (int id : ids) {
            if (agent.getSkillLevel(id) > 0) {
                return id;
            }
        }
        return 0;
    }

    public static int mpCost(Character agent, AgentNavigationGraph.EdgeType edgeType) {
        int skillId = skillId(agent, edgeType);
        if (skillId == 0) {
            return Integer.MAX_VALUE;
        }
        Skill skill = SkillFactory.getSkill(skillId);
        int level = agent.getSkillLevel(skillId);
        return skill == null || level <= 0 ? Integer.MAX_VALUE : Math.max(0, skill.getEffect(level).getMpCon());
    }

    private static boolean baseEligibility(Character agent, AgentNavigationGraph.Edge edge) {
        return baseEligibility(agent, edge.type);
    }

    private static server.agents.capabilities.movement.AgentMovementSkillMode mode(
            AgentNavigationGraph.EdgeType edgeType) {
        return switch (edgeType) {
            case TELEPORT -> AgentMovementSkillConfig.TELEPORT_MODE;
            case FLASH_JUMP -> AgentMovementSkillConfig.FLASH_JUMP_MODE;
            default -> throw new IllegalArgumentException("Not a movement-skill edge: " + edgeType);
        };
    }

    private static boolean baseEligibility(Character agent, AgentNavigationGraph.EdgeType edgeType) {
        return agent != null
                && agent.getMap() != null
                && !FieldLimit.MOVEMENTSKILLS.check(agent.getMap().getFieldLimit())
                && skillId(agent, edgeType) != 0;
    }

    private static boolean activeEligibility(Character agent, AgentNavigationGraph.EdgeType edgeType) {
        return baseEligibility(agent, edgeType)
                && affordableWithReserve(agent, edgeType);
    }

    private static boolean affordableWithReserve(Character agent, AgentNavigationGraph.Edge edge) {
        return affordableWithReserve(agent, edge.type);
    }

    private static boolean affordableWithReserve(Character agent,
                                                 AgentNavigationGraph.EdgeType edgeType) {
        int cost = mpCost(agent, edgeType);
        if (cost == Integer.MAX_VALUE || agent.getMaxMp() <= 0) {
            return false;
        }
        int reserve = (int) Math.ceil(agent.getMaxMp()
                * Math.clamp(AgentMovementSkillConfig.MIN_MP_RESERVE_PERCENT, 0, 100) / 100.0);
        return agent.getMp() - cost >= reserve;
    }
}
