package server.agents.capabilities.partyquest.lpq;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;

/** Idempotent LPQ event, party, action, and index cleanup. */
final class AgentLpqTerminationService {
    private AgentLpqTerminationService() { }
    static void fail(AgentLpqSession session, String reason, long nowMs) { release(session, reason, nowMs, true, false); }
    static void complete(AgentLpqSession session, long nowMs) { release(session, "completed", nowMs, false, true); }
    private static void release(AgentLpqSession session, String reason, long nowMs, boolean dispose, boolean completed) {
        if (session == null || !session.beginTermination()) return;
        session.members().forEach(member -> {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        });
        EventInstanceManager event = session.eventInstance();
        if (dispose && event != null) {
            for (Character participant : new ArrayList<>(event.getPlayers())) {
                try { event.exitPlayer(participant); } catch (RuntimeException ignored) { }
            }
            try { event.dispose(); } catch (RuntimeException ignored) { }
        }
        session.members().stream().filter(member -> session.partyOwnership() == AgentLpqSession.PartyOwnership.LPQ_OWNED
                        || member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .map(member -> character(member.characterId())).filter(java.util.Objects::nonNull)
                .filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        session.clearEventInstance();
        if (completed) session.complete(nowMs); else session.fail(reason, nowMs);
        AgentLpqSessionRegistry.remove(session);
        AgentPartyQuestLifecycleRuntime.childFinished(session.sessionId(), completed, reason, nowMs);
    }
    private static Character character(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
}
