package server.agents.runtime;

import server.agents.capabilities.movement.AgentMovementStateResetService;

import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;

import client.Character;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.integration.AgentBotManagerStatusRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotPhysicsEngine;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;

public final class AgentLeaderSafetyRuntime {
    private static final int PLATFORM_EDGE_INSET_PX = 12;

    private AgentLeaderSafetyRuntime() {
    }

    public static boolean handleInactiveLeaderTick(BotEntry entry,
                                                   Character agent,
                                                   Character leader,
                                                   long nowMs,
                                                   int leaderCharId) {
        return handleInactiveLeaderTick(
                entry,
                agent,
                leader,
                nowMs,
                leaderCharId,
                AgentRuntimeConfig.cfg.OWNER_INACTIVE_TOWN_RETURN_MS);
    }

    public static boolean handleInactiveLeaderTick(BotEntry entry,
                                                   Character agent,
                                                   Character leader,
                                                   long nowMs,
                                                   int leaderCharId,
                                                   long inactiveTownReturnMs) {
        return AgentLeaderSafetyService.handleInactiveLeaderTick(
                entry,
                leader,
                nowMs,
                new AgentLeaderSafetyService.InactiveLeaderTickHooks(
                        activeEntry -> AgentLeaderSafetyService.handleActiveLeaderReturn(
                                activeEntry,
                                () -> AgentBotMoveTargetStateRuntime.clearMoveTarget(activeEntry),
                                () -> AgentLeaderSafetyService.townClusterAnchorsByLeaderId().remove(leaderCharId),
                                () -> AgentBotManagerStatusRuntime.announceOwnerReturnedFromOffline(activeEntry)),
                        AgentLeaderSafetyRuntime::shouldTownWarpForInactiveEntry,
                        (inactiveEntry, town) -> enterInactiveSafeMode(inactiveEntry, agent, leaderCharId, town),
                        inactiveTownReturnMs));
    }

    public static boolean shouldTownWarpForInactiveEntry(BotEntry entry) {
        MapleMap currentMap = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return AgentLeaderSafetyService.shouldTownWarpForInactiveLeader(currentMap);
    }

    public static void issueInactiveSafeModeForLeader(int leaderCharId, boolean town) {
        AgentLeaderSafetyService.issueInactiveSafeModeForLeader(
                AgentRuntimeRegistry.entriesForLeader(leaderCharId),
                town,
                AgentBotRuntimeIdentityRuntime::botHasMap,
                AgentLeaderSafetyRuntime::shouldTownWarpForInactiveEntry,
                (entry, shouldTown) -> enterInactiveSafeMode(
                        entry,
                        AgentBotRuntimeIdentityRuntime.bot(entry),
                        leaderCharId,
                        shouldTown));
    }

    private static boolean enterInactiveSafeMode(BotEntry entry, Character agent, int leaderCharId, boolean town) {
        return AgentLeaderSafetyService.enterInactiveSafeMode(
                () -> prepareInactiveIdle(entry),
                town,
                () -> scrollAgentToTown(entry, agent, leaderCharId),
                () -> AgentLeaderSafetyService.idleInactiveAgentInPlace(
                        entry,
                        () -> AgentMovementPoseService.idleOnGround(entry, agent),
                        () -> AgentMovementBroadcastService.broadcastMovement(entry)));
    }

    private static void prepareInactiveIdle(BotEntry entry) {
        AgentLeaderSafetyService.prepareInactiveIdle(
                entry,
                () -> AgentScriptTaskQueueService.clearTasks(entry),
                () -> AgentShopService.cancelShopVisit(entry),
                () -> AgentModeService.clearMode(entry));
    }

    private static boolean scrollAgentToTown(BotEntry entry, Character agent, int leaderCharId) {
        return AgentLeaderSafetyService.scrollInactiveAgentToTown(
                entry,
                new AgentLeaderSafetyService.TownScrollHooks(
                        agent::getMap,
                        () -> AgentLeaderSafetyService.markInactiveTownReturnHandled(entry),
                        () -> AgentMovementPoseService.idleOnGround(entry, agent),
                        () -> AgentReturnScrollService.tryUseNearestTownReturnScroll(agent),
                        agent::changeMap,
                        () -> AgentMapTransitionRuntime.groundAfterMapChange(entry, agent),
                        agent::getPosition,
                        post -> AgentLeaderSafetyService.townClusterAnchorsByLeaderId()
                                .putIfAbsent(leaderCharId, post),
                        (returnMap, anchor) -> resolveTownClusterTarget(entry, leaderCharId, returnMap, anchor),
                        () -> AgentMovementStateResetService.resetEntryState(entry),
                        target -> AgentModeService.startMoveTo(entry, target, true)));
    }

    private static Point resolveTownClusterTarget(BotEntry entry, int leaderCharId, MapleMap map, Point anchor) {
        List<BotEntry> entries = AgentRuntimeRegistry.entriesForLeader(leaderCharId);
        AgentFormationService.FormationState formation =
                AgentFormationService.stateForLeader(
                        AgentFormationService.formationsByLeaderId(),
                        leaderCharId,
                        defaultFormationState());
        return AgentLeaderSafetyService.resolveTownClusterTarget(
                entry,
                map,
                anchor,
                entries,
                formation,
                PLATFORM_EDGE_INSET_PX,
                BotPhysicsEngine::findGroundPoint);
    }

    private static AgentFormationService.FormationState defaultFormationState() {
        return new AgentFormationService.FormationState(
                AgentFormationService.FormationType.STAGGER,
                AgentRuntimeConfig.cfg.FOLLOW_STAGGER,
                0);
    }
}
