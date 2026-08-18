package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.activity.AgentActivityBootstrap;

import java.util.ArrayList;
import java.util.List;

/** Production-facing admission seam; population decisions remain outside the KPQ system. */
public final class AgentKpqAdmissionService {
    private AgentKpqAdmissionService() {
    }

    public static AdmissionResult admit(
            Character operator, Character eventLeader, List<Character> partyMembers, long seed, long nowMs) {
        if (operator == null || eventLeader == null || partyMembers == null) {
            return AdmissionResult.failure("Operator, leader, and party members are required");
        }
        List<Character> unique = new ArrayList<>();
        if (!partyMembers.contains(eventLeader)) unique.add(eventLeader);
        for (Character member : partyMembers) if (member != null && !unique.contains(member)) unique.add(member);
        if (unique.size() < AgentKpqRecruitmentPolicy.MIN_PARTY_SIZE
                || unique.size() > AgentKpqRecruitmentPolicy.MAX_PARTY_SIZE) {
            return AdmissionResult.failure("The current Kerning event accepts three or four members");
        }
        AgentRuntimeEntry leaderEntry = AgentRuntimeRegistry.findByAgentCharacterId(eventLeader.getId());
        if (leaderEntry == null) {
            return AdmissionResult.failure("Autonomous KPQ currently requires an Agent event leader; humans may fill member slots");
        }
        var leaderParty = AgentPartyGatewayRuntime.party().snapshot(eventLeader);
        if (leaderParty == null) return AdmissionResult.failure("The event leader has no party");
        for (Character member : unique) {
            var party = AgentPartyGatewayRuntime.party().snapshot(member);
            if (party == null || party.id() != leaderParty.id()) {
                return AdmissionResult.failure("Every KPQ member must already be in the event leader's party");
            }
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.getId());
            if (entry != null && !AgentActivityBootstrap.admission().prepare(
                    AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, member,
                    "admitted to autonomous KPQ", nowMs)) {
                return AdmissionResult.failure(member.getName() + " could not leave its current activity");
            }
        }
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.PRODUCTION, seed, operator.getId(), unique.size(), nowMs);
        session.addMember(eventLeader.getId(), AgentKpqMemberState.MemberType.AGENT);
        for (Character member : unique) {
            if (member.getId() == eventLeader.getId()) continue;
            session.addMember(member.getId(), AgentRuntimeRegistry.findByAgentCharacterId(member.getId()) == null
                    ? AgentKpqMemberState.MemberType.HUMAN : AgentKpqMemberState.MemberType.AGENT);
        }
        AgentKpqSessionRegistry.register(session);
        session.members().forEach(member -> AgentKpqSessionRegistry.indexMember(session, member.characterId()));
        session.resetForRun(nowMs);
        return new AdmissionResult(true, "KPQ party admitted", session);
    }

    public record AdmissionResult(boolean success, String message, AgentKpqSession session) {
        static AdmissionResult failure(String message) {
            return new AdmissionResult(false, message, null);
        }
    }
}
