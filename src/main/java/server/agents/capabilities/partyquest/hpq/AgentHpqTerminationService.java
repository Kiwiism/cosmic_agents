package server.agents.capabilities.partyquest.hpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.ArrayList;

/** Idempotent owner for HPQ event, registry, and HPQ-owned party cleanup. */
final class AgentHpqTerminationService {
    private static final Logger log = LoggerFactory.getLogger(AgentHpqTerminationService.class);

    private AgentHpqTerminationService() {
    }

    static void fail(AgentHpqSession session, String reason, long nowMs) {
        release(session, reason, nowMs, true, false);
    }

    static void complete(AgentHpqSession session, long nowMs) {
        release(session, "completed", nowMs, false, true);
    }

    private static void release(AgentHpqSession session, String reason, long nowMs,
                                boolean disposeEvent, boolean completed) {
        if (session == null || !session.beginTermination()) return;
        EventInstanceManager event = session.eventInstance();
        tryCleanup(session, "stop-actions", () -> session.members().forEach(member -> {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        }));
        if (disposeEvent && event != null) {
            tryCleanup(session, "dispose-event", () -> closeEvent(event));
        }
        tryCleanup(session, "cleanup-party", () -> session.members().stream()
                    .filter(member -> session.partyOwnership() == AgentHpqSession.PartyOwnership.HPQ_OWNED
                            || member.memberType() == AgentHpqMemberState.MemberType.AGENT)
                    .map(member -> character(member.characterId()))
                    .filter(java.util.Objects::nonNull)
                    .filter(AgentPartyGatewayRuntime.party()::hasParty)
                    .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty));
        session.clearEventInstance();
        if (completed) session.complete(nowMs);
        else session.fail(reason, nowMs);
        AgentHpqSessionRegistry.remove(session);
        AgentPartyQuestLifecycleRuntime.childFinished(
                session.sessionId(), completed, reason, nowMs);
        log.info("HPQ session released: session={} outcome={} members={}",
                session.sessionId(), completed ? "completed" : reason, session.memberCount());
    }

    private static void closeEvent(EventInstanceManager event) {
        for (Character participant : new ArrayList<>(event.getPlayers())) {
            try {
                event.exitPlayer(participant);
            } catch (RuntimeException failure) {
                log.warn("HPQ participant exit failed: event={} member={}",
                        event.getName(), participant == null ? -1 : participant.getId(), failure);
            }
        }
        event.dispose();
    }

    private static void tryCleanup(AgentHpqSession session, String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.warn("HPQ cleanup failed but release will continue: session={} operation={}",
                    session.sessionId(), operation, failure);
        }
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }
}
