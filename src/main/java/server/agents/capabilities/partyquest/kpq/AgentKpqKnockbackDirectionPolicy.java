package server.agents.capabilities.partyquest.kpq;

import client.Character;

import java.util.concurrent.ThreadLocalRandom;

/** Adds Agent-only left/right knockback variation during KPQ Stage 1. */
public final class AgentKpqKnockbackDirectionPolicy {
    private static final int RANDOM_DIRECTION_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqKnockbackDirectionPolicy.RANDOM_DIRECTION_PERCENT");

    private AgentKpqKnockbackDirectionPolicy() {
    }

    public static int adjustAirVelocityX(Character agent, int naturalAirVelocityX) {
        int percent = randomDirectionPercent(agent);
        if (percent <= 0) return naturalAirVelocityX;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return adjustAirVelocityX(naturalAirVelocityX, percent,
                random.nextFloat(), random.nextFloat());
    }

    private static int randomDirectionPercent(Character agent) {
        if (agent == null || agent.getMapId() != AgentKpqDefinition.STAGE_1_MAP) return 0;
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(agent.getId());
        AgentKpqMemberState member = session == null ? null : session.member(agent.getId());
        return randomDirectionPercent(agent.getMapId(), session == null ? null : session.phase(),
                member == null ? null : member.memberType(), RANDOM_DIRECTION_PERCENT);
    }

    static int randomDirectionPercent(int mapId,
                                      AgentKpqSession.Phase phase,
                                      AgentKpqMemberState.MemberType memberType,
                                      int configuredPercent) {
        if (mapId != AgentKpqDefinition.STAGE_1_MAP
                || phase != AgentKpqSession.Phase.STAGE_1
                || memberType != AgentKpqMemberState.MemberType.AGENT) return 0;
        return Math.max(0, Math.min(100, configuredPercent));
    }

    static int adjustAirVelocityX(int naturalAirVelocityX,
                                  int randomDirectionPercent,
                                  float activationRoll,
                                  float directionRoll) {
        if (naturalAirVelocityX == 0) return 0;
        float chance = Math.max(0f, Math.min(1f, randomDirectionPercent / 100f));
        if (activationRoll >= chance) return naturalAirVelocityX;
        int magnitude = Math.abs(naturalAirVelocityX);
        return directionRoll < 0.5f ? -magnitude : magnitude;
    }
}
