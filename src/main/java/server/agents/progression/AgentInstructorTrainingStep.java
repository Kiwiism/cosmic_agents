package server.agents.progression;

import java.util.Map;
import java.util.Set;

record AgentInstructorTrainingStep(
        int questId,
        int huntingMapId,
        Set<Integer> mobIds,
        Map<Integer, Integer> requiredKills,
        AgentVictoriaLevel15Catalog.TrainingGround trainingGround) {
    AgentInstructorTrainingStep {
        if (questId <= 0 || huntingMapId <= 0 || mobIds == null || mobIds.isEmpty()
                || requiredKills == null || !requiredKills.keySet().equals(mobIds)
                || requiredKills.values().stream().anyMatch(count -> count == null || count <= 0)) {
            throw new IllegalArgumentException("training quest, map, and mobs are required");
        }
        mobIds = Set.copyOf(mobIds);
        requiredKills = Map.copyOf(requiredKills);
    }
}
