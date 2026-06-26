package server.agents.capabilities.dialogue;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

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

        assertEquals("watk", AgentDialogueReportFormatter.rangeAttackLabel(false));
        assertEquals("matk", AgentDialogueReportFormatter.rangeAttackLabel(true));
        assertEquals("acc", AgentDialogueReportFormatter.rangeAccuracyLabel(false));
        assertEquals("magic acc", AgentDialogueReportFormatter.rangeAccuracyLabel(true));
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
        assertEquals(
                AgentDialogueReportFormatter.expPercent(125, constants.game.ExpTable.getExpNeededForLevel(5)),
                AgentDialogueReportFormatter.expReport(125, 5));
        assertEquals("0%", AgentDialogueReportFormatter.expPercent(125, 0));

        assertEquals("no scrolls on me", AgentDialogueReportFormatter.scrollCount(0));
        assertEquals("I have 1 scroll on me", AgentDialogueReportFormatter.scrollCount(1));
        assertEquals("I have 2 scrolls on me", AgentDialogueReportFormatter.scrollCount(2));

        assertEquals("no pots on me rn", AgentDialogueReportFormatter.potionCount(0, 0));
        assertEquals("I have 1 hp pot, no mp pots", AgentDialogueReportFormatter.potionCount(1, 0));
        assertEquals("no hp pots, 2 mp pots", AgentDialogueReportFormatter.potionCount(0, 2));
        assertEquals("I have 3 hp pots and 4 mp pots", AgentDialogueReportFormatter.potionCount(3, 4));
        assertEquals("I have 3 hp pots and 4 mp pots", AgentDialogueReportFormatter.potionReport(3, 4));
    }

    @Test
    void shouldFormatCompactMesosAndMesoReportsExactlyLikeLegacyChat() {
        assertEquals("999", AgentDialogueReportFormatter.compactMesos(999));
        assertEquals("6k", AgentDialogueReportFormatter.compactMesos(6_000));
        assertEquals("3.5k", AgentDialogueReportFormatter.compactMesos(3_500));
        assertEquals("2.1m", AgentDialogueReportFormatter.compactMesos(2_100_000));

        assertEquals("I have 6k", AgentDialogueReportFormatter.mesoReport(6_000, java.util.List.of("I have %s")));
        assertEquals("6k", AgentDialogueReportFormatter.compactMesos(6_000));
        assertEquals(true, AgentDialogueReportFormatter.mesoReport(6_000).contains("6k"));
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
        assertEquals("wb! we've been waiting at town since u went offline",
                AgentDialogueReportFormatter.welcomeBackOfflineReply(
                        "wb! we've been waiting at %s since u went offline", null));
        assertEquals("wb! we've been waiting at town since u went offline",
                AgentDialogueReportFormatter.welcomeBackOfflineReply(
                        "wb! we've been waiting at %s since u went offline", " "));
        assertEquals("we're low on hp pots too, boss",
                AgentDialogueReportFormatter.ownerPotShortageReply("we're low on %s pots too, boss", "hp"));
        assertEquals("hp", AgentDialogueReportFormatter.potionTypeLabel(true));
        assertEquals("mp", AgentDialogueReportFormatter.potionTypeLabel(false));
        assertEquals("already famed Alice this month",
                AgentDialogueReportFormatter.fameSamePersonReply("already famed %s this month", "Alice"));
        assertEquals("famed Alice", AgentDialogueReportFormatter.fameOkReply("famed %s", "Alice"));
        assertEquals("done", AgentDialogueReportFormatter.fameOkReply("done", "Alice"));
    }

    @Test
    void shouldFormatApBuildRepliesExactlyLikeLegacyChat() {
        assertEquals(
                "dexless it is! keeping dex at 25, rest into luk",
                AgentDialogueReportFormatter.apPureBuildConfirm("dexless", "dex", 25, "luk"));
        assertEquals(
                "dexless it is! keeping dex at 25, rest into luk",
                AgentDialogueReportFormatter.apPureBuildConfirm(
                        AgentDialogueReportFormatter.THIEF_DEXLESS_AP_BUILD, 25));
        assertEquals("already doing dexless!", AgentDialogueReportFormatter.apPureBuildAlready("dexless"));
        assertEquals("already doing dexless!",
                AgentDialogueReportFormatter.apPureBuildAlready(
                        AgentDialogueReportFormatter.WARRIOR_DEXLESS_AP_BUILD));
        assertEquals(
                "ok! keeping dex at 40, rest into str",
                AgentDialogueReportFormatter.apFixedBuildConfirm("dex", 40, "str"));
        assertEquals(
                "ok! keeping dex at 40, rest into str",
                AgentDialogueReportFormatter.apFixedBuildConfirm(
                        AgentDialogueReportFormatter.WARRIOR_FIXED_DEX_AP_BUILD, 40));
        assertEquals("already doing 40 dex build!", AgentDialogueReportFormatter.apFixedBuildAlready(40, "dex"));
        assertEquals("already doing 40 dex build!",
                AgentDialogueReportFormatter.apFixedBuildAlready(
                        AgentDialogueReportFormatter.THIEF_FIXED_DEX_AP_BUILD, 40));
        assertEquals("lukless it is! keeping luk at 4, rest into int",
                AgentDialogueReportFormatter.apPureBuildConfirm(
                        AgentDialogueReportFormatter.MAGICIAN_LUKLESS_AP_BUILD, 4));
        assertEquals("strless it is! keeping str at 4, rest into dex",
                AgentDialogueReportFormatter.apPureBuildConfirm(
                        AgentDialogueReportFormatter.BOWMAN_STRLESS_AP_BUILD, 4));
        assertEquals("ok! keeping luk at 23, rest into int",
                AgentDialogueReportFormatter.apFixedBuildConfirm(
                        AgentDialogueReportFormatter.MAGICIAN_FIXED_LUK_AP_BUILD, 23));
        assertEquals("ok! keeping str at 12, rest into dex",
                AgentDialogueReportFormatter.apFixedBuildConfirm(
                        AgentDialogueReportFormatter.BOWMAN_FIXED_STR_AP_BUILD, 12));
        assertEquals("luk", AgentDialogueReportFormatter.statTypeName("LUK"));
    }

    @Test
    void shouldFormatJobDisplayNamesExactlyLikeLegacyChat() {
        assertEquals("mage", AgentDialogueReportFormatter.jobDisplayName(Job.MAGICIAN));
        assertEquals("f/p wizard", AgentDialogueReportFormatter.jobDisplayName(Job.FP_WIZARD));
        assertEquals("dark knight", AgentDialogueReportFormatter.jobDisplayName(Job.DARKKNIGHT));
        assertEquals("night lord", AgentDialogueReportFormatter.jobDisplayName(Job.NIGHTLORD));
        assertEquals("dawn warrior", AgentDialogueReportFormatter.jobDisplayName(Job.DAWNWARRIOR2));
        assertEquals("aran", AgentDialogueReportFormatter.jobDisplayName(Job.ARAN4));
        assertEquals("corsair", AgentDialogueReportFormatter.jobDisplayName(Job.CORSAIR));
    }

    @Test
    void shouldFormatSkillTreeLabelsAndChoicePromptExactlyLikeLegacyChat() {
        assertEquals("warrior (100)", AgentDialogueReportFormatter.skillTreeLabel(100));
        assertEquals("f/p wizard (210)", AgentDialogueReportFormatter.skillTreeLabel(210));
        assertEquals("dawn warrior 2nd job (1110)", AgentDialogueReportFormatter.skillTreeLabel(1110));
        assertEquals("aran 4th job (2112)", AgentDialogueReportFormatter.skillTreeLabel(2112));
        assertEquals("evan 10th job (2218)", AgentDialogueReportFormatter.skillTreeLabel(2218));
        assertEquals("tree 999999", AgentDialogueReportFormatter.skillTreeLabel(999999));
        assertEquals(
                "which skill tree? warrior (100), fighter (110), crusader (111)",
                AgentDialogueReportFormatter.skillTreeChoicePrompt(List.of(100, 110, 111)));
    }

    @Test
    void shouldFormatSkillReportsExactlyLikeLegacyChat() {
        List<AgentDialogueReportFormatter.AgentSkillLine> beginnerSkills = List.of(
                new AgentDialogueReportFormatter.AgentSkillLine(1000, "Three Snails", 1),
                new AgentDialogueReportFormatter.AgentSkillLine(1001, "Recovery", 2));
        assertEquals(
                "beginner: Three Snails lv1, Recovery lv2 | 3 beginner SP left",
                AgentDialogueReportFormatter.beginnerSkillReport(beginnerSkills, 3));

        List<AgentDialogueReportFormatter.AgentSkillLine> fighterSkills = List.of(
                new AgentDialogueReportFormatter.AgentSkillLine(1100000, "Improving Max HP Increase", 10),
                new AgentDialogueReportFormatter.AgentSkillLine(1100001, "Sword Mastery", 20),
                new AgentDialogueReportFormatter.AgentSkillLine(1101004, "Power Guard", 20),
                new AgentDialogueReportFormatter.AgentSkillLine(1101005, "Rage", 20));
        assertEquals(List.of(
                        "fighter (110): Improving Max HP Increase lv10, Sword Mastery lv20, Power Guard lv20",
                        "more fighter (110): Rage lv20"),
                AgentDialogueReportFormatter.skillTreeReportLines(110, fighterSkills));
    }
}
