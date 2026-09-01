package server.agents.capabilities.partyquest.opq;

import client.Character;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;

/** Idempotent OPQ cleanup; never moves a live run as a recovery shortcut. */
final class AgentOpqTerminationService {
    private AgentOpqTerminationService() { }
    static void release(AgentOpqSession session, String reason, long nowMs, boolean dispose) {
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
        session.members().stream().map(member -> AgentOpqCoordinator.character(member.characterId()))
                .filter(java.util.Objects::nonNull).filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
        session.clearEventInstance();
        boolean completed = session.phase() == AgentOpqSession.Phase.COMPLETED;
        if (!completed) session.fail(reason, nowMs);
        AgentOpqSessionRegistry.remove(session);
        AgentPartyQuestLifecycleRuntime.childFinished(session.sessionId(), completed, reason, nowMs);
    }
}
