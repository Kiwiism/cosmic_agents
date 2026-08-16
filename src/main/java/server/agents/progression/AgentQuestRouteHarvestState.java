package server.agents.progression;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded state for collecting relevant objectives before leaving the current map. */
final class AgentQuestRouteHarvestState {
    static final AgentCapabilityStateKey<AgentQuestRouteHarvestState> STATE_KEY =
            new AgentCapabilityStateKey<>("progression.quest-route-harvest",
                    AgentQuestRouteHarvestState.class, AgentQuestRouteHarvestState::new);

    private String activeKey = "";
    private String exhaustedKey = "";
    private long expiresAtMs;
    private int startProgress;

    synchronized Decision evaluate(
            String huntKey,
            String debtScope,
            int mapId,
            int progress,
            boolean liveTargets,
            long nowMs,
            long maximumDurationMs,
            int maximumProgressUnits) {
        String key = huntKey + "|" + debtScope + "|" + mapId;
        if (!activeKey.equals(key)) {
            activeKey = "";
        }
        if (!activeKey.isEmpty()) {
            if (!liveTargets || nowMs >= expiresAtMs
                    || progress - startProgress >= maximumProgressUnits) {
                activeKey = "";
                exhaustedKey = key;
                return Decision.FINISHED;
            }
            return Decision.HARVEST;
        }
        if (!liveTargets || exhaustedKey.equals(key)) {
            return Decision.SKIP;
        }
        activeKey = key;
        expiresAtMs = nowMs + maximumDurationMs;
        startProgress = progress;
        return Decision.STARTED;
    }

    synchronized void clear() {
        activeKey = "";
        exhaustedKey = "";
        expiresAtMs = 0L;
        startProgress = 0;
    }

    enum Decision {
        STARTED,
        HARVEST,
        FINISHED,
        SKIP
    }
}
