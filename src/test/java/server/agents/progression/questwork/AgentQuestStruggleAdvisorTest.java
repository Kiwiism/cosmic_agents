package server.agents.progression.questwork;

import org.junit.jupiter.api.Test;
import server.agents.runtime.decision.AgentDecisionAdvisoryService;
import server.agents.runtime.decision.AgentDecisionReasonCode;
import server.agents.runtime.decision.AgentDecisionRecommendation;
import server.agents.runtime.decision.AgentRecommendedAction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentQuestStruggleAdvisorTest {
    private final AgentQuestStruggleAssessmentFactory assessments =
            new AgentQuestStruggleAssessmentFactory();
    private final AgentDecisionAdvisoryService advisory =
            new AgentDecisionAdvisoryService(new AgentQuestStruggleAdvisor());

    @Test
    void relevantDamageProtectsSlowCollectionQuestFromFalseFailure() {
        AgentDecisionRecommendation result = evaluate(observation(
                0L, 500_000L, -1L, 499_000L, -1L,
                0L, 0, 0, 0, 100));

        assertEquals(AgentRecommendedAction.CONTINUE, result.action());
        assertEquals(AgentDecisionReasonCode.PROGRESS_CONTINUES, result.reasonCode());
        assertTrue(result.explanation().contains("relevant damage"));
    }

    @Test
    void legitimateGraphWarmupDoesNotBecomeQuestFailure() {
        AgentDecisionRecommendation result = evaluate(observation(
                0L, 500_000L, -1L, -1L, -1L,
                510_000L, 5, 0, 0, 100));

        assertEquals(AgentRecommendedAction.CONTINUE, result.action());
        assertTrue(result.explanation().contains("declared warmup"));
    }

    @Test
    void navigationFailureReplansWithoutAbandoningQuest() {
        AgentDecisionRecommendation result = evaluate(observation(
                0L, 500_000L, 450_000L, -1L, -1L,
                0L, 3, 0, 0, 100));

        assertEquals(AgentRecommendedAction.REPLAN_CURRENT, result.action());
        assertEquals(AgentDecisionReasonCode.NAVIGATION_BLOCKED, result.reasonCode());
    }

    @Test
    void resourceBudgetAndExhaustedRetriesRequestBoundedExit() {
        AgentDecisionRecommendation resources = evaluate(observation(
                0L, 500_000L, 499_000L, 499_000L, 499_000L,
                0L, 0, 0, 100, 100));
        AgentDecisionRecommendation retries = evaluate(observation(
                0L, 500_000L, 499_000L, 499_000L, 499_000L,
                0L, 0, 3, 0, 100));

        assertEquals(AgentRecommendedAction.RESUPPLY, resources.action());
        assertEquals(AgentRecommendedAction.SUSPEND, retries.action());
        assertEquals(AgentDecisionReasonCode.RETRIES_EXHAUSTED, retries.reasonCode());
    }

    @Test
    void absenceOfProgressRequestsSuspensionRatherThanExecutingIt() {
        AgentDecisionRecommendation result = evaluate(observation(
                0L, 500_000L, -1L, -1L, -1L,
                0L, 0, 0, 0, 100));

        assertEquals(AgentRecommendedAction.SUSPEND, result.action());
        assertEquals(AgentDecisionReasonCode.SAFE_BOUNDARY_REQUESTED, result.reasonCode());
        assertEquals("quest-struggle-fallback", result.policyId());
    }

    private AgentDecisionRecommendation evaluate(AgentQuestAttemptObservation observation) {
        return advisory.evaluate(assessments.create(observation));
    }

    private static AgentQuestAttemptObservation observation(
            long startedAt,
            long observedAt,
            long objectiveAt,
            long damageAt,
            long navigationAt,
            long waitUntil,
            int navigationFailures,
            int retries,
            int resources,
            int resourceBudget) {
        return new AgentQuestAttemptObservation(
                "agent-1", "work-1", 2018, startedAt, observedAt,
                objectiveAt, damageAt, navigationAt, waitUntil,
                navigationFailures, retries, resources, resourceBudget);
    }
}
