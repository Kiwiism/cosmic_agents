package server.agents.integration.cosmic;

import client.Character;
import server.agents.model.AgentPosition;
import server.agents.perception.AgentDropPerception;
import server.agents.perception.AgentMapPerception;
import server.agents.perception.AgentMobPerception;
import server.agents.perception.AgentPerceptionSnapshot;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.MapleMap;
import server.agents.integration.AgentMapGatewayRuntime;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmicAgentPerceptionSnapshotFactory {
    private static final long CACHE_WINDOW_MS = 50L;
    private static final Map<Integer, AgentPerceptionSnapshot> CACHE = new ConcurrentHashMap<>();
    private CosmicAgentPerceptionSnapshotFactory() {
    }

    public static AgentPerceptionSnapshot capture(Character agent, long nowMs) {
        MapleMap map = agent == null ? null : agent.getMap();
        if (map == null) {
            return AgentPerceptionSnapshot.unavailable();
        }
        AgentPerceptionSnapshot cached = CACHE.get(map.getId());
        if (cached != null && nowMs - cached.observedAtMs() >= 0
                && nowMs - cached.observedAtMs() <= CACHE_WINDOW_MS) {
            return cached;
        }
        List<AgentMobPerception> mobs = AgentMapPerception.monsters(map).stream()
                .map(CosmicAgentPerceptionSnapshotFactory::mob)
                .toList();
        List<AgentDropPerception> drops = AgentMapPerception.items(map).stream()
                .map(CosmicAgentPerceptionSnapshotFactory::drop)
                .toList();
        int realPlayers = AgentMapGatewayRuntime.map().isObservedByPlayer(map) ? 1 : 0;
        AgentPerceptionSnapshot captured = new AgentPerceptionSnapshot(map.getId(), nowMs, mobs, drops,
                realPlayers);
        CACHE.put(map.getId(), captured);
        return captured;
    }

    private static AgentMobPerception mob(Monster mob) {
        Point position = mob.getPosition();
        return new AgentMobPerception(mob.getObjectId(), mob.getId(),
                new AgentPosition(position.x, position.y), mob.getHp(), mob.isAlive());
    }

    private static AgentDropPerception drop(MapItem drop) {
        Point position = drop.getPosition();
        return new AgentDropPerception(drop.getObjectId(), drop.getItemId(), drop.getMeso(),
                drop.getOwnerId(), new AgentPosition(position.x, position.y));
    }
}
