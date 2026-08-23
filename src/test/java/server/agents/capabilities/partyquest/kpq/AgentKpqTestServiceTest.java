package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import scripting.event.EventInstanceManager;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class AgentKpqTestServiceTest {
    private static final Set<String> MELEE = Set.of("warrior", "thief-dagger", "pirate-knuckle");
    private static final Set<String> RANGED = Set.of("bowman", "magician", "thief-claw", "pirate-gun");

    @Test
    void balancedFourAgentRosterHasTwoDistinctMeleeAndRangedCareers() {
        Map<String, String> assignments = AgentKpqTestService.balancedCareerAssignments(
                List.of("KPQer01", "KPQer02", "KPQer03", "KPQer04"), 77L);

        assertEquals(4, Set.copyOf(assignments.values()).size());
        assertEquals(2, assignments.values().stream().filter(MELEE::contains).count());
        assertEquals(2, assignments.values().stream().filter(RANGED::contains).count());
    }

    @Test
    void fixedFourAgentRosterUsesRequestedComposition() {
        Map<String, String> assignments = AgentKpqTestService.fixedCareerAssignments(
                List.of("KPQer13", "KPQer04", "KPQer22", "KPQer09"),
                List.of("thief-claw", "pirate-knuckle", "warrior", "magician"));

        assertEquals(List.of("thief-claw", "pirate-knuckle", "warrior", "magician"),
                List.copyOf(assignments.values()));
    }

    @Test
    void balancedPresetKeepsLegacySeedSyntaxBackwardCompatible() {
        AgentKpqTestService.StartOptions legacy = AgentKpqTestService.startOptions(
                new String[]{"start", "4", "123"}, 1, 999L);
        AgentKpqTestService.StartOptions balanced = AgentKpqTestService.startOptions(
                new String[]{"start", "4", "balanced", "123"}, 1, 999L);

        assertEquals(new AgentKpqTestService.StartOptions(4, 123L, false), legacy);
        assertEquals(new AgentKpqTestService.StartOptions(4, 123L, true), balanced);
        assertTrue(AgentKpqTestService.startOptions(
                new String[]{"start", "balanced"}, 1, 999L).balanced());
        assertThrows(IllegalArgumentException.class, () -> AgentKpqTestService.startOptions(
                new String[]{"start", "5"}, 1, 999L));
    }

    @Test
    void stoppingTestExitsParticipantsBeforeDisposingTheLobby() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character first = mock(Character.class);
        Character second = mock(Character.class);
        when(event.getPlayers()).thenReturn(List.of(first, second));

        AgentKpqTestService.closeEventInstance(event);

        InOrder order = inOrder(event);
        order.verify(event).exitPlayer(first);
        order.verify(event).exitPlayer(second);
        order.verify(event).dispose();
    }

    @Test
    void terminationIsIdempotentWhenTwoRecoveryPathsRace() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(event.getPlayers()).thenReturn(List.of());
        when(event.getName()).thenReturn("Kerning0");
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 99, 3, 1_000L);

        AgentKpqTerminationService.stopTest(session, event, 2_000L);
        AgentKpqTerminationService.stopTest(session, event, 2_001L);

        org.mockito.Mockito.verify(event, times(1)).dispose();
    }

    @Test
    void failedObservationRunReturnsAgentsInsteadOfLeavingThemInTheExitMap() {
        assertTrue(AgentKpqTerminationService.shouldReturnTestAgentsToKerning(
                AgentKpqSession.Mode.TEST_OBSERVATION, false));
        org.junit.jupiter.api.Assertions.assertFalse(
                AgentKpqTerminationService.shouldReturnTestAgentsToKerning(
                        AgentKpqSession.Mode.TEST_OBSERVATION, true));
        org.junit.jupiter.api.Assertions.assertFalse(
                AgentKpqTerminationService.shouldReturnTestAgentsToKerning(
                        AgentKpqSession.Mode.PRODUCTION, false));
    }

    @Test
    void watchdogDisposesEventWhenEveryAgentRuntimeDisappears() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(event.getPlayers()).thenReturn(List.of());
        when(event.getName()).thenReturn("Kerning1");
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.BACKGROUND_POPULATION, 1L, 99, 3, 1_000L);
        session.bindEventInstance(event);
        session.transition(AgentKpqSession.Phase.STAGE_1, 1_000L);

        AgentKpqWatchdogRuntime.tick(session, 5_000L);

        org.mockito.Mockito.verify(event).dispose();
        org.junit.jupiter.api.Assertions.assertEquals(
                AgentKpqSession.Phase.FAILED, session.phase());
    }
}
