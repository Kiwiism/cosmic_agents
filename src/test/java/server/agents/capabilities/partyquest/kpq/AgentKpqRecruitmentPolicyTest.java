package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqRecruitmentPolicyTest {
    @Test
    void fullPartyLaunchesImmediatelyButThreeWait() {
        assertTrue(AgentKpqRecruitmentPolicy.shouldLaunch(4, 0L, 1L));
        assertFalse(AgentKpqRecruitmentPolicy.shouldLaunch(3, 19_999L, 1L));
        assertTrue(AgentKpqRecruitmentPolicy.shouldLaunch(3, 45_000L, 1L));
    }

    @Test
    void willingnessRisesWithLobbyAge() {
        int initial = AgentKpqRecruitmentPolicy.joinWillingness(2, 0L);
        int waiting = AgentKpqRecruitmentPolicy.joinWillingness(2, 30_000L);
        assertTrue(waiting > initial);
    }
}
