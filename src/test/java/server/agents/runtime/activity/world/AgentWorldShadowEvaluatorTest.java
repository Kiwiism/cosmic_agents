package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivitySourcePort;
import server.agents.runtime.activity.session.AgentActivityTargetPort;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldShadowEvaluatorTest {
    @Test
    void evaluationIsDeterministicAndCannotCarryAnAdmissionHandle() {
        AgentWorldContext context = AgentWorldMilestoneEvaluatorTest.context(
                18, 100, 100_000_000, "COMPLETE", false);
        AgentWorldShadowEvaluator evaluator = AgentWorldShadowEvaluator.baseline();

        AgentWorldShadowReport first = evaluator.evaluate(context);
        AgentWorldShadowReport second = evaluator.evaluate(context);

        assertEquals(first.decision(), second.decision());
        assertEquals("progression:individual-quest", first.decision().proposalId());
        assertTrue(first.intents().stream().allMatch(intent -> !intent.requestId().isBlank()));
        assertFalse(Arrays.stream(AgentWorldActivityIntent.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getType)
                .anyMatch(type -> AgentActivitySourcePort.class.isAssignableFrom(type)
                        || AgentActivityTargetPort.class.isAssignableFrom(type)));
    }

    @Test
    void mandatoryFirstJobProposalWinsBeforeOptionalActivities() {
        AgentWorldContext context = AgentWorldMilestoneEvaluatorTest.context(
                10, 0, 104_000_000, "", false);

        AgentWorldShadowReport report = AgentWorldShadowEvaluator.baseline().evaluate(context);

        assertEquals("milestone:first-job", report.decision().proposalId());
        assertEquals(900, report.intents().getFirst().proposal().priority());
    }
}
