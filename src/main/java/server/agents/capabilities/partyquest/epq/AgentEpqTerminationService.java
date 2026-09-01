package server.agents.capabilities.partyquest.epq;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;

/** Idempotent EPQ cleanup; it cannot address another PQ's state. */
final class AgentEpqTerminationService {
    private AgentEpqTerminationService() { }

    static void release(AgentEpqSession session, String reason, long nowMs, boolean dispose) {
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
        session.members().stream().map(member -> AgentEpqCoordinator.character(member.characterId()))
                .filter(java.util.Objects::nonNull).filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        session.clearEventInstance();
        boolean completed = session.phase() == AgentEpqSession.Phase.COMPLETED;
        if (!completed) session.fail(reason, nowMs);
        AgentEpqSessionRegistry.remove(session);
        AgentPartyQuestLifecycleRuntime.childFinished(session.sessionId(), completed, reason, nowMs);
    }
}
