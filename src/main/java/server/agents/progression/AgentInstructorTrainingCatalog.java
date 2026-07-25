package server.agents.progression;

import java.util.List;
import java.util.Set;

final class AgentInstructorTrainingCatalog {
    private AgentInstructorTrainingCatalog() {
    }

    static List<AgentInstructorTrainingStep> steps(AgentCareerBuildBundle bundle) {
        AgentVictoriaLevel15Catalog.Career career = career(bundle);
        return career.trainingSteps().stream()
                .map(step -> step(step.questId(), step.huntingMapId(), step.mobIds(),
                        career.trainingGround()))
                .toList();
    }

    static AgentInstructorTrainingStep milestoneGrind(AgentCareerBuildBundle bundle) {
        AgentVictoriaLevel15Catalog.MilestoneGrind grind = career(bundle).milestoneGrind();
        return step(1, grind.huntingMapId(), grind.mobIds(), null);
    }

    private static AgentVictoriaLevel15Catalog.Career career(AgentCareerBuildBundle bundle) {
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().careerFor(bundle);
    }

    private static AgentInstructorTrainingStep step(
            int questId,
            int mapId,
            List<Integer> mobIds,
            AgentVictoriaLevel15Catalog.TrainingGround trainingGround) {
        return new AgentInstructorTrainingStep(
                questId, mapId, Set.copyOf(mobIds), trainingGround);
    }
}
