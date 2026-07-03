package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationHooks;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationScope;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentEquipmentRecommendationPolicyTest {
    @Test
    void immediateCandidateAcceptsCurrentlyWearableEquip() {
        Character agent = agent();
        Equip equip = mock(Equip.class);
        RecommendationHooks hooks = mock(RecommendationHooks.class);
        when(hooks.canWear(agent, equip, (short) -5)).thenReturn(true);

        assertTrue(AgentEquipmentRecommendationPolicy.isRecommendationCandidate(
                agent, hooks, equip, (short) -5, RecommendationScope.IMMEDIATE));
    }

    @Test
    void immediateCandidateAcceptsStatOnlyBlockedEquip() {
        Character agent = agent();
        Equip equip = mock(Equip.class);
        RecommendationHooks hooks = mock(RecommendationHooks.class);
        when(hooks.canWear(agent, equip, (short) -5)).thenReturn(false);
        when(hooks.meetsReqs(equip, Job.FIGHTER, 30,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, 12)).thenReturn(true);

        assertTrue(AgentEquipmentRecommendationPolicy.isRecommendationCandidate(
                agent, hooks, equip, (short) -5, RecommendationScope.IMMEDIATE));
    }

    @Test
    void immediateCandidateRejectsUnwearableNonFutureEquip() {
        Character agent = agent();
        Equip equip = mock(Equip.class);
        RecommendationHooks hooks = mock(RecommendationHooks.class);
        when(hooks.canWear(agent, equip, (short) -5)).thenReturn(false);
        when(hooks.meetsReqs(equip, Job.FIGHTER, 30,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, 12)).thenReturn(false);

        assertFalse(AgentEquipmentRecommendationPolicy.isRecommendationCandidate(
                agent, hooks, equip, (short) -5, RecommendationScope.IMMEDIATE));
    }

    @Test
    void futureCandidateUsesStatOnlyFutureGate() {
        Character agent = agent();
        Equip equip = mock(Equip.class);
        RecommendationHooks hooks = mock(RecommendationHooks.class);
        when(hooks.canWear(agent, equip, (short) -5)).thenReturn(true);
        when(hooks.meetsReqs(equip, Job.FIGHTER, 30,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, 12)).thenReturn(false);

        assertFalse(AgentEquipmentRecommendationPolicy.isRecommendationCandidate(
                agent, hooks, equip, (short) -5, RecommendationScope.FUTURE));
    }

    private static Character agent() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        when(agent.getLevel()).thenReturn(30);
        when(agent.getFame()).thenReturn(12);
        return agent;
    }
}
