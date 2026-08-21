package server.agents.runtime.decision;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentDecisionAdvisoryServiceTest {
    @Test
    void preservesTypedEvidenceAndActivityRequestWithoutExecutingIt() {
        List<AgentDecisionSignal> mutable = new ArrayList<>();
        mutable.add(AgentDecisionSignal.observed(
                AgentDecisionSignalKind.RESOURCE_PRESSURE, 100L,
                "quest-watchdog", "quest:2018", "HP potions below reserve"));
        AgentDecisionAssessment assessment = new AgentDecisionAssessment(
                "agent-7", AgentActivityKind.QUESTING, 100L, "decision-7", mutable);
        AgentDecisionAdvisoryService service = new AgentDecisionAdvisoryService(input ->
                AgentDecisionRecommendation.from(input,
                        AgentRecommendedAction.REQUEST_TOWN_LIFE,
                        AgentDecisionReasonCode.SUPPLIES_REQUIRED,
                        "resource-fallback", "v1", "resupply at a safe quest boundary"));

        AgentDecisionRecommendation recommendation = service.evaluate(assessment);
        mutable.clear();

        assertEquals(AgentActivityKind.TOWN_LIFE, recommendation.targetActivity());
        assertEquals(AgentActivityKind.QUESTING, assessment.currentActivity());
        assertEquals(1, recommendation.evidence().size());
        assertTrue(recommendation.action().requestsActivity());
    }

    @Test
    void recordsStableProvenanceWithoutChangingForegroundState() {
        Character character = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character, null, null);
        AgentDecisionAssessment assessment = new AgentDecisionAssessment(
                "agent-8", AgentActivityKind.HUNTING, 200L, "decision-8",
                List.of(AgentDecisionSignal.observed(
                        AgentDecisionSignalKind.PROGRESS_OBSERVED, 200L,
                        "hunting", "map:100000001", "kills continue")));
        AgentDecisionAdvisoryService service = new AgentDecisionAdvisoryService(input ->
                AgentDecisionRecommendation.from(input,
                        AgentRecommendedAction.CONTINUE,
                        AgentDecisionReasonCode.PROGRESS_CONTINUES,
                        "continue-policy", "v1", "meaningful progress remains visible"));

        AgentDecisionRecord record = service.record(entry, service.evaluate(assessment));

        assertEquals("CONTINUE", record.choice());
        assertTrue(record.reason().startsWith("PROGRESS_CONTINUES"));
        assertEquals(java.util.Set.of("runtime.decision-provenance"),
                entry.capabilityStates().registeredStateIds());
    }

    @Test
    void rejectsMismatchedOrIncompleteAdvisorOutput() {
        AgentDecisionAssessment assessment = new AgentDecisionAssessment(
                "agent-9", null, 300L, "decision-9", List.of());
        AgentDecisionAdvisoryService mismatched = new AgentDecisionAdvisoryService(input ->
                new AgentDecisionRecommendation(
                        AgentRecommendedAction.SAFE_FALLBACK,
                        AgentDecisionReasonCode.NO_ELIGIBLE_ACTIVITY,
                        301L, "fallback", "v1", "other", "", List.of()));

        assertThrows(IllegalStateException.class, () -> mismatched.evaluate(assessment));
        assertThrows(IllegalArgumentException.class, () -> new AgentDecisionAssessment(
                "", null, 0L, "", List.of()));
    }
}
