package server.agents.integration;

import server.agents.capabilities.equipment.AgentMapDamageProfile;

import client.Character;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.capabilities.equipment.AgentEquipmentService;

/**
 * Temporary Agent-owned range-report data adapter while damage-profile source
 * data still comes from the bot equipment runtime.
 */
public final class AgentBotRangeReportRuntime {
    private AgentBotRangeReportRuntime() {
    }

    public static String rangeReport(Character bot) {
        AgentMapDamageProfile dmgProfile = AgentMapDamageProfile.snapshot(bot);
        AgentMapDamageProfile hitProfile = AgentMapDamageProfile.snapshotByAvoid(bot);
        return rangeReport(bot, dmgProfile, hitProfile);
    }

    public static String rangeReport(Character bot, AgentMapDamageProfile mobProfile) {
        return rangeReport(bot, mobProfile, mobProfile);
    }

    private static String rangeReport(Character bot, AgentMapDamageProfile mobProfile,
                                      AgentMapDamageProfile hitProfile) {
        AgentCombatDialogueReporter.MobHitProfile agentHitProfile = hitProfile == null
                ? null
                : new AgentCombatDialogueReporter.MobHitProfile(hitProfile.mobLevel(), hitProfile.mobAvoid());
        return AgentCombatDialogueReporter.rangeReport(
                bot, AgentEquipmentService.isMageJob(bot.getJob()), agentHitProfile);
    }
}
