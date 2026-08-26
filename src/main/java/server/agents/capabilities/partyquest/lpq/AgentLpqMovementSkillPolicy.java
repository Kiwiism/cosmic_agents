package server.agents.capabilities.partyquest.lpq;

import client.Character;

/** LPQ-owned exception for the authored Stage 5 Teleport route. */
public final class AgentLpqMovementSkillPolicy {
    public static final int STAGE_FIVE_MAP = 922_010_500;
    public static final int TELEPORT_ROOM_MAP = 922_010_501;

    private static final boolean ENABLE_TELEPORT_NAVIGATION = config.AgentTuning.booleanValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqMovementSkillPolicy.ENABLE_TELEPORT_NAVIGATION");

    private AgentLpqMovementSkillPolicy() {
    }

    public static boolean authorsTeleportEdges(int mapId) {
        return ENABLE_TELEPORT_NAVIGATION
                && (mapId == STAGE_FIVE_MAP || mapId == TELEPORT_ROOM_MAP);
    }

    public static boolean allowsActiveTeleport(Character agent) {
        if (agent == null || !authorsTeleportEdges(agent.getMapId())) {
            return false;
        }
        AgentLpqSession session = AgentLpqSessionRegistry.forMember(agent.getId());
        AgentLpqMemberState member = session == null ? null : session.member(agent.getId());
        return session != null
                && session.phase() == AgentLpqSession.Phase.STAGE_5
                && session.eventInstance() != null
                && agent.getEventInstance() == session.eventInstance()
                && member != null
                && member.memberType() == AgentLpqMemberState.MemberType.AGENT
                && member.role() == AgentLpqMemberState.Role.TELEPORT_RUNNER
                && member.assignedMapId() == TELEPORT_ROOM_MAP;
    }
}
