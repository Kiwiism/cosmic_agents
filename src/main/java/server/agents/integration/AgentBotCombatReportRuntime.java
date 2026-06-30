package server.agents.integration;

import client.Character;
import net.server.PlayerBuffValueHolder;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.bots.BotBuffManager;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;
import server.StatEffect;
import server.combat.CombatFormulaProvider;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;

/**
 * Temporary Agent-owned combat report adapter while combat and buff debug data
 * still comes from bot runtime managers.
 */
public final class AgentBotCombatReportRuntime {
    private AgentBotCombatReportRuntime() {
    }

    public static String debugStatsReport(BotEntry entry, Character bot) {
        Monster target = AgentBotGrindTargetStateRuntime.target(entry);
        if (target == null || !target.isAlive()) {
            target = AgentBotCombatTargetRuntime.findGrindTarget(entry, bot, AgentCombatConfig.cfg);
        }

        AgentAttackPlan plan = target != null
                ? AgentBotCombatPlanRuntime.planAttack(entry, bot, target, AgentCombatConfig.cfg)
                : null;
        String route = plan != null
                ? plan.route.name().toLowerCase()
                : AgentAttackExecutionProvider.determineBasicAttackRoute(bot).name().toLowerCase();
        int speed = plan != null
                ? plan.speed
                : AgentAttackExecutionProvider.buildBasicAttackData(bot, bot.getPosition()).speed();
        double cooldownSeconds = (plan != null ? plan.cooldownMs : 0) / 1000.0;
        double remainingSeconds = AgentBotCombatCooldownStateRuntime.attackCooldownMs(entry) / 1000.0;
        String targetName = target != null ? target.getName() : "none";

        return AgentCombatDialogueReporter.debugStatsReport(
                route, speed, cooldownSeconds, remainingSeconds,
                BotMovementManager.configuredTickMs(), BotManager.cfg.AI_TICK_MS, targetName);
    }

    public static String critDebugReport(Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        return AgentCombatDialogueReporter.critReport(crit, dmg);
    }

    public static List<String> buffDebugLines(BotEntry entry, Character bot) {
        return BotBuffManager.getDebugLines(entry, bot);
    }

    public static List<String> skillBuffDebugLines(BotEntry entry, Character bot) {
        long now = System.currentTimeMillis();

        long lastActionAgeMs = AgentBotSkillBuffDebugStateRuntime.lastActionAgeMs(entry, now);
        List<AgentCombatDialogueReporter.ActiveSkillBuffDebugLine> activeBuffs = new ArrayList<>();
        for (PlayerBuffValueHolder holder : bot.getAllBuffs()) {
            StatEffect effect = holder.effect;
            if (effect == null || !effect.isSkill()) {
                continue;
            }
            int skillId = effect.getSourceId();
            long remainingMs = effect.getDuration() > 0
                    ? Math.max(0, effect.getDuration() - holder.usedTime)
                    : 0L;
            activeBuffs.add(new AgentCombatDialogueReporter.ActiveSkillBuffDebugLine(
                    AgentCombatDialogueReporter.combatSkillLabel(skillId), remainingMs));
        }

        List<AgentCombatDialogueReporter.CachedSkillBuffDebugLine> cachedBuffs = new ArrayList<>();
        for (int skillId : AgentBotCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            boolean cooling = bot.skillIsCooling(skillId);
            long nextAt = AgentBotCombatBuffStateRuntime.nextBuffAt(entry, skillId);
            String status;
            if (cooling) {
                status = "cd";
            } else if (now < nextAt) {
                status = AgentCombatDialogueReporter.skillBuffRebuffStatus(nextAt - now);
            } else {
                status = "ready";
            }
            cachedBuffs.add(new AgentCombatDialogueReporter.CachedSkillBuffDebugLine(
                    AgentCombatDialogueReporter.combatSkillLabel(skillId), status));
        }

        return AgentCombatDialogueReporter.skillBuffDebugLines(
                AgentBotSkillBuffDebugStateRuntime.lastActionSummary(entry), lastActionAgeMs, activeBuffs, cachedBuffs);
    }
}
