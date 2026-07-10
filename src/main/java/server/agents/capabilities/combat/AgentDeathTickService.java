package server.agents.capabilities.combat;

import client.Character;
import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

public final class AgentDeathTickService {
    public record RespawnHooks(ChangeMapNearLeader changeMapNearLeader,
                               GroundPointResolver groundPointResolver,
                               TeleportToPoint teleportToPoint,
                               BiConsumer<AgentRuntimeEntry, Character> resetEntryStateAfterTeleport,
                               BiConsumer<AgentRuntimeEntry, Character> broadcastMovement,
                               BiConsumer<Character, String> mapSpeaker) {
    }

    @FunctionalInterface
    public interface ChangeMapNearLeader {
        void change(Character agent, MapleMap leaderMap, Point leaderPosition);
    }

    @FunctionalInterface
    public interface GroundPointResolver {
        Point resolve(MapleMap map, Point point);
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

    public static void respawnNearLeader(AgentRuntimeEntry entry, Character agent, Character leader, RespawnHooks hooks) {
        AgentDeathStateRuntime.clear(entry);
        agent.updateHp(agent.getMaxHp());

        if (agent.getMapId() != leader.getMapId()) {
            hooks.changeMapNearLeader().change(agent, leader.getMap(), leader.getPosition());
        }
        Point leaderPosition = leader.getPosition();
        Point spawnPosition = hooks.groundPointResolver().resolve(
                agent.getMap(), new Point(leaderPosition.x, leaderPosition.y - 1));
        hooks.teleportToPoint().teleport(entry, agent, spawnPosition != null ? spawnPosition : leaderPosition);
        hooks.resetEntryStateAfterTeleport().accept(entry, agent);
        hooks.broadcastMovement().accept(entry, agent);
        hooks.mapSpeaker().accept(agent, "back!");
        agent.changeFaceExpression(AgentEmote.GLARE.getValue());
    }
}
