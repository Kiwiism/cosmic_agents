package server.agents.capabilities.partyquest;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.partyquest.lobby.AgentPartyQuestLobbyRuntime;
import server.agents.capabilities.townlife.AgentTownLifeAdmissionMode;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeVisitRequest;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.MapleMap;

import java.awt.Point;

/** Keeps party-quest Agents owned until an active event, test hold, or ordinary system receives them. */
public final class AgentPartyQuestLifecycleRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentPartyQuestLifecycleRuntime.class);
    private static final long RECOVERY_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime.RECOVERY_RETRY_MS");
    private static final long RECOVERY_WARN_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime.RECOVERY_WARN_MS");

    private AgentPartyQuestLifecycleRuntime() {
    }

    public static boolean active(int characterId) {
        return AgentPartyQuestEngagementRegistry.active(characterId);
    }

    public static boolean tick(int characterId, long nowMs) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null || !engagement.ownsAgent(characterId)) return false;
        if (engagement.claimRecoveryAttempt(nowMs, Math.max(1_000L, RECOVERY_RETRY_MS))) {
            recover(engagement, nowMs);
        }
        return true;
    }

    public static void childFinished(String sessionId, boolean success, String reason, long nowMs) {
        if (sessionId == null || sessionId.isBlank()) return;
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.engagements().stream()
                .filter(candidate -> sessionId.equals(candidate.activeSessionId()))
                .findFirst().orElse(null);
        if (engagement == null) return;
        engagement.finishRun(success, reason, nowMs);
        if (engagement.state() == AgentPartyQuestEngagement.State.RECOVERING) recover(engagement, nowMs);
    }

    public static boolean requestStop(int characterId, String reason, long nowMs) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null) return true;
        if (engagement.state() == AgentPartyQuestEngagement.State.ACTIVE_EVENT) return false;
        if (!engagement.lobbyId().isBlank()) AgentPartyQuestLobbyRuntime.unregister(engagement.lobbyId(), nowMs);
        engagement.beginRecovery(reason, nowMs);
        recover(engagement, nowMs);
        return AgentPartyQuestEngagementRegistry.byId(engagement.engagementId()) == null;
    }

    public static void forceStop(int characterId, String reason, long nowMs) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null) return;
        if (!engagement.lobbyId().isBlank()) AgentPartyQuestLobbyRuntime.unregister(engagement.lobbyId(), nowMs);
        engagement.beginRecovery(reason, nowMs);
        recover(engagement, nowMs);
    }

    /** Runtime teardown is terminal for the whole engagement; surviving Agents remain owned through recovery. */
    public static void runtimeRemoved(int characterId, String reason, long nowMs) {
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        if (engagement == null) return;
        if (!engagement.lobbyId().isBlank()) AgentPartyQuestLobbyRuntime.unregister(engagement.lobbyId(), nowMs);
        engagement.beginRecovery(reason, nowMs);
        recover(engagement, nowMs);
    }

    /** Test-owned Agents are disconnected by the harness, so no fallback activity is needed. */
    public static void closeTest(AgentPartyQuestEngagement engagement, long nowMs) {
        if (engagement == null) return;
        if (!engagement.lobbyId().isBlank()) AgentPartyQuestLobbyRuntime.unregister(engagement.lobbyId(), nowMs);
        engagement.close(nowMs);
        AgentPartyQuestEngagementRegistry.remove(engagement);
    }

    public static boolean recover(AgentPartyQuestEngagement engagement, long nowMs) {
        if (engagement == null || engagement.state() != AgentPartyQuestEngagement.State.RECOVERING) return false;
        boolean allRecovered = true;
        for (int agentId : engagement.agentIds()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agentId);
            Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
            if (entry == null || agent == null) continue;
            if (AgentTownLifeRuntime.active(entry)) continue;
            AgentPartyQuestDefinition definition = AgentPartyQuestCatalog.find(engagement.questKey());
            if (definition == null) {
                allRecovered = false;
                engagement.addDiagnostic("No recovery definition for " + engagement.questKey(), nowMs);
                continue;
            }
            if (agent.getMapId() != definition.recoveryMapId()) {
                moveToRecoveryMap(agent, definition.recoveryMapId());
            }
            var result = AgentTownLifeRuntime.requestLocal(
                    entry, agent, AgentTownLifeVisitRequest.leisure(agent.getMapId()),
                    AgentTownLifeAdmissionMode.MANUAL_ONLY, nowMs, agent.getId());
            if (!result.started()) {
                allRecovered = false;
                engagement.addDiagnostic("TownLife recovery deferred for " + agent.getName()
                        + ": " + result.status() + " " + result.reason(), nowMs);
            }
        }
        if (allRecovered) {
            engagement.close(nowMs);
            AgentPartyQuestEngagementRegistry.remove(engagement);
            return true;
        }
        long warningIntervalMs = Math.max(5_000L, RECOVERY_WARN_MS);
        if (nowMs - engagement.stateEnteredAtMs() >= warningIntervalMs
                && engagement.claimRecoveryWarning(nowMs, warningIntervalMs)) {
            log.warn("Party-quest engagement remains in owned recovery: engagement={} quest={} members={} diagnostics={}",
                    engagement.engagementId(), engagement.questKey(), engagement.memberIds(),
                    engagement.diagnostics());
        }
        return false;
    }

    /** Rehomes one member removed from an active party without ending the remaining party quest. */
    public static void recoverDetachedMember(int characterId, long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        if (entry == null || agent == null) return;
        AgentPartyQuestEngagement engagement = AgentPartyQuestEngagementRegistry.forMember(characterId);
        AgentPartyQuestDefinition definition = engagement == null
                ? null : AgentPartyQuestCatalog.find(engagement.questKey());
        // A detached member may already have been removed from the engagement index.
        // Preserve the legacy KPQ recovery destination for that compatibility path;
        // new PQ systems should recover before unindexing or use their own definition.
        int recoveryMapId = definition == null
                ? AgentPartyQuestCatalog.require("kpq").recoveryMapId()
                : definition.recoveryMapId();
        if (agent.getMapId() != recoveryMapId) moveToRecoveryMap(agent, recoveryMapId);
        if (!AgentTownLifeRuntime.active(entry)) {
            AgentTownLifeRuntime.requestLocal(
                    entry, agent, AgentTownLifeVisitRequest.leisure(agent.getMapId()),
                    AgentTownLifeAdmissionMode.MANUAL_ONLY, nowMs, agent.getId());
        }
    }

    private static void moveToRecoveryMap(Character agent, int recoveryMapId) {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                AgentClientGatewayRuntime.clients().world(agent),
                AgentClientGatewayRuntime.clients().channel(agent), recoveryMapId);
        var portal = map == null ? null : map.getRandomPlayerSpawnpoint();
        Point spawn = portal == null ? new Point(0, 0) : portal.getPosition();
        if (map != null) {
            AgentMapGatewayRuntime.map().changeMapNear(agent, map, spawn);
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
            if (entry != null) {
                AgentMovementStateResetService.resetEntryState(entry);
                AgentMovementBroadcastService.broadcastMovement(entry);
            }
        }
    }
}
