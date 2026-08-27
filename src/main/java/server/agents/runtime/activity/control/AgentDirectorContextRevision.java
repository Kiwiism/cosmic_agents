package server.agents.runtime.activity.control;

import server.agents.runtime.activity.world.AgentWorldContext;
import server.agents.runtime.activity.world.AgentWorldDirectiveEnvelope;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

/** Optimistic-concurrency token derived only from decision-relevant live facts. */
final class AgentDirectorContextRevision {
    private AgentDirectorContextRevision() { }

    static String create(
            AgentWorldContext context,
            AgentDirectorResourceSnapshot resources,
            AgentDirectorEnergySnapshot energy,
            AgentDirectorProfileSnapshot profile,
            AgentWorldDirectorSession session,
            List<AgentWorldDirectiveEnvelope> directives) {
        StringBuilder value = new StringBuilder(512)
                .append(context.agentId()).append('|').append(context.level()).append('|')
                .append(context.jobId()).append('|').append(context.mapId()).append('|')
                .append(context.hp()).append('|').append(context.mp()).append('|')
                .append(context.meso()).append('|').append(context.currentActivityKind()).append('|')
                .append(context.currentSessionId()).append('|').append(context.currentPlanId()).append('|')
                .append(new TreeSet<>(context.activeQuestIds())).append('|')
                .append(new TreeSet<>(context.completedQuestIds())).append('|')
                .append(context.pepeEquipment()).append('|')
                .append(context.mushroomKingdomFarming()).append('|')
                .append(resources.exp()).append('|').append(resources.remainingAp()).append('|')
                .append(resources.remainingSp()).append('|').append(resources.hpPotions()).append('|')
                .append(resources.mpPotions()).append('|').append(resources.weaponType()).append('|')
                .append(resources.ammunition()).append('|').append(resources.ammunitionRequired()).append('|')
                .append(resources.ammunitionUnlimited()).append('|')
                .append(new TreeMap<>(resources.freeInventorySlots())).append('|')
                .append(new TreeMap<>(resources.equippedItemIds())).append('|')
                .append(energy.energyPercent()).append('|').append(energy.restDebtPercent()).append('|')
                .append(energy.confidencePercent()).append('|').append(energy.frustrationPercent()).append('|')
                .append(profile.profileId()).append('|').append(profile.profileVersion()).append('|')
                .append(new TreeMap<>(profile.traits())).append('|')
                .append(session.mode()).append('|')
                .append(session.phase()).append('|').append(session.observedSessionId());
        for (AgentWorldDirectiveEnvelope directive : directives.stream()
                .sorted(java.util.Comparator.comparing(envelope ->
                        envelope.directive().directiveId())).toList()) {
            value.append('|').append(directive.directive().directiveId())
                    .append(':').append(directive.revision());
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
