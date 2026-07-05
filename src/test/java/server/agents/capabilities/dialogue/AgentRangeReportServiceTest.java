package server.agents.capabilities.dialogue;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.equipment.AgentMapDamageProfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRangeReportServiceTest {
    @Test
    void rangeReportUsesDamageProfileAndAgentFormatting() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.MAGICIAN);
        when(agent.getLevel()).thenReturn(50);
        when(agent.getTotalMagic()).thenReturn(200);
        when(agent.getTotalInt()).thenReturn(100);
        when(agent.getTotalLuk()).thenReturn(50);

        String report = AgentRangeReportService.rangeReport(agent,
                new AgentMapDamageProfile(100, 30, 50));

        assertEquals("my dmg is 3-9, matk 200, magic acc 75 | hit 26% vs hardest mob (avd 30)", report);
    }
}
