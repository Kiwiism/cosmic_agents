package server.agents.progression;

import java.util.Set;

record AgentInstructorTrainingStep(int questId, int huntingMapId, Set<Integer> mobIds) {
    AgentInstructorTrainingStep {
        if (questId <= 0 || huntingMapId <= 0 || mobIds == null || mobIds.isEmpty()) {
            throw new IllegalArgumentException("training quest, map, and mobs are required");
        }
        mobIds = Set.copyOf(mobIds);
    }
}
