package server.bots;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotRangeReportRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotRangeReportRuntimeTest {
    @Test
    void rangeReportUsesBotDamageProfileAndAgentFormatting() {
        Character bot = mock(Character.class);
        when(bot.getJob()).thenReturn(Job.MAGICIAN);
        when(bot.getLevel()).thenReturn(50);
        when(bot.getTotalMagic()).thenReturn(200);
        when(bot.getTotalInt()).thenReturn(100);
        when(bot.getTotalLuk()).thenReturn(50);

        String report = AgentBotRangeReportRuntime.rangeReport(bot,
                new BotEquipManager.MapDamageProfile(100, 30, 50));

        assertEquals("my dmg is 3-9, matk 200, magic acc 75 | hit 26% vs hardest mob (avd 30)", report);
    }
}
