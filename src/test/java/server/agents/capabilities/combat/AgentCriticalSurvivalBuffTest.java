package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.SkillGateway;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentCriticalSurvivalBuffTest {
    @Test
    void skillBuffTogglePreventsMagicGuardCasting() {
        Character agent = mock(Character.class);
        when(agent.isAlive()).thenReturn(true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCombatBuffStateRuntime.setSkillBuffsEnabled(entry, false);
        SkillGateway skills = mock(SkillGateway.class);

        assertFalse(AgentCombatBuffRuntime.tryCastCriticalSurvivalBuff(entry, agent, skills));
        verifyNoInteractions(skills);
    }

    @Test
    void activeMagicGuardPreventsRedundantCasting() {
        Character agent = mock(Character.class);
        when(agent.isAlive()).thenReturn(true);
        when(agent.getBuffedValue(BuffStat.MAGIC_GUARD)).thenReturn(80);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        SkillGateway skills = mock(SkillGateway.class);

        assertFalse(AgentCombatBuffRuntime.tryCastCriticalSurvivalBuff(entry, agent, skills));
        verifyNoInteractions(skills);
    }
}
