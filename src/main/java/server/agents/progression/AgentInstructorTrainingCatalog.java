package server.agents.progression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class AgentInstructorTrainingCatalog {
    private AgentInstructorTrainingCatalog() {
    }

    static List<AgentInstructorTrainingStep> steps(AgentCareerBuildBundle bundle) {
        AgentVictoriaLevel15Catalog.Career career = career(bundle);
        return career.trainingSteps().stream()
                .map(step -> step(step.questId(), step.huntingMapId(), step.mobIds(),
                        step.requiredCounts(),
                        career.trainingGround()))
                .toList();
    }

    static AgentInstructorTrainingStep milestoneGrind(AgentCareerBuildBundle bundle) {
        AgentVictoriaLevel15Catalog.MilestoneGrind grind = career(bundle).milestoneGrind();
        Map<Integer, Integer> requirements = new LinkedHashMap<>();
        for (int mobId : grind.mobIds()) {
            requirements.put(mobId, Integer.MAX_VALUE);
        }
        return new AgentInstructorTrainingStep(
                1, grind.huntingMapId(), Set.copyOf(grind.mobIds()), requirements, null);
    }

    private static AgentVictoriaLevel15Catalog.Career career(AgentCareerBuildBundle bundle) {
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().careerFor(bundle);
    }

    private static AgentInstructorTrainingStep step(
            int questId,
            int mapId,
            List<Integer> mobIds,
            List<Integer> requiredCounts,
            AgentVictoriaLevel15Catalog.TrainingGround trainingGround) {
        Map<Integer, Integer> requirements = new LinkedHashMap<>();
        for (int i = 0; i < mobIds.size(); i++) {
            requirements.put(mobIds.get(i), requiredCounts.get(i));
        }
        return new AgentInstructorTrainingStep(
                questId, mapId, Set.copyOf(mobIds), requirements, trainingGround);
    }
}
