package server.agents.capabilities.shop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentShopWorkflowTest {
    @Test
    void followsDurableTransactionPhases() {
        AgentShopWorkflow workflow = new AgentShopWorkflow();
        workflow.start("shop:1", 1000, 10);
        workflow.transition(AgentShopWorkflowPhase.NAVIGATING, "route", 11);
        workflow.transition(AgentShopWorkflowPhase.APPROACHING, "near", 12);
        workflow.transition(AgentShopWorkflowPhase.OPENING, "open", 13);
        workflow.transition(AgentShopWorkflowPhase.TRANSACTING, "buy", 14);
        workflow.transition(AgentShopWorkflowPhase.VERIFYING, "reconcile", 15);
        workflow.transition(AgentShopWorkflowPhase.COMPLETED, "done", 16);

        assertEquals(AgentShopWorkflowPhase.COMPLETED, workflow.phase());
        assertEquals("done", workflow.reason());
    }

    @Test
    void rejectsSkippedMutationPhases() {
        AgentShopWorkflow workflow = new AgentShopWorkflow();
        workflow.start("shop:1", 1000, 10);
        assertThrows(IllegalStateException.class, () ->
                workflow.transition(AgentShopWorkflowPhase.COMPLETED, "skip", 11));
    }
}
