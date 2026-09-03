package server.agents.capabilities.partyquest.ppq;

import client.Character;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestDirectedAdmissionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

/** Director-facing PPQ admission through the shared unpartied queue and lobby contract. */
public final class AgentPpqLobbyAdmissionRuntime {
    private static final AgentPartyQuestDirectedAdmissionRuntime RUNTIME =
            new AgentPartyQuestDirectedAdmissionRuntime(
                    AgentPpqLobbyProfile.profile(), AgentPpqDefinition.PARTY_SIZE,
                    AgentPpqDefinition.PARTY_SIZE,
                    (engagement, lobby, operator, leader, members, seed, nowMs) -> {
                        AgentPpqAdmissionService.AdmissionResult result =
                                AgentPpqAdmissionService.admitFromLobby(
                                        engagement, lobby, operator, leader, members, seed, nowMs);
                        return new AgentPartyQuestDirectedAdmissionRuntime.Result(
                                result.success(), result.message());
                    });

    private AgentPpqLobbyAdmissionRuntime() { }

    public static String blocker(Character agent, String scenarioId, int partySize, int maximumRuns) {
        return RUNTIME.blocker(agent, scenarioId, partySize, maximumRuns);
    }

    public static AgentActivityAdmissionResult requestEntry(
            AgentRuntimeEntry entry, Character agent, String scenarioId,
            int partySize, int maximumRuns, long nowMs) {
        return RUNTIME.requestEntry(entry, agent, scenarioId, partySize, maximumRuns, nowMs);
    }

    public static boolean tick(int characterId, long nowMs) {
        return RUNTIME.tick(characterId, nowMs);
    }

    public static void releaseTracking(int characterId) {
        RUNTIME.releaseTracking(characterId);
    }
}
