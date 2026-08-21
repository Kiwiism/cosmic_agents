package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqSessionTest {
    @Test
    void firstMemberOwnsLeadershipAndOnlyCoordinatorTicksOncePerTimestamp() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 100, 3, 1_000L);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(10, AgentKpqMemberState.MemberType.AGENT);

        assertEquals(20, session.eventLeaderId());
        assertEquals(1, session.member(20).partyNumber());
        assertEquals(2, session.member(10).partyNumber());
        assertTrue(session.claimCoordinatorTick(20, 2_000L));
        assertFalse(session.claimCoordinatorTick(20, 2_000L));
        assertFalse(session.claimCoordinatorTick(10, 2_001L));
    }

    @Test
    void liveAgentTakesCoordinatorLeaseAfterHeartbeatExpires() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, 7L, 100, 3, 1_000L);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(10, AgentKpqMemberState.MemberType.AGENT);

        assertTrue(session.claimCoordinatorTick(20, 2_000L, 3_000L));
        assertFalse(session.claimCoordinatorTick(10, 4_999L, 3_000L));
        assertTrue(session.claimCoordinatorTick(10, 5_000L, 3_000L));
        assertEquals(10, session.coordinatorAgentId());
        assertEquals(20, session.formationCallerId());
    }

    @Test
    void watchdogClaimDoesNotDuplicateAHealthyCoordinatorTick() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, 7L, 100, 3, 1_000L);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(10, AgentKpqMemberState.MemberType.AGENT);

        assertTrue(session.claimCoordinatorTick(20, 2_000L, 3_000L));
        assertFalse(session.claimExpiredCoordinatorTick(20, 2_500L, 3_000L));
        assertTrue(session.claimExpiredCoordinatorTick(10, 5_000L, 3_000L));
        assertEquals(10, session.coordinatorAgentId());
    }

    @Test
    void rotationReusesVacatedStablePartyNumber() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 100, 4, 1_000L);
        session.addMember(1, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(2, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(3, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(4, AgentKpqMemberState.MemberType.AGENT);
        session.removeMember(2);
        session.addMember(5, AgentKpqMemberState.MemberType.AGENT);
        assertEquals(2, session.member(5).partyNumber());
    }

    @Test
    void narrationKeysAreUniqueAndANewRunUsesANewSession() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 100, 3, 1_000L);
        session.addMember(1, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(2, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(3, AgentKpqMemberState.MemberType.AGENT);

        assertTrue(session.narrateOnce("ready"));
        assertTrue(session.narrateOnce("another"));
        assertFalse(session.narrateOnce("ready"));

        AgentKpqSession next = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 8L, 100, 3, 2_000L);
        assertTrue(next.narrateOnce("ready"));
        assertFalse(session.narrateOnce("ready"));
    }

    @Test
    void humanLeaderUsesAgentCoordinatorAndNpcVerdictsAreConsumedOnce() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, 7L, 100, 4, 1_000L);
        session.addMember(100, AgentKpqMemberState.MemberType.HUMAN);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(21, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(22, AgentKpqMemberState.MemberType.AGENT);
        session.setLeadership(100, 20);

        assertEquals(100, session.eventLeaderId());
        assertEquals(20, session.coordinatorAgentId());
        assertEquals(20, session.formationCallerId());
        assertTrue(session.claimCoordinatorTick(20, 2_000L));
        assertFalse(session.claimCoordinatorTick(100, 2_001L));
        assertTrue(session.claimCoordinatorTick(21, 5_000L, 3_000L));
        assertEquals(21, session.coordinatorAgentId());
        assertEquals(20, session.formationCallerId());

        session.recordHumanPuzzleValidation(2, false);
        assertFalse(session.consumeHumanPuzzleValidation(2).accepted());
        assertNull(session.consumeHumanPuzzleValidation(2));
        session.recordHumanPuzzleValidation(2, true);
        assertTrue(session.consumeHumanPuzzleValidation(2).accepted());
    }

    @Test
    void agentEventLeaderRemainsFormationCallerWhenAnotherAgentExecutes() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, 7L, 100, 3, 1_000L);
        session.addMember(20, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(10, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(30, AgentKpqMemberState.MemberType.AGENT);
        session.setLeadership(20, 10);

        assertEquals(20, session.eventLeaderId());
        assertEquals(10, session.coordinatorAgentId());
        assertEquals(20, session.formationCallerId());
    }

    @Test
    void phaseTransitionClearsCouponSweepState() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 1, 3, 1_000L);
        session.setCouponSweepStartedAtMs(90_000L);
        session.setNextCouponSweepAtMs(180_000L);
        session.setCouponSweepCollectorId(22);
        session.setMissingPassSinceMs(185_000L);

        session.transition(AgentKpqSession.Phase.STAGE_2, 2_000L);

        assertEquals(0L, session.couponSweepStartedAtMs());
        assertEquals(0L, session.nextCouponSweepAtMs());
        assertEquals(0, session.couponSweepCollectorId());
        assertEquals(0L, session.missingPassSinceMs());
    }

    @Test
    void phaseTransitionClearsPresentationOnlyPuzzleFidgetState() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 1, 3, 1_000L);
        session.addMember(22, AgentKpqMemberState.MemberType.AGENT);
        AgentKpqMemberState member = session.member(22);
        member.setFidgetedAttemptId(3);
        member.beginFidget(new Point(10, 20), 9_000L);

        session.transition(AgentKpqSession.Phase.STAGE_3, 2_000L);

        assertEquals(-1, member.fidgetedAttemptId());
        assertNull(member.fidgetTarget());
        assertEquals(0L, member.fidgetUntilMs());
    }

    @Test
    void kingSlimeDeathKeepsReviveGraceOpenForDelayedSpawns() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 1, 3, 1_000L);

        assertFalse(session.stage5ReviveGraceActive(1, 2_000L, 2_000L));
        assertTrue(session.stage5ReviveGraceActive(0, 3_000L, 2_000L));
        assertTrue(session.stage5ReviveGraceActive(0, 4_999L, 2_000L));
        assertFalse(session.stage5ReviveGraceActive(0, 5_000L, 2_000L));
    }

    @Test
    void kingSlimeLootDelayStartsOnlyAfterObservedBossCombatEnds() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 7L, 1, 3, 1_000L);

        session.beginStage5LootDelayIfBossDefeated(0, 2_000L, 3_000L);
        assertFalse(session.stage5LootDelayActive(2_001L));

        assertTrue(session.beginStage5BossCombat(2_500L));
        session.beginStage5LootDelayIfBossDefeated(0, 3_000L, 3_000L);
        assertTrue(session.stage5LootDelayActive(5_999L));
        assertFalse(session.stage5LootDelayActive(6_000L));
    }
}
