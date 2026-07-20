package server.agents.capabilities.recovery;

import client.Character;
import config.YamlConfig;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.combat.AgentDeathStateRuntime;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;

import java.awt.Point;
import java.util.function.BiConsumer;

/**
 * Applies the generic Agent death-recovery policy without coupling it to a plan or region.
 */
public final class AgentRespawnCoordinator {
    private static final long EVENT_REVIVE_RETRY_MS = 1_000L;

    enum EventReviveResult {
        NO_EVENT,
        STANDARD_RESPAWN,
        HANDLED_BY_EVENT
    }

    record RecoveryConfig(boolean reviveNearFollowTarget, boolean fullHpInTown) {
    }

    record RecoveryHooks(EventReviver eventReviver,
                         BiConsumer<Character, Integer> standardRespawn,
                         SpawnPointResolver spawnPointResolver,
                         TeleportToPoint teleportToPoint,
                         BiConsumer<AgentRuntimeEntry, Character> resetEntryState,
                         BiConsumer<AgentRuntimeEntry, Character> broadcastMovement,
                         BiConsumer<Character, String> mapSpeaker) {
    }

    @FunctionalInterface
    interface EventReviver {
        EventReviveResult revive(Character agent);
    }

    @FunctionalInterface
    interface SpawnPointResolver {
        Point resolve(MapleMap map, Point referencePosition);
    }

    @FunctionalInterface
    interface TeleportToPoint {
        void teleport(AgentRuntimeEntry entry, Character agent, Point point);
    }

    private AgentRespawnCoordinator() {
    }

    public static boolean recover(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return recover(
                entry,
                agent,
                nowMs,
                new RecoveryConfig(
                        YamlConfig.config.server.AGENT_DEATH_REVIVE_NEAR_FOLLOW_TARGET,
                        YamlConfig.config.server.AGENT_DEATH_FULL_HP_IN_TOWN),
                new RecoveryHooks(
                        AgentRespawnCoordinator::attemptEventRevive,
                        Character::respawn,
                        AgentRespawnCoordinator::resolvePlayerSpawn,
                        AgentMovementPoseService::teleportTo,
                        (respawnEntry, ignoredAgent) -> AgentMovementStateResetService.resetEntryState(respawnEntry),
                        (respawnEntry, ignoredAgent) -> AgentMovementBroadcastService.broadcastMovement(respawnEntry),
                        AgentReplyRuntime::sayMapNow));
    }

    static boolean recover(AgentRuntimeEntry entry,
                           Character agent,
                           long nowMs,
                           RecoveryConfig config,
                           RecoveryHooks hooks) {
        if (entry == null || agent == null || agent.getMap() == null) {
            deferEventRecovery(entry, nowMs);
            return false;
        }

        MapleMap deathMap = agent.getMap();
        EventReviveResult eventResult = hooks.eventReviver().revive(agent);
        if (eventResult == EventReviveResult.HANDLED_BY_EVENT) {
            if (!agent.isAlive() || agent.getMap() == null) {
                deferEventRecovery(entry, nowMs);
                return false;
            }
            finishRecovery(entry, agent, nowMs, config, hooks);
            return true;
        }

        RecoveryDestination destination = eventResult == EventReviveResult.NO_EVENT
                ? selectNormalDestination(entry, agent, deathMap, config)
                : new RecoveryDestination(deathMap.getReturnMap(), null);
        if (destination.map() == null) {
            deferEventRecovery(entry, nowMs);
            return false;
        }

        hooks.standardRespawn().accept(agent, destination.map().getId());
        if (!agent.isAlive() || agent.getMap() == null) {
            deferEventRecovery(entry, nowMs);
            return false;
        }

        if (destination.referencePosition() != null
                && agent.getMapId() == destination.map().getId()) {
            Point spawnPoint = hooks.spawnPointResolver().resolve(
                    destination.map(), destination.referencePosition());
            if (spawnPoint != null) {
                hooks.teleportToPoint().teleport(entry, agent, spawnPoint);
            }
        }

        finishRecovery(entry, agent, nowMs, config, hooks);
        return true;
    }

    private static RecoveryDestination selectNormalDestination(AgentRuntimeEntry entry,
                                                               Character agent,
                                                               MapleMap deathMap,
                                                               RecoveryConfig config) {
        Character followTarget = AgentRelationshipRuntime.followTarget(entry);
        if (config.reviveNearFollowTarget()
                && AgentModeStateRuntime.following(entry)
                && validFollowTarget(agent, followTarget)) {
            return new RecoveryDestination(followTarget.getMap(), followTarget.getPosition());
        }
        return new RecoveryDestination(deathMap.getReturnMap(), null);
    }

    private static boolean validFollowTarget(Character deadAgent, Character followTarget) {
        return followTarget != null
                && followTarget != deadAgent
                && followTarget.isLoggedinWorld()
                && followTarget.isAlive()
                && followTarget.getMap() != null;
    }

    private static EventReviveResult attemptEventRevive(Character agent) {
        EventInstanceManager event = agent.getEventInstance();
        if (event == null) {
            return EventReviveResult.NO_EVENT;
        }
        return event.revivePlayer(agent)
                ? EventReviveResult.STANDARD_RESPAWN
                : EventReviveResult.HANDLED_BY_EVENT;
    }

    private static Point resolvePlayerSpawn(MapleMap map, Point referencePosition) {
        Portal portal = map.findClosestPlayerSpawnpoint(referencePosition);
        if (portal == null) {
            portal = map.getPortal(0);
        }
        return portal == null ? null : portal.getPosition();
    }

    private static void finishRecovery(AgentRuntimeEntry entry,
                                       Character agent,
                                       long nowMs,
                                       RecoveryConfig config,
                                       RecoveryHooks hooks) {
        long deadSinceMs = AgentDeathStateRuntime.deadSinceMs(entry);
        if (deadSinceMs > 0L && nowMs > deadSinceMs) {
            AgentCapabilityRuntime.extendActiveDeadlines(entry, nowMs - deadSinceMs);
        }
        AgentDeathStateRuntime.clear(entry);

        if (config.fullHpInTown() && agent.getMap().isTown()) {
            agent.updateHp(agent.getMaxHp());
        }

        // The active plan/objective and capability memory are intentionally retained. Only
        // transient pose, navigation, target, and cooldown state is reset for a clean replan.
        hooks.resetEntryState().accept(entry, agent);
        hooks.broadcastMovement().accept(entry, agent);
        hooks.mapSpeaker().accept(agent, "back!");
        agent.changeFaceExpression(AgentEmote.GLARE.getValue());
    }

    private static void deferEventRecovery(AgentRuntimeEntry entry, long nowMs) {
        if (entry != null) {
            AgentDeathStateRuntime.deferRespawnUntil(entry, nowMs + EVENT_REVIVE_RETRY_MS);
        }
    }

    private record RecoveryDestination(MapleMap map, Point referencePosition) {
    }
}
