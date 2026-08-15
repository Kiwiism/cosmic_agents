package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.awt.Point;
import java.util.LinkedHashSet;

/** Selects physics-probed jump-shot arcs without owning attack execution or navigation. */
public final class AgentRangedKitingPolicy {
    @FunctionalInterface
    public interface GroundedPolicy {
        boolean grounded(AgentRuntimeEntry entry);
    }

    @FunctionalInterface
    public interface JumpLandingSelector {
        Point select(AgentRuntimeEntry entry,
                     Character agent,
                     Point origin,
                     int direction,
                     int minimumTravelX,
                     int yTolerance);
    }

    public record Hooks(GroundedPolicy groundedPolicy,
                        JumpLandingSelector jumpLandingSelector) {
    }

    private static final String TUNING_PREFIX =
            "server.agents.capabilities.combat.AgentRangedKitingPolicy.";
    private static final int CROWD_JUMP_MIN_MOBS = tuningInt("CROWD_JUMP_MIN_MOBS");
    private static final int CROWD_JUMP_RADIUS_X = tuningInt("CROWD_JUMP_RADIUS_X");
    private static final int CROWD_JUMP_RADIUS_Y = tuningInt("CROWD_JUMP_RADIUS_Y");
    private static final int CROWD_JUMP_MIN_TRAVEL_X = tuningInt("CROWD_JUMP_MIN_TRAVEL_X");
    private static final int CROWD_JUMP_MIN_DISTANCE_GAIN_X =
            tuningInt("CROWD_JUMP_MIN_DISTANCE_GAIN_X");
    private static final int SAME_PLATFORM_Y_TOLERANCE =
            tuningInt("SAME_PLATFORM_Y_TOLERANCE");

    private AgentRangedKitingPolicy() {
    }

    public static Point selectCrowdJumpTarget(AgentRuntimeEntry entry,
                                              Character agent,
                                              WeaponType weaponType,
                                              Point agentPosition,
                                              Point targetPosition,
                                              Hooks hooks) {
        if (entry == null || agent == null || agent.getMap() == null
                || agentPosition == null || targetPosition == null
                || !AgentCombatRangePolicy.supportsMobileJumpAttack(weaponType)
                || hooks == null || !hooks.groundedPolicy().grounded(entry)
                || crowdedMobCount(entry, agent, agentPosition) < CROWD_JUMP_MIN_MOBS) {
            return null;
        }
        int projectileRange = AgentProjectileHitbox.CLIENT_PROJECTILE_BASE_RANGE
                + AgentProjectileHitbox.passiveProjectileRangeBonus(agent);
        if (Math.abs(targetPosition.x - agentPosition.x) > projectileRange
                || Math.abs(targetPosition.y - agentPosition.y)
                > AgentCombatConfig.cfg.RANGED_DEGENERATE_RANGE_Y * 2) {
            return null;
        }

        int awayFromTarget = Integer.compare(agentPosition.x, targetPosition.x);
        int openSide = AgentAttackExecutionProvider.pickRetreatDirection(
                agent, agentPosition, targetPosition);
        LinkedHashSet<Integer> directions = new LinkedHashSet<>();
        if (awayFromTarget != 0) {
            directions.add(awayFromTarget);
        }
        if (openSide != 0) {
            directions.add(openSide);
        }
        if (awayFromTarget != 0) {
            directions.add(-awayFromTarget);
        }

        for (int direction : directions) {
            Point landing = hooks.jumpLandingSelector().select(
                    entry,
                    agent,
                    agentPosition,
                    direction,
                    CROWD_JUMP_MIN_TRAVEL_X,
                    SAME_PLATFORM_Y_TOLERANCE);
            if (landing == null) {
                continue;
            }
            int currentDistance = Math.abs(agentPosition.x - targetPosition.x);
            int landingDistance = Math.abs(landing.x - targetPosition.x);
            if (landingDistance >= currentDistance + CROWD_JUMP_MIN_DISTANCE_GAIN_X) {
                return landing;
            }
        }
        return null;
    }

    static int crowdedMobCount(AgentRuntimeEntry entry,
                               Character agent,
                               Point agentPosition) {
        int count = 0;
        for (Monster monster : AgentMapPerception.monsters(agent.getMap())) {
            if (!AgentCombatTargetEligibilityPolicy.isHostileLivingMonster(monster)
                    || monster.getPosition() == null
                    || !AgentCombatObjectiveTargetStateRuntime.allows(entry, monster.getId())) {
                continue;
            }
            Point position = monster.getPosition();
            if (Math.abs(position.x - agentPosition.x) <= CROWD_JUMP_RADIUS_X
                    && Math.abs(position.y - agentPosition.y) <= CROWD_JUMP_RADIUS_Y) {
                count++;
            }
        }
        return count;
    }

    private static int tuningInt(String suffix) {
        return config.AgentTuning.intValue(TUNING_PREFIX + suffix);
    }
}
