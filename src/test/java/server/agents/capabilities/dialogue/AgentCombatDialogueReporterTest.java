package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.combat.CombatFormulaProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentCombatDialogueReporterTest {
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
}
