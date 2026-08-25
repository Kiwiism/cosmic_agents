package server.agents.capabilities.partyquest;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

/** Lifecycle-only contract. Every PQ retains its own session, coordinator, and tuning. */
public interface AgentPartyQuestSystem {
    AgentPartyQuestDefinition definition();

    boolean sessionActive(int characterId);

    boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs);

    boolean requestStop(int characterId, String reason, long nowMs);

    void forceStop(int characterId, String reason, long nowMs);

    void runtimeRemoved(int characterId, long nowMs);

    AgentPartyQuestSessionView sessionView(int characterId);

    boolean pause(int characterId);

    boolean resumeExact(int characterId, String sessionId, long nowMs);

    AgentActivityAdmissionResult requestEntry(
            AgentRuntimeEntry entry, Character agent, String scenarioId,
            int partySize, int maximumRuns, long nowMs);

    String entryBlocker(Character agent, String scenarioId, int partySize, int maximumRuns);
}
