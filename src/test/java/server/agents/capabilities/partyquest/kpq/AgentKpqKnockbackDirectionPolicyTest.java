package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentKpqKnockbackDirectionPolicyTest {
    @Test
    void randomizesSuccessfulStageOneAgentKnockbackLeftOrRight() {
        assertEquals(-3, AgentKpqKnockbackDirectionPolicy.adjustAirVelocityX(
                3, 100, 0.5f, 0.25f));
        assertEquals(3, AgentKpqKnockbackDirectionPolicy.adjustAirVelocityX(
                -3, 100, 0.5f, 0.75f));
    }

    @Test
    void preservesNaturalDirectionOutsideConfiguredVariation() {
        assertEquals(-3, AgentKpqKnockbackDirectionPolicy.adjustAirVelocityX(
                -3, 0, 0.0f, 0.75f));
        assertEquals(0, AgentKpqKnockbackDirectionPolicy.randomDirectionPercent(
                AgentKpqDefinition.STAGE_2_MAP, AgentKpqSession.Phase.STAGE_2,
                AgentKpqMemberState.MemberType.AGENT, 100));
        assertEquals(0, AgentKpqKnockbackDirectionPolicy.randomDirectionPercent(
                AgentKpqDefinition.STAGE_1_MAP, AgentKpqSession.Phase.STAGE_1,
                AgentKpqMemberState.MemberType.HUMAN, 100));
    }
}
