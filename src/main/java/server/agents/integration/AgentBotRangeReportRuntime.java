package server.agents.integration;

import client.Character;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.bots.BotEquipManager;

/**
 * Temporary Agent-owned range-report data adapter while damage-profile source
 * data still comes from the bot equipment runtime.
 */
public final class AgentBotRangeReportRuntime {
    private AgentBotRangeReportRuntime() {
    }

    public static String rangeReport(Character bot) {
        BotEquipManager.MapDamageProfile dmgProfile = BotEquipManager.MapDamageProfile.snapshot(bot);
        BotEquipManager.MapDamageProfile hitProfile = BotEquipManager.MapDamageProfile.snapshotByAvoid(bot);
        return rangeReport(bot, dmgProfile, hitProfile);
    }

    public static String rangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return rangeReport(bot, mobProfile, mobProfile);
    }

    private static String rangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile,
                                      BotEquipManager.MapDamageProfile hitProfile) {
        AgentCombatDialogueReporter.MobHitProfile agentHitProfile = hitProfile == null
                ? null
                : new AgentCombatDialogueReporter.MobHitProfile(hitProfile.mobLevel(), hitProfile.mobAvoid());
        return AgentCombatDialogueReporter.rangeReport(
                bot, BotEquipManager.isMageJob(bot.getJob()), agentHitProfile);
    }
}
