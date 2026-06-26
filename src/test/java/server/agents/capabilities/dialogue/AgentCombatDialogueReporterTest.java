package server.agents.capabilities.dialogue;

import client.Character;
import client.Job;
import client.inventory.Inventory;
import org.junit.jupiter.api.Test;
import server.combat.CombatFormulaProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCombatDialogueReporterTest {
    @Test
    void shouldBuildPhysicalRangeReportFromEffectiveTotalsLikeLegacyChat() {
        Character agent = mock(Character.class);
        Inventory equipped = mock(Inventory.class);
        when(agent.getJob()).thenReturn(Job.FIGHTER);
        when(agent.getLevel()).thenReturn(48);
        when(agent.getTotalWatk()).thenReturn(20);
        when(agent.getTotalDex()).thenReturn(100);
        when(agent.getTotalLuk()).thenReturn(40);
        when(agent.getInventory(client.inventory.InventoryType.EQUIPPED)).thenReturn(equipped);
        when(equipped.getItem((short) -11)).thenReturn(null);
        when(equipped.iterator()).thenReturn(List.<client.inventory.Item>of().iterator());
        when(agent.calculateMinBaseDamage(20, 0.1d)).thenReturn(50);
        when(agent.calculateMaxBaseDamage(20)).thenReturn(99);

        String report = AgentCombatDialogueReporter.rangeReport(
                agent, false, new AgentCombatDialogueReporter.MobHitProfile(48, 40));

        assertEquals("my dmg is 50-99, watk 20, acc 100 | hit 47% vs hardest mob (avd 40)", report);
    }

    @Test
    void shouldBuildMageRangeReportFromEffectiveMagicTotalsLikeLegacyChat() {
        Character agent = mock(Character.class);
        when(agent.getJob()).thenReturn(Job.MAGICIAN);
        when(agent.getLevel()).thenReturn(50);
        when(agent.getTotalMagic()).thenReturn(200);
        when(agent.getTotalInt()).thenReturn(100);
        when(agent.getTotalLuk()).thenReturn(50);

        String report = AgentCombatDialogueReporter.rangeReport(
                agent, true, new AgentCombatDialogueReporter.MobHitProfile(50, 30));

        assertEquals("my dmg is 3-9, matk 200, magic acc 75 | hit 26% vs hardest mob (avd 30)", report);
    }

    @Test
    void shouldReportNoCritPassiveLikeLegacyChat() {
        String report = AgentCombatDialogueReporter.critReport(
                CombatFormulaProvider.CritProfile.NONE,
                new CombatFormulaProvider.DamageProfile(100, 200, false, false));

        assertEquals("i can't crit (my job doesn't have a crit passive)", report);
    }

    @Test
    void shouldBuildCritDebugReportLikeLegacyChat() {
        String report = AgentCombatDialogueReporter.critReport(
                new CombatFormulaProvider.CritProfile(0.55d, 2.0d),
                new CombatFormulaProvider.DamageProfile(100, 200, false, false));

        assertEquals("crit: 55% chance, 2.00x multiplier | base 100-200 | crit 200-400", report);
    }

    @Test
    void shouldCapCritDamageLikeLegacyChat() {
        String report = AgentCombatDialogueReporter.critReport(
                new CombatFormulaProvider.CritProfile(0.5d, 2.0d),
                new CombatFormulaProvider.DamageProfile(60_000, 80_000, false, false));

        assertEquals("crit: 50% chance, 2.00x multiplier | base 60000-80000 | crit 99999-99999", report);
    }

    @Test
    void shouldBuildCombatDebugStatsReportLikeLegacyChat() {
        String report = AgentCombatDialogueReporter.debugStatsReport(
                "skill", 4, 1.23d, 0.45d, 80, 250, "Slime");

        assertEquals("debug: route skill, atk speed 4, atk cd 1.23s, remaining 0.45s, tick 80ms, ai 250ms, target Slime",
                report);
    }

    @Test
    void shouldBuildSkillBuffDebugLinesLikeLegacyChat() {
        List<String> lines = AgentCombatDialogueReporter.skillBuffDebugLines(
                "cast Haste",
                125_000L,
                List.of(new AgentCombatDialogueReporter.ActiveSkillBuffDebugLine("Haste", 61_000L)),
                List.of(
                        new AgentCombatDialogueReporter.CachedSkillBuffDebugLine("Haste", "cd"),
                        new AgentCombatDialogueReporter.CachedSkillBuffDebugLine(
                                "Bless", AgentCombatDialogueReporter.skillBuffRebuffStatus(5_000L))));

        assertEquals(List.of(
                "skill buffs: last: cast Haste (2m5s ago)",
                "active: haste 1m1s left",
                "cached: haste (cd), bless (rebuff 5s)"
        ), lines);
    }

    @Test
    void shouldBuildEmptySkillBuffDebugLinesLikeLegacyChat() {
        assertEquals(List.of(
                        "skill buffs: last: all skill buffs active or on cooldown",
                        "active: none",
                        "cached: none cached"),
                AgentCombatDialogueReporter.skillBuffDebugLines(
                        "all skill buffs active or on cooldown", -1L, List.of(), List.of()));
    }

    @Test
    void shouldFormatBuffAgeLikeLegacyChat() {
        assertEquals("0s", AgentCombatDialogueReporter.formatBuffAge(-10));
        assertEquals("59s", AgentCombatDialogueReporter.formatBuffAge(59_999));
        assertEquals("1m0s", AgentCombatDialogueReporter.formatBuffAge(60_000));
    }
}
