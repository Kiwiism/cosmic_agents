package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.combat.CombatFormulaProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentBotCombatReportRuntimeTest {
    @Test
    void debugReportsDelegateToLegacyCombatAndBuffManagers() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<BotCombatManager> combat = mockStatic(BotCombatManager.class);
             MockedStatic<BotBuffManager> buffs = mockStatic(BotBuffManager.class)) {
            combat.when(() -> BotCombatManager.describeDebugStats(entry, bot)).thenReturn("debug");
            combat.when(() -> BotCombatManager.getSkillBuffDebugLines(entry, bot)).thenReturn(List.of("skill"));
            buffs.when(() -> BotBuffManager.getDebugLines(entry, bot)).thenReturn(List.of("buff"));

            assertEquals("debug", AgentBotCombatReportRuntime.debugStatsReport(entry, bot));
            assertEquals(List.of("buff"), AgentBotCombatReportRuntime.buffDebugLines(entry, bot));
            assertEquals(List.of("skill"), AgentBotCombatReportRuntime.skillBuffDebugLines(entry, bot));
        }
    }

    @Test
    void critDebugReportUsesCombatFormulaProviderAndAgentFormatting() {
        Character bot = mock(Character.class);
        CombatFormulaProvider formula = mock(CombatFormulaProvider.class);

        try (MockedStatic<CombatFormulaProvider> formulas = mockStatic(CombatFormulaProvider.class)) {
            formulas.when(CombatFormulaProvider::getInstance).thenReturn(formula);
            when(formula.resolveCritProfile(bot))
                    .thenReturn(new CombatFormulaProvider.CritProfile(0.55d, 2.0d));
            when(formula.resolveDamageProfile(bot, 0, 0, false))
                    .thenReturn(new CombatFormulaProvider.DamageProfile(100, 200, false, false));

            assertEquals("crit: 55% chance, 2.00x multiplier | base 100-200 | crit 200-400",
                    AgentBotCombatReportRuntime.critDebugReport(bot));
        }
    }
}
