package server.bots;

import client.Character;
import client.SkillFactory;
import net.server.PlayerBuffValueHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotCombatReportRuntime;
import server.agents.integration.AgentBotCombatSkillCacheStateRuntime;
import server.StatEffect;
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
            buffs.when(() -> BotBuffManager.getDebugLines(entry, bot)).thenReturn(List.of("buff"));

            assertEquals("debug", AgentBotCombatReportRuntime.debugStatsReport(entry, bot));
            assertEquals(List.of("buff"), AgentBotCombatReportRuntime.buffDebugLines(entry, bot));
        }
    }

    @Test
    void skillBuffDebugLinesUseAgentOwnedReportAssembly() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        StatEffect activeEffect = mock(StatEffect.class);
        when(activeEffect.isSkill()).thenReturn(true);
        when(activeEffect.getSourceId()).thenReturn(1001005);
        when(activeEffect.getDuration()).thenReturn(61_000);
        when(bot.getAllBuffs()).thenReturn(List.of(new PlayerBuffValueHolder(0, activeEffect)));
        when(bot.skillIsCooling(1001005)).thenReturn(true);
        AgentBotCombatSkillCacheStateRuntime.addBuffSkillId(entry, 1001005);

        try (MockedStatic<SkillFactory> skillFactory = mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkillName(1001005)).thenReturn("Slash Blast");

            assertEquals(List.of(
                            "skill buffs: last: no skill buff checks yet",
                            "active: slash blast 1m1s left",
                            "cached: slash blast (cd)"),
                    AgentBotCombatReportRuntime.skillBuffDebugLines(entry, bot));
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
