package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;
import server.agents.catalog.AgentMapRegionAssignment;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCombatRegionAssignmentPolicyTest {
    @Test
    void onlyCurrentUnexpiredNumericRegionsAreAccepted() {
        AgentMapRegionAssignment assignment = new AgentMapRegionAssignment(
                "a", 100, List.of("4", "future-signature", "9"), 0, 2, 2_000L);

        assertEquals(Set.of(4, 9),
                AgentCombatRegionAssignmentPolicy.assignedRegions(assignment, 100, 1_999L));
        assertTrue(AgentCombatRegionAssignmentPolicy.assignedRegions(
                assignment, 100, 2_000L).isEmpty());
        assertTrue(AgentCombatRegionAssignmentPolicy.assignedRegions(
                assignment, 101, 1_000L).isEmpty());
    }

    @Test
    void emptyScansOpenABoundedBorrowWindowAndTargetsCloseIt() {
        AgentCombatRegionAssignmentState state = new AgentCombatRegionAssignmentState();

        assertFalse(state.observe("a", false, 3, 1_000L, 5_000L));
        assertFalse(state.observe("a", false, 3, 1_100L, 5_000L));
        assertTrue(state.observe("a", false, 3, 1_200L, 5_000L));
        assertTrue(state.observe("a", false, 3, 6_199L, 5_000L));
        assertFalse(state.observe("a", true, 3, 6_200L, 5_000L));
        assertEquals(0L, state.snapshot(6_200L).borrowRemainingMs());
    }

    @Test
    void newAssignmentDoesNotInheritPreviousBorrowing() {
        AgentCombatRegionAssignmentState state = new AgentCombatRegionAssignmentState();
        state.observe("a", false, 1, 1_000L, 5_000L);

        assertFalse(state.observe("b", true, 1, 1_001L, 5_000L));
        assertEquals("b", state.snapshot(1_001L).assignmentId());
    }
}
