package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentDialogueReportFormatterTest {
    @Test
    void shouldFormatStatsReportExactlyLikeLegacyChat() {
        assertEquals(
                "lv42 fighter | str 100 dex 40 int 4 luk 4 | hp 1200/1500 mp 300/400",
                AgentDialogueReportFormatter.stats(42, "fighter", 100, 40, 4, 4, 1200, 1500, 300, 400));
    }

    @Test
    void shouldFormatRangeReportsExactlyLikeLegacyChat() {
        String report = AgentDialogueReportFormatter.range(50, 99, "watk", 20, "acc", 100);

        assertEquals("my dmg is 50-99, watk 20, acc 100", report);
        assertEquals(
                "my dmg is 50-99, watk 20, acc 100 | hit 47% vs hardest mob (avd 40)",
                AgentDialogueReportFormatter.rangeWithHit(report, 47, 40));
    }

    @Test
    void shouldFormatBuildAndCritReportsExactlyLikeLegacyChat() {
        assertEquals(
                "build: str 12 / dex 13 / int 14 / luk 15, 6 ap left",
                AgentDialogueReportFormatter.build(12, 13, 14, 15, 6));
        assertEquals(
                "crit: 55% chance, 2.00x multiplier | base 100-200 | crit 200-400",
                AgentDialogueReportFormatter.crit(55, 2.0d, 100, 200, 200, 400));
    }
}
