package server.agents.capabilities.partyquest.lobby;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.session.AgentActivityPhase;
import server.agents.runtime.activity.session.AgentActivitySessionSnapshot;

/** Small adapter that makes observation fixtures enter through the production queue cadence. */
public final class AgentPartyQuestTestQueueRuntime {
    private static final long REPLACEMENT_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lobby.AgentPartyQuestTestQueueRuntime.REPLACEMENT_DELAY_MS");
    @FunctionalInterface
    public interface Admission {
        void admit(Character agent, long nowMs);
    }

    private AgentPartyQuestTestQueueRuntime() { }

    public static long replacementDelayMs() {
        return Math.max(1_000L, REPLACEMENT_DELAY_MS);
    }

    public static AgentActivityAdmissionResult enqueue(
            AgentPartyQuestLobbyProfile profile, AgentRuntimeEntry entry, Character agent,
            int agentCohortSize, long nowMs, Admission admission) {
        return AgentPartyQuestQueueRuntime.requestEntry(
                profile, entry, agent, profile.questKey(), agentCohortSize, 1, nowMs,
                (ignoredEntry, candidate, ignoredScenario, ignoredSize, ignoredRuns, admittedAtMs) -> {
                    try {
                        admission.admit(candidate, admittedAtMs);
                        AgentPartyQuestEngagement engagement =
                                AgentPartyQuestEngagementRegistry.forMember(candidate.getId());
                        if (engagement == null) {
                            return AgentActivityAdmissionResult.rejected(
                                    "test queue admission did not retain the engagement");
                        }
                        return AgentActivityAdmissionResult.accepted(
                                new AgentActivitySessionSnapshot(
                                        AgentActivityKind.PARTY_QUEST, AgentActivityPhase.ACTIVE,
                                        engagement.engagementId(), engagement.engagementId(),
                                        profile.questKey() + "-test-lobby",
                                        Integer.toString(candidate.getId()),
                                        engagement.startedAtMs(), ""));
                    } catch (RuntimeException failure) {
                        return AgentActivityAdmissionResult.rejected(
                                "test queue admission failed: " + failure.getMessage());
                    }
                });
    }
}
