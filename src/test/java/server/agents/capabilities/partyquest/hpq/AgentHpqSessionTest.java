package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentHpqSessionTest {
    @Test
    void watchdogCanClaimOnlyAnExpiredExecutionLease() {
        AgentHpqSession session = session();

        assertTrue(session.claimExecutionTick(101, 1_000L, 3_000L));
        assertFalse(session.claimExpiredExecutionTick(102, 3_999L, 3_000L));
        assertTrue(session.claimExpiredExecutionTick(102, 4_000L, 3_000L));
        assertEquals(102, session.executionAgentId());
    }

    @Test
    void phaseEntryAndBonusChoiceRemainSessionLocal() {
        AgentHpqSession session = session();
        session.setBonusMode(AgentHpqSession.BonusMode.ENTER);
        session.transition(AgentHpqSession.Phase.BONUS_FARMING, 5_000L);

        assertEquals(AgentHpqSession.BonusMode.ENTER, session.bonusMode());
        assertEquals(5_000L, session.phaseEnteredAtMs());
    }

    @Test
    void lobbyPreparationDeadlineRemainsSessionLocal() {
        AgentHpqSession first = session();
        AgentHpqSession second = session();

        first.setReadyAtMs(6_000L);

        assertEquals(6_000L, first.readyAtMs());
        assertEquals(0L, second.readyAtMs());
    }

    @Test
    void preparationCountdownStartsOnceAndResetsWhenThePartyDisperses() {
        assertEquals(6_000L, AgentHpqCoordinator.preparationReadyAtMs(
                true, 0L, 1_000L, 5_000L));
        assertEquals(6_000L, AgentHpqCoordinator.preparationReadyAtMs(
                true, 6_000L, 2_000L, 5_000L));
        assertEquals(0L, AgentHpqCoordinator.preparationReadyAtMs(
                false, 6_000L, 2_000L, 5_000L));
    }

    @Test
    void watchdogDisposesAnOrphanedHpqEvent() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(event.getPlayers()).thenReturn(List.of());
        when(event.getName()).thenReturn("Henesys0");
        AgentHpqSession session = new AgentHpqSession(
                AgentHpqSession.Mode.BACKGROUND_POPULATION, 1L, 99, 3, 1_000L);
        session.bindEventInstance(event);
        session.transition(AgentHpqSession.Phase.COLLECTING_SEEDS, 1_000L);

        AgentHpqWatchdogRuntime.tick(session, 5_000L);

        verify(event).dispose();
        assertEquals(AgentHpqSession.Phase.FAILED, session.phase());
    }

    private static AgentHpqSession session() {
        AgentHpqSession session = new AgentHpqSession(
                AgentHpqSession.Mode.TEST_OBSERVATION, 7L, 101, 3, 1_000L);
        session.addMember(101, AgentHpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentHpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentHpqMemberState.MemberType.HUMAN);
        session.setLeadership(101, 101);
        return session;
    }
}
