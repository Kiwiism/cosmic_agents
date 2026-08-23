package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.event.EventInstanceManager;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;

/** One idempotent owner for KPQ event, activity, registry, and owned-party cleanup. */
final class AgentKpqTerminationService {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqTerminationService.class);
    private static final PartyQuestGateway KPQ = AgentPartyQuestGatewayRuntime.partyQuest();

    private AgentKpqTerminationService() {
    }

    static void fail(AgentKpqSession session, String reason, long nowMs) {
        release(session, reason, nowMs, true, false);
    }

    static void complete(AgentKpqSession session, long nowMs) {
        release(session, "completed", nowMs, false, true);
    }

    static void stopTest(AgentKpqSession session, EventInstanceManager event, long nowMs) {
        if (session != null && event != null) session.bindEventInstance(event);
        release(session, "test stopped", nowMs, true, false);
    }

    private static void release(AgentKpqSession session,
                                String reason,
                                long nowMs,
                                boolean disposeEvent,
                                boolean completed) {
        if (session == null || !session.beginTermination()) return;
        EventInstanceManager event = session.eventInstance();
        tryCleanup(session, "stop-actions", () -> stopAll(session));
        if (disposeEvent && event != null) {
            tryCleanup(session, "dispose-event", () -> closeEvent(event));
        }
        if (shouldReturnTestAgentsToKerning(session.mode(), completed)) {
            tryCleanup(session, "return-test-agents", () -> returnTestAgentsToKerning(session));
        }
        if (!(completed && session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION)) {
            tryCleanup(session, "cleanup-party", () -> cleanupOwnedParty(session));
        }
        session.clearEventInstance();
        if (completed) session.complete(nowMs);
        else session.fail(reason, nowMs);
        // The parent engagement remains indexed, so removing the child first closes the
        // overlap without creating a controller-less Agent between systems.
        AgentKpqSessionRegistry.remove(session);
        AgentPartyQuestLifecycleRuntime.childFinished(
                session.sessionId(), completed, reason, nowMs);
        log.info("KPQ session released: session={} outcome={} phase={} event={} members={}",
                session.sessionId(), completed ? "completed" : reason, session.phase(),
                event == null ? "none" : event.getName(), session.memberCount());
    }

    static void closeEvent(EventInstanceManager event) {
        if (event == null) return;
        for (Character participant : new ArrayList<>(event.getPlayers())) {
            try {
                event.exitPlayer(participant);
            } catch (RuntimeException failure) {
                log.warn("KPQ participant exit failed during event disposal: event={} member={}",
                        event.getName(), participant == null ? -1 : participant.getId(), failure);
            }
        }
        event.dispose();
    }

    static boolean shouldReturnTestAgentsToKerning(
            AgentKpqSession.Mode mode, boolean completed) {
        return mode == AgentKpqSession.Mode.TEST_OBSERVATION && !completed;
    }

    private static void returnTestAgentsToKerning(AgentKpqSession session) {
        for (AgentKpqMemberState member : session.members()) {
            if (member.memberType() != AgentKpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            if (agent == null || agent.getMap() == null
                    || agent.getMapId() == AgentKpqDefinition.RECRUIT_MAP) continue;
            if (agent.getMapId() == AgentKpqDefinition.EXIT_MAP) {
                KPQ.runNpc(agent, AgentKpqDefinition.EXIT_NPC);
            }
            if (agent.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
                log.warn("KPQ test Agent required direct Kerning recovery after exit NPC: "
                                + "session={} member={} map={}",
                        session.sessionId(), agent.getId(), agent.getMapId());
                moveToKerning(agent);
            }
        }
    }

    private static void moveToKerning(Character agent) {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                AgentClientGatewayRuntime.clients().world(agent),
                AgentClientGatewayRuntime.clients().channel(agent),
                AgentKpqDefinition.RECRUIT_MAP);
        if (map == null) return;
        var portal = map.getRandomPlayerSpawnpoint();
        Point spawn = portal == null ? new Point(0, 0) : portal.getPosition();
        AgentMapGatewayRuntime.map().changeMapNear(agent, map, spawn);
    }

    static void cleanupOwnedParty(AgentKpqSession session) {
        if (session == null || session.partyOwnership() != AgentKpqSession.PartyOwnership.KPQ_OWNED) return;
        session.members().stream()
                .sorted(Comparator.comparing((AgentKpqMemberState member) ->
                                member.characterId() == session.eventLeaderId())
                        .thenComparingInt(AgentKpqMemberState::partyNumber))
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(AgentPartyGatewayRuntime.party()::hasParty)
                .forEach(character -> {
                    try {
                        AgentPartyGatewayRuntime.party().leaveCurrentParty(character);
                    } catch (RuntimeException failure) {
                        log.warn("KPQ owned-party cleanup failed: session={} member={}",
                                session.sessionId(), character.getId(), failure);
                    }
                });
    }

    private static void stopAll(AgentKpqSession session) {
        session.members().forEach(member -> {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry != null) AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        });
    }

    private static void tryCleanup(AgentKpqSession session, String operation, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            log.warn("KPQ cleanup operation failed but release will continue: session={} operation={}",
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
