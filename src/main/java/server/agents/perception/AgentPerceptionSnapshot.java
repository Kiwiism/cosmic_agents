package server.agents.perception;

import java.util.List;

/** Immutable shared read model for policy; no live Monster, MapItem, or MapleMap references. */
public record AgentPerceptionSnapshot(
        int mapId,
        long observedAtMs,
        List<AgentMobPerception> mobs,
        List<AgentDropPerception> drops,
        int realPlayerObservers) {

    public AgentPerceptionSnapshot {
        if (mapId < 0 || observedAtMs < 0 || mobs == null || drops == null || realPlayerObservers < 0) {
            throw new IllegalArgumentException("Valid perception map, timestamp, collections, and observer count are required");
        }
        mobs = List.copyOf(mobs);
        drops = List.copyOf(drops);
    }

    public static AgentPerceptionSnapshot unavailable() {
        return new AgentPerceptionSnapshot(0, 0, List.of(), List.of(), 0);
    }
}
