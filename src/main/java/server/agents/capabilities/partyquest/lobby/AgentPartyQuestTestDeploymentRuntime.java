package server.agents.capabilities.partyquest.lobby;

import client.Character;
import server.agents.capabilities.partyquest.AgentPartyQuestDefinition;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestCatalog;
import server.agents.capabilities.partyquest.AgentPartyQuestRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentLifecycleService;
import server.agents.runtime.AgentRuntimeCleanupService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.activity.AgentActivityBootstrap;
import server.agents.runtime.activity.session.AgentActivityAdmissionResult;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Converts a completed all-Agent observation roster into the real production
 * queue after its post-run hold. One member is deliberately logged out and
 * respawned after the configured replacement delay, so tests exercise the same
 * underfilled-lobby behavior expected in deployment.
 */
public final class AgentPartyQuestTestDeploymentRuntime {
    private static final Set<String> TRANSITIONING = ConcurrentHashMap.newKeySet();

    private AgentPartyQuestTestDeploymentRuntime() { }

    public static boolean transition(AgentPartyQuestEngagement engagement, long nowMs) {
        if (engagement == null
                || engagement.mode() != AgentPartyQuestEngagement.Mode.TEST_OBSERVATION
                || engagement.state() != AgentPartyQuestEngagement.State.POST_RUN_HOLD
                || engagement.members().values().stream()
                .anyMatch(type -> type != AgentPartyQuestEngagement.MemberType.AGENT)
                || engagement.agentIds().size() != engagement.requestedPartySize()
                || !TRANSITIONING.add(engagement.engagementId())) {
            return false;
        }
        try {
            AgentPartyQuestDefinition definition = AgentPartyQuestCatalog.find(engagement.questKey());
            Character operator = online(engagement.operatorId());
            List<Character> agents = engagement.agentIds().stream().map(
                            AgentPartyQuestTestDeploymentRuntime::online)
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingInt(Character::getId)).toList();
            if (definition == null || operator == null
                    || agents.size() != engagement.requestedPartySize()) return false;

            Character replacement = agents.getLast();
            String replacementName = replacement.getName();
            int world = AgentClientGatewayRuntime.clients().world(replacement);
            int channel = AgentClientGatewayRuntime.clients().channel(replacement);
            MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                    world, channel, definition.recruitMapId());
            if (recruit == null) return false;
            Point spawn = spawnPoint(recruit);

            agents.stream().filter(AgentPartyGatewayRuntime.party()::hasParty)
                    .forEach(AgentPartyGatewayRuntime.party()::leaveCurrentParty);
            engagement.close(nowMs);
            AgentPartyQuestEngagementRegistry.remove(engagement);

            List<Integer> queued = new ArrayList<>();
            for (Character agent : agents) {
                if (agent.getId() == replacement.getId()) continue;
                if (!queue(definition, engagement.requestedPartySize(), agent, recruit, spawn, nowMs)) {
                    queued.forEach(id -> AgentPartyQuestRuntime.forceStop(
                            id, "test deployment transition rolled back", nowMs));
                    return false;
                }
                queued.add(agent.getId());
            }

            AgentRuntimeCleanupService.removeAgentByCharacterId(replacement.getId());
            AgentCharacterGatewayRuntime.characters().disconnect(replacement, false, false);
            operator.dropMessage(6, definition.questKey().toUpperCase()
                    + " observation hold completed. " + replacementName
                    + " rotated out; its replacement will queue in 30 seconds.");
            AgentSchedulerRuntime.schedule(() -> spawnReplacement(
                            operator.getId(), replacementName, definition,
                            engagement.requestedPartySize(), world, channel),
                    AgentPartyQuestTestQueueRuntime.replacementDelayMs());
            return true;
        } finally {
            TRANSITIONING.remove(engagement.engagementId());
        }
    }

    private static void spawnReplacement(
            int operatorId, String name, AgentPartyQuestDefinition definition,
            int partySize, int world, int channel) {
        Character operator = online(operatorId);
        if (operator == null) return;
        MapleMap recruit = AgentMapGatewayRuntime.map().resolveMap(
                world, channel, definition.recruitMapId());
        if (recruit == null) return;
        Point spawn = spawnPoint(recruit);
        AgentLifecycleService.AgentSpawnResult result = AgentInteractionRuntime
                .spawnStationaryAgentForLeaderAt(operator, name, recruit, spawn);
        if (!result.success()) {
            operator.dropMessage(6, definition.questKey().toUpperCase()
                    + " replacement failed to spawn: " + result.errorMessage());
            return;
        }
        Character replacement = result.agent();
        if (!queue(definition, partySize, replacement, recruit, spawn,
                System.currentTimeMillis())) {
            operator.dropMessage(6, replacement.getName()
                    + " could not enter the " + definition.questKey().toUpperCase() + " queue.");
        }
    }

    private static boolean queue(
            AgentPartyQuestDefinition definition, int partySize, Character agent,
            MapleMap recruit, Point spawn, long nowMs) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry == null) return false;
        if (agent.getMapId() != recruit.getId()) {
            AgentMapGatewayRuntime.map().changeMapNear(agent, recruit, spawn);
        }
        if (!AgentActivityBootstrap.admission().prepare(
                AgentActivityBootstrap.PARTY_QUEST_CONTROLLER_ID, entry, agent,
                "entering deployed " + definition.questKey().toUpperCase() + " queue", nowMs)) {
            return false;
        }
        AgentActivityAdmissionResult result = AgentPartyQuestRuntime
                .requireSystem(definition.questKey())
                .requestEntry(entry, agent, definition.questKey(), partySize, 1, nowMs);
        return result.status() == AgentActivityAdmissionResult.Status.ACCEPTED;
    }

    private static Point spawnPoint(MapleMap map) {
        var portal = map.getRandomPlayerSpawnpoint();
        Point candidate = portal == null ? new Point(0, 0) : portal.getPosition();
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .groundPoint(map, candidate);
        return grounded == null ? candidate : grounded;
    }

    private static Character online(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }
}
