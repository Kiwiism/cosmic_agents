package server.agents.commands;

import server.agents.capabilities.movement.AgentFormationService;
import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class AgentFormationCommandCoordinatorTest {
    @Test
    void returnsFalseForNonFormationMessages() {
        Character leader = leader(901);

        boolean handled = AgentFormationCommandCoordinator.handleFormationCommand(
                leader,
                "follow me",
                leaderCharId -> List.of(),
                defaultFormation(),
                60,
                120);

        assertFalse(handled);
        verify(leader, never()).yellowMessage(anyString());
    }

    @Test
    void sendsLegacyHelpToLeaderWhenNoAgentsAreActive() {
        Character leader = leader(902);

        boolean handled = AgentFormationCommandCoordinator.handleFormationCommand(
                leader,
                "formation",
                leaderCharId -> List.of(),
                defaultFormation(),
                60,
                120);

        assertTrue(handled);
        verify(leader).yellowMessage(
                "formations: stagger/split/random/spread/left/right <px>, stack, tight, loose | snap <px/on/off>");
    }

    @Test
    void updatesSharedFormationStateWithLegacyDefaults() {
        Character leader = leader(903);

        boolean handled = AgentFormationCommandCoordinator.handleFormationCommand(
                leader,
                "formation spread 90",
                leaderCharId -> List.of(),
                defaultFormation(),
                60,
                120);

        assertTrue(handled);
        AgentFormationService.FormationState state = AgentFormationService.formationsByLeaderId().get(903);
        assertEquals(AgentFormationService.FormationType.SPREAD, state.type());
        assertEquals(90, state.px());
        assertEquals(0, state.snapRange());
    }

    @Test
    void repliesThroughAgentEntryWhenAgentsAreActive() {
        Character leader = leader(904);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), leader, null);

        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            boolean handled = AgentFormationCommandCoordinator.handleFormationCommand(
                    leader,
                    "formation",
                    leaderCharId -> List.of(entry),
                    defaultFormation(),
                    60,
                    120);

            assertTrue(handled);
            replies.verify(() -> AgentReplyRuntime.queueReply(
                    eq(entry),
                    eq("formations: stagger/split/random/spread/left/right <px>, stack, tight, loose | snap <px/on/off>")));
        }
    }

    private static AgentFormationService.FormationState defaultFormation() {
        return new AgentFormationService.FormationState(AgentFormationService.FormationType.STAGGER, 60, 0);
    }

    private static Character leader(int id) {
        Character leader = mock(Character.class);
        when(leader.getId()).thenReturn(id);
        return leader;
    }
}
