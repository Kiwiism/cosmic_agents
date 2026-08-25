package server.agents.capabilities.partyquest.lpq;

import client.Character;

/** Receives authored LPQ outcomes that cannot be inferred safely from map polling. */
public final class AgentLpqHumanInteractionRuntime {
    private AgentLpqHumanInteractionRuntime() { }

    public static void stageEightChecked(Character leader, boolean correct, long nowMs) {
        if (leader == null) return;
        AgentLpqSession session = AgentLpqSessionRegistry.forMember(leader.getId());
        AgentLpqMemberState member = session == null ? null : session.member(leader.getId());
        if (session == null || member == null
                || member.memberType() != AgentLpqMemberState.MemberType.HUMAN
                || leader.getId() != session.eventLeaderId()
                || session.phase() != AgentLpqSession.Phase.STAGE_8) return;
        if (correct) session.markProgress(nowMs);
        else session.advanceStage8(nowMs);
    }
}
