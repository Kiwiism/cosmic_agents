package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldMilestoneEvaluatorTest {
    @Test
    void derivesOnlyObservableMilestonesAndLeavesMissingCareerEvidenceUnknown() {
        AgentWorldMilestoneSnapshot milestones = AgentWorldMilestoneEvaluator.evaluate(
                context(15, 100, 100_000_000, "", false));

        assertTrue(milestones.achieved(AgentWorldMilestone.VICTORIA_REACHED));
        assertTrue(milestones.achieved(AgentWorldMilestone.MAPLE_ISLAND_COMPLETE));
        assertTrue(milestones.achieved(AgentWorldMilestone.FIRST_JOB_COMPLETE));
        assertTrue(milestones.achieved(AgentWorldMilestone.LEVEL_15_REACHED));
        assertEquals(AgentWorldMilestoneStatus.UNKNOWN,
                milestones.status(AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE));
        assertEquals(AgentWorldMilestoneStatus.NOT_ACHIEVED,
                milestones.status(AgentWorldMilestone.KPQ_LEVEL_ELIGIBLE));
    }

    @Test
    void recognizesCompletedFoundationKpqRewardAndSecondJob() {
        AgentWorldMilestoneSnapshot milestones = AgentWorldMilestoneEvaluator.evaluate(
                context(30, 110, 103_000_000, "COMPLETE", true));

        assertTrue(milestones.achieved(AgentWorldMilestone.LEVEL_15_FOUNDATION_COMPLETE));
        assertTrue(milestones.achieved(AgentWorldMilestone.KPQ_LEVEL_ELIGIBLE));
        assertTrue(milestones.achieved(AgentWorldMilestone.SQUISHY_SHOES_ACQUIRED));
        assertTrue(milestones.achieved(AgentWorldMilestone.LEVEL_30_REACHED));
        assertTrue(milestones.achieved(AgentWorldMilestone.SECOND_JOB_COMPLETE));
    }

    @Test
    void recognizesSouthperryHandoffBoundaryWithoutClaimingVictoriaArrival() {
        AgentWorldContext context = new AgentWorldContext(
                2L, 2_000L, 27, "KiwiAgent", 9, 0, 2_000_000,
                100, 100, 50, 50, 0L, true, false, Set.of(1046), Set.of(),
                null, "", "", "", "", Map.of());

        AgentWorldMilestoneSnapshot milestones = AgentWorldMilestoneEvaluator.evaluate(context);

        assertTrue(milestones.achieved(AgentWorldMilestone.MAPLE_ISLAND_COMPLETE));
        assertEquals(AgentWorldMilestoneStatus.NOT_ACHIEVED,
                milestones.status(AgentWorldMilestone.VICTORIA_REACHED));
        assertEquals("milestone:lith-handoff",
                AgentWorldShadowEvaluator.baseline().evaluate(context).decision().proposalId());
    }

    static AgentWorldContext context(
            int level, int job, int map, String careerStage, boolean shoes) {
        return new AgentWorldContext(1L, 1_000L, 27, "KiwiAgent", level, job, map,
                100, 100, 50, 50, 1_000L, true, shoes, Set.of(), Set.of(),
                AgentActivityKind.QUESTING, "questing", "session", "plan",
                careerStage, Map.of("captureMode", "test"));
    }
}
