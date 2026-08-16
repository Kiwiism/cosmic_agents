package server.agents.field;

import java.util.List;
import java.util.Map;

/** Immutable diagnostics projection for a live field session. */
public record AgentFieldSnapshot(
        String sessionId,
        int mapId,
        AgentFieldMode mode,
        long revision,
        long observedAtMs,
        int realPlayers,
        int liveMobs,
        boolean acceptingQuestVisitors,
        boolean objectiveComplete,
        Map<Integer, Integer> requiredKills,
        Map<Integer, Integer> completedKills,
        List<Cell> cells,
        List<Participant> participants) {

    public AgentFieldSnapshot {
        requiredKills = Map.copyOf(requiredKills);
        completedKills = Map.copyOf(completedKills);
        cells = List.copyOf(cells);
        participants = List.copyOf(participants);
    }

    public record Cell(
            String cellId,
            List<Integer> regionIds,
            Map<Integer, Integer> mobCounts,
            Map<Integer, Integer> expectedMobCounts,
            int capacity,
            boolean deadEnd,
            List<String> adjacentCellIds) {
        public Cell {
            regionIds = List.copyOf(regionIds);
            mobCounts = Map.copyOf(mobCounts);
            expectedMobCounts = Map.copyOf(expectedMobCounts);
            adjacentCellIds = List.copyOf(adjacentCellIds);
        }
    }

    public record Participant(
            int agentId,
            String name,
            int partyId,
            int jobId,
            int level,
            int exp,
            int positionX,
            int positionY,
            long kills,
            long targetTransitions,
            long routeFailures,
            long stuckDetections,
            long recoveries,
            long lifeTransitions,
            AgentFieldIntent.Type intent,
            AgentFieldRole role,
            String lifecycle,
            String combatPosture,
            Map<String, Long> postureTimeMs,
            long attacks,
            long hitLines,
            long missLines,
            long damage,
            long assignmentChanges,
            int targetMobId,
            int targetX,
            int targetY,
            List<String> cellIds,
            List<Integer> regionIds,
            int anchorX,
            int anchorY,
            long leaseRemainingMs,
            String reason,
            List<AgentFieldObservationState.TimelineEntry> timeline) {
        public Participant {
            postureTimeMs = Map.copyOf(postureTimeMs);
            cellIds = List.copyOf(cellIds);
            regionIds = List.copyOf(regionIds);
            timeline = List.copyOf(timeline);
        }
    }
}
