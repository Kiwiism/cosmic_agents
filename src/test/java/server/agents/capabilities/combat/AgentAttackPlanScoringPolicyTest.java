package server.agents.capabilities.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.WeaponType;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentAttackPlanScoringPolicyTest {
    @Test
    void shouldUseLegacyTieBreakWhenEmptyPlansHaveEqualScore() {
        Character agent = agent();
        AgentAttackPlan slower = plan(2001005, 900);
        AgentAttackPlan faster = plan(1001005, 600);

        assertEquals(faster, AgentAttackPlanScoringPolicy.selectBestAttackPlan(agent, List.of(slower, faster)));
    }

    @Test
    void shouldUseLowerSkillIdWhenCooldownsTie() {
        Character agent = agent();
        AgentAttackPlan higherSkill = plan(2001005, 720);
        AgentAttackPlan lowerSkill = plan(1001005, 720);

        assertEquals(lowerSkill, AgentAttackPlanScoringPolicy.selectBestAttackPlan(agent,
                List.of(higherSkill, lowerSkill)));
    }

    private static AgentAttackPlan plan(int skillId, int cooldownMs) {
        return new AgentAttackPlan(skillId, 0, 1, null, List.of(), AgentAttackRoute.CLOSE,
                0, 0, 0, 0, 0, 0, cooldownMs, WeaponType.SWORD1H);
    }

    private static Character agent() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        when(agent.getInventory(InventoryType.EQUIPPED)).thenReturn(equipped);
        when(agent.getJob()).thenReturn(Job.BEGINNER);
        when(agent.getStr()).thenReturn(100);
        when(agent.getDex()).thenReturn(20);
        when(agent.getLuk()).thenReturn(20);
        when(agent.getInt()).thenReturn(20);
        when(agent.getTotalWatk()).thenReturn(30);
        return agent;
    }
}
