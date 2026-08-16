package server.agents.runtime.field;

import server.agents.field.AgentFieldIntent;
import server.agents.field.AgentFieldObservationState;

import java.util.Map;
import java.util.Set;

/** Durable field intent; live targets, routes, and map objects are deliberately excluded. */
record AgentFieldCheckpoint(
        int schemaVersion,
        int characterId,
        int mapId,
        String requestId,
        String callerId,
        AgentFieldIntent.Type intentType,
        String objectiveId,
        Set<Integer> requiredMobIds,
        Map<Integer, Integer> requiredKills,
        boolean temporaryVisitor,
        boolean acceptingQuestVisitors,
        int maximumParticipants,
        boolean restAllowed,
        AgentFieldObservationState.NarrationLevel narrationLevel,
        AgentFieldActivityState.Phase phase,
        String exitReason,
        long remainingExitDeadlineMs,
        String restReason,
        long remainingRestMs,
        long updatedAtMs) {
    AgentFieldCheckpoint {
        requestId = normalize(requestId);
        callerId = normalize(callerId);
        objectiveId = normalize(objectiveId);
        exitReason = normalize(exitReason);
        restReason = normalize(restReason);
        requiredMobIds = requiredMobIds == null ? Set.of() : Set.copyOf(requiredMobIds);
        requiredKills = requiredKills == null ? Map.of() : Map.copyOf(requiredKills);
        narrationLevel = narrationLevel == null
                ? AgentFieldObservationState.NarrationLevel.SUMMARY : narrationLevel;
        phase = phase == null ? AgentFieldActivityState.Phase.GRINDING : phase;
        if (schemaVersion != 1 || characterId <= 0 || mapId <= 0
                || requestId.isEmpty() || callerId.isEmpty() || intentType == null
                || maximumParticipants < 1 || maximumParticipants > 12
                || remainingExitDeadlineMs < 0L || remainingRestMs < 0L || updatedAtMs < 0L) {
            throw new IllegalArgumentException("valid field checkpoint is required");
        }
    }

    AgentFieldEntryRequest entryRequest() {
        AgentFieldIntent intent = new AgentFieldIntent(
                intentType, objectiveId, requiredMobIds, requiredKills, temporaryVisitor);
        return new AgentFieldEntryRequest(requestId, callerId,
                new AgentFieldVisitRequest(mapId, intent, acceptingQuestVisitors,
                        maximumParticipants, restAllowed, narrationLevel));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
