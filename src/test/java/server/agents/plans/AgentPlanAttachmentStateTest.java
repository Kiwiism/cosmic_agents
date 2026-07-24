package server.agents.plans;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanAttachmentStateTest {
    @Test
    void attachedIdentityStaysSuppressedWhileAReplacementCanAttach() {
        AgentPlanAttachmentState state = new AgentPlanAttachmentState();

        assertTrue(state.ready("plan:one", 10L));
        state.attached("plan:one");

        assertFalse(state.ready("plan:one", Long.MAX_VALUE));
        assertTrue(state.ready("plan:two", 11L));
    }

    @Test
    void failedIdentityRetriesOnlyAfterItsDeadline() {
        AgentPlanAttachmentState state = new AgentPlanAttachmentState();

        state.failed("plan:one", 100L);

        assertFalse(state.ready("plan:one", 99L));
        assertTrue(state.ready("plan:one", 100L));
    }
}
