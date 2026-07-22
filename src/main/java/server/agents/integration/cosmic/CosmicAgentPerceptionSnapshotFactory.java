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
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.perception.AgentPeerPerception;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;

import java.awt.Point;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmicAgentPerceptionSnapshotFactory {
    private static final long CACHE_WINDOW_MS = 50L;
    private static final Map<MapleMap, AgentPerceptionSnapshot> CACHE = new ConcurrentHashMap<>();
    private CosmicAgentPerceptionSnapshotFactory() {
    }

    public static AgentPerceptionSnapshot capture(Character agent, long nowMs) {
        MapleMap map = agent == null ? null : agent.getMap();
        if (map == null) {
            return AgentPerceptionSnapshot.unavailable();
        }
        if (CACHE.size() > 512) {
            CACHE.entrySet().removeIf(entry -> nowMs - entry.getValue().observedAtMs() > 5_000L);
        }
        AgentPerceptionSnapshot cached = CACHE.get(map);
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
        int realPlayers = (int) map.getCharacters().stream()
                .filter(character -> !AgentCharacterGatewayRuntime.characters().isAgentCharacter(character))
                .count();
        List<AgentPeerPerception> peers = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .map(entry -> peer(entry, map))
                .filter(java.util.Objects::nonNull)
                .toList();
        AgentPerceptionSnapshot captured = new AgentPerceptionSnapshot(map.getId(), nowMs, mobs, drops,
                realPlayers, peers);
        CACHE.put(map, captured);
        return captured;
    }

    private static AgentPeerPerception peer(AgentRuntimeEntry entry, MapleMap map) {
        Character peer = AgentRuntimeIdentityRuntime.bot(entry);
        if (peer == null || peer.getMap() != map || peer.getPosition() == null || peer.getHp() <= 0) return null;
        Monster target = AgentGrindTargetStateRuntime.target(entry);
        return new AgentPeerPerception(peer.getId(),
                new AgentPosition(peer.getPosition().x, peer.getPosition().y),
                AgentModeStateRuntime.grinding(entry), target == null ? -1 : target.getObjectId());
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
