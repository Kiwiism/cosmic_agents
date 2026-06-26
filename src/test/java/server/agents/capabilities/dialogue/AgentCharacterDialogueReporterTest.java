package server.agents.capabilities.dialogue;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCharacterDialogueReporterTest {
    @Test
    void shouldBuildStatsReportLikeLegacyChat() {
        Character agent = mock(Character.class);
        when(agent.getLevel()).thenReturn(42);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        when(agent.getStr()).thenReturn(100);
        when(agent.getDex()).thenReturn(40);
        when(agent.getInt()).thenReturn(4);
        when(agent.getLuk()).thenReturn(4);
        when(agent.getHp()).thenReturn(1200);
        when(agent.getCurrentMaxHp()).thenReturn(1500);
        when(agent.getMp()).thenReturn(300);
        when(agent.getCurrentMaxMp()).thenReturn(400);

        assertEquals(
                "lv42 fighter | str 100 dex 40 int 4 luk 4 | hp 1200/1500 mp 300/400",
                AgentCharacterDialogueReporter.statsReport(agent));
    }

    @Test
    void shouldBuildApReportLikeLegacyChat() {
        Character agent = mock(Character.class);
        when(agent.getStr()).thenReturn(12);
        when(agent.getDex()).thenReturn(13);
        when(agent.getInt()).thenReturn(14);
        when(agent.getLuk()).thenReturn(15);
        when(agent.getRemainingAp()).thenReturn(6);

        assertEquals(
                "build: str 12 / dex 13 / int 14 / luk 15, 6 ap left",
                AgentCharacterDialogueReporter.buildReport(agent));
    }

    @Test
    void shouldBuildMesoReportLikeLegacyChat() {
        Character agent = mock(Character.class);
        when(agent.getMeso()).thenReturn(6_000);

        String report = AgentCharacterDialogueReporter.mesoReport(agent);

        assertTrue(report.contains("6k"));
    }

    @Test
    void shouldBuildExpReportLikeLegacyChat() {
        Character agent = mock(Character.class);
        when(agent.getExp()).thenReturn(125);
        when(agent.getLevel()).thenReturn(5);

        assertEquals(
                AgentDialogueReportFormatter.expPercent(125, constants.game.ExpTable.getExpNeededForLevel(5)),
                AgentCharacterDialogueReporter.expReport(agent));
    }
}
