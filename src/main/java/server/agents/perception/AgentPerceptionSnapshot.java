package server.agents.perception;

import java.util.List;

/** Immutable shared read model for policy; no live Monster, MapItem, or MapleMap references. */
public record AgentPerceptionSnapshot(
        int mapId,
        long observedAtMs,
        List<AgentMobPerception> mobs,
        List<AgentDropPerception> drops,
        int realPlayerObservers,
        List<AgentPeerPerception> agentPeers,
        List<AgentCharacterPerception> characters) {

    public AgentPerceptionSnapshot {
        if (mapId < 0 || observedAtMs < 0 || mobs == null || drops == null || realPlayerObservers < 0
                || agentPeers == null || characters == null) {
            throw new IllegalArgumentException("Valid perception map, timestamp, collections, and observer count are required");
        }
        mobs = List.copyOf(mobs);
        drops = List.copyOf(drops);
        agentPeers = List.copyOf(agentPeers);
        characters = List.copyOf(characters);
    }

    public AgentPerceptionSnapshot(int mapId, long observedAtMs, List<AgentMobPerception> mobs,
                                   List<AgentDropPerception> drops, int realPlayerObservers,
                                   List<AgentPeerPerception> agentPeers) {
        this(mapId, observedAtMs, mobs, drops, realPlayerObservers, agentPeers, List.of());
    }

    public AgentPerceptionSnapshot(int mapId, long observedAtMs, List<AgentMobPerception> mobs,
                                   List<AgentDropPerception> drops, int realPlayerObservers) {
        this(mapId, observedAtMs, mobs, drops, realPlayerObservers, List.of(), List.of());
    }

    public static AgentPerceptionSnapshot unavailable() {
        return new AgentPerceptionSnapshot(0, 0, List.of(), List.of(), 0, List.of(), List.of());
    }
}
