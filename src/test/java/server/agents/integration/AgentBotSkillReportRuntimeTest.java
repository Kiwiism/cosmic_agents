package server.agents.integration;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotSkillReportRuntimeTest {
    @Test
    void skillReportDecisionUsesAgentSkillReportFlowForNoJobSkillsWithSp() {
        Character bot = mock(Character.class);
        when(bot.isBeginnerJob()).thenReturn(false);
        when(bot.getRemainingSp()).thenReturn(3);
        when(bot.getSkills()).thenReturn(Map.of());

        AgentSkillReportFlow.SkillReportDecision decision =
                AgentBotSkillReportRuntime.skillReportDecision(bot);

        assertFalse(decision.requestSkillTreeChoice());
        assertFalse(decision.clearPendingAction());
        assertEquals(1, decision.replies().size());
    }
}
