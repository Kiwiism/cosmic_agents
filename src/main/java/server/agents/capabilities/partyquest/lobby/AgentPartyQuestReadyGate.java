package server.agents.capabilities.partyquest.lobby;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Resets a PQ entry countdown whenever the authoritative lobby roster changes. */
public final class AgentPartyQuestReadyGate {
    private static final long READY_MINIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestReadyGate.READY_MINIMUM_MS");
    private static final long READY_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestReadyGate.READY_MAXIMUM_MS");
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    private AgentPartyQuestReadyGate() { }

    public static boolean ready(String lobbyId, long rosterRevision, long seed, long nowMs) {
        if (lobbyId == null || lobbyId.isBlank() || rosterRevision < 0L || nowMs < 0L) return false;
        State state = STATES.compute(lobbyId, (ignored, old) -> {
            if (old != null && old.rosterRevision == rosterRevision) return old;
            long min = Math.max(0L, Math.min(READY_MINIMUM_MS, READY_MAXIMUM_MS));
            long max = Math.max(min, Math.max(READY_MINIMUM_MS, READY_MAXIMUM_MS));
            long delay = min == max ? min : min + Math.floorMod(
                    seed ^ rosterRevision * 307L, max - min + 1L);
            return new State(rosterRevision, nowMs + delay);
        });
        return nowMs >= state.readyAtMs;
    }

    public static void release(String lobbyId) {
        if (lobbyId != null) STATES.remove(lobbyId);
    }

    private record State(long rosterRevision, long readyAtMs) { }
}
