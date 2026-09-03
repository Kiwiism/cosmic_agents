package server.agents.capabilities.partyquest.lpq;

import client.Character;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestDirectedAdmissionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

/** Director-facing LPQ admission through the shared unpartied queue and lobby contract. */
public final class AgentLpqLobbyAdmissionRuntime {
    private static final AgentPartyQuestDirectedAdmissionRuntime RUNTIME =
            new AgentPartyQuestDirectedAdmissionRuntime(
                    AgentLpqLobbyProfile.profile(),
                    AgentLpqDefinition.RECOMMENDED_PARTY_SIZE,
                    AgentLpqDefinition.RECOMMENDED_PARTY_SIZE,
                    (engagement, lobby, operator, leader, members, seed, nowMs) -> {
                        AgentLpqAdmissionService.AdmissionResult result =
                                AgentLpqAdmissionService.admitFromLobby(
                                        engagement, lobby, operator, leader, members, seed, nowMs,
                                        AgentLpqSession.Mode.PRODUCTION, 0,
                                        AgentLpqSession.HumanRolePreference.DEFAULT);
                        return new AgentPartyQuestDirectedAdmissionRuntime.Result(
                                result.success(), result.message());
                    });

    private AgentLpqLobbyAdmissionRuntime() { }

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
