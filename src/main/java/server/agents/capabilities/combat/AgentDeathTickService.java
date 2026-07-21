package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.recovery.AgentRespawnHealthPolicy;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;
import server.maps.Portal;
import server.agents.events.AgentEventPriority;
import server.agents.operations.events.AgentLifeStateChangedEvent;
import server.agents.operations.events.AgentOperationalEventPublisher;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public final class AgentDeathTickService {
    public record RespawnHooks(ChangeMapToTown changeMapToTown,
                               TeleportToPoint teleportToPoint,
                               BiConsumer<AgentRuntimeEntry, Character> resetEntryStateAfterTeleport,
                               BiConsumer<AgentRuntimeEntry, Character> broadcastMovement) {
    }

    @FunctionalInterface
    public interface ChangeMapToTown {
        void change(Character agent, MapleMap townMap, Point spawnPosition);
    }

    @FunctionalInterface
    public interface TeleportToPoint {
        void teleport(AgentRuntimeEntry entry, Character agent, Point point);
    }

    private AgentDeathTickService() {
    }

    public static boolean handleDeadTick(AgentRuntimeEntry entry,
                                         Character agent,
                                         BooleanSupplier shouldEnterDeadState,
                                         BiConsumer<AgentRuntimeEntry, Character> enterDeadState,
                                         Runnable respawnAction,
                                         long nowMs) {
        if (shouldEnterDeadState.getAsBoolean()) {
            enterDeadState.accept(entry, agent);
        }
        if (!AgentDeathStateRuntime.isDead(entry)) {
            return false;
        }
        if (AgentDeathStateRuntime.isRespawnDue(entry, nowMs)) {
            respawnAction.run();
        }
        return true;
    }

    public static void respawnAtNearestTown(AgentRuntimeEntry entry,
                                            Character agent,
                                            int restoredHpPercent,
                                            RespawnHooks hooks) {
        AgentDeathStateRuntime.clear(entry);
        agent.updateHp(AgentRespawnHealthPolicy.restoredHp(agent.getMaxHp(), restoredHpPercent));

        MapleMap currentMap = agent.getMap();
        MapleMap townMap = currentMap == null ? null : currentMap.getReturnMap();
        if (townMap == null) {
            townMap = currentMap;
        }
        Point spawnPosition = townSpawnPosition(townMap, agent.getPosition());
        if (townMap != null && (currentMap == null || currentMap.getId() != townMap.getId())) {
            hooks.changeMapToTown().change(agent, townMap, spawnPosition);
        }
        hooks.teleportToPoint().teleport(entry, agent, spawnPosition);
        hooks.resetEntryStateAfterTeleport().accept(entry, agent);
        hooks.broadcastMovement().accept(entry, agent);
        agent.changeFaceExpression(AgentEmote.GLARE.getValue());
        AgentOperationalEventPublisher.publish(entry,
                objectiveId -> new AgentLifeStateChangedEvent(
                        agent.getId(), System.currentTimeMillis(), "DEAD", "ALIVE",
                        agent.getMapId(), true, objectiveId),
                AgentEventPriority.IMPORTANT);
    }

    private static Point townSpawnPosition(MapleMap townMap, Point fallback) {
        if (townMap != null) {
            Portal portal = townMap.getPortal(0);
            if (portal != null && portal.getPosition() != null) {
                return new Point(portal.getPosition());
            }
        }
        return fallback == null ? new Point() : new Point(fallback);
    }
}
