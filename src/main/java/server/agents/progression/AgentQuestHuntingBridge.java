package server.agents.progression;

import client.Character;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;
import server.agents.runtime.hunting.AgentHuntingVisitRuntime;

import java.util.Set;

/** Questing adapter into Hunting; selection and completion remain owned by Questing. */
final class AgentQuestHuntingBridge {
    private AgentQuestHuntingBridge() {
    }

    static void engage(
            AgentRuntimeEntry entry,
            Character agent,
            PrimitiveCapabilityGateway gateway,
            String visitId,
            AgentHuntingVisitRequest.Purpose purpose,
            Set<Integer> preferredMobIds,
            Set<Integer> incidentalMobIds,
            long nowMs) {
        AgentHuntingVisitRuntime.engage(entry, agent, gateway,
                new AgentHuntingVisitRequest(visitId, AgentActivityKind.QUESTING,
                        purpose, agent.getMapId(), preferredMobIds, incidentalMobIds), nowMs);
    }
}
