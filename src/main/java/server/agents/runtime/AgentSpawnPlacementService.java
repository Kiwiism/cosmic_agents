package server.agents.runtime;

import client.Character;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentSpawnPlacementService {
    private AgentSpawnPlacementService() {
    }

    public record Hooks<E extends AgentRuntimeHandle>(AgentResolver<E> agentResolver,
                                                      LeaderResolver<E> leaderResolver,
                                                      SpawnPositionResolver spawnPositionResolver,
                                                      Teleporter<E> teleporter,
                                                      MovementReset<E> movementReset,
                                                      DeathStateClear<E> deathStateClear,
                                                      MapTrackingUpdater<E> mapTrackingUpdater,
                                                      NavigationWarmup<E> navigationWarmup,
                                                      TickCadenceReset<E> tickCadenceReset,
                                                      MovementDirectionClear<E> movementDirectionClear,
                                                      MovementBroadcastInvalidation<E> movementBroadcastInvalidation,
                                                      MovementBroadcaster<E> movementBroadcaster,
                                                      PartyHpUpdater partyHpUpdater,
                                                      LeaderPartyJoiner leaderPartyJoiner) {
    }

    @FunctionalInterface
    public interface AgentResolver<E extends AgentRuntimeHandle> {
        Character agent(E entry);
    }

    @FunctionalInterface
    public interface LeaderResolver<E extends AgentRuntimeHandle> {
        Character leader(E entry);
    }

    @FunctionalInterface
    public interface SpawnPositionResolver {
        Point resolve(MapleMap map, Point desiredPosition);
    }

    @FunctionalInterface
    public interface Teleporter<E extends AgentRuntimeHandle> {
        void teleport(E entry, Character agent, Point position);
    }

    @FunctionalInterface
    public interface MovementReset<E extends AgentRuntimeHandle> {
        void reset(E entry);
    }

    @FunctionalInterface
    public interface DeathStateClear<E extends AgentRuntimeHandle> {
        void clear(E entry);
    }

    @FunctionalInterface
    public interface MapTrackingUpdater<E extends AgentRuntimeHandle> {
        void update(E entry, MapleMap map, int mapId);
    }

    @FunctionalInterface
    public interface NavigationWarmup<E extends AgentRuntimeHandle> {
        void warm(E entry, MapleMap map);
    }

    @FunctionalInterface
    public interface TickCadenceReset<E extends AgentRuntimeHandle> {
        void reset(E entry);
    }

    @FunctionalInterface
    public interface MovementDirectionClear<E extends AgentRuntimeHandle> {
        void clear(E entry);
    }

    @FunctionalInterface
    public interface MovementBroadcastInvalidation<E extends AgentRuntimeHandle> {
        void invalidate(E entry);
    }

    @FunctionalInterface
    public interface MovementBroadcaster<E extends AgentRuntimeHandle> {
        void broadcast(E entry);
    }

    @FunctionalInterface
    public interface PartyHpUpdater {
        void update(Character agent);
    }

    @FunctionalInterface
    public interface LeaderPartyJoiner {
        void join(Character leader, Character agent);
    }

    public static <E extends AgentRuntimeHandle> void placeSpawnedOnlineAgent(E entry,
                                               Character agent,
                                               MapleMap spawnMap,
                                               Point spawnPosition,
                                               Hooks<E> hooks) {
        if (entry == null) {
            agent.setPosition(spawnPosition);
            agent.broadcastStance();
            hooks.partyHpUpdater().update(agent);
            return;
        }

        resetSpawnRuntime(entry, agent, spawnMap, spawnPosition, hooks);
        hooks.partyHpUpdater().update(agent);
    }

    public static <E extends AgentRuntimeHandle> void normalizeSpawnedAgent(E entry, Hooks<E> hooks) {
        Character agent = hooks.agentResolver().agent(entry);
        Point spawnPosition = hooks.spawnPositionResolver().resolve(agent.getMap(), agent.getPosition());
        if (agent.getHp() <= 0) {
            agent.updateHp(Math.max(1, agent.getCurrentMaxHp()));
        }

        resetSpawnRuntime(entry, agent, agent.getMap(), spawnPosition != null ? spawnPosition : agent.getPosition(), hooks);
        Character leader = hooks.leaderResolver().leader(entry);
        if (leader != null) {
            hooks.leaderPartyJoiner().join(leader, agent);
        }
    }

    private static <E extends AgentRuntimeHandle> void resetSpawnRuntime(E entry,
                                          Character agent,
                                          MapleMap spawnMap,
                                          Point spawnPosition,
                                          Hooks<E> hooks) {
        hooks.teleporter().teleport(entry, agent, spawnPosition);
        hooks.movementReset().reset(entry);
        hooks.deathStateClear().clear(entry);
        int spawnMapId = spawnMap != null ? spawnMap.getId() : agent.getMapId();
        hooks.mapTrackingUpdater().update(entry, spawnMap, spawnMapId);
        if (spawnMap != null && spawnMap.getFootholds() != null) {
            hooks.navigationWarmup().warm(entry, spawnMap);
        }
        hooks.tickCadenceReset().reset(entry);
        hooks.movementDirectionClear().clear(entry);
        hooks.movementBroadcastInvalidation().invalidate(entry);
        hooks.movementBroadcaster().broadcast(entry);
    }
}
