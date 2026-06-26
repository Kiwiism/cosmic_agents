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

    @Test
    void shouldFormatInventorySupplyAndExpReportsExactlyLikeLegacyChat() {
        assertEquals("12.5%", AgentDialogueReportFormatter.expPercent(125, 1000));
        assertEquals("0%", AgentDialogueReportFormatter.expPercent(125, 0));

        assertEquals("no scrolls on me", AgentDialogueReportFormatter.scrollCount(0));
        assertEquals("I have 1 scroll on me", AgentDialogueReportFormatter.scrollCount(1));
        assertEquals("I have 2 scrolls on me", AgentDialogueReportFormatter.scrollCount(2));

        assertEquals("no pots on me rn", AgentDialogueReportFormatter.potionCount(0, 0));
        assertEquals("I have 1 hp pot, no mp pots", AgentDialogueReportFormatter.potionCount(1, 0));
        assertEquals("no hp pots, 2 mp pots", AgentDialogueReportFormatter.potionCount(0, 2));
        assertEquals("I have 3 hp pots and 4 mp pots", AgentDialogueReportFormatter.potionCount(3, 4));
    }

    @Test
    void shouldFormatCompactMesosAndMesoReportsExactlyLikeLegacyChat() {
        assertEquals("999", AgentDialogueReportFormatter.compactMesos(999));
        assertEquals("6k", AgentDialogueReportFormatter.compactMesos(6_000));
        assertEquals("3.5k", AgentDialogueReportFormatter.compactMesos(3_500));
        assertEquals("2.1m", AgentDialogueReportFormatter.compactMesos(2_100_000));

        assertEquals("I have 6k", AgentDialogueReportFormatter.mesoReport(6_000, java.util.List.of("I have %s")));
    }

    @Test
    void shouldFormatMovementReportsExactlyLikeLegacyChat() {
        assertEquals("speed 120% jump 110%", AgentDialogueReportFormatter.movementStatLine(120, 110));
        assertEquals(
                "speed 100% jump 100% (map forced; raw 140%/125%)",
                AgentDialogueReportFormatter.movementStatLineForced(100, 100, 140, 125));
        assertEquals(
                "walk 125.5 px/s, hforce 1.2, climb 3 px/tick",
                AgentDialogueReportFormatter.movementWalkNoMap(125.5d, 1.2d, 3));
        assertEquals(
                "jump 5.5/tick, rope 4.5/tick, max jump 42.0 px",
                AgentDialogueReportFormatter.movementJumpNoMap(5.5d, 4.5d, 42.0d));
        assertEquals(
                "walk 125.5 px/s, 7 px/tick, climb 3, hforce 1.2",
                AgentDialogueReportFormatter.movementWalkWithMap(125.5d, 7, 3, 1.2d));
        assertEquals(
                "jump 5.5, rope 4.5, max 42.0 px, reach 123/234 px",
                AgentDialogueReportFormatter.movementJumpWithMap(5.5d, 4.5d, 42.0d, 123, 234));
    }

    @Test
    void shouldFormatDropOrTradePromptsExactlyLikeLegacyChat() {
        assertEquals(
                "got 2 scrolls, want me to trade or drop?",
                AgentDialogueReportFormatter.dropOrTradePrompt(
                        "scrolls", 2, java.util.List.of("got %s, want me to trade or drop?")));
        assertEquals(
                "want me to trade or drop chaos scroll?",
                AgentDialogueReportFormatter.dropOrTradePrompt(
                        "name:chaos scroll", 0, java.util.List.of("want me to trade or drop %s?")));
    }

    @Test
    void shouldApplyDialogueTemplatesExactlyLikeLegacyChat() {
        assertEquals("ok, ill change to warrior!",
                AgentDialogueReportFormatter.jobChangeReply("ok, ill change to %s!", "warrior"));
        assertEquals("wb! we've been waiting at Henesys since u went offline",
                AgentDialogueReportFormatter.welcomeBackOfflineReply(
                        "wb! we've been waiting at %s since u went offline", "Henesys"));
        assertEquals("we're low on hp pots too, boss",
                AgentDialogueReportFormatter.ownerPotShortageReply("we're low on %s pots too, boss", "hp"));
        assertEquals("already famed Alice this month",
                AgentDialogueReportFormatter.fameSamePersonReply("already famed %s this month", "Alice"));
        assertEquals("famed Alice", AgentDialogueReportFormatter.fameOkReply("famed %s", "Alice"));
        assertEquals("done", AgentDialogueReportFormatter.fameOkReply("done", "Alice"));
    }
}
