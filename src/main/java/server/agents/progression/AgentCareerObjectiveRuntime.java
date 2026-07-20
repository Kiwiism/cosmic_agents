package server.agents.progression;

import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.objectives.AgentObjectiveStatus;
import server.agents.runtime.AgentRuntimeEntry;

final class AgentCareerObjectiveRuntime {
    private static final String PREFIX = "career:level15:";

    private AgentCareerObjectiveRuntime() {
    }

    static void ensureStarted(AgentRuntimeEntry entry, AgentCareerBuildBundle bundle, long nowMs) {
        if (AgentObjectiveKernel.active(entry) != null || entry.bot() == null) {
            return;
        }
        String objectiveId = objectiveId(entry);
        String planId = AgentVictoriaLevel15PlanRepository.defaultPlan().planId();
        AgentObjectiveKernel.start(entry, new AgentObjectiveDefinition(
                objectiveId, AgentFirstJobJourneyRuntime.OBJECTIVE_TYPE, 100, Long.MAX_VALUE, 3,
                AgentObjectiveSource.PROGRESSION_POLICY, planId,
                bundle.bundleId() + ':' + entry.bot().getId()), nowMs);
    }

    static void succeed(AgentRuntimeEntry entry, long nowMs) {
        AgentObjectiveKernel.transition(entry, objectiveId(entry), AgentObjectiveStatus.SUCCEEDED,
                "instructor training complete and level 15 reached", nowMs);
    }

    static void block(AgentRuntimeEntry entry, String reason, long nowMs) {
        AgentObjectiveKernel.transition(entry, objectiveId(entry), AgentObjectiveStatus.BLOCKED, reason, nowMs);
    }

    private static String objectiveId(AgentRuntimeEntry entry) {
        return PREFIX + (entry.bot() == null ? 0 : entry.bot().getId());
    }
}
