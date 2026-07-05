package server.agents.capabilities.dialogue;

import client.Character;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.capabilities.equipment.AgentMapDamageProfile;

public final class AgentRangeReportService {
    private AgentRangeReportService() {
    }

    public static String rangeReport(Character agent) {
        AgentMapDamageProfile damageProfile = AgentMapDamageProfile.snapshot(agent);
        AgentMapDamageProfile hitProfile = AgentMapDamageProfile.snapshotByAvoid(agent);
        return rangeReport(agent, damageProfile, hitProfile);
    }

    public static String rangeReport(Character agent, AgentMapDamageProfile mobProfile) {
        return rangeReport(agent, mobProfile, mobProfile);
    }

    private static String rangeReport(Character agent,
                                      AgentMapDamageProfile mobProfile,
                                      AgentMapDamageProfile hitProfile) {
        AgentCombatDialogueReporter.MobHitProfile agentHitProfile = hitProfile == null
                ? null
                : new AgentCombatDialogueReporter.MobHitProfile(hitProfile.mobLevel(), hitProfile.mobAvoid());
        return AgentCombatDialogueReporter.rangeReport(
                agent, AgentEquipmentService.isMageJob(agent.getJob()), agentHitProfile);
    }
}
