package server.agents.capabilities.partyquest.kpq;

import client.Character;

/** Supplies the hidden Agent-only knockback resistance for KPQ Stage 1. */
public final class AgentKpqKnockbackResistancePolicy {
    private static final int STAGE_1_RESISTANCE_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqKnockbackResistancePolicy.STAGE_1_RESISTANCE_PERCENT");

    private AgentKpqKnockbackResistancePolicy() {
    }

    public static int resistancePercent(Character agent) {
        if (agent == null || agent.getMapId() != AgentKpqDefinition.STAGE_1_MAP) {
            return 0;
        }
        AgentKpqSession session = AgentKpqSessionRegistry.forMember(agent.getId());
        AgentKpqMemberState member = session == null ? null : session.member(agent.getId());
        return resistancePercent(agent.getMapId(), session == null ? null : session.phase(),
                member == null ? null : member.memberType(), STAGE_1_RESISTANCE_PERCENT);
    }

    static int resistancePercent(int mapId,
                                 AgentKpqSession.Phase phase,
                                 AgentKpqMemberState.MemberType memberType,
                                 int configuredPercent) {
        if (mapId != AgentKpqDefinition.STAGE_1_MAP
                || phase != AgentKpqSession.Phase.STAGE_1
                || memberType != AgentKpqMemberState.MemberType.AGENT) {
            return 0;
        }
        return Math.max(0, Math.min(100, configuredPercent));
    }
}
