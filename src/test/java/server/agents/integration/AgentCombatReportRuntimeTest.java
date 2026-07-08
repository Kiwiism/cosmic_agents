package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.SkillFactory;
import client.inventory.WeaponType;
import net.server.PlayerBuffValueHolder;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentBuffService;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.capabilities.combat.AgentCombatPlanRuntime;
import server.agents.integration.AgentCombatReportRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;
import server.agents.integration.AgentCombatTargetRuntime;
import server.StatEffect;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentCombatReportRuntimeTest {
    @Test
    void debugStatsReportUsesAgentOwnedReportAssembly() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        Monster target = mock(Monster.class);
        AgentAttackPlan plan = new AgentAttackPlan(
                0, 0, 1, new Rectangle(), List.of(target),
                AgentAttackRoute.RANGED, 0, 0, 0, 0, 5,
                0, 1500, WeaponType.BOW);
        AgentCombatCooldownStateRuntime.maxAttackCooldown(entry, 250);
        when(target.isAlive()).thenReturn(true);
        when(target.getName()).thenReturn("Slime");

        try (MockedStatic<AgentCombatTargetRuntime> targets = mockStatic(AgentCombatTargetRuntime.class);
             MockedStatic<AgentCombatPlanRuntime> plans = mockStatic(AgentCombatPlanRuntime.class)) {
            targets.when(() -> AgentCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg))
                    .thenReturn(target);
            plans.when(() -> AgentCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg))
                    .thenReturn(plan);

            assertEquals("debug: route ranged, atk speed 5, atk cd 1.50s, remaining 0.25s, tick 50ms, ai 100ms, target Slime",
                    AgentCombatReportRuntime.debugStatsReport(entry, bot));
        }
    }

    @Test
    void buffDebugReportsDelegateToAgentBuffService() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentBuffService> buffs = mockStatic(AgentBuffService.class)) {
            buffs.when(() -> AgentBuffService.getDebugLines(entry, bot)).thenReturn(List.of("buff"));
            assertEquals(List.of("buff"), AgentCombatReportRuntime.buffDebugLines(entry, bot));
        }
    }

    @Test
    void skillBuffDebugLinesUseAgentOwnedReportAssembly() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        StatEffect activeEffect = mock(StatEffect.class);
        when(activeEffect.isSkill()).thenReturn(true);
        when(activeEffect.getSourceId()).thenReturn(1001005);
        when(activeEffect.getDuration()).thenReturn(61_000);
        when(bot.getAllBuffs()).thenReturn(List.of(new PlayerBuffValueHolder(0, activeEffect)));
        when(bot.skillIsCooling(1001005)).thenReturn(true);
        AgentCombatSkillCacheStateRuntime.addBuffSkillId(entry, 1001005);

        try (MockedStatic<SkillFactory> skillFactory = mockStatic(SkillFactory.class)) {
            skillFactory.when(() -> SkillFactory.getSkillName(1001005)).thenReturn("Slash Blast");

            assertEquals(List.of(
                            "skill buffs: last: no skill buff checks yet",
                            "active: slash blast 1m1s left",
                            "cached: slash blast (cd)"),
                    AgentCombatReportRuntime.skillBuffDebugLines(entry, bot));
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
                    AgentCombatReportRuntime.critDebugReport(bot));
        }
    }
}
