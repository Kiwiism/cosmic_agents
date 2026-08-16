package server.agents.runtime.field;

import server.agents.field.AgentFieldIntent;
import server.agents.field.AgentFieldObservationState;

/** Caller-authored local field participation policy; it does not choose the next map. */
public record AgentFieldVisitRequest(
        int mapId,
        AgentFieldIntent intent,
        boolean acceptingQuestVisitors,
        int maximumParticipants,
        boolean restAllowed,
        AgentFieldObservationState.NarrationLevel narrationLevel) {
    public AgentFieldVisitRequest {
        narrationLevel = narrationLevel == null
                ? AgentFieldObservationState.NarrationLevel.SUMMARY : narrationLevel;
        if (mapId <= 0 || intent == null || maximumParticipants < 1 || maximumParticipants > 12) {
            throw new IllegalArgumentException("valid field visit map, intent, and capacity are required");
        }
    }
}
