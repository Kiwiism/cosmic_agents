package server.agents.integration;

import client.Character;
import client.Job;
import constants.game.ExpTable;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueReportFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotCharacterReportRuntimeTest {
    @Test
    void statsReportUsesAgentCharacterFormatter() {
        Character bot = mock(Character.class);
        when(bot.getLevel()).thenReturn(42);
        when(bot.getJob()).thenReturn(Job.FIGHTER);
        when(bot.getStr()).thenReturn(100);
        when(bot.getDex()).thenReturn(40);
        when(bot.getInt()).thenReturn(4);
        when(bot.getLuk()).thenReturn(4);
        when(bot.getHp()).thenReturn(1200);
        when(bot.getCurrentMaxHp()).thenReturn(1500);
        when(bot.getMp()).thenReturn(300);
        when(bot.getCurrentMaxMp()).thenReturn(400);

        assertEquals("lv42 fighter | str 100 dex 40 int 4 luk 4 | hp 1200/1500 mp 300/400",
                AgentBotCharacterReportRuntime.statsReport(bot));
    }

    @Test
    void buildReportUsesAgentCharacterFormatter() {
        Character bot = mock(Character.class);
        when(bot.getStr()).thenReturn(4);
        when(bot.getDex()).thenReturn(5);
        when(bot.getInt()).thenReturn(6);
        when(bot.getLuk()).thenReturn(7);
        when(bot.getRemainingAp()).thenReturn(8);

        assertEquals("build: str 4 / dex 5 / int 6 / luk 7, 8 ap left",
                AgentBotCharacterReportRuntime.buildReport(bot));
    }

    @Test
    void mesoAndExpReportsUseAgentCharacterFormatter() {
        Character bot = mock(Character.class);
        when(bot.getMeso()).thenReturn(1234);
        when(bot.getExp()).thenReturn(5678);
        when(bot.getLevel()).thenReturn(9);

        assertTrue(AgentBotCharacterReportRuntime.mesoReport(bot).contains("1.2k"));
        assertEquals(
                AgentDialogueReportFormatter.expPercent(5678, ExpTable.getExpNeededForLevel(9)),
                AgentBotCharacterReportRuntime.expReport(bot));
    }
}
