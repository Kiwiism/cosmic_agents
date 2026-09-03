package server.agents.capabilities.partyquest.opq;

import client.Character;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestDirectedAdmissionRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;

/** Director-facing OPQ admission through the shared unpartied queue and lobby contract. */
public final class AgentOpqLobbyAdmissionRuntime {
    private static final AgentPartyQuestDirectedAdmissionRuntime RUNTIME =
            new AgentPartyQuestDirectedAdmissionRuntime(
                    AgentOpqLobbyProfile.profile(), AgentOpqDefinition.PARTY_SIZE,
                    AgentOpqDefinition.PARTY_SIZE,
                    (engagement, lobby, operator, leader, members, seed, nowMs) -> {
                        AgentOpqAdmissionService.AdmissionResult result =
                                AgentOpqAdmissionService.admitFromLobby(
                                        engagement, lobby, operator, leader, members, seed, nowMs,
                                        AgentRuntimeRegistry.findByAgentCharacterId(leader.getId()) != null
                                                ? AgentOpqSession.Mode.AUTONOMOUS
                                                : AgentOpqSession.Mode.HUMAN_ASSISTED);
                        return new AgentPartyQuestDirectedAdmissionRuntime.Result(
                                result.success(), result.message());
                    });

    private AgentOpqLobbyAdmissionRuntime() { }

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
