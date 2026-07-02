package server.agents.runtime;

import client.Character;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;

public final class AgentSpawnPlacementService {
    private AgentSpawnPlacementService() {
    }

    public record Hooks(SpawnPositionResolver spawnPositionResolver,
                        Teleporter teleporter,
                        MovementReset movementReset,
                        DeathStateClear deathStateClear,
                        MapTrackingUpdater mapTrackingUpdater,
                        NavigationWarmup navigationWarmup,
                        TickCadenceReset tickCadenceReset,
                        MovementDirectionClear movementDirectionClear,
                        MovementBroadcastInvalidation movementBroadcastInvalidation,
                        MovementBroadcaster movementBroadcaster,
                        PartyHpUpdater partyHpUpdater,
                        LeaderPartyJoiner leaderPartyJoiner) {
    }

    @FunctionalInterface
    public interface SpawnPositionResolver {
        Point resolve(MapleMap map, Point desiredPosition);
    }

    @FunctionalInterface
    public interface Teleporter {
        void teleport(BotEntry entry, Character agent, Point position);
    }

    @FunctionalInterface
    public interface MovementReset {
        void reset(BotEntry entry);
    }

    @FunctionalInterface
    public interface DeathStateClear {
        void clear(BotEntry entry);
    }

    @FunctionalInterface
    public interface MapTrackingUpdater {
        void update(BotEntry entry, MapleMap map, int mapId);
    }

    @FunctionalInterface
    public interface NavigationWarmup {
        void warm(BotEntry entry, MapleMap map);
    }

    @FunctionalInterface
    public interface TickCadenceReset {
        void reset(BotEntry entry);
    }

    @FunctionalInterface
    public interface MovementDirectionClear {
        void clear(BotEntry entry);
    }

    @FunctionalInterface
    public interface MovementBroadcastInvalidation {
        void invalidate(BotEntry entry);
    }

    @FunctionalInterface
    public interface MovementBroadcaster {
        void broadcast(BotEntry entry);
    }

    @FunctionalInterface
    public interface PartyHpUpdater {
        void update(Character agent);
    }

    @FunctionalInterface
    public interface LeaderPartyJoiner {
        void join(Character leader, Character agent);
    }

    public static void placeSpawnedOnlineAgent(BotEntry entry,
                                               Character agent,
                                               MapleMap spawnMap,
                                               Point spawnPosition,
                                               Hooks hooks) {
        if (entry == null) {
            agent.setPosition(spawnPosition);
            agent.broadcastStance();
            hooks.partyHpUpdater().update(agent);
            return;
        }

        resetSpawnRuntime(entry, agent, spawnMap, spawnPosition, hooks);
        hooks.partyHpUpdater().update(agent);
    }

    public static void normalizeSpawnedAgent(BotEntry entry, Hooks hooks) {
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        Point spawnPosition = hooks.spawnPositionResolver().resolve(agent.getMap(), agent.getPosition());
        if (agent.getHp() <= 0) {
            agent.updateHp(Math.max(1, agent.getCurrentMaxHp()));
        }

        resetSpawnRuntime(entry, agent, agent.getMap(), spawnPosition != null ? spawnPosition : agent.getPosition(), hooks);
        Character leader = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (leader != null) {
            hooks.leaderPartyJoiner().join(leader, agent);
        }
    }

    private static void resetSpawnRuntime(BotEntry entry,
                                          Character agent,
                                          MapleMap spawnMap,
                                          Point spawnPosition,
                                          Hooks hooks) {
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
