package server.agents.capabilities.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillReportDecisionServiceTest {
    @Test
    void skillReportDecisionUsesAgentSkillReportFlowForNoJobSkillsWithSp() {
        Character agent = mock(Character.class);
        when(agent.isBeginnerJob()).thenReturn(false);
        when(agent.getRemainingSp()).thenReturn(3);
        when(agent.getSkills()).thenReturn(Map.of());

        AgentSkillReportFlow.SkillReportDecision decision =
                AgentSkillReportDecisionService.skillReportDecision(agent);

        assertFalse(decision.requestSkillTreeChoice());
        assertFalse(decision.clearPendingAction());
        assertEquals(1, decision.replies().size());
    }
}
