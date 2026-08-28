package server.agents.capabilities.partyquest.lpq;

import client.BuffStat;
import client.Character;
import client.Job;
import client.inventory.InventoryType;
import constants.skills.Rogue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentAttackTransactionResult;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.combat.AgentCombatAttackRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatHitCounter;
import server.agents.capabilities.combat.AgentCombatPlanRuntime;
import server.agents.capabilities.combat.AgentCombatTargetRuntime;
import server.agents.capabilities.combat.AgentCombatWeaponPolicy;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.capabilities.dialogue.AgentDialogueReportFormatter;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PartyGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.perception.AgentMapPerception;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import scripting.reactor.ReactorActionManager;
import server.StatEffect;
import server.life.Monster;
import server.maps.Portal;
import server.maps.Reactor;
import server.maps.MapleMap;
import server.maps.MapItem;
import scripting.event.EventInstanceManager;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** LPQ-only coordinator that advances the authored NPC, portal, reactor, combat, and loot flow. */
final class AgentLpqCoordinator {
    enum PassHandoffExitAction { NAVIGATE, PLACE_AT_PORTAL, MOVE_TO_STAGE }
    private static final Logger log = LoggerFactory.getLogger(AgentLpqCoordinator.class);
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway LPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final PartyGateway PARTY = AgentPartyGatewayRuntime.party();
    private static final long PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.PHASE_TIMEOUT_MS");
    private static final long PREPARATION_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.PREPARATION_DELAY_MS");
    private static final long INTERACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.INTERACTION_RETRY_MS");
    private static final long ROOM_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.ROOM_LEASE_MS");
    private static final long STAGE_1_MAX_NATURAL_COMBAT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_1_MAX_NATURAL_COMBAT_MS");
    private static final int INTERACTION_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.INTERACTION_RADIUS_PX");
    /** Ordinary LPQ boxes must be struck from their own platform, not an adjacent floor. */
    private static final int REACTOR_PLATFORM_VERTICAL_TOLERANCE_PX = 40;
    private static final int NPC_APPROACH_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.NPC_APPROACH_PX");
    private static final int GATHER_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.GATHER_RADIUS_PX");
    private static final long BONUS_DRAIN_SETTLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.BONUS_DRAIN_SETTLE_MS");
    private static final long DARK_SIGHT_REFRESH_WINDOW_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.DARK_SIGHT_REFRESH_WINDOW_MS");
    private static final long STAGE_4_COMBAT_STALL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_4_COMBAT_STALL_MS");
    private static final long STAGE_4_EYE_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_4_EYE_RETRY_MS");
    private static final int STAGE_4_STALL_OBSERVATION_RANGE_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_4_STALL_OBSERVATION_RANGE_PX");
    private static final long ROOM_PROGRESS_LOG_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.ROOM_PROGRESS_LOG_INTERVAL_MS");
    private static final int DARK_SIGHT_SAFE_PORTAL_ID = 0;
    private static final Point STAGE_7_BOTTOM = new Point(1, -211);
    private static final int STAGE_7_FIRING_ANCHOR_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_7_FIRING_ANCHOR_RADIUS");
    /**
     * The Stage 9 balloon NPC stands on authored foothold 89 at y=74. Ranged party
     * members must finish navigating onto that platform before the LPQ-only Black
     * Ratz shot or Alishar reactor strike is allowed.
     */
    private static final Point STAGE_9_BALLOON_PLATFORM_ANCHOR = new Point(222, 74);
    private static final int STAGE_9_RATZ_FIRING_ANCHOR_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_9_RATZ_FIRING_ANCHOR_RADIUS");
    private static final long STAGE_7_FORCE_LOOT_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.STAGE_7_FORCE_LOOT_DELAY_MS");
    private static final List<Point> STAGE_7_LOOT_SWEEP = List.of(
            new Point(1, -211), new Point(-171, -707), new Point(224, -1_044),
            new Point(219, -1_276), new Point(228, -1_543), new Point(179, -774));
    /** Authored Stage 6 portal-ID route, from the bottom row upward. */
    private static final List<Integer> STAGE_6_PORTAL_IDS = List.of(
            4, 7, 8, 12, 15, 17, 20, 23, 27, 29, 34, 36, 38, 41, 46);
    private static final List<String> STAGE_6_SEQUENCE_CHAT = List.of(
            "Stage 6 sequence: 133 221 333 123 111");
    private static final String STARTING_CHAT =
            "Party ready. Everyone is here. Starting LPQ in 5 seconds.";
    private static final String STAGE_2_SCOUT_CHAT =
            "Stage 2: two scouts are going first. Everyone else wait for trap clear.";
    private static final String STAGE_2_TRAP_CLEAR_CHAT =
            "Stage 2 trap clear. Everyone enter now.";

    private AgentLpqCoordinator() { }

    private static AgentLpqStageRecoveryPolicy recovery(int stage) {
        return AgentLpqStageRecoveryPolicy.forStage(stage);
    }

    static void tick(AgentLpqSession session, long nowMs) {
        synchronized (session) {
            if (session.terminal()) return;
            if (nowMs - session.lastProgressAtMs() > PHASE_TIMEOUT_MS) {
                AgentLpqTerminationService.fail(session, "No LPQ progress before phase timeout", nowMs);
                return;
            }
            Character leader = character(session.eventLeaderId());
            if (leader == null || leader.getHp() <= 0) {
                AgentLpqTerminationService.fail(session, "The LPQ event leader is unavailable", nowMs);
                return;
            }
            if (session.eventInstance() == null && LPQ.event(leader) != null) session.bindEventInstance(LPQ.event(leader));
            synchronizePhase(session, leader, nowMs);
            String eventFailure = liveEventFailure(session, leader);
            if (!eventFailure.isEmpty()) {
                AgentLpqTerminationService.fail(session, eventFailure, nowMs);
                return;
            }
            recalculateStageAssignments(session, nowMs);
            switch (session.phase()) {
                case PREPARING -> prepare(session, leader, nowMs);
                case ENTERING -> enter(session, leader, nowMs);
                case STAGE_1, STAGE_2, STAGE_3, STAGE_4, STAGE_5,
                        STAGE_7 -> collectionStage(session, leader, nowMs);
                case STAGE_6 -> portalMaze(session, leader, nowMs);
                case STAGE_8 -> platformPuzzle(session, leader, nowMs);
                case STAGE_9 -> boss(session, leader, nowMs);
                case BONUS -> bonus(session, nowMs);
                case CLAIMING_REWARD -> claim(session, nowMs);
                case EXITING -> exitAfterRewardWarpSettles(session, nowMs);
                default -> { }
            }
        }
    }

    /** Rebuilds authoritative stage ownership once, after transition cleared the old stage. */
    private static void recalculateStageAssignments(AgentLpqSession session, long nowMs) {
        if (!session.stageAssignmentsNeedRecalculation()) return;
        if (session.members().stream().anyMatch(member -> character(member.characterId()) == null)) return;
        int stage = stage(session.phase());
        switch (stage) {
            case 4, 5 -> assignRooms(session, stage, AgentLpqDefinition.roomMaps(stage), nowMs);
            case 7 -> assignStageSevenRoles(session);
            case 8 -> {
                if (!assignStageEightRoles(session)) return;
            }
            case 9 -> assignStageNineRoles(session);
            default -> {
                // The transition baseline (leader + general members) is the full plan.
            }
        }
        session.markStageAssignmentsRecalculated(nowMs);
    }

    private static void assignStageSevenRoles(AgentLpqSession session) {
        List<Integer> topIds = stageSevenTopMemberIds(
                session.members(), id -> AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(id)),
                session.eventLeaderId());
        for (AgentLpqMemberState member : session.members()) {
            int topIndex = topIds.indexOf(member.characterId());
            member.assign(topIndex >= 0 ? AgentLpqMemberState.Role.RANGED_TRIGGER
                    : AgentLpqMemberState.Role.BOSS_ATTACKER, AgentLpqDefinition.stage(7).mapId());
            member.assignPlatform(topIndex >= 0 ? topIndex + 1 : 0);
        }
    }

    private static boolean assignStageEightRoles(AgentLpqSession session) {
        List<AgentLpqMemberState> participants = session.members().stream()
                .filter(member -> member.characterId() != session.eventLeaderId())
                .sorted(Comparator.comparingInt(AgentLpqMemberState::characterId)).toList();
        if (participants.size() < 5) return false;
        Map<Integer, Integer> assignments = session.stage8Assignments(
                participants.stream().map(AgentLpqMemberState::characterId).toList());
        for (int index = 0; index < 5; index++) {
            AgentLpqMemberState member = participants.get(index);
            member.assign(index == 4 ? AgentLpqMemberState.Role.PLATFORM_MOVER
                    : AgentLpqMemberState.Role.PLATFORM_HOLDER, AgentLpqDefinition.stage(8).mapId());
            member.assignPlatform(assignments.get(member.characterId()));
        }
        return true;
    }

    static String liveEventFailure(AgentLpqSession session, Character leader) {
        if (session == null || !requiresLiveEvent(session.phase())) return "";
        EventInstanceManager event = session.eventInstance();
        if (session.phase() == AgentLpqSession.Phase.CLAIMING_REWARD) {
            return event == null || event.isEventDisposed()
                    ? "The LPQ reward instance ended before all claims were resolved" : "";
        }
        EventInstanceManager leaderEvent = LPQ.event(leader);
        if (event == null || leaderEvent == null) {
            return "The LPQ event instance ended or expired";
        }
        if (event != leaderEvent || event.isEventDisposed()) {
            return "The LPQ event instance no longer matches the leader";
        }
        if (leader == null || event.getLeaderId() != leader.getId()
                || event.getPlayerById(leader.getId()) != leader) {
            return "The LPQ event leader is no longer registered correctly";
        }
        if (!leader.isPartyLeader()) {
            return "The LPQ event leader is no longer the party leader";
        }
        MapleMap leaderMap = leader.getMap();
        if (leaderMap == null || leaderMap.getEventInstance() != event
                || !AgentLpqDefinition.isEventMap(leader.getMapId())) {
            return "The LPQ event leader left the bound instance map";
        }
        if (session.phase() != AgentLpqSession.Phase.BONUS
                && session.phase() != AgentLpqSession.Phase.CLAIMING_REWARD
                && (!event.isTimerStarted() || event.getTimeLeft() <= 0L)) {
            return "The LPQ event timer expired";
        }
        Set<Integer> sessionMemberIds = session.members().stream()
                .map(AgentLpqMemberState::characterId)
                .collect(java.util.stream.Collectors.toSet());
        long registeredMembers = event.getPlayers().stream()
                .filter(java.util.Objects::nonNull)
                .filter(member -> sessionMemberIds.contains(member.getId()))
                .filter(member -> member.getEventInstance() == event)
                .filter(member -> member.getMap() != null
                        && member.getMap().getEventInstance() == event)
                .count();
        if (registeredMembers < AgentLpqDefinition.MIN_PARTY_SIZE) {
            return "LPQ no longer has five registered session members";
        }
        return "";
    }

    static boolean requiresLiveEvent(AgentLpqSession.Phase phase) {
        return phase != null && switch (phase) {
            case ENTERING,
                    STAGE_1, STAGE_2, STAGE_3, STAGE_4, STAGE_5,
                    STAGE_6, STAGE_7, STAGE_8, STAGE_9,
                    BONUS, CLAIMING_REWARD -> true;
            default -> false;
        };
    }

    private static void synchronizePhase(AgentLpqSession session, Character leader, long nowMs) {
        int mapId = leader.getMapId();
        if (mapId == AgentLpqDefinition.CLEAR_MAP) {
            session.freezeRewardEligibility();
            session.transition(AgentLpqSession.Phase.BONUS, nowMs);
            return;
        }
        if (mapId == AgentLpqDefinition.BONUS_MAP) {
            boolean bonusMemberStillInside = session.members().stream()
                    .map(member -> character(member.characterId()))
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(member -> member.getMapId() == AgentLpqDefinition.CLEAR_MAP);
            if (session.phase() == AgentLpqSession.Phase.BONUS && bonusMemberStillInside) return;
            session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, nowMs);
            return;
        }
        int stage = AgentLpqDefinition.stageNumber(mapId);
        if (stage >= 1 && stage <= 9) {
            AgentLpqSession.Phase expected = AgentLpqSession.Phase.valueOf("STAGE_" + stage);
            if (session.phase() == AgentLpqSession.Phase.STAGE_2
                    && expected == AgentLpqSession.Phase.STAGE_1) {
                return;
            }
            if (session.phase() != expected) session.transition(expected, nowMs);
        }
    }

    private static void prepare(AgentLpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentLpqDefinition.stage(1).mapId() && LPQ.event(leader) != null) {
            session.transition(AgentLpqSession.Phase.ENTERING, nowMs);
            return;
        }
        if (leader.getMapId() != AgentLpqDefinition.RECRUIT_MAP) return;
        Point npc = ACTIONS.npcPosition(leader, AgentLpqDefinition.ENTRY_NPC);
        if (npc == null) return;
        boolean allGathered = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != AgentLpqDefinition.RECRUIT_MAP) {
                allGathered = false;
                continue;
            }
            AgentRuntimeEntry participantEntry = entry(member.characterId());
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                if (participantEntry == null) {
                    allGathered = false;
                    continue;
                }
                Point target = lobbyApproachPoint(session, member, participant, npc);
                if (!near(participant.getPosition(), target, NPC_APPROACH_PX)) {
                    ACTIONS.navigate(participantEntry, target, true);
                    allGathered = false;
                }
            } else if (!near(participant.getPosition(), npc, GATHER_RADIUS_PX)) {
                allGathered = false;
            }
        }
        if (!allGathered) {
            session.setReadyAtMs(0L);
            return;
        }
        if (session.readyAtMs() == 0L) {
            session.setReadyAtMs(nowMs + PREPARATION_DELAY_MS);
            Character speaker = isAgent(leader) ? leader : session.members().stream()
                    .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                    .map(member -> character(member.characterId()))
                    .filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            if (speaker != null) {
                AgentPacketGatewayRuntime.packets().broadcastChatText(
                        speaker, STARTING_CHAT, false, 0);
            }
            return;
        }
        if (nowMs < session.readyAtMs() || !isAgent(leader)) return;
        AgentRuntimeEntry entry = entry(leader.getId());
        if (entry == null) return;
        if (LPQ.runNpc(leader, AgentLpqDefinition.ENTRY_NPC, 0)) session.markProgress(nowMs);
    }

    private static Point lobbyApproachPoint(
            AgentLpqSession session, AgentLpqMemberState member, Character character, Point npc) {
        long mixed = session.seed() + member.characterId() * 131L;
        int offset = 24 + Math.floorMod(mixed, Math.max(1, NPC_APPROACH_PX));
        Point target = new Point(npc.x - offset, npc.y);
        Point grounded = ACTIONS.groundPoint(character.getMap(), target);
        return grounded == null ? target : grounded;
    }

    private static void enter(AgentLpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() != AgentLpqDefinition.stage(1).mapId() || LPQ.event(leader) == null) return;
        session.bindEventInstance(LPQ.event(leader));
        boolean allEntered = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant != null && LPQ.sameEvent(leader, participant)) continue;
            allEntered = false;
            if (participant != null && member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                restoreEventMember(session, participant, AgentLpqDefinition.stage(1).mapId(), nowMs);
            } else if (participant != null && nowMs >= member.nextActionAtMs()) {
                participant.dropMessage(6, "Enter LPQ through the Red Sign to join your party instance.");
                member.deferUntil(nowMs + 5_000L);
            }
        }
        if (allEntered) session.transition(AgentLpqSession.Phase.STAGE_1, nowMs);
    }

    private static void collectionStage(AgentLpqSession session, Character leader, long nowMs) {
        int stage = stage(session.phase());
        AgentLpqDefinition.Stage contract = AgentLpqDefinition.stage(stage);
        if (LPQ.property(leader, stage + "stageclear") != null) {
            movePartyToNextStage(session, stage, nowMs);
            return;
        }
        if (stage == 2 && (!session.stage2TrapClearAnnounced()
                || leader.getMapId() == AgentLpqDefinition.stage(1).mapId())) {
            advanceStageTwoScoutProtocol(session, nowMs);
            return;
        }
        if (session.passHandoffRecoveryActive()) {
            if (recoverPassHandoffAtNpc(session, leader, contract, nowMs)) {
                submit(session, leader, contract, nowMs);
                if (LPQ.property(leader, stage + "stageclear") != null) {
                    movePartyToNextStage(session, stage, nowMs);
                }
            }
            return;
        }
        catchUpLaggingMembers(session, stage, contract.mapId(), nowMs);
        cooperativeCouponCollection(session, leader, contract, nowMs);
    }

    private static void cooperativeCouponCollection(AgentLpqSession session, Character leader,
                                                    AgentLpqDefinition.Stage contract, long nowMs) {
        int stage = contract.number();
        if (LPQ.property(leader, stage + "stageclear") != null) {
            movePartyToNextStage(session, stage, nowMs);
            return;
        }
        if (!session.couponRegrouping(stage)) {
            int partyPasses = partyPassCount(session);
            if (partyPasses >= contract.submissionCount()) {
                if (passHandoffRecoveryApplicable(
                        stage, partyPasses, contract.submissionCount())
                        && memberOutsideStageMap(session, contract.mapId())) {
                    runCollectionObjectives(session, stage, nowMs);
                    boolean allOutsideRoomsComplete = outsideMembersHaveCompletedRooms(
                            session, stage, contract.mapId());
                    long stalledForMs = splitRoomCompletionInactivity(
                            session, stage, partyPasses, nowMs);
                    if ((allOutsideRoomsComplete
                            || stalledForMs >= recovery(stage).missingPassMs())
                            && session.beginPassHandoffRecovery(nowMs)) {
                        session.members().forEach(AgentLpqMemberState::clearTraversalProgress);
                        log.info("LPQ Stage {} coordinated pass-handoff exit started: "
                                        + "session={} partyPasses={}/{} stalledForMs={} "
                                        + "allOutsideRoomsComplete={}",
                                stage, session.sessionId(), partyPasses,
                                contract.submissionCount(), stalledForMs,
                                allOutsideRoomsComplete);
                    }
                    return;
                }
                if (!allLoosePassesCollected(session, stage, nowMs)) return;
                session.beginCouponRegroup(stage, nowMs);
            } else {
                session.observeSubmissionReady(false, nowMs);
                runCollectionObjectives(session, stage, nowMs);
                recoverMissingPasses(session, leader, stage, contract.submissionCount(), nowMs);
                if (partyPassCount(session) < contract.submissionCount()) return;
                if (!allLoosePassesCollected(session, stage, nowMs)) return;
                session.beginCouponRegroup(stage, nowMs);
            }
        }

        regroupCouponsAtNpc(session, leader, contract, nowMs);
        if (leader.getItemQuantity(AgentLpqDefinition.PASS, false) < contract.submissionCount()) return;
        submit(session, leader, contract, nowMs);
        if (LPQ.property(leader, stage + "stageclear") != null) {
            movePartyToNextStage(session, stage, nowMs);
        }
    }

    private static void runCollectionObjectives(
            AgentLpqSession session, int stage, long nowMs) {
        if (stage == 4 || stage == 5) splitRooms(session, stage, nowMs);
        else if (stage == 7) stageSevenObjectives(session, nowMs);
        else ordinaryObjectives(session, stage, nowMs);
    }

    /** Collection stages end as a visible handoff: regroup at the balloon, then drop coupons. */
    private static void regroupCouponsAtNpc(AgentLpqSession session, Character leader,
                                            AgentLpqDefinition.Stage contract, long nowMs) {
        if (contract.number() == 2) {
            for (AgentLpqMemberState member : session.members()) {
                Character participant = character(member.characterId());
                AgentRuntimeEntry participantEntry = entry(member.characterId());
                if (participant == null || participant.getMapId() != 922_010_201) continue;
                if (member.memberType() == AgentLpqMemberState.MemberType.AGENT
                        && participantEntry != null) {
                    useStageTwoTrapExit(session, participantEntry, participant, nowMs);
                } else if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "Stage 2 is complete. Use the exit and return to the balloon.");
                    member.deferUntil(nowMs + 5_000L);
                }
            }
        }
        Point npc = ACTIONS.npcPosition(leader, contract.npcId());
        if (npc == null) return;
        boolean everyoneOnMainMap = session.members().stream()
                .map(member -> character(member.characterId()))
                .allMatch(participant -> participant != null
                        && participant.getMapId() == contract.mapId());
        long regroupElapsedMs = session.observeMainMapRally(
                contract.mapId(), everyoneOnMainMap, nowMs);

        boolean everyoneAtNpc = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != contract.mapId()) {
                everyoneAtNpc = false;
                continue;
            }
            if (member.couponRegroupRecoveredFor(contract.number())) {
                AgentRuntimeEntry participantEntry = entry(participant.getId());
                if (participantEntry != null && participant.getId() != leader.getId()) {
                    ACTIONS.stop(participantEntry);
                }
                continue;
            }
            if (near(participant.getPosition(), npc, INTERACTION_RADIUS)) {
                AgentRuntimeEntry participantEntry = entry(participant.getId());
                if (participantEntry != null && participant.getId() != leader.getId()) {
                    ACTIONS.stop(participantEntry);
                }
                if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                    member.deferUntil(nowMs);
                }
                continue;
            }
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry participantEntry = entry(participant.getId());
                if (participantEntry != null) {
                    if (nowMs < member.nextActionAtMs()) {
                        everyoneAtNpc = false;
                        continue;
                    }
                    everyoneAtNpc = false;
                    rallyMemberAtNpc(session, contract.number(), member,
                            participant, participantEntry, npc, nowMs);
                } else {
                    everyoneAtNpc = false;
                }
            } else if (nowMs >= member.nextActionAtMs()) {
                everyoneAtNpc = false;
                participant.dropMessage(6, "LPQ Stage " + contract.number()
                        + " is complete. Return to the balloon with your passes.");
                member.deferUntil(nowMs + 5_000L);
            } else {
                everyoneAtNpc = false;
            }
        }
        if (!everyoneAtNpc && couponRegroupPositionRecoveryDue(
                contract.number(), regroupElapsedMs)) {
            int recovered = 0;
            for (AgentLpqMemberState member : session.members()) {
                if (member.memberType() != AgentLpqMemberState.MemberType.AGENT
                        || member.couponRegroupRecoveredFor(contract.number())) continue;
                Character participant = character(member.characterId());
                AgentRuntimeEntry participantEntry = entry(member.characterId());
                if (participant == null || participantEntry == null
                        || participant.getMapId() != contract.mapId()
                        || near(participant.getPosition(), npc, INTERACTION_RADIUS)) continue;
                int offset = Math.floorMod(participant.getId() * 37, 81) - 40;
                Point desired = new Point(npc.x + offset, npc.y);
                Point grounded = ACTIONS.groundPoint(participant.getMap(), desired);
                Point target = grounded == null ? desired : grounded;
                ACTIONS.stop(participantEntry);
                ACTIONS.stagePosition(participantEntry, participant, target);
                member.markCouponRegroupRecovered(contract.number());
                member.clearNpcRallyProgress();
                recovered++;
            }
            if (recovered > 0) {
                session.markProgress(nowMs);
                log.warn("LPQ Stage {} coupon-regroup position recovery moved {} lagging "
                                + "members to the NPC after {}ms: session={}",
                        contract.number(), recovered, regroupElapsedMs, session.sessionId());
            }
            return;
        }
        if (!everyoneAtNpc) return;

        for (AgentLpqMemberState member : session.members()) {
            if (member.characterId() == leader.getId()) continue;
            Character participant = character(member.characterId());
            int coupons = participant == null ? 0
                    : participant.getItemQuantity(AgentLpqDefinition.PASS, false);
            if (coupons <= 0) continue;
            if (member.memberType() == AgentLpqMemberState.MemberType.HUMAN) {
                if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "Drop all Stage " + contract.number()
                            + " passes here for the party leader.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            AgentRuntimeEntry participantEntry = entry(participant.getId());
            if (participantEntry != null && nowMs >= member.nextActionAtMs()
                    && AgentScriptItemActionService.dropItem(
                    participantEntry, InventoryType.ETC, AgentLpqDefinition.PASS,
                    (short) Math.min(Short.MAX_VALUE, coupons))) {
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
                session.markProgress(nowMs);
            }
        }

        if (isAgent(leader)) {
            AgentRuntimeEntry leaderEntry = entry(leader.getId());
            if (leaderEntry != null) {
                if (collectNearbyDrops(
                        leaderEntry, leader, Set.of(AgentLpqDefinition.PASS)) == 0) {
                    collectNearestDrop(leaderEntry, leader,
                            Set.of(AgentLpqDefinition.PASS), new LinkedHashSet<>());
                }
            }
        } else if (leader.getItemQuantity(AgentLpqDefinition.PASS, false) < contract.submissionCount()) {
            AgentLpqMemberState leaderState = session.member(leader.getId());
            if (leaderState != null && nowMs >= leaderState.nextActionAtMs()) {
                leader.dropMessage(6, "Your party dropped its Stage " + contract.number()
                        + " passes here. Loot them, then talk to the balloon.");
                leaderState.deferUntil(nowMs + 5_000L);
            }
        }
        if (regroupElapsedMs >= recovery(contract.number()).missingPassMs()
                && leader.getItemQuantity(AgentLpqDefinition.PASS, false)
                < contract.submissionCount()) {
            int vacuumed = vacuumPassDrops(leader, Set.of(leader.getMap()));
            if (vacuumed > 0) {
                session.markProgress(nowMs);
                log.warn("LPQ Stage {} coupon-regroup recovery vacuumed {} real pass drops: "
                                + "session={} leader={}({}) elapsedMs={}",
                        contract.number(), vacuumed, session.sessionId(), leader.getName(),
                        leader.getId(), regroupElapsedMs);
            }
        }
    }

    /**
     * A leader can reach the next portal before the rest of the party. Phase synchronization follows the
     * leader, so keep advancing Agents that are still standing in an earlier authored stage instead of
     * abandoning them when the session phase changes.
     */
    private static void catchUpLaggingMembers(AgentLpqSession session, int stage,
                                              int destination, long nowMs) {
        long phaseElapsedMs = Math.max(0L, nowMs - session.phaseEnteredAtMs());
        boolean absoluteRecoveryDue = stageTransitionCatchUpRecoveryDue(
                stage, phaseElapsedMs);
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            if (agent.getMapId() == AgentLpqDefinition.EXIT_MAP
                    && restoreEventMember(session, agent, destination, nowMs)) {
                continue;
            }
            int currentStage = AgentLpqDefinition.stageNumber(agent.getMapId());
            if (currentStage <= 0 || currentStage >= stage) continue;
            if (absoluteRecoveryDue) {
                int sourceMapId = agent.getMapId();
                if (moveWithinEvent(session, agent, destination, nowMs)) {
                    member.clearTraversalProgress();
                    log.warn("LPQ lagging-member absolute transition recovery: session={} "
                                    + "member={}({}) source={} stage={} destination={} "
                                    + "phaseElapsedMs={}",
                            session.sessionId(), agent.getName(), agent.getId(), sourceMapId,
                            stage, destination, phaseElapsedMs);
                }
                continue;
            }
            if (currentStage == 2 && agent.getMapId() == 922_010_201) {
                recoverStageTwoTrap(session, agent, nowMs);
                continue;
            }
            if (currentStage == 6 && agent.getMapId() == AgentLpqDefinition.stage(6).mapId()) {
                advancePortalMazeMember(session, member, agent, entry, nowMs);
                continue;
            }
            int nextMapId = AgentLpqDefinition.nextTraversalMap(agent.getMapId());
            long inactiveForMs = nextMapId == 0 ? 0L
                    : traversalInactivity(member, agent, nextMapId, nowMs);
            if (nextMapId != 0) enterPortalTo(entry, agent, nextMapId);
            if (agent.getMapId() != destination
                    && inactiveForMs >= recovery(stage).traversalMs()) {
                log.warn("LPQ lagging-member traversal recovery: session={} member={}({}) "
                                + "source={} stage={} destination={} inactiveForMs={}",
                        session.sessionId(), agent.getName(), agent.getId(), agent.getMapId(),
                        stage, destination, inactiveForMs);
                moveWithinEvent(session, agent, destination, nowMs);
                member.clearTraversalProgress();
            }
        }
    }

    private static void ordinaryObjectives(AgentLpqSession session, int stage, long nowMs) {
        int mapId = AgentLpqDefinition.stage(stage).mapId();
        Point npc = stageNpcPosition(session, stage);
        Set<Integer> reactorClaims = stage == 2 || stage == 3
                ? activeReactorClaims(session, mapId) : new LinkedHashSet<>();
        Set<Integer> trapReactorClaims = stage == 2
                ? activeReactorClaims(session, 922_010_201) : Set.of();
        Map<Integer, Set<Integer>> lootClaimsByMap = new LinkedHashMap<>();
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            if (stage == 2 && agent.getMapId() == 922_010_201) {
                Set<Integer> mapLootClaims = lootClaimsByMap.computeIfAbsent(
                        agent.getMapId(), ignored -> new LinkedHashSet<>());
                if (collectNearestDrop(entry, agent,
                        Set.of(AgentLpqDefinition.PASS), mapLootClaims)) {
                    continue;
                }
                announcePassProgress(session, member, agent, stage, agent.getMapId(),
                        agent.getItemQuantity(AgentLpqDefinition.PASS, false),
                        AgentLpqDefinition.stage(stage).submissionCount(),
                        AgentLpqDefinition.stage(stage).submissionCount());
                boolean hitTrapReactor = hitNearestReactor(
                        entry, agent, nowMs, member, false, trapReactorClaims);
                if (member.reactorTargetObjectId() != 0) {
                    trapReactorClaims.add(member.reactorTargetObjectId());
                }
                boolean looseTrapPass = AgentMapPerception.items(agent.getMap()).stream()
                        .anyMatch(item -> !item.isPickedUp()
                                && item.getItemId() == AgentLpqDefinition.PASS);
                if (!hitTrapReactor && activeReactors(agent).isEmpty() && !looseTrapPass) {
                    member.clearReactorWork();
                    useStageTwoTrapExit(session, entry, agent, nowMs);
                }
                continue;
            }
            if (agent.getMapId() != mapId) continue;
            Set<Integer> mapLootClaims = lootClaimsByMap.computeIfAbsent(
                    agent.getMapId(), ignored -> new LinkedHashSet<>());
            if (collectNearestDrop(entry, agent,
                    Set.of(AgentLpqDefinition.PASS), mapLootClaims)) {
                continue;
            }
            announcePassProgress(session, member, agent, stage, mapId,
                    agent.getItemQuantity(AgentLpqDefinition.PASS, false),
                    AgentLpqDefinition.stage(stage).submissionCount(),
                    AgentLpqDefinition.stage(stage).submissionCount());
            boolean hitReactor = (stage == 2 || stage == 3)
                    && hitNearestReactor(entry, agent, nowMs, member, false, reactorClaims);
            if ((stage == 2 || stage == 3) && member.reactorTargetObjectId() != 0) {
                reactorClaims.add(member.reactorTargetObjectId());
            }
            if (!hitReactor) {
                if (stageMapObjectivesExhausted(agent) && npc != null) {
                    rallyMemberAtNpc(session, stage, member, agent, entry, npc, nowMs);
                    continue;
                }
                if (stage == 1) {
                    announceStageOneMeleeIntent(session, member, agent);
                    grindReachableStageOneMobs(entry, agent);
                } else {
                    Set<Integer> liveTargets = agent.getMap().getAllMonsters().stream()
                            .map(Monster::getId).collect(java.util.stream.Collectors.toSet());
                    Set<Integer> combatTargets = collectionCombatTargets(
                            stage, ACTIONS.configuredMonsterSpawnIds(agent), liveTargets);
                    if (ordinaryStageShouldRallyAtNpc(
                            stage, ACTIONS.liveMonsterCount(agent, combatTargets)) && npc != null) {
                        rallyMemberAtNpc(session, stage, member, agent, entry, npc, nowMs);
                    } else {
                        ACTIONS.grind(entry, combatTargets);
                    }
                }
            }
        }
    }

    static boolean ordinaryStageShouldRallyAtNpc(int stage, int liveCombatTargets) {
        return (stage == 2 || stage == 3) && liveCombatTargets <= 0;
    }

    static Set<Integer> collectionCombatTargets(
            int stage, Set<Integer> configuredTargets, Set<Integer> liveTargets) {
        Set<Integer> targets = new LinkedHashSet<>(stageCombatTargets(stage, configuredTargets));
        if (stage == 3 && liveTargets != null) targets.addAll(liveTargets);
        return Set.copyOf(targets);
    }

    private static Set<Integer> activeReactorClaims(AgentLpqSession session, int mapId) {
        Set<Integer> claims = new LinkedHashSet<>();
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant != null && participant.getMapId() == mapId
                    && member.reactorTargetMapId() == mapId
                    && member.reactorTargetObjectId() != 0) {
                claims.add(member.reactorTargetObjectId());
            }
        }
        return claims;
    }

    private static boolean allLoosePassesCollected(AgentLpqSession session, int stage, long nowMs) {
        Set<MapleMap> maps = collectionMaps(session, stage);
        int before = maps.stream().mapToInt(map -> unpickedPassDropCount(map.getDroppedItems())).sum();
        if (before == 0) {
            session.observeLoosePasses(0, nowMs);
            return true;
        }
        long inactiveForMs = session.observeLoosePasses(before, nowMs);
        Map<Integer, Set<Integer>> claimsByMap = new LinkedHashMap<>();
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent != null && entry != null && maps.contains(agent.getMap())) {
                Set<Integer> mapClaims = claimsByMap.computeIfAbsent(
                        agent.getMapId(), ignored -> new LinkedHashSet<>());
                collectNearestDrop(entry, agent, Set.of(AgentLpqDefinition.PASS), mapClaims);
            }
        }
        int after = maps.stream().mapToInt(map -> unpickedPassDropCount(map.getDroppedItems())).sum();
        if (after < before) {
            session.markProgress(nowMs);
            session.observeLoosePasses(after, nowMs);
        }
        if (after > 0 && inactiveForMs >= recovery(stage).missingPassMs()) {
            Character leader = character(session.eventLeaderId());
            int vacuumed = leader == null ? 0 : vacuumPassDrops(leader, maps);
            int remaining = maps.stream()
                    .mapToInt(map -> unpickedPassDropCount(map.getDroppedItems())).sum();
            if (vacuumed > 0) {
                session.markProgress(nowMs);
                session.observeLoosePasses(remaining, nowMs);
                log.warn("LPQ Stage {} loose-pass recovery vacuumed {} stranded drops "
                                + "after {}ms of natural collection: session={} remaining={}",
                        stage, vacuumed, inactiveForMs, session.sessionId(), remaining);
            }
            return remaining == 0;
        }
        return after == 0;
    }

    private static long splitRoomCompletionInactivity(
            AgentLpqSession session, int stage, int partyPasses, long nowMs) {
        Set<MapleMap> maps = collectionMaps(session, stage);
        int liveMobs = maps.stream().mapToInt(map -> map.getAllMonsters().size()).sum();
        long liveMobHp = maps.stream().flatMap(map -> map.getAllMonsters().stream())
                .mapToLong(Monster::getHp).sum();
        return session.observePassRecoveryProgress(
                partyPasses, liveMobs, activeReactorCount(session, maps), liveMobHp, nowMs);
    }

    static int unpickedPassDropCount(Collection<MapItem> drops) {
        if (drops == null) return 0;
        return (int) drops.stream()
                .filter(java.util.Objects::nonNull)
                .filter(drop -> !drop.isPickedUp() && drop.getItemId() == AgentLpqDefinition.PASS)
                .count();
    }

    private static boolean collectNearestDrop(
            AgentRuntimeEntry entry, Character agent, Set<Integer> itemIds,
            Set<Integer> claimedObjectIds) {
        if (entry == null || agent == null || agent.getMap() == null) return false;
        Set<Integer> claimed = claimedObjectIds == null ? Set.of() : claimedObjectIds;
        MapItem target = agent.getMap().getDroppedItems().stream()
                .filter(java.util.Objects::nonNull)
                .filter(drop -> !drop.isPickedUp() && !claimed.contains(drop.getObjectId()))
                .filter(drop -> itemIds == null || itemIds.contains(drop.getItemId()))
                .min(Comparator.comparingDouble(drop ->
                        drop.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null) return false;
        if (claimedObjectIds != null) claimedObjectIds.add(target.getObjectId());
        int radius = server.agents.runtime.AgentRuntimeConfig.cfg.LOOT_RADIUS;
        Point position = target.getPosition();
        if (Math.abs(position.x - agent.getPosition().x) <= radius
                && Math.abs(position.y - agent.getPosition().y) <= radius) {
            ACTIONS.stop(entry);
            agent.pickupItem(target);
        } else {
            ACTIONS.navigate(entry, position, true);
        }
        return true;
    }

    /** Collects only drops already lying at the character's feet; this is not a vacuum. */
    private static int collectNearbyDrops(
            AgentRuntimeEntry entry, Character agent, Set<Integer> itemIds) {
        if (entry == null || agent == null || agent.getMap() == null
                || agent.getPosition() == null) return 0;
        int radius = server.agents.runtime.AgentRuntimeConfig.cfg.LOOT_RADIUS;
        List<MapItem> nearby = agent.getMap().getDroppedItems().stream()
                .filter(java.util.Objects::nonNull)
                .filter(drop -> !drop.isPickedUp())
                .filter(drop -> itemIds == null || itemIds.contains(drop.getItemId()))
                .filter(drop -> Math.abs(drop.getPosition().x - agent.getPosition().x) <= radius
                        && Math.abs(drop.getPosition().y - agent.getPosition().y) <= radius)
                .toList();
        if (nearby.isEmpty()) return 0;
        ACTIONS.stop(entry);
        int collected = 0;
        for (MapItem drop : nearby) {
            if (drop.isPickedUp()) continue;
            agent.pickupItem(drop);
            collected++;
        }
        return collected;
    }

    private static boolean stageMapObjectivesExhausted(Character agent) {
        return agent != null && activeReactors(agent).isEmpty()
                && agent.getMap().getAllMonsters().isEmpty()
                && unpickedPassDropCount(agent.getMap().getDroppedItems()) == 0;
    }

    private static Point stageNpcPosition(AgentLpqSession session, int stage) {
        int mapId = AgentLpqDefinition.stage(stage).mapId();
        Character observer = session.members().stream()
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(member -> member.getMapId() == mapId)
                .findFirst().orElse(null);
        return observer == null ? null
                : ACTIONS.npcPosition(observer, AgentLpqDefinition.stage(stage).npcId());
    }

    private static void rallyMemberAtNpc(
            AgentLpqSession session, int stage, AgentLpqMemberState member,
            Character agent, AgentRuntimeEntry entry, Point npc, long nowMs) {
        if (agent == null || entry == null || npc == null) return;
        if (member != null) {
            member.assign(AgentLpqMemberState.Role.NPC_RALLY, 0);
        }
        if (member != null && member.couponRegroupRecoveredFor(stage)) {
            ACTIONS.stop(entry);
            return;
        }
        int offset = Math.floorMod(agent.getId() * 37, 81) - 40;
        Point desired = new Point(npc.x + offset, npc.y);
        Point grounded = ACTIONS.groundPoint(agent.getMap(), desired);
        Point finalTarget = grounded == null ? desired : grounded;
        Point target = finalTarget;
        int nextMapId = AgentLpqDefinition.nextTraversalMap(agent.getMapId());
        Point authoredWaypoint = nextMapId == 0 ? null
                : AgentLpqExitRoutePolicy.nextWaypoint(
                agent.getMapId(), nextMapId, agent.getPosition(), finalTarget);
        if (authoredWaypoint != null) target = authoredWaypoint;
        if (near(agent.getPosition(), finalTarget, 30)) {
            ACTIONS.stop(entry);
            if (member != null) member.clearNpcRallyProgress();
            return;
        }
        long distanceSq = agent.getPosition() == null
                ? Long.MAX_VALUE : (long) agent.getPosition().distanceSq(finalTarget);
        long inactiveForMs = member == null ? 0L : member.observeNpcRallyProgress(
                stage, agent.getMapId(), agent.getPosition(), distanceSq, nowMs);
        AgentLpqStageRecoveryPolicy recovery = recovery(stage);
        boolean partyRallyRecoveryReady = session.mainMapRallyElapsed(
                agent.getMapId(), nowMs)
                >= recovery(stage).rallyRecoveryMs();
        if (partyRallyRecoveryReady && npcRallyRecoveryDue(stage, inactiveForMs)) {
            ACTIONS.stop(entry);
            ACTIONS.stagePosition(entry, agent, finalTarget);
            member.markCouponRegroupRecovered(stage);
            member.clearNpcRallyProgress();
            session.markProgress(nowMs);
            log.warn("LPQ Stage {} NPC rally position recovery: session={} member={}({}) "
                            + "map={} target={} inactiveForMs={}", stage, session.sessionId(),
                    agent.getName(), agent.getId(), agent.getMapId(), finalTarget, inactiveForMs);
            return;
        }
        if (member != null && member.claimNpcRallyRetry(
                inactiveForMs, nowMs, recovery.rallyRetryMs())) {
            log.info("LPQ Stage {} NPC rally watchdog retry: session={} member={}({}) "
                            + "map={} waypoint={} inactiveForMs={}", stage, session.sessionId(),
                    agent.getName(), agent.getId(), agent.getMapId(), target, inactiveForMs);
        }
        ACTIONS.navigate(entry, target, true);
    }

    private static boolean rallyPartyAtNpc(
            AgentLpqSession session, Character leader, int stage, long nowMs) {
        Point npc = ACTIONS.npcPosition(leader, AgentLpqDefinition.stage(stage).npcId());
        if (npc == null) return false;
        boolean gathered = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != AgentLpqDefinition.stage(stage).mapId()) {
                gathered = false;
                continue;
            }
            if (near(participant.getPosition(), npc, INTERACTION_RADIUS)) continue;
            gathered = false;
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                rallyMemberAtNpc(session, stage, member, participant,
                        entry(member.characterId()), npc, nowMs);
            } else if (nowMs >= member.nextActionAtMs()) {
                participant.dropMessage(6, "LPQ Stage " + stage
                        + ": gather at the balloon with the party.");
                member.deferUntil(nowMs + 5_000L);
            }
        }
        return gathered;
    }

    private static void grindReachableStageOneMobs(AgentRuntimeEntry entry, Character agent) {
        var current = AgentGrindTargetStateRuntime.target(entry);
        if (current != null && !AgentCombatTargetRuntime.isReachableGrindTarget(entry, agent, current)) {
            AgentGrindTargetStateRuntime.clear(entry);
        }
        Set<Integer> stageTargets = stageCombatTargets(1, ACTIONS.configuredMonsterSpawnIds(agent));
        ACTIONS.grind(entry, stageTargets, Set.of());
    }

    private static void announceStageOneMeleeIntent(
            AgentLpqSession session, AgentLpqMemberState member, Character agent) {
        if (member.memberType() != AgentLpqMemberState.MemberType.AGENT
                || !stageOneMelee(agent)
                || !member.claimIntentAnnouncement("stage1:upper-melee")) return;
        sendPartyAndAllChat(agent, "Stage 1: I'm climbing to clear every reachable upper mob as a "
                + jobLabel(agent) + ".");
    }

    static boolean stageOneMelee(Character agent) {
        if (agent == null || agent.getJob() == null) return false;
        Job job = agent.getJob();
        return job.isA(Job.WARRIOR) || job.isA(Job.BANDIT);
    }

    private static void announceRoomIntent(
            AgentLpqSession session, AgentLpqMemberState member, Character agent,
            int stage, int roomMapId, AgentLpqMemberState.Role role, boolean helper) {
        if (agent == null || member.memberType() != AgentLpqMemberState.MemberType.AGENT
                || !member.claimIntentAnnouncement("stage" + stage + ":room:" + roomMapId
                + (helper ? ":helper" : ""))) return;
        String room = String.valueOf(roomMapId % 1_000);
        String purpose;
        if (stage == 4) {
            purpose = AgentLpqDefinition.STAGE_4_MAGIC_ROOMS.contains(roomMapId)
                    ? "magic-weak mob room " : "physical-weak mob room ";
            if (helper) purpose = "help finish " + purpose;
        } else if (role == AgentLpqMemberState.Role.TELEPORT_RUNNER) {
            purpose = "Teleport-required room ";
        } else if (role == AgentLpqMemberState.Role.DARK_SIGHT_RUNNER) {
            purpose = "Dark Sight-required room ";
        } else {
            purpose = "regular room ";
        }
        sendPartyAndAllChat(agent, "Stage " + stage + ": I'm going to " + purpose + room
                + " as a " + jobLabel(agent) + ".");
    }

    private static void announceStageSevenIntent(
            AgentLpqSession session, AgentLpqMemberState member,
            Character participant, int topIndex) {
        if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) return;
        String key = topIndex >= 0 ? "stage7:top:" + topIndex : "stage7:bottom";
        if (!member.claimIntentAnnouncement(key)) return;
        sendPartyAndAllChat(participant, topIndex >= 0
                ? "Stage 7: I'm going up to hit trigger box " + (topIndex + 1)
                + " as a " + jobLabel(participant) + "."
                : "Stage 7: I'm holding the bottom to kill Rombards as a "
                + jobLabel(participant) + ".");
    }

    private static void announcePassProgress(
            AgentLpqSession session, AgentLpqMemberState member, Character agent,
            int stage, int mapId, int localCount, int localQuota, int partyQuota) {
        if (agent == null || member.memberType() != AgentLpqMemberState.MemberType.AGENT
                || !member.shouldReportPassProgress(
                stage, mapId, localCount, localQuota)) return;
        int partyCount = partyPassCount(session);
        String location = AgentLpqDefinition.roomMaps(stage).contains(mapId)
                ? "room " + (mapId % 1_000) + " " : "";
        sendPartyAndAllChat(agent, "Stage " + stage + ": " + location + "passes "
                + localCount + "/" + localQuota + "; party "
                + partyCount + "/" + partyQuota + ".");
    }

    private static String jobLabel(Character character) {
        return character == null || character.getJob() == null ? "member"
                : AgentDialogueReportFormatter.jobDisplayName(character.getJob());
    }

    private static void stageSevenObjectives(AgentLpqSession session, long nowMs) {
        int mapId = AgentLpqDefinition.stage(7).mapId();
        List<Integer> topIds = stageSevenTopMemberIds(
                session.members(), id -> AgentLpqRosterRequirementPolicy.stageSevenBoxWacker(character(id)),
                session.eventLeaderId());
        Character observer = session.members().stream().map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull).filter(member -> member.getMapId() == mapId)
                .findFirst().orElse(null);
        List<Reactor> liveTriggerReactors = observer == null ? List.of() : activeReactors(observer).stream()
                .filter(reactor -> reactor.getId() == AgentLpqDefinition.STAGE_7_TRIGGER_REACTOR)
                .toList();
        boolean rombardAlive = observer != null && ACTIONS.liveMonsterCount(
                observer, Set.of(AgentLpqDefinition.ROMBARD)) > 0;
        boolean combatCleared = observer != null && liveTriggerReactors.isEmpty() && !rombardAlive;
        long combatClearedForMs = session.observeStage7CombatCleared(combatCleared, nowMs);
        Set<Integer> reactorClaims = new LinkedHashSet<>();
        Point npc = observer == null ? null : ACTIONS.npcPosition(
                observer, AgentLpqDefinition.stage(7).npcId());
        for (AgentLpqMemberState member : session.members()) {
            int topIndex = topIds.indexOf(member.characterId());
            if (topIndex >= 0) {
                member.assign(AgentLpqMemberState.Role.RANGED_TRIGGER, mapId);
                member.assignPlatform(topIndex + 1);
            } else {
                member.assign(AgentLpqMemberState.Role.BOSS_ATTACKER, mapId);
                member.assignPlatform(0);
            }

            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != mapId) continue;
            announceStageSevenIntent(session, member, participant, topIndex);
            if (member.memberType() == AgentLpqMemberState.MemberType.HUMAN) {
                if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, topIndex >= 0
                            ? "LPQ Stage 7: take the upper platforms and kill trigger " + (topIndex + 1) + "."
                            : "LPQ Stage 7: hold the bottom and kill the summoned Rombards.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }

            AgentRuntimeEntry entry = entry(member.characterId());
            if (entry == null) continue;
            if (combatCleared) {
                if (member.characterId() != session.eventLeaderId() && npc != null) {
                    rallyMemberAtNpc(session, 7, member, participant, entry, npc, nowMs);
                }
                continue;
            }
            if (topIndex >= 0) {
                if (liveTriggerReactors.isEmpty()) {
                    if (npc != null) rallyMemberAtNpc(
                            session, 7, member, participant, entry, npc, nowMs);
                    continue;
                }
                boolean workingReactor = hitStageSevenTrigger(
                        session, entry, participant, nowMs, member,
                        reactorClaims, liveTriggerReactors);
                if (member.reactorTargetObjectId() != 0) {
                    reactorClaims.add(member.reactorTargetObjectId());
                }
                if (!workingReactor) {
                    ACTIONS.stop(entry);
                }
                continue;
            }

            if (ACTIONS.liveMonsterCount(participant, Set.of(AgentLpqDefinition.ROMBARD)) > 0) {
                ACTIONS.grind(entry, Set.of(AgentLpqDefinition.ROMBARD));
            } else if (npc != null) {
                rallyMemberAtNpc(session, 7, member, participant, entry, npc, nowMs);
            } else {
                ACTIONS.stop(entry);
            }
        }
        if (combatCleared) stageSevenLeaderLootSweep(session, combatClearedForMs, nowMs);
    }

    private static boolean hitStageSevenTrigger(
            AgentLpqSession session, AgentRuntimeEntry entry, Character agent, long nowMs,
            AgentLpqMemberState member, Set<Integer> claimedByParty,
            List<Reactor> liveTriggerReactors) {
        if (nowMs < member.nextActionAtMs()) return false;
        Reactor reactor = selectCommittedReactor(member, agent.getMapId(), agent.getPosition(),
                liveTriggerReactors, false, claimedByParty);
        if (reactor == null) return false;
        Point firingAnchor = stageSevenFiringAnchor(reactor.getPosition());
        if (firingAnchor == null) return false;
        if (!near(agent.getPosition(), firingAnchor, STAGE_7_FIRING_ANCHOR_RADIUS)) {
            ACTIONS.navigate(entry, firingAnchor, true);
            return true;
        }
        ACTIONS.stop(entry);
        ACTIONS.facePosition(agent, reactor.getPosition());
        if (!ACTIONS.hitReactor(agent, reactor.getObjectId())) return false;
        member.deferUntil(nowMs + INTERACTION_RETRY_MS);
        session.markProgress(nowMs);
        return true;
    }

    static Point stageSevenFiringAnchor(Point reactorPosition) {
        if (reactorPosition == null) return null;
        return switch (reactorPosition.y) {
            case -1_037 -> new Point(-240, -990);
            case -1_263 -> new Point(-240, -1_263);
            case -1_535 -> new Point(-240, -1_469);
            default -> null;
        };
    }

    static List<Integer> stageSevenTopMemberIds(Collection<AgentLpqMemberState> members,
                                                IntPredicate rangedAttack,
                                                int eventLeaderId) {
        if (members == null || rangedAttack == null) return List.of();
        return members.stream()
                .sorted(Comparator
                        .comparing((AgentLpqMemberState member) ->
                                !rangedAttack.test(member.characterId()))
                        .thenComparing((AgentLpqMemberState member) ->
                                member.memberType() == AgentLpqMemberState.MemberType.HUMAN)
                        .thenComparing(member -> member.characterId() == eventLeaderId)
                        .thenComparingInt(AgentLpqMemberState::characterId))
                .limit(Math.min(2, members.size()))
                .map(AgentLpqMemberState::characterId)
                .toList();
    }

    private static void stageSevenLeaderLootSweep(AgentLpqSession session,
                                                   long combatClearedForMs, long nowMs) {
        Character leader = character(session.eventLeaderId());
        if (leader == null || leader.getMapId() != AgentLpqDefinition.stage(7).mapId()) return;
        AgentLpqMemberState leaderState = session.member(leader.getId());
        if (leaderState != null) {
            announcePassProgress(session, leaderState, leader, 7, leader.getMapId(),
                    leader.getItemQuantity(AgentLpqDefinition.PASS, false), 3, 3);
        }
        AgentRuntimeEntry leaderEntry = entry(leader.getId());
        if (leaderEntry != null && collectNearestDrop(leaderEntry, leader,
                Set.of(AgentLpqDefinition.PASS), new LinkedHashSet<>())) return;
        int sweepIndex = session.stage7LootSweepIndex();
        if (leaderEntry != null && sweepIndex < STAGE_7_LOOT_SWEEP.size()) {
            Point target = STAGE_7_LOOT_SWEEP.get(sweepIndex);
            if (near(leader.getPosition(), target, INTERACTION_RADIUS)) {
                session.advanceStage7LootSweep(nowMs);
            } else {
                ACTIONS.navigate(leaderEntry, target, true);
            }
        } else if (leaderEntry == null) {
            if (leaderState != null && nowMs >= leaderState.nextActionAtMs()) {
                leader.dropMessage(6, "Stage 7 is clear. Sweep the map and collect all three passes.");
                leaderState.deferUntil(nowMs + 5_000L);
            }
        }
        boolean sweepComplete = session.stage7LootSweepIndex() >= STAGE_7_LOOT_SWEEP.size();
        if (sweepComplete && leaderEntry != null) {
            Point npc = ACTIONS.npcPosition(leader, AgentLpqDefinition.stage(7).npcId());
            if (npc != null) rallyMemberAtNpc(
                    session, 7, leaderState, leader, leaderEntry, npc, nowMs);
        }
        if (session.stage7ForceLootAttempted() || !sweepComplete
                || combatClearedForMs < STAGE_7_FORCE_LOOT_DELAY_MS) return;
        int vacuumed = vacuumPassDrops(leader, Set.of(leader.getMap()));
        session.markStage7ForceLootAttempted(nowMs);
        log.info("LPQ Stage 7 verified leader sweep: session={} leader={}({}) "
                        + "sweepComplete={} clearForMs={} vacuumedPasses={}",
                session.sessionId(), leader.getName(), leader.getId(),
                sweepComplete, combatClearedForMs, vacuumed);
    }

    private static void splitRooms(AgentLpqSession session, int stage, long nowMs) {
        int mainMap = AgentLpqDefinition.stage(stage).mapId();
        List<Integer> rooms = AgentLpqDefinition.roomMaps(stage);
        // An occupant is authoritative evidence of progress and must not lose its room lease.
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || !rooms.contains(participant.getMapId())) continue;
            session.rooms().markEntered(participant.getMapId());
            if (session.rooms().completed(participant.getMapId())) continue;
            if (java.util.Objects.equals(session.rooms().owner(participant.getMapId()), participant.getId())) {
                session.rooms().markProgress(participant.getMapId(), nowMs);
            }
        }
        for (AgentLpqRoomAssignment.ExpiredReservation expired
                : session.rooms().releaseExpired(nowMs, ROOM_LEASE_MS)) {
            AgentLpqMemberState displaced = session.member(expired.characterId());
            if (displaced != null && displaced.assignedMapId() == expired.roomMapId()) {
                displaced.assign(AgentLpqMemberState.Role.GENERAL, 0);
            }
            log.warn("LPQ room lease expired atomically: session={} room={} formerOwner={}",
                    session.sessionId(), expired.roomMapId(), expired.characterId());
        }
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant != null && rooms.contains(participant.getMapId())) {
                if (session.rooms().completed(participant.getMapId())) continue;
                Integer owner = session.rooms().owner(participant.getMapId());
                if (suitable(member.characterId(), stage, participant.getMapId())
                        && owner != null && owner != participant.getId()) {
                    session.rooms().release(participant.getMapId());
                    AgentLpqMemberState displaced = session.member(owner);
                    if (displaced != null) displaced.assign(AgentLpqMemberState.Role.GENERAL, 0);
                }
                if (session.rooms().reserve(participant.getMapId(), participant.getId(), nowMs)
                        && member.assignedMapId() != participant.getMapId()) {
                    assignRoom(session, member, roomRole(stage, participant.getMapId()),
                            participant.getMapId(), stage, false);
                }
            }
        }
        assignRooms(session, stage, rooms, nowMs);
        Point npc = stageNpcPosition(session, stage);
        Map<Integer, Set<Integer>> lootClaimsByMap = new LinkedHashMap<>();
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            if (!rooms.contains(agent.getMapId())) member.clearRoomExitProgress();
            if (rooms.contains(agent.getMapId())) {
                // Map object ids are scoped to one MapleMap. Sharing one bare OID set
                // across all five/six split rooms makes, for example, room 503's pass
                // look claimed merely because room 502 happened to allocate the same
                // object id. Keep the cooperative claim set map-scoped.
                Set<Integer> roomLootClaims = lootClaimsByMap.computeIfAbsent(
                        agent.getMapId(), ignored -> new LinkedHashSet<>());
                if (collectNearestDrop(entry, agent,
                        Set.of(AgentLpqDefinition.PASS), roomLootClaims)) continue;
            }
            int assigned = member.assignedMapId();
            if (agent.getMapId() == mainMap && assigned != 0) {
                if (session.rooms().completed(assigned)) {
                    member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                    member.clearTraversalProgress();
                    if (npc != null) rallyMemberAtNpc(
                            session, stage, member, agent, entry, npc, nowMs);
                    continue;
                }
                if (!java.util.Objects.equals(session.rooms().owner(assigned), agent.getId())) {
                    member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                    if (npc != null) rallyMemberAtNpc(
                            session, stage, member, agent, entry, npc, nowMs);
                    continue;
                }
                if (member.observeRoomApproachProgress(assigned, agent.getPosition())) {
                    session.rooms().markProgress(assigned, nowMs);
                }
                markRoomAndEnter(session, member, entry, agent, assigned, nowMs);
                continue;
            }
            if (agent.getMapId() == mainMap && assigned == 0) {
                if (npc != null) rallyMemberAtNpc(
                        session, stage, member, agent, entry, npc, nowMs);
                continue;
            }
            if (!rooms.contains(agent.getMapId())) continue;
            int roomMapId = agent.getMapId();
            session.rooms().markProgress(roomMapId, nowMs);
            if (session.rooms().completed(roomMapId)) {
                member.beginRoomExit(roomMapId, nowMs);
                if (prepareDarkSightRoomExit(
                        stage, roomMapId, entry, agent, member, nowMs)) {
                    continue;
                }
                long exitInactiveMs = traversalInactivity(member, agent, mainMap, nowMs);
                long exitElapsedMs = member.roomExitElapsed(roomMapId, nowMs);
                if (exitInactiveMs >= recovery(stage).roomExitPlacementMs()
                        && exitElapsedMs >= recovery(stage).roomExitPlacementMs()) {
                    forceRoomExitThroughPortal(session, member, entry, agent, mainMap, nowMs);
                } else {
                    enterPortalTo(entry, agent, mainMap);
                    if (agent.getMapId() == mainMap) {
                        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                        member.clearTraversalProgress();
                        if (npc != null) rallyMemberAtNpc(
                                session, stage, member, agent, entry, npc, nowMs);
                    }
                }
                continue;
            }
            Integer roomOwner = session.rooms().owner(roomMapId);
            boolean stageFourMagicHelper = stage == 4
                    && AgentLpqDefinition.STAGE_4_MAGIC_ROOMS.contains(roomMapId)
                    && member.assignedMapId() == roomMapId
                    && AgentLpqRosterRequirementPolicy.magicAttack(agent);
            if (roomOwner != null && roomOwner != agent.getId() && !stageFourMagicHelper) {
                member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                enterPortalTo(entry, agent, mainMap);
                continue;
            }
            Set<Integer> mobs = stage == 4
                    ? stageFourCombatTargets(roomMapId)
                    : ACTIONS.configuredMonsterSpawnIds(agent);
            boolean darkSightRoom = stage == 5
                    && roomMapId == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM;
            boolean reactorSpawnsMustBeCleared = stage == 4;
            boolean roomMobsMustBeCleared = reactorSpawnsMustBeCleared
                    || (stage == 5 && !darkSightRoom);
            if (darkSightRoom && maintainDarkSight(entry, agent, member, nowMs)) continue;
            boolean acted;
            if (member.reactorSpawnCleanupPending()) {
                if (reactorSpawnsMustBeCleared && ACTIONS.liveMonsterCount(agent, mobs) > 0) {
                    ACTIONS.grind(entry, mobs);
                    acted = true;
                } else {
                    member.finishReactorSpawnCleanup();
                    acted = hitNearestReactor(
                            entry, agent, nowMs, member, reactorSpawnsMustBeCleared);
                }
            } else {
                acted = hitNearestReactor(
                        entry, agent, nowMs, member, reactorSpawnsMustBeCleared);
            }
            if (!acted && roomMobsMustBeCleared && !mobs.isEmpty()) {
                if (stage == 4) grindStageFourEyeMonster(entry, agent, member, mobs, nowMs);
                else ACTIONS.grind(entry, mobs);
            }
            // A Stage 5 hazard can eject the Dark Sight runner while this tick is executing.
            // Never complete or release a room using the character's new exit-map id.
            if (agent.getMapId() != roomMapId) continue;
            boolean exhausted = activeReactors(agent).isEmpty()
                    && (!roomMobsMustBeCleared || ACTIONS.liveMonsterCount(agent, mobs) == 0)
                    && AgentMapPerception.items(agent.getMap()).stream()
                    .noneMatch(item -> !item.isPickedUp() && item.getItemId() == AgentLpqDefinition.PASS);
            int passQuota = AgentLpqDefinition.roomPassQuota(roomMapId);
            int roomPasses = member.roomPassesCollectedFor(roomMapId,
                    agent.getItemQuantity(AgentLpqDefinition.PASS, false));
            boolean passQuotaMet = roomPasses >= passQuota;
            reportRoomProgress(session, member, agent, roomMapId, activeReactors(agent).size(),
                    ACTIONS.liveMonsterCount(agent, mobs), roomPasses, passQuota, nowMs);
            announcePassProgress(session, member, agent, stage, roomMapId,
                    roomPasses, passQuota, AgentLpqDefinition.stage(stage).submissionCount());
            if (exhausted && passQuotaMet) {
                session.rooms().complete(roomMapId);
                member.beginRoomExit(roomMapId, nowMs);
                if (prepareDarkSightRoomExit(
                        stage, roomMapId, entry, agent, member, nowMs)) {
                    session.markProgress(nowMs);
                    continue;
                }
                enterPortalTo(entry, agent, mainMap);
                if (agent.getMapId() == mainMap) {
                    member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                    member.clearTraversalProgress();
                    if (npc != null) rallyMemberAtNpc(
                            session, stage, member, agent, entry, npc, nowMs);
                }
                session.markProgress(nowMs);
            }
        }
    }

    private static boolean markRoomAndEnter(AgentLpqSession session, AgentLpqMemberState member,
                                            AgentRuntimeEntry entry, Character agent,
                                            int roomMapId, long nowMs) {
        if (!java.util.Objects.equals(session.rooms().owner(roomMapId), agent.getId())) return false;
        Integer portalId = AgentLpqExitRoutePolicy.portalId(agent.getMapId(), roomMapId);
        if (portalId == null) portalId = ACTIONS.directPortalIdTo(agent, roomMapId);
        Point portal = portalId == null ? null : ACTIONS.portalPosition(agent, portalId);
        if (portalId == null || portal == null) return false;
        if (requiresDarkSightBeforeHazardTraversal(roomMapId, darkSightRefreshDue(agent, nowMs))) {
            Point safeSpot = ACTIONS.portalPosition(agent, DARK_SIGHT_SAFE_PORTAL_ID);
            if (safeSpot != null && !near(agent.getPosition(), safeSpot, INTERACTION_RADIUS)) {
                ACTIONS.navigate(entry, safeSpot, true);
                return true;
            }
            ACTIONS.stop(entry);
            if (refreshDarkSight(entry, agent)) {
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
            }
            return true;
        }
        if (!atDoorMarkerPosition(agent.getPosition(), portal)) {
            ACTIONS.navigate(entry, portal, true);
            return true;
        }
        ACTIONS.stop(entry);
        if (!member.roomMarkerDroppedFor(roomMapId)) {
            if (nowMs < member.nextActionAtMs()) return true;
            boolean markerDropped = AgentScriptItemActionService.dropMesos(
                    entry, AgentLpqDefinition.ROOM_MARKER_MESOS);
            if (!markerDropped) {
                int markerItem = AgentLpqDefinition.ROOM_MARKER_ITEMS.stream()
                        .filter(itemId -> ACTIONS.itemCount(agent, itemId) > 0)
                        .findFirst().orElse(0);
                if (markerItem == 0 && AgentInventoryGatewayRuntime.inventory()
                        .addItem(agent, AgentLpqDefinition.RED_POTION, (short) 1)) {
                    markerItem = AgentLpqDefinition.RED_POTION;
                }
                markerDropped = markerItem != 0 && AgentScriptItemActionService.dropItem(
                        entry, InventoryType.USE, markerItem, (short) 1);
            }
            if (!markerDropped) {
                log.warn("LPQ room marker unavailable; retrying before room entry: "
                                + "session={} member={}({}) stage={} room={}",
                        session.sessionId(), agent.getName(), agent.getId(),
                        stage(session.phase()), roomMapId);
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
                return true;
            }
            member.markRoomMarkerDropped(roomMapId);
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
            session.rooms().markProgress(roomMapId, nowMs);
            session.markProgress(nowMs);
            return true;
        }
        if (nowMs < member.nextActionAtMs()) return true;
        boolean entered = LPQ.enterPortal(agent, portalId);
        if (entered) session.rooms().markProgress(roomMapId, nowMs);
        return entered;
    }

    static boolean atDoorMarkerPosition(Point characterPosition, Point portalPosition) {
        if (characterPosition == null || portalPosition == null) return false;
        return Math.abs(characterPosition.x - portalPosition.x) <= 28
                && Math.abs(characterPosition.y - portalPosition.y) <= 24;
    }

    private static void assignRooms(AgentLpqSession session, int stage, List<Integer> rooms, long nowMs) {
        List<AgentLpqMemberState> agents = session.members().stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(member -> member.characterId() == session.eventLeaderId() ? 1 : 0))
                .toList();
        List<Integer> assignmentOrder = stage == 5
                ? List.of(AgentLpqDefinition.STAGE_5_TELEPORT_ROOM,
                AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM, 922_010_502,
                922_010_503, 922_010_504, 922_010_505)
                : stage == 4
                ? List.of(922_010_401, 922_010_402, 922_010_404,
                922_010_405, 922_010_403)
                : rooms;
        for (int room : assignmentOrder) {
            if (session.rooms().completed(room) || session.rooms().owner(room) != null) continue;
            var eligible = agents.stream().filter(member -> member.assignedMapId() == 0)
                    .filter(member -> suitable(member.characterId(), stage, room));
            AgentLpqMemberState chosen = stage == 4 && room == 922_010_404
                    ? eligible.min(Comparator.comparingInt(member ->
                    AgentLpqRosterRequirementPolicy.stageFourTwoMonsterRoomPriority(
                            character(member.characterId())))).orElse(null)
                    : eligible.findFirst().orElse(null);
            boolean specialized = stage == 4
                    || room == AgentLpqDefinition.STAGE_5_TELEPORT_ROOM
                    || room == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM;
            if (chosen == null && stage == 4
                    && room == AgentLpqDefinition.STAGE_4_WEAK_MAGIC_ROOM) {
                chosen = agents.stream().filter(member -> member.assignedMapId() == 0)
                        .findFirst().orElse(null);
            }
            if (chosen == null && !specialized) {
                chosen = agents.stream().filter(member -> member.assignedMapId() == 0).findFirst().orElse(null);
            }
            if (chosen == null) continue;
            if (!session.rooms().reserve(room, chosen.characterId(), nowMs)) continue;
            assignRoom(session, chosen, roomRole(stage, room), room, stage, false);
        }
        if (stage == 4) assignStageFourMagicHelper(session, agents);
    }

    private static void assignStageFourMagicHelper(AgentLpqSession session,
                                                    List<AgentLpqMemberState> agents) {
        if (session.rooms().completed(AgentLpqDefinition.STAGE_4_WEAK_MAGIC_ROOM)) return;
        Integer ownerId = session.rooms().owner(AgentLpqDefinition.STAGE_4_WEAK_MAGIC_ROOM);
        Character owner = ownerId == null ? null : character(ownerId);
        if (owner == null || AgentLpqRosterRequirementPolicy.magicAttack(owner)) return;
        AgentLpqMemberState helper = agents.stream()
                .filter(member -> member.assignedMapId() == 0)
                .filter(member -> {
                    Character candidate = character(member.characterId());
                    return AgentLpqRosterRequirementPolicy.magicAttack(candidate);
                })
                .findFirst().orElse(null);
        if (helper != null) {
            assignRoom(session, helper, AgentLpqMemberState.Role.MAGIC_ATTACKER,
                    AgentLpqDefinition.STAGE_4_WEAK_MAGIC_ROOM, 4, true);
        }
    }

    private static void assignRoom(AgentLpqSession session, AgentLpqMemberState member,
                                   AgentLpqMemberState.Role role, int roomMapId,
                                   int stage, boolean helper) {
        member.assign(role, roomMapId);
        Character character = character(member.characterId());
        int passCount = character == null ? 0
                : character.getItemQuantity(AgentLpqDefinition.PASS, false);
        member.beginRoomPassCollection(roomMapId, passCount);
        if (stage == 5 && character != null && session.eventInstance() != null) {
            MapleMap room = session.eventInstance().getMapInstance(roomMapId);
            AgentRuntimeEntry entry = entry(member.characterId());
            AgentNavigationGraphService.warmGraphAsync(
                    room, AgentMovementStateRuntime.movementProfileOrCharacter(entry, character));
        }
        announceRoomIntent(session, member, character, stage, roomMapId, role, helper);
    }

    private static AgentLpqMemberState.Role roomRole(int stage, int room) {
        if (stage == 5 && room == AgentLpqDefinition.STAGE_5_TELEPORT_ROOM) {
            return AgentLpqMemberState.Role.TELEPORT_RUNNER;
        }
        if (stage == 5 && room == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM) {
            return AgentLpqMemberState.Role.DARK_SIGHT_RUNNER;
        }
        if (AgentLpqDefinition.STAGE_4_MAGIC_ROOMS.contains(room)) {
            return AgentLpqMemberState.Role.MAGIC_ATTACKER;
        }
        if (AgentLpqDefinition.STAGE_4_PHYSICAL_ROOMS.contains(room)) {
            return AgentLpqMemberState.Role.PHYSICAL_ATTACKER;
        }
        return AgentLpqMemberState.Role.GENERAL;
    }

    private static boolean suitable(int id, int stage, int room) {
        Character member = character(id);
        if (stage == 4 && AgentLpqDefinition.STAGE_4_MAGIC_ROOMS.contains(room)) {
            return AgentLpqRosterRequirementPolicy.magicAttack(member);
        }
        if (stage == 4 && AgentLpqDefinition.STAGE_4_PHYSICAL_ROOMS.contains(room)) {
            return AgentLpqRosterRequirementPolicy.physicalAttack(member);
        }
        if (stage == 5 && room == AgentLpqDefinition.STAGE_5_TELEPORT_ROOM) {
            return AgentLpqRosterRequirementPolicy.teleportMagic(member);
        }
        if (stage == 5 && room == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM) {
            return AgentLpqRosterRequirementPolicy.darkSight(member);
        }
        return true;
    }

    static Set<Integer> stageFourCombatTargets(int roomMapId) {
        if (AgentLpqDefinition.STAGE_4_MAGIC_ROOMS.contains(roomMapId)) {
            return Set.of(AgentLpqDefinition.STAGE_4_MAGIC_MOB);
        }
        if (AgentLpqDefinition.STAGE_4_PHYSICAL_ROOMS.contains(roomMapId)) {
            return Set.of(AgentLpqDefinition.STAGE_4_PHYSICAL_MOB);
        }
        return Set.of();
    }

    private static void grindStageFourEyeMonster(AgentRuntimeEntry entry, Character agent,
                                                  AgentLpqMemberState member, Set<Integer> mobs,
                                                  long nowMs) {
        if (nowMs < member.nextActionAtMs()) return;
        Monster target = AgentGrindTargetStateRuntime.activeTargetInMap(entry, agent.getMap());
        if (target != null && (!mobs.contains(target.getId())
                || !AgentCombatTargetRuntime.isReachableGrindTarget(entry, agent, target))) {
            AgentGrindTargetStateRuntime.clear(entry);
            member.clearRoomCombatProgress();
            target = null;
        }
        if (target != null && target.getPosition().distanceSq(agent.getPosition())
                <= (long) STAGE_4_STALL_OBSERVATION_RANGE_PX
                * STAGE_4_STALL_OBSERVATION_RANGE_PX) {
            long stalledForMs = member.observeRoomCombatTarget(
                    agent.getMapId(), target.getObjectId(), target.getHp(), nowMs);
            if (stalledForMs >= STAGE_4_COMBAT_STALL_MS) {
                ACTIONS.stop(entry);
                AgentGrindTargetStateRuntime.clear(entry);
                member.clearRoomCombatProgress();
                member.deferUntil(nowMs + STAGE_4_EYE_RETRY_MS);
                log.warn("LPQ Stage 4 eye-monster attack made no authoritative HP progress; "
                                + "retrying target selection: member={}({}) map={} mob={} oid={} stalledForMs={}",
                        agent.getName(), agent.getId(), agent.getMapId(), target.getId(),
                        target.getObjectId(), stalledForMs);
                return;
            }
        } else {
            member.clearRoomCombatProgress();
        }
        ACTIONS.grind(entry, mobs);
    }

    private static void reportRoomProgress(AgentLpqSession session, AgentLpqMemberState member,
                                           Character agent, int roomMapId, int reactors,
                                           int mobs, int passes, int quota, long nowMs) {
        int loosePasses = unpickedPassDropCount(agent.getMap().getDroppedItems());
        String signature = reactors + ":" + mobs + ":" + passes + ":" + loosePasses;
        if (!member.shouldReportRoomProgress(
                roomMapId, signature, nowMs, ROOM_PROGRESS_LOG_INTERVAL_MS)) return;
        Monster target = AgentGrindTargetStateRuntime.activeTargetInMap(entry(agent.getId()), agent.getMap());
        Integer ownerId = session.rooms().owner(roomMapId);
        log.info("LPQ room progress: stage={} map={} owner={} member={}({}) role={} "
                        + "reactors={} mobs={} passes={}/{} loosePasses={} targetOid={} targetMob={} targetHp={}",
                AgentLpqDefinition.stageNumber(roomMapId), roomMapId, ownerId,
                agent.getName(), agent.getId(), member.role(), reactors, mobs, passes, quota,
                loosePasses, target == null ? 0 : target.getObjectId(),
                target == null ? 0 : target.getId(), target == null ? 0 : target.getHp());
    }

    static boolean requiresDarkSightBeforeHazardTraversal(int roomMapId, boolean refreshDue) {
        return roomMapId == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM && refreshDue;
    }

    static boolean requiresDarkSightBeforeRoomExit(int stage, int roomMapId,
                                                    boolean protectionPrepared,
                                                    boolean refreshDue) {
        return stage == 5 && roomMapId == AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM
                && !protectionPrepared && refreshDue;
    }

    private static boolean prepareDarkSightRoomExit(
            int stage, int roomMapId, AgentRuntimeEntry entry, Character agent,
            AgentLpqMemberState member, long nowMs) {
        if (!requiresDarkSightBeforeRoomExit(
                stage, roomMapId, member.roomExitProtectionPreparedFor(roomMapId),
                darkSightRefreshDue(agent, nowMs))) {
            return false;
        }
        Integer exitPortalId = portalIdTo(agent, AgentLpqDefinition.stage(stage).mapId());
        Point safeSpot = exitPortalId == null ? null : ACTIONS.portalPosition(agent, exitPortalId);
        if (safeSpot != null && (!near(agent.getPosition(), safeSpot, INTERACTION_RADIUS)
                || AgentMovementStateRuntime.inAir(entry)
                || AgentMovementStateRuntime.climbing(entry))) {
            ACTIONS.navigate(entry, safeSpot, true);
            return true;
        }
        ACTIONS.stop(entry);
        if (refreshDarkSight(entry, agent)) {
            member.markRoomExitProtectionPrepared(roomMapId);
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
        }
        return true;
    }

    private static void recoverMissingPasses(AgentLpqSession session, Character leader,
                                             int stage, int required, long nowMs) {
        if (stage == 5 && !allSplitRoomsEntered(session, stage)) {
            session.markPassRecoveryConsolidation(nowMs);
            return;
        }
        if (session.passHandoffRecoveryActive()) {
            recoverPassHandoffAtNpc(
                    session, leader, AgentLpqDefinition.stage(stage), nowMs);
            return;
        }
        int partyPasses = partyPassCount(session);
        int leaderPasses = leader.getItemQuantity(AgentLpqDefinition.PASS, false);
        Set<MapleMap> maps = collectionMaps(session, stage);
        int liveMobs = maps.stream().mapToInt(map -> map.getAllMonsters().size()).sum();
        int activeReactorCount = activeReactorCount(session, maps);
        long liveMobHp = maps.stream().flatMap(map -> map.getAllMonsters().stream())
                .mapToLong(Monster::getHp).sum();
        long inactiveForMs = session.observePassRecoveryProgress(
                partyPasses, liveMobs, activeReactorCount, liveMobHp, nowMs);
        boolean stageOneCombatCeilingReached = stageOneCombatCeilingReached(
                stage, session.phaseEnteredAtMs(), nowMs, STAGE_1_MAX_NATURAL_COMBAT_MS);
        if (leaderPasses >= required
                || (!stageOneCombatCeilingReached
                && inactiveForMs < recovery(stage).missingPassMs())) {
            return;
        }
        int vacuumed = recoverPassDrops(session, leader, stage, maps);
        partyPasses = partyPassCount(session);
        if (passHandoffRecoveryApplicable(stage, partyPasses, required)) {
            if (session.beginPassHandoffRecovery(nowMs)) {
                log.warn("LPQ Stage {} pass handoff recovery started: session={} leader={}({}) "
                                + "partyPasses={}/{} vacuumedDrops={} currentStageMap={}",
                        stage, session.sessionId(), leader.getName(), leader.getId(), partyPasses,
                        required, vacuumed, AgentLpqDefinition.stage(stage).mapId());
            }
            recoverPassHandoffAtNpc(
                    session, leader, AgentLpqDefinition.stage(stage), nowMs);
            return;
        }
        if (vacuumed > 0) {
            session.markPassRecoveryConsolidation(nowMs);
            log.warn("LPQ Stage {} pass recovery vacuumed {} unreachable pass drops while "
                            + "preserving split-room ownership: session={} leader={}({}) "
                            + "partyPasses={}/{} maps={}",
                    stage, vacuumed, session.sessionId(), leader.getName(), leader.getId(),
                    partyPasses, required, maps.stream().map(MapleMap::getId).toList());
            return;
        }
        if (partyPasses >= required) return;
        if (stage == 5) {
            int resumedReactors = recoverStageFiveReactors(session, nowMs);
            if (resumedReactors > 0) {
                session.markPassRecoveryConsolidation(nowMs);
                log.warn("LPQ Stage 5 missing-pass recovery resumed normal navigation for {} "
                                + "stalled box workers; hazard mobs were left untouched: "
                                + "session={} partyPasses={}/{}",
                        resumedReactors, session.sessionId(), partyPasses, required);
                return;
            }
        }
        if (stage == 7) {
            int resumedReactors = recoverStageSevenReactors(session, nowMs);
            if (resumedReactors > 0) {
                session.markPassRecoveryConsolidation(nowMs);
                log.warn("LPQ Stage 7 recovery resumed {} ranged trigger workers before "
                                + "considering any Rombard or pass fallback: session={} partyPasses={}/{}",
                        resumedReactors, session.sessionId(), partyPasses, required);
                return;
            }
        }
        if (missingPassMobSweepAllowed(stage)
                && liveMobs > 0 && !session.passRecoveryMobSweepAttempted()) {
            for (MapleMap map : maps) {
                List<Integer> mobIds = map.getAllMonsters().stream()
                        .map(mob -> mob.getId()).distinct().toList();
                for (int mobId : mobIds) map.killMonsterWithDrops(mobId);
            }
            session.markPassRecoveryMobSweep(nowMs);
            log.warn("LPQ Stage {} missing-pass recovery killed {} stalled mobs with drops: "
                            + "session={} partyPasses={}/{} maps={} phaseElapsedMs={} inactiveForMs={}",
                    stage, liveMobs, session.sessionId(), partyPasses, required,
                    maps.stream().map(MapleMap::getId).toList(),
                    Math.max(0L, nowMs - session.phaseEnteredAtMs()), inactiveForMs);
            return;
        }
        int missing = Math.max(0, required - partyPasses);
        if (missing == 0 || session.passRecoveryPassesAwarded()) return;
        if (!AgentInventoryGatewayRuntime.inventory().addItem(
                leader, AgentLpqDefinition.PASS, (short) missing)) {
            AgentLpqTerminationService.fail(
                    session, "LPQ passes disappeared and the leader has no ETC space for recovery", nowMs);
            return;
        }
        session.markPassRecoveryPassesAwarded(nowMs);
        log.warn("LPQ Stage {} missing-pass recovery awarded {} passes to leader: "
                        + "session={} leader={}({}) partyPasses={}/{} mobs={} maps={}",
                stage, missing, session.sessionId(), leader.getName(), leader.getId(),
                partyPasses, required, liveMobs, maps.stream().map(MapleMap::getId).toList());
    }

    private static boolean memberOutsideStageMap(AgentLpqSession session, int stageMapId) {
        return session.members().stream()
                .map(member -> character(member.characterId()))
                .anyMatch(participant -> participant == null || participant.getMapId() != stageMapId);
    }

    private static boolean outsideMembersHaveCompletedRooms(
            AgentLpqSession session, int stage, int stageMapId) {
        List<Integer> rooms = AgentLpqDefinition.roomMaps(stage);
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null) return false;
            if (participant.getMapId() == stageMapId) continue;
            if (!rooms.contains(participant.getMapId())
                    || !session.rooms().completed(participant.getMapId())) return false;
        }
        return true;
    }

    private static void purgeResidualPasses(AgentLpqSession session, int clearedStage) {
        int removed = 0;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            int quantity = participant == null ? 0
                    : participant.getItemQuantity(AgentLpqDefinition.PASS, false);
            if (quantity <= 0) continue;
            AgentInventoryGatewayRuntime.inventory().removeById(
                    participant, InventoryType.ETC, AgentLpqDefinition.PASS,
                    quantity, false, false);
            removed += quantity;
        }
        if (removed > 0) {
            log.info("LPQ cleared Stage {} residual pass cleanup removed {} coupons: session={}",
                    clearedStage, removed, session.sessionId());
        }
    }

    private static int recoverStageFiveReactors(AgentLpqSession session, long nowMs) {
        int resumed = 0;
        for (int roomMapId : AgentLpqDefinition.roomMaps(5)) {
            AgentLpqMemberState workerState = session.members().stream()
                    .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                    .filter(member -> member.characterId() == java.util.Objects.requireNonNullElse(
                            session.rooms().owner(roomMapId), -1)
                            || member.assignedMapId() == roomMapId)
                    .filter(member -> {
                        Character worker = character(member.characterId());
                        return worker != null && worker.getMapId() == roomMapId;
                    })
                    .findFirst().orElse(null);
            if (workerState == null) continue;
            Character worker = character(workerState.characterId());
            AgentRuntimeEntry workerEntry = entry(workerState.characterId());
            if (workerEntry == null) continue;
            List<Reactor> stalled = activeReactors(worker);
            if (stalled.isEmpty()) continue;
            if (hitReactorFromCandidates(workerEntry, worker, nowMs, workerState,
                    false, Set.of(), stalled, true)) {
                resumed++;
                log.warn("LPQ Stage 5 recovery resumed committed-box navigation: map={} "
                                + "owner={} member={}({}) reactorOids={}",
                        roomMapId, session.rooms().owner(roomMapId), worker.getName(),
                        worker.getId(), stalled.stream().map(Reactor::getObjectId).toList());
            }
        }
        return resumed;
    }

    private static int recoverStageSevenReactors(AgentLpqSession session, long nowMs) {
        AgentLpqMemberState workerState = session.members().stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .filter(member -> member.role() == AgentLpqMemberState.Role.RANGED_TRIGGER)
                .filter(member -> {
                    Character worker = character(member.characterId());
                    return worker != null
                            && worker.getMapId() == AgentLpqDefinition.stage(7).mapId();
                })
                .findFirst().orElse(null);
        if (workerState == null) return 0;
        Character worker = character(workerState.characterId());
        AgentRuntimeEntry workerEntry = entry(workerState.characterId());
        if (worker == null || workerEntry == null) return 0;
        List<Reactor> stalled = activeReactors(worker).stream()
                .filter(reactor -> reactor.getId() == AgentLpqDefinition.STAGE_7_TRIGGER_REACTOR)
                .toList();
        if (stalled.isEmpty()) return 0;
        return hitStageSevenTrigger(session, workerEntry, worker, nowMs, workerState,
                Set.of(), stalled) ? 1 : 0;
    }

    static boolean missingPassMobSweepAllowed(int stage) {
        return stage != 5;
    }

    static boolean npcRallyRecoveryDue(int stage, long inactiveForMs) {
        return inactiveForMs >= recovery(stage).rallyRecoveryMs();
    }

    static boolean couponRegroupPositionRecoveryDue(int stage, long regroupElapsedMs) {
        return regroupElapsedMs >= recovery(stage).rallyRecoveryMs();
    }

    static boolean postClearTransitionRecoveryDue(int stage, long transitionElapsedMs) {
        return transitionElapsedMs >= recovery(stage).rallyRecoveryMs();
    }

    static boolean stageTransitionCatchUpRecoveryDue(int destinationStage, long phaseElapsedMs) {
        return destinationStage != 2
                && phaseElapsedMs >= recovery(destinationStage).rallyRecoveryMs();
    }

    static boolean stageOneCombatCeilingReached(int stage, long phaseEnteredAtMs,
                                                long nowMs, long ceilingMs) {
        return stage == 1
                && ceilingMs > 0L
                && nowMs - phaseEnteredAtMs >= ceilingMs;
    }

    static boolean allSplitRoomsEntered(AgentLpqSession session, int stage) {
        if (session == null) return false;
        List<Integer> rooms = AgentLpqDefinition.roomMaps(stage);
        return rooms.isEmpty() || session.rooms().enteredAll(rooms);
    }

    private static int activeReactorCount(AgentLpqSession session, Set<MapleMap> maps) {
        EventInstanceManager event = session.eventInstance();
        int count = 0;
        for (MapleMap map : maps) {
            for (Reactor reactor : map.getAllReactors()) {
                if (reactor == null || !reactor.isAlive() || !reactor.isActive()) continue;
                if (event != null && event.getProperty(ReactorActionManager.eventReactorActionKey(
                        "lpq", map.getId(), reactor.getObjectId())) != null) continue;
                count++;
            }
        }
        return count;
    }

    static boolean passHandoffRecoveryApplicable(int stage, int partyPasses, int required) {
        if (partyPasses < required) return false;
        return stage == 4 || stage == 5 || stage == 7;
    }

    static PassHandoffExitAction passHandoffExitAction(int stage, long exitInactiveMs) {
        AgentLpqStageRecoveryPolicy recovery = recovery(stage);
        if (exitInactiveMs >= recovery.portalMs()) {
            return PassHandoffExitAction.MOVE_TO_STAGE;
        }
        if (exitInactiveMs >= recovery.roomExitPlacementMs()) {
            return PassHandoffExitAction.PLACE_AT_PORTAL;
        }
        return PassHandoffExitAction.NAVIGATE;
    }

    private static boolean recoverPassHandoffAtNpc(
            AgentLpqSession session, Character leader,
            AgentLpqDefinition.Stage contract, long nowMs) {
        int mainMapId = contract.mapId();
        List<Integer> roomMaps = AgentLpqDefinition.roomMaps(contract.number());
        long recoveryElapsedMs = session.passHandoffRecoveryElapsed(nowMs);
        boolean everyoneOnMainMap = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null) {
                everyoneOnMainMap = false;
                continue;
            }
            if (participant.getMapId() == mainMapId) continue;
            everyoneOnMainMap = false;
            PassHandoffExitAction exitAction = passHandoffExitAction(
                    contract.number(), recoveryElapsedMs);
            if (roomMaps.contains(participant.getMapId())
                    && member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry participantEntry = entry(participant.getId());
                if (participantEntry == null) continue;
                int roomMapId = participant.getMapId();
                member.beginRoomExit(roomMapId, nowMs);
                if (exitAction == PassHandoffExitAction.NAVIGATE
                        && prepareDarkSightRoomExit(contract.number(), roomMapId,
                        participantEntry, participant, member, nowMs)) {
                    continue;
                }
                if (exitAction == PassHandoffExitAction.NAVIGATE) {
                    enterPortalTo(participantEntry, participant, mainMapId);
                    continue;
                }
                if (exitAction == PassHandoffExitAction.PLACE_AT_PORTAL) {
                    forceRoomExitThroughPortal(
                            session, member, participantEntry, participant, mainMapId, nowMs);
                    continue;
                }
            } else if (roomMaps.contains(participant.getMapId())
                    && member.memberType() == AgentLpqMemberState.MemberType.HUMAN
                    && exitAction != PassHandoffExitAction.MOVE_TO_STAGE) {
                if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "LPQ Stage " + contract.number()
                            + " is ready. Use the room's exit portal and return to the balloon.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            if (exitAction == PassHandoffExitAction.MOVE_TO_STAGE) {
                int sourceMapId = participant.getMapId();
                if (moveWithinEvent(session, participant, mainMapId, nowMs)) {
                    member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                    member.clearTraversalProgress();
                    log.warn("LPQ hard current-stage handoff recovery: session={} stage={} "
                                    + "member={}({}) sourceMap={} destination={} elapsedMs={}",
                            session.sessionId(), contract.number(), participant.getName(),
                            participant.getId(), sourceMapId, mainMapId, recoveryElapsedMs);
                }
            }
        }
        if (!everyoneOnMainMap) {
            session.observeMainMapRally(mainMapId, false, nowMs);
            return false;
        }

        regroupCouponsAtNpc(session, leader, contract, nowMs);
        Point npc = ACTIONS.npcPosition(leader, contract.npcId());
        if (npc == null) return false;
        boolean everyoneAtNpc = true;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != mainMapId) {
                everyoneAtNpc = false;
                continue;
            }
            if (near(participant.getPosition(), npc, INTERACTION_RADIUS)) continue;
            AgentRuntimeEntry participantEntry = entry(participant.getId());
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT
                    && participantEntry != null) {
                rallyMemberAtNpc(session, contract.number(), member,
                        participant, participantEntry, npc, nowMs);
                everyoneAtNpc = false;
                continue;
            } else {
                everyoneAtNpc = false;
            }
        }
        return everyoneAtNpc && leader.getItemQuantity(
                AgentLpqDefinition.PASS, false) >= contract.submissionCount();
    }

    private static boolean forceRoomExitThroughPortal(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, int destinationMapId, long nowMs) {
        int sourceMapId = agent.getMapId();
        Integer portalId = portalIdTo(agent, destinationMapId);
        Point portal = portalId == null ? null : ACTIONS.portalPosition(agent, portalId);
        if (portalId == null || portal == null) return false;
        ACTIONS.stop(entry);
        ACTIONS.stagePosition(entry, agent, portal);
        boolean exited = LPQ.enterPortal(agent, portalId);
        if (!exited) return false;
        session.rooms().release(sourceMapId);
        member.assign(AgentLpqMemberState.Role.GENERAL, 0);
        member.clearTraversalProgress();
        session.markProgress(nowMs);
        log.warn("LPQ authored room-exit recovery: session={} member={}({}) sourceMap={} "
                        + "portal={} destination={}", session.sessionId(), agent.getName(),
                agent.getId(), sourceMapId, portalId, destinationMapId);
        return true;
    }

    private static int vacuumPassDrops(Character recipient, Set<MapleMap> maps) {
        return vacuumItemDrops(recipient, maps, AgentLpqDefinition.PASS);
    }

    private static int recoverPassDrops(AgentLpqSession session, Character leader,
                                        int stage, Set<MapleMap> maps) {
        if (stage != 4 && stage != 5) return vacuumPassDrops(leader, maps);
        int vacuumed = 0;
        int mainMapId = AgentLpqDefinition.stage(stage).mapId();
        for (MapleMap map : maps) {
            Character recipient = map.getId() == mainMapId
                    ? leader : splitRoomPassRecipient(session, map.getId());
            if (recipient == null) continue;
            vacuumed += vacuumPassDrops(recipient, Set.of(map));
        }
        return vacuumed;
    }

    private static Character splitRoomPassRecipient(AgentLpqSession session, int roomMapId) {
        Integer ownerId = session.rooms().owner(roomMapId);
        Character owner = ownerId == null ? null : character(ownerId);
        if (owner != null && owner.getMapId() == roomMapId) return owner;
        for (AgentLpqMemberState member : session.members()) {
            if (member.assignedMapId() != roomMapId) continue;
            Character participant = character(member.characterId());
            if (participant != null && participant.getMapId() == roomMapId) return participant;
        }
        return null;
    }

    private static int vacuumItemDrops(Character recipient, Set<MapleMap> maps, int itemId) {
        int vacuumed = 0;
        for (MapleMap map : maps) {
            for (MapItem drop : map.getDroppedItems()) {
                if (drop == null || drop.isPickedUp() || drop.getItemId() != itemId
                        || drop.getItem() == null) continue;
                drop.lockItem();
                try {
                    if (drop.isPickedUp()) continue;
                    short quantity = drop.getItem().getQuantity();
                    if (quantity <= 0 || !AgentInventoryGatewayRuntime.inventory().addItem(
                            recipient, itemId, quantity)) continue;
                    AgentMapGatewayRuntime.map().removeItemDrop(map, drop, 2, recipient.getId());
                    vacuumed += quantity;
                } finally {
                    drop.unlockItem();
                }
            }
        }
        return vacuumed;
    }

    private static int partyPassCount(AgentLpqSession session) {
        return partyItemCount(session, AgentLpqDefinition.PASS);
    }

    private static int partyItemCount(AgentLpqSession session, int itemId) {
        int total = 0;
        for (AgentLpqMemberState member : session.members()) {
            Character character = character(member.characterId());
            if (character != null) total += character.getItemQuantity(itemId, false);
        }
        return total;
    }

    private static Set<MapleMap> collectionMaps(AgentLpqSession session, int stage) {
        Set<Integer> allowed = new LinkedHashSet<>(AgentLpqDefinition.roomMaps(stage));
        allowed.add(AgentLpqDefinition.stage(stage).mapId());
        if (stage == 2) allowed.add(922_010_201);
        Set<MapleMap> maps = new LinkedHashSet<>();
        var event = session.eventInstance();
        if (event == null) return maps;
        for (int mapId : allowed) {
            MapleMap map = event.getMapInstance(mapId);
            if (map != null) maps.add(map);
        }
        return maps;
    }

    private static boolean recoverStageTwoTrap(AgentLpqSession session, Character agent, long nowMs) {
        var event = session.eventInstance();
        MapleMap destination = event == null ? null
                : event.getMapInstance(AgentLpqDefinition.stage(2).mapId());
        Portal spawn = destination == null ? null : destination.getRandomPlayerSpawnpoint();
        if (destination == null || spawn == null) return false;
        log.warn("LPQ Stage 2 trap portal recovery: session={} member={}({}) sourceMap={} destination={}",
                session.sessionId(), agent.getName(), agent.getId(), agent.getMapId(), destination.getId());
        AgentMapGatewayRuntime.map().changeMapNear(agent, destination, spawn.getPosition());
        session.markProgress(nowMs);
        return true;
    }

    private static boolean useStageTwoTrapExit(AgentLpqSession session, AgentRuntimeEntry entry,
                                               Character agent, long nowMs) {
        int destinationMapId = AgentLpqDefinition.stage(2).mapId();
        AgentLpqMemberState member = session.member(agent.getId());
        long inactiveForMs = member == null ? 0L
                : traversalInactivity(member, agent, destinationMapId, nowMs);
        Integer portalId = portalIdTo(agent, destinationMapId);
        boolean stalled = inactiveForMs >= recovery(2).portalMs();
        if (portalId != null && !stalled) return enterPortalTo(entry, agent, destinationMapId);
        ACTIONS.stop(entry);
        boolean recovered = recoverStageTwoTrap(session, agent, nowMs);
        if (recovered && member != null) member.clearTraversalProgress();
        return recovered;
    }

    private static boolean restoreEventMember(AgentLpqSession session, Character agent,
                                              int destinationMapId, long nowMs) {
        var event = session.eventInstance();
        MapleMap destination = event == null ? null : event.getMapInstance(destinationMapId);
        Portal spawn = destination == null ? null : destination.getRandomPlayerSpawnpoint();
        if (event == null || destination == null || spawn == null) return false;
        event.registerPlayer(agent, false);
        AgentMapGatewayRuntime.map().changeMapNear(agent, destination, spawn.getPosition());
        session.markProgress(nowMs);
        log.warn("LPQ event-member recovery: session={} member={}({}) destination={}",
                session.sessionId(), agent.getName(), agent.getId(), destinationMapId);
        return true;
    }

    private static boolean moveWithinEvent(AgentLpqSession session, Character agent,
                                           int destinationMapId, long nowMs) {
        var event = session.eventInstance();
        MapleMap destination = event == null ? null : event.getMapInstance(destinationMapId);
        Portal spawn = destination == null ? null : destination.getRandomPlayerSpawnpoint();
        if (destination == null || spawn == null) return false;
        AgentMapGatewayRuntime.map().changeMapNear(agent, destination, spawn.getPosition());
        session.markProgress(nowMs);
        return true;
    }

    private static void submit(AgentLpqSession session, Character leader,
                               AgentLpqDefinition.Stage stage, long nowMs) {
        long readyForMs = session.observeSubmissionReady(true, nowMs);
        if (!isAgent(leader)) {
            AgentLpqMemberState state = session.member(leader.getId());
            if (state != null && nowMs >= state.nextActionAtMs()) {
                leader.dropMessage(6, "LPQ Stage " + stage.number() + " is ready. Talk to the balloon to submit.");
                state.deferUntil(nowMs + 5_000L);
            }
            return;
        }
        if (LPQ.event(leader) != session.eventInstance()) {
            restoreEventMember(session, leader, stage.mapId(), nowMs);
            return;
        }
        AgentRuntimeEntry entry = entry(leader.getId());
        AgentLpqMemberState leaderState = session.member(leader.getId());
        if (leaderState != null && nowMs < leaderState.nextActionAtMs()) return;
        Point npc = ACTIONS.npcPosition(leader, stage.npcId());
        if (entry == null) return;
        boolean nearNpc = npc != null && near(leader.getPosition(), npc, INTERACTION_RADIUS);
        if (!nearNpc && readyForMs < recovery(stage.number()).submissionMs()) {
            if (npc != null) ACTIONS.navigate(entry, npc, true);
            return;
        }
        ACTIONS.stop(entry);
        String clearBefore = LPQ.property(leader, stage.number() + "stageclear");
        if (LPQ.runNpc(leader, stage.npcId(), 1)) {
            if (leaderState != null) leaderState.deferUntil(nowMs + INTERACTION_RETRY_MS);
            String clearAfter = LPQ.property(leader, stage.number() + "stageclear");
            if (clearBefore == null && clearAfter != null) {
                purgeResidualPasses(session, stage.number());
                session.markProgress(nowMs);
            }
            if (!nearNpc && clearAfter != null) {
                log.warn("LPQ Stage {} submission navigation recovery: session={} leader={}({}) readyForMs={}",
                        stage.number(), session.sessionId(), leader.getName(), leader.getId(), readyForMs);
            }
        }
    }

    private static void movePartyToNextStage(AgentLpqSession session, int stage, long nowMs) {
        if (stage == 1) {
            session.transition(AgentLpqSession.Phase.STAGE_2, nowMs);
            advanceStageTwoScoutProtocol(session, nowMs);
            return;
        }
        movePartyToNextStageTogether(session, stage, nowMs);
    }

    private static void movePartyToNextStageTogether(
            AgentLpqSession session, int stage, long nowMs) {
        int source = AgentLpqDefinition.stage(stage).mapId();
        int destination = stage == 9
                ? AgentLpqDefinition.CLEAR_MAP : AgentLpqDefinition.stage(stage + 1).mapId();
        long transitionElapsedMs = session.beginOrObservePostClearTransition(nowMs);
        boolean recoveryDue = postClearTransitionRecoveryDue(stage, transitionElapsedMs);
        boolean allReady = true;
        int recovered = 0;
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null) {
                allReady = false;
                continue;
            }
            if (participant.getMapId() == destination) continue;
            if (participant.getMapId() != source) {
                allReady = false;
                if (recoveryDue
                        && member.memberType() == AgentLpqMemberState.MemberType.AGENT
                        && moveWithinEvent(session, participant, destination, nowMs)) {
                    member.clearTraversalProgress();
                    recovered++;
                }
                continue;
            }
            Integer portalId = portalIdTo(participant, destination);
            Point portal = portalId == null ? null : ACTIONS.portalPosition(participant, portalId);
            if (portal == null || !near(participant.getPosition(), portal, INTERACTION_RADIUS)) {
                allReady = false;
                if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                    AgentRuntimeEntry entry = entry(member.characterId());
                    if (entry != null && recoveryDue) {
                        if (portal == null) {
                            if (moveWithinEvent(session, participant, destination, nowMs)) {
                                member.clearTraversalProgress();
                                recovered++;
                            }
                        } else {
                            ACTIONS.stop(entry);
                            ACTIONS.stagePosition(entry, participant, portal);
                            member.clearTraversalProgress();
                            recovered++;
                        }
                    } else if (entry != null && portal != null) {
                        ACTIONS.navigate(entry, portal, true);
                    }
                } else if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "LPQ Stage " + stage
                            + " is clear. Gather with the party at the exit portal.");
                    member.deferUntil(nowMs + 5_000L);
                }
            }
        }
        if (recovered > 0) {
            session.markProgress(nowMs);
            log.warn("LPQ Stage {} shared post-clear transition recovery moved {} lagging "
                            + "members after {}ms: session={} destination={}",
                    stage, recovered, transitionElapsedMs, session.sessionId(), destination);
            return;
        }
        if (!allReady) return;
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent != null && entry != null && agent.getMapId() == source) {
                enterPortalTo(entry, agent, destination);
            }
        }
    }

    private static void advanceStageTwoScoutProtocol(AgentLpqSession session, long nowMs) {
        List<Integer> scoutIds = stageTwoScoutIds(
                session.members(), session.eventLeaderId(), AgentLpqDefinition.STAGE_2_SCOUT_COUNT);
        if (scoutIds.isEmpty()) return;

        Character speaker = scoutIds.stream().map(AgentLpqCoordinator::character)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (!session.stage2ScoutPlanAnnounced()
                && sendPartyAndAllChat(speaker, STAGE_2_SCOUT_CHAT)) {
            session.markStage2ScoutPlanAnnounced(nowMs);
        }

        int stageOneMap = AgentLpqDefinition.stage(1).mapId();
        int stageTwoMap = AgentLpqDefinition.stage(2).mapId();
        boolean trapReached = scoutIds.stream().map(AgentLpqCoordinator::character)
                .filter(java.util.Objects::nonNull)
                .anyMatch(scout -> scout.getMapId() == AgentLpqDefinition.STAGE_2_TRAP_MAP);
        if (!trapReached) {
            boolean allScoutsReady = true;
            for (int scoutId : scoutIds) {
                Character scout = character(scoutId);
                AgentRuntimeEntry scoutEntry = entry(scoutId);
                if (scout == null || scoutEntry == null) {
                    allScoutsReady = false;
                    continue;
                }
                if (scout.getMapId() == stageOneMap) {
                    enterStagePortalWithRecovery(session, session.member(scoutId),
                            scoutEntry, scout, stageTwoMap, nowMs, "Stage 2 scout entry");
                    allScoutsReady = false;
                } else if (scout.getMapId() != stageTwoMap) {
                    allScoutsReady = false;
                }
            }
            holdStageTwoWaitingAgents(session, scoutIds, stageOneMap);
            if (!allScoutsReady) return;

            for (int scoutId : scoutIds) {
                Character scout = character(scoutId);
                AgentRuntimeEntry scoutEntry = entry(scoutId);
                AgentLpqMemberState scoutState = session.member(scoutId);
                if (scout != null && scoutEntry != null && scoutState != null
                        && scout.getMapId() == stageTwoMap) {
                    hitStageTwoTrapReactor(scoutEntry, scout, nowMs, scoutState);
                }
            }
            return;
        }

        if (!session.stage2TrapClearAnnounced()
                && sendPartyAndAllChat(speaker, STAGE_2_TRAP_CLEAR_CHAT)) {
            session.markStage2TrapClearAnnounced(nowMs);
        }
        if (!session.stage2TrapClearAnnounced()) return;

        for (AgentLpqMemberState member : session.members()) {
            if (scoutIds.contains(member.characterId())) continue;
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != stageOneMap) continue;
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry participantEntry = entry(member.characterId());
                if (participantEntry != null) enterStagePortalWithRecovery(
                        session, member, participantEntry, participant, stageTwoMap,
                        nowMs, "Stage 2 party entry");
            } else if (nowMs >= member.nextActionAtMs()) {
                participant.dropMessage(6, STAGE_2_TRAP_CLEAR_CHAT);
                member.deferUntil(nowMs + 5_000L);
            }
        }
    }

    private static void enterStagePortalWithRecovery(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, int destinationMapId,
            long nowMs, String context) {
        long inactiveForMs = member == null ? 0L
                : traversalInactivity(member, agent, destinationMapId, nowMs);
        enterPortalTo(entry, agent, destinationMapId);
        int sourceStage = AgentLpqDefinition.stageNumber(agent.getMapId());
        if (agent.getMapId() == destinationMapId || sourceStage < 1 || sourceStage > 9
                || inactiveForMs < recovery(sourceStage).traversalMs()) return;
        int sourceMapId = agent.getMapId();
        if (!moveWithinEvent(session, agent, destinationMapId, nowMs)) return;
        if (member != null) member.clearTraversalProgress();
        log.warn("LPQ {} traversal recovery: session={} member={}({}) source={} "
                        + "destination={} inactiveForMs={}", context, session.sessionId(),
                agent.getName(), agent.getId(), sourceMapId, destinationMapId, inactiveForMs);
    }

    private static void holdStageTwoWaitingAgents(AgentLpqSession session,
                                                  List<Integer> scoutIds,
                                                  int stageOneMap) {
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT
                    || scoutIds.contains(member.characterId())) continue;
            Character participant = character(member.characterId());
            AgentRuntimeEntry participantEntry = entry(member.characterId());
            if (participant != null && participant.getMapId() == stageOneMap
                    && participantEntry != null) {
                ACTIONS.stop(participantEntry);
            }
        }
    }

    static List<Integer> stageTwoScoutIds(Collection<AgentLpqMemberState> members,
                                          int eventLeaderId,
                                          int requestedScoutCount) {
        if (members == null || requestedScoutCount <= 0) return List.of();
        return members.stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .sorted(Comparator
                        .comparing((AgentLpqMemberState member) ->
                                member.characterId() == eventLeaderId)
                        .thenComparingInt(AgentLpqMemberState::characterId))
                .limit(requestedScoutCount)
                .map(AgentLpqMemberState::characterId)
                .toList();
    }

    private static boolean sendPartyAndAllChat(Character speaker, String message) {
        if (speaker == null || message == null || message.isBlank()
                || !PARTY.sendPartyChat(speaker, message)) {
            return false;
        }
        AgentPacketGatewayRuntime.packets().broadcastChatText(speaker, message, false, 0);
        return true;
    }

    private static void portalMaze(AgentLpqSession session, Character leader, long nowMs) {
        catchUpLaggingMembers(session, 6, AgentLpqDefinition.stage(6).mapId(), nowMs);
        announceStageSixSequence(session, leader, nowMs);
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != 922_010_600) continue;
            advancePortalMazeMember(session, member, agent, entry, nowMs);
        }
        if (stageSixPartyFinishedMaze(session)) {
            movePartyToNextStageTogether(session, 6, nowMs);
        }
    }

    private static boolean stageSixPartyFinishedMaze(AgentLpqSession session) {
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null) return false;
            if (participant.getMapId() == AgentLpqDefinition.stage(7).mapId()) continue;
            if (participant.getMapId() != AgentLpqDefinition.stage(6).mapId()
                    || participant.getPosition().y >= -3_050) return false;
        }
        return true;
    }

    private static void announceStageSixSequence(AgentLpqSession session, Character leader, long nowMs) {
        if (!session.stage6SequenceChatReady(nowMs)) return;
        int speakerId = stageSixAnnouncementSpeakerId(session.members(), session.eventLeaderId());
        Character speaker = speakerId == leader.getId() ? leader : character(speakerId);
        if (speaker != null && sendPartyAndAllChat(speaker, STAGE_6_SEQUENCE_CHAT.getFirst())) {
            session.markStage6SequenceAnnounced(nowMs);
        }
    }

    static int stageSixAnnouncementSpeakerId(Collection<AgentLpqMemberState> members, int eventLeaderId) {
        AgentLpqMemberState leader = members == null ? null : members.stream()
                .filter(member -> member.characterId() == eventLeaderId)
                .findFirst().orElse(null);
        if (leader != null && leader.memberType() == AgentLpqMemberState.MemberType.AGENT) {
            return eventLeaderId;
        }
        return members == null ? 0 : members.stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .mapToInt(AgentLpqMemberState::characterId)
                .min().orElse(0);
    }

    private static void advancePortalMazeMember(
            AgentLpqSession session, AgentLpqMemberState member,
            Character agent, AgentRuntimeEntry entry, long nowMs) {
        if (agent.getPosition().y < -3_050) {
            Integer portalId = portalIdTo(agent, 922_010_700);
            Point portal = portalId == null ? null : ACTIONS.portalPosition(agent, portalId);
            if (portal != null) approachWithRecovery(session, member, entry, agent,
                    922_010_700, portal, "Stage 6 exit", nowMs);
            return;
        }
        int row = mazeRow(agent);
        if (row < 0) return;
        int portalId = stageSixPortalId(row);
        Portal portal = agent.getMap().getPortal(portalId);
        if (portal == null) return;
        if (!approachWithRecovery(session, member, entry, agent, 922_010_600,
                portal.getPosition(), "Stage 6 row " + (row + 1), nowMs)) return;
        enterMazePortal(session, member, agent, portalId, nowMs, row);
    }

    private static void enterMazePortal(
            AgentLpqSession session, AgentLpqMemberState member, Character agent,
            int portalId, long nowMs, int row) {
        int beforeY = agent.getPosition().y;
        if (!LPQ.enterPortal(agent, portalId)) return;
        member.clearTraversalProgress();
        if (agent.getMapId() == 922_010_700) {
            session.markProgress(nowMs);
        } else if (agent.getPosition().y < beforeY - 80) {
            if (row >= 0) session.maze().recordSuccess(row, portalId);
            session.markProgress(nowMs);
        }
    }

    static int stageSixPortalId(int row) {
        if (row < 0 || row >= STAGE_6_PORTAL_IDS.size()) {
            throw new IllegalArgumentException("LPQ Stage 6 row must be between 0 and 14");
        }
        return STAGE_6_PORTAL_IDS.get(row);
    }

    static List<String> stageSixSequenceChats() {
        return STAGE_6_SEQUENCE_CHAT;
    }

    private static int mazeRow(Character agent) {
        Portal first = agent.getMap().getPortal(2);
        if (first != null && agent.getPosition().y > first.getPosition().y + 130) return 0;
        int bestRow = -1;
        double best = Double.MAX_VALUE;
        for (int row = 0; row < 15; row++) {
            Portal portal = agent.getMap().getPortal(2 + row * 3);
            if (portal == null) continue;
            double distance = Math.abs(agent.getPosition().y - portal.getPosition().y);
            if (distance < best) { best = distance; bestRow = row; }
        }
        // A knockback or an interrupted climb can leave a member between the
        // authored row bands. The nearest row is still the only meaningful
        // continuation, and approachWithRecovery provides the bounded escape.
        return bestRow;
    }

    private static void platformPuzzle(AgentLpqSession session, Character leader, long nowMs) {
        catchUpLaggingMembers(session, 8, AgentLpqDefinition.stage(8).mapId(), nowMs);
        if (LPQ.property(leader, "8stageclear") != null) {
            movePartyToNextStageTogether(session, 8, nowMs);
            return;
        }
        if ("-1".equals(LPQ.property(leader, "statusStg8"))) {
            if (!rallyPartyAtNpc(session, leader, 8, nowMs)) return;
            if (!isAgent(leader)) {
                AgentLpqMemberState state = session.member(leader.getId());
                if (state != null && nowMs >= state.nextActionAtMs()) {
                    leader.dropMessage(6, "Talk to the Stage 8 balloon once to initialize the puzzle.");
                    state.deferUntil(nowMs + 5_000L);
                }
                return;
            }
            AgentRuntimeEntry leaderEntry = entry(leader.getId());
            AgentLpqMemberState leaderState = session.member(leader.getId());
            if (leaderState != null && nowMs < leaderState.nextActionAtMs()) return;
            Point balloon = ACTIONS.npcPosition(leader, AgentLpqDefinition.stage(8).npcId());
            if (leaderEntry == null || balloon == null) return;
            if (leaderState == null || !approachWithRecovery(
                    session, leaderState, leaderEntry, leader, 922_010_800,
                    balloon, "Stage 8 initialization", nowMs)) {
                return;
            }
            if (LPQ.runNpc(leader, AgentLpqDefinition.stage(8).npcId())) {
                if (leaderState != null) leaderState.deferUntil(nowMs + INTERACTION_RETRY_MS);
                session.markProgress(nowMs);
            }
            return;
        }
        List<AgentLpqMemberState> participants = session.members().stream()
                .filter(member -> member.characterId() != session.eventLeaderId())
                .sorted(Comparator.comparingInt(AgentLpqMemberState::characterId)).toList();
        if (participants.size() < 5) {
            AgentLpqTerminationService.fail(session, "LPQ Stage 8 needs five nonleader participants", nowMs);
            return;
        }
        java.util.Map<Integer, Integer> assignments = session.stage8Assignments(
                participants.stream().map(AgentLpqMemberState::characterId).toList());
        if (session.stage8AssignmentChatEnabled() && !session.stage8AssignmentAnnounced()) {
            Character speaker = isAgent(leader) ? leader : participants.stream()
                    .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                    .map(member -> character(member.characterId()))
                    .filter(java.util.Objects::nonNull).findFirst().orElse(null);
            String assignmentChat = stageEightAssignmentChat(assignments);
            if (sendPartyAndAllChat(speaker, assignmentChat)) {
                session.markStage8AssignmentAnnounced(nowMs);
            }
        }
        boolean positioned = true;
        for (int index = 0; index < 5; index++) {
            AgentLpqMemberState member = participants.get(index);
            int platform = assignments.get(member.characterId());
            member.assign(index == 4 ? AgentLpqMemberState.Role.PLATFORM_MOVER
                    : AgentLpqMemberState.Role.PLATFORM_HOLDER, 922_010_800);
            member.assignPlatform(platform);
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != 922_010_800) { positioned = false; continue; }
            Rectangle area = participant.getMap().getAreas().get(platform - 1);
            if (area.contains(participant.getPosition())) {
                member.clearTraversalProgress();
                continue;
            }
            positioned = false;
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry entry = entry(member.characterId());
                if (entry != null) occupyStageEightPlatformWithRecovery(
                        session, member, entry, participant, area, platform, nowMs);
            } else if (nowMs >= member.nextActionAtMs()) {
                participant.dropMessage(6, "LPQ Stage 8: stand on box " + platform + " and remain still.");
                member.deferUntil(nowMs + 5_000L);
            }
        }
        if (!positioned) return;
        if (!isAgent(leader)) {
            AgentLpqMemberState state = session.member(leader.getId());
            if (state != null && nowMs >= state.nextActionAtMs()) {
                leader.dropMessage(6, "Five members are positioned. Check the Stage 8 balloon now.");
                state.deferUntil(nowMs + 5_000L);
            }
            return;
        }
        AgentRuntimeEntry entry = entry(leader.getId());
        AgentLpqMemberState leaderState = session.member(leader.getId());
        if (leaderState != null && nowMs < leaderState.nextActionAtMs()) return;
        Point npc = ACTIONS.npcPosition(leader, AgentLpqDefinition.stage(8).npcId());
        if (entry == null || npc == null) return;
        if (leaderState == null || !approachWithRecovery(
                session, leaderState, entry, leader, 922_010_800,
                npc, "Stage 8 submission", nowMs)) return;
        if (LPQ.runNpc(leader, AgentLpqDefinition.stage(8).npcId(), 1)) {
            if (leaderState != null) leaderState.deferUntil(nowMs + INTERACTION_RETRY_MS);
            session.advanceStage8(nowMs);
        }
        if (LPQ.property(leader, "8stageclear") != null) {
            movePartyToNextStageTogether(session, 8, nowMs);
        }
    }

    static Point stageEightPlatformTarget(Rectangle area) {
        if (area == null) throw new IllegalArgumentException("LPQ Stage 8 platform area is required");
        // WZ areas straddle the standing foothold. Their center is slightly below that
        // foothold, which makes grounding select the next platform down. Approach from
        // the authored area's top edge so navigation resolves the intended box surface.
        return new Point((int) area.getCenterX(), area.y);
    }

    static String stageEightAssignmentChat(java.util.Map<Integer, Integer> assignments) {
        if (assignments == null || assignments.isEmpty()) return "";
        String mapping = assignments.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> {
                    Character member = character(entry.getKey());
                    return (member == null ? String.valueOf(entry.getKey()) : member.getName())
                            + "->" + entry.getValue();
                })
                .collect(java.util.stream.Collectors.joining(", "));
        return "Stage 8: " + mapping;
    }

    private static boolean occupyStageEightPlatformWithRecovery(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, Rectangle area,
            int platform, long nowMs) {
        if (area.contains(agent.getPosition())) {
            member.clearTraversalProgress();
            return true;
        }
        Point target = stageEightPlatformTarget(area);
        long distanceSq = agent.getPosition() == null
                ? Long.MAX_VALUE : (long) agent.getPosition().distanceSq(target);
        long inactiveForMs = member.observeTraversalProgress(
                agent.getMapId(), 922_010_800, agent.getPosition(), distanceSq, nowMs);
        if (inactiveForMs < recovery(8).traversalMs()) {
            ACTIONS.navigate(entry, target, true);
            return false;
        }
        ACTIONS.stop(entry);
        ACTIONS.stagePosition(entry, agent, target);
        member.clearTraversalProgress();
        session.markProgress(nowMs);
        log.warn("LPQ Stage 8 platform containment recovery: session={} member={}({}) "
                        + "platform={} target={} inactiveForMs={}", session.sessionId(),
                agent.getName(), agent.getId(), platform, target, inactiveForMs);
        return area.contains(agent.getPosition());
    }

    private static void boss(AgentLpqSession session, Character leader, long nowMs) {
        if (LPQ.property(leader, "9stageclear") != null) {
            movePartyToNextStageTogether(session, 9, nowMs);
            return;
        }
        catchUpLaggingMembers(session, 9, AgentLpqDefinition.stage(9).mapId(), nowMs);
        Set<Integer> lootClaims = new LinkedHashSet<>();
        boolean triggerRatzAlive = ACTIONS.liveMonsterCount(
                leader, Set.of(AgentLpqDefinition.BOSS_TRIGGER_RATZ)) > 0;
        boolean alisharAlive = ACTIONS.liveMonsterCount(
                leader, Set.of(AgentLpqDefinition.ALISHAR)) > 0;
        Reactor alisharReactor = activeReactors(leader).stream().findFirst().orElse(null);
        assignStageNineRoles(session);
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != 922_010_900) continue;
            if (collectNearestDrop(entry, agent,
                    Set.of(AgentLpqDefinition.BOSS_KEY), lootClaims)) continue;
            Set<Integer> combatTargets = stageCombatTargets(
                    9, ACTIONS.configuredMonsterSpawnIds(agent));
            if (alisharAlive) {
                member.assign(AgentLpqMemberState.Role.BOSS_ATTACKER, 922_010_900);
                ACTIONS.grind(entry, Set.of(AgentLpqDefinition.ALISHAR));
                continue;
            }
            if ((triggerRatzAlive || alisharReactor != null)
                    && AgentLpqRosterRequirementPolicy.rangedAttack(agent)) {
                member.assign(AgentLpqMemberState.Role.RANGED_TRIGGER, 922_010_900);
                if (triggerRatzAlive) {
                    engageBossTriggerRatz(session, member, entry, agent, nowMs);
                } else {
                    hitBossReactorFromFiringPlatform(session, member, entry,
                            agent, alisharReactor, nowMs);
                }
                continue;
            }
            if (triggerRatzAlive || alisharReactor != null) {
                member.assign(AgentLpqMemberState.Role.BOSS_ATTACKER, 922_010_900);
                ACTIONS.stop(entry);
                continue;
            }
            if (!agent.getMap().getAllMonsters().isEmpty()) {
                member.assign(AgentLpqMemberState.Role.BOSS_ATTACKER, 922_010_900);
                ACTIONS.grind(entry, combatTargets);
                continue;
            }
            ACTIONS.stop(entry);
        }
        consolidateBossKey(session, leader, nowMs);
        recoverBossKey(session, leader, nowMs);
        if (leader.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false) > 0) {
            if (isAgent(leader)) submit(session, leader, AgentLpqDefinition.stage(9), nowMs);
            else leader.dropMessage(6, "Alishar's key is ready. Talk to the balloon to finish LPQ.");
        }
    }

    private static void assignStageNineRoles(AgentLpqSession session) {
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character participant = character(member.characterId());
            member.assign(AgentLpqRosterRequirementPolicy.rangedAttack(participant)
                    ? AgentLpqMemberState.Role.RANGED_TRIGGER
                    : AgentLpqMemberState.Role.BOSS_ATTACKER, AgentLpqDefinition.stage(9).mapId());
        }
    }

    static int stageNineTriggerPriority(Character character) {
        if (character == null || character.getJob() == null) return 4;
        if (character.getJob().isA(client.Job.BOWMAN)) return 0;
        if (character.getJob().isA(client.Job.ASSASSIN)) return 1;
        if (character.getJob().isA(client.Job.GUNSLINGER)) return 2;
        return AgentLpqRosterRequirementPolicy.rangedAttack(character) ? 3 : 4;
    }

    private static void engageBossTriggerRatz(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (!reachStageNineBalloonPlatform(session, member, entry, agent, nowMs)) return;
        ACTIONS.facePosition(agent, new Point(588, -3));
        Monster trigger = agent.getMap().getAllMonsters().stream()
                .filter(monster -> monster.getId() == AgentLpqDefinition.BOSS_TRIGGER_RATZ)
                .findFirst().orElse(null);
        if (trigger == null) return;
        int hpBefore = trigger.getHp();
        AgentAttackPlan attackPlan = AgentCombatPlanRuntime.planAttack(
                entry, agent, trigger, AgentCombatConfig.cfg);
        if (attackPlan == null) attackPlan = authoredBossTriggerAttackPlan(agent, trigger);
        if (attackPlan == null) {
            ACTIONS.grind(entry, Set.of(AgentLpqDefinition.BOSS_TRIGGER_RATZ));
            return;
        }
        AgentAttackTransactionResult result = AgentCombatAttackRuntime.attackMonster(
                entry, agent, attackPlan);
        if (result.status() == AgentAttackTransactionResult.Status.REJECTED
                && member.claimIntentAnnouncement("stage9:ratz-rejected:" + result.reason())) {
            log.warn("LPQ Stage 9 Black Ratz attack rejected: session={} member={}({}) reason={}",
                    session.sessionId(), agent.getName(), agent.getId(), result.reason());
        }
        if (result.status() == AgentAttackTransactionResult.Status.REJECTED) {
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
        }
        if (trigger.getHp() < hpBefore) session.markProgress(nowMs);
        member.observeRoomCombatTarget(
                922_010_900, trigger.getObjectId(), trigger.getHp(), nowMs);
    }

    /**
     * LPQ's Black Ratz is an authored trigger target 187 px above the continuous firing
     * floor. The v83 client permits the shot from that platform, but the general combat
     * planner's conservative +/-50 px projectile band correctly rejects it for ordinary
     * grinding. Preserve the normal weapon animation, ammo, cooldown, damage formula and
     * authoritative attack transaction while extending only this LPQ objective hit box.
     */
    private static AgentAttackPlan authoredBossTriggerAttackPlan(Character agent, Monster trigger) {
        if (agent == null || trigger == null || agent.getPosition() == null
                || trigger.getPosition() == null) return null;
        AgentAttackExecutionProvider.BasicAttackData attack =
                AgentAttackExecutionProvider.buildBasicAttackData(agent, trigger.getPosition());
        if (attack == null || attack.route() != AgentAttackRoute.RANGED) return null;
        Rectangle hitBox = authoredBossTriggerHitBox(attack.hitBox(), trigger.getPosition());
        return new AgentAttackPlan(0, 0,
                AgentCombatHitCounter.packetSafeHitCount(agent, attack.route(), 1),
                hitBox, List.of(trigger), attack.route(), attack.display(), attack.direction(),
                attack.rangedDirection(), attack.stance(), attack.speed(), attack.hitDelayMs(),
                attack.cooldownMs(), AgentCombatWeaponPolicy.damageWeaponTypeForAction(
                0, AgentAttackExecutionProvider.getEquippedWeaponType(agent), attack.action()));
    }

    static Rectangle authoredBossTriggerHitBox(Rectangle ordinaryHitBox, Point triggerPosition) {
        if (triggerPosition == null) throw new IllegalArgumentException("trigger position is required");
        Rectangle triggerPoint = new Rectangle(triggerPosition.x, triggerPosition.y, 1, 1);
        return ordinaryHitBox == null ? triggerPoint : ordinaryHitBox.union(triggerPoint);
    }

    private static void hitBossReactorFromFiringPlatform(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, Reactor reactor, long nowMs) {
        if (reactor == null) return;
        if (!reachStageNineBalloonPlatform(session, member, entry, agent, nowMs)) return;
        ACTIONS.facePosition(agent, reactor.getPosition());
        if (ACTIONS.hitReactor(agent, reactor.getObjectId())) session.markProgress(nowMs);
    }

    private static boolean reachStageNineBalloonPlatform(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, long nowMs) {
        Point firingAnchor = stageNineRatzFiringAnchor(agent.getId());
        if (stageNineRatzFiringReady(
                agent.getPosition(), ACTIONS.grounded(agent), firingAnchor)) {
            member.clearTraversalProgress();
            return true;
        }
        approachWithRecovery(session, member, entry, agent, 922_010_900,
                firingAnchor, STAGE_9_RATZ_FIRING_ANCHOR_RADIUS,
                "Stage 9 balloon firing platform", nowMs);
        return false;
    }

    static Point stageNineRatzFiringAnchor(int characterId) {
        return new Point(STAGE_9_BALLOON_PLATFORM_ANCHOR);
    }

    static boolean stageNineRatzFiringReady(
            Point position, boolean grounded, Point firingAnchor) {
        return grounded && near(position, firingAnchor, STAGE_9_RATZ_FIRING_ANCHOR_RADIUS);
    }

    private static void recoverBossKey(AgentLpqSession session, Character leader, long nowMs) {
        int partyKeys = partyItemCount(session, AgentLpqDefinition.BOSS_KEY);
        boolean bossMapCleared = activeReactors(leader).isEmpty()
                && leader.getMap().getAllMonsters().isEmpty();
        // Distinguish "no key while fighting" from "no key after clear" so
        // the missing-key grace starts at clear rather than at stage entry.
        int recoverySignature = partyKeys + (bossMapCleared ? 1_000_000 : 0);
        if (leader.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false) > 0
                || session.observePassRecovery(recoverySignature, nowMs)
                < recovery(7).missingPassMs()) return;

        int vacuumed = vacuumItemDrops(
                leader, Set.of(leader.getMap()), AgentLpqDefinition.BOSS_KEY);
        if (vacuumed > 0) {
            session.markPassRecoveryConsolidation(nowMs);
            log.warn("LPQ boss-key recovery vacuumed {} unreachable key drops: session={} leader={}({})",
                    vacuumed, session.sessionId(), leader.getName(), leader.getId());
            return;
        }
        boolean repositionedHolder = false;
        for (AgentLpqMemberState member : session.members()) {
            if (member.characterId() == leader.getId()) continue;
            Character holder = character(member.characterId());
            if (holder == null || holder.getMap() != leader.getMap()
                    || holder.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false) <= 0) continue;
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) {
                if (nowMs >= member.nextActionAtMs()) {
                    holder.dropMessage(6, "Drop Alishar's key beside the party leader.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            AgentRuntimeEntry holderEntry = entry(member.characterId());
            if (holderEntry == null) continue;
            ACTIONS.stop(holderEntry);
            ACTIONS.stagePosition(holderEntry, holder, leader.getPosition());
            repositionedHolder = true;
        }
        if (repositionedHolder) {
            consolidateBossKey(session, leader, nowMs);
            session.markPassRecoveryConsolidation(nowMs);
            log.warn("LPQ boss-key holder regroup recovery: session={} leader={}({})",
                    session.sessionId(), leader.getName(), leader.getId());
            return;
        }
        if (partyKeys > 0 || !bossMapCleared
                || session.passRecoveryPassesAwarded()) return;
        if (!AgentInventoryGatewayRuntime.inventory().addItem(
                leader, AgentLpqDefinition.BOSS_KEY, (short) 1)) {
            AgentLpqTerminationService.fail(
                    session, "Alishar's key disappeared and the leader has no ETC space", nowMs);
            return;
        }
        session.markPassRecoveryPassesAwarded(nowMs);
        log.warn("LPQ boss-key recovery awarded one missing key after the cleared boss map: "
                        + "session={} leader={}({})", session.sessionId(),
                leader.getName(), leader.getId());
    }

    private static void consolidateBossKey(AgentLpqSession session, Character leader, long nowMs) {
        if (isAgent(leader)) {
            AgentRuntimeEntry leaderEntry = entry(leader.getId());
            if (leaderEntry != null) collectNearestDrop(leaderEntry, leader,
                    Set.of(AgentLpqDefinition.BOSS_KEY), new LinkedHashSet<>());
        }
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT || member.characterId() == leader.getId()) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != leader.getMapId()
                    || agent.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false) <= 0) continue;
            if (!near(agent.getPosition(), leader.getPosition(), INTERACTION_RADIUS)) {
                ACTIONS.navigate(entry, leader.getPosition(), true); continue;
            }
            AgentScriptItemActionService.dropItem(entry, InventoryType.ETC, AgentLpqDefinition.BOSS_KEY, (short) 1);
            session.markProgress(nowMs);
        }
    }

    private static void bonus(AgentLpqSession session, long nowMs) {
        List<Character> bonusAgents = new ArrayList<>();
        boolean participantRemaining = false;
        Set<Integer> reactorClaims = activeReactorClaims(session, AgentLpqDefinition.CLEAR_MAP);
        Set<Integer> lootClaims = new LinkedHashSet<>();
        for (AgentLpqMemberState member : session.members()) {
            Character agent = character(member.characterId());
            if (agent == null || agent.getMapId() != AgentLpqDefinition.CLEAR_MAP) continue;
            participantRemaining = true;
            if (member.memberType() == AgentLpqMemberState.MemberType.HUMAN) {
                if (nowMs >= member.nextActionAtMs()) {
                    agent.dropMessage(6, "Talk to the Pink Balloon when you are ready to leave the LPQ bonus stage.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            AgentRuntimeEntry entry = entry(member.characterId());
            if (entry == null) continue;
            bonusAgents.add(agent);
            if (session.bonusMode() == AgentLpqSession.BonusMode.SKIP) continue;

            if (collectNearestDrop(entry, agent, null, lootClaims)) continue;
            if (hitNearestReactorWithoutRecovery(
                    entry, agent, nowMs, member, reactorClaims)) {
                session.markProgress(nowMs);
            }
            if (member.reactorTargetObjectId() != 0) reactorClaims.add(member.reactorTargetObjectId());
        }
        if (!participantRemaining) {
            session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, nowMs);
            return;
        }

        if (bonusAgents.isEmpty()) return;

        boolean drained = session.bonusMode() == AgentLpqSession.BonusMode.SKIP
                || bonusMapDrained(bonusAgents.getFirst());
        if (session.bonusMode() != AgentLpqSession.BonusMode.SKIP
                && session.observeBonusDrained(drained, nowMs) < BONUS_DRAIN_SETTLE_MS) return;
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null
                    || agent.getMapId() != AgentLpqDefinition.CLEAR_MAP) continue;
            if (approachRewardNpc(session, member, entry, agent,
                    AgentLpqDefinition.CLEAR_NPC, "bonus exit balloon", nowMs)
                    && LPQ.runNpc(agent, AgentLpqDefinition.CLEAR_NPC, 1)) {
                session.markProgress(nowMs);
            }
        }
    }

    private static void claim(AgentLpqSession session, long nowMs) {
        session.freezeRewardEligibility();
        boolean nonLeaderRewardsResolved = session.members().stream()
                .filter(member -> member.characterId() != session.eventLeaderId())
                .allMatch(AgentLpqMemberState::rewardResolved);
        for (AgentLpqMemberState member : session.members()) {
            if (member.rewardResolved()) continue;
            Character participant = character(member.characterId());
            if (participant == null) {
                session.forfeitReward(member.characterId());
                continue;
            }
            if (participant.getMapId() == AgentLpqDefinition.CLEAR_MAP) {
                if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                    AgentRuntimeEntry participantEntry = entry(member.characterId());
                    if (participantEntry != null && approachRewardNpc(
                            session, member, participantEntry, participant,
                            AgentLpqDefinition.CLEAR_NPC, "bonus exit balloon", nowMs)
                            && LPQ.runNpc(participant, AgentLpqDefinition.CLEAR_NPC, 1)) {
                        session.markProgress(nowMs);
                    }
                } else if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "Talk to the Pink Balloon to proceed to your LPQ reward.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            if (participant.getMapId() != AgentLpqDefinition.BONUS_MAP) continue;
            if (member.memberType() == AgentLpqMemberState.MemberType.HUMAN) {
                if (nowMs >= member.nextActionAtMs()) {
                    participant.dropMessage(6, "Talk to Arturo to claim your LPQ reward.");
                    member.deferUntil(nowMs + 5_000L);
                }
                continue;
            }
            if (member.characterId() == session.eventLeaderId() && !nonLeaderRewardsResolved) continue;
            AgentRuntimeEntry participantEntry = entry(member.characterId());
            if (participantEntry != null && approachRewardNpc(
                    session, member, participantEntry, participant,
                    AgentLpqDefinition.REWARD_NPC, "Arturo reward", nowMs)
                    && LPQ.runNpc(participant, AgentLpqDefinition.REWARD_NPC, 1)) {
                session.markProgress(nowMs);
            }
        }
        if (session.allRewardsResolved()) session.transition(AgentLpqSession.Phase.EXITING, nowMs);
    }

    private static boolean approachRewardNpc(
            AgentLpqSession session, AgentLpqMemberState member, AgentRuntimeEntry entry,
            Character agent, int npcId, String context, long nowMs) {
        Point npc = ACTIONS.npcPosition(agent, npcId);
        if (npc == null) return false;
        Point grounded = ACTIONS.groundPoint(agent.getMap(), npc);
        Point target = grounded == null ? npc : grounded;
        if (near(agent.getPosition(), target, INTERACTION_RADIUS) && ACTIONS.grounded(agent)) {
            ACTIONS.stop(entry);
            member.clearNpcRallyProgress();
            return true;
        }
        long distanceSq = agent.getPosition() == null
                ? Long.MAX_VALUE : (long) agent.getPosition().distanceSq(target);
        long inactiveForMs = member.observeNpcRallyProgress(
                9, agent.getMapId(), agent.getPosition(), distanceSq, nowMs);
        if (inactiveForMs >= recovery(9).rallyRecoveryMs()) {
            ACTIONS.stop(entry);
            ACTIONS.stagePosition(entry, agent, target);
            member.clearNpcRallyProgress();
            session.markProgress(nowMs);
            log.warn("LPQ {} approach recovery: session={} member={}({}) map={} "
                            + "target={} inactiveForMs={}", context, session.sessionId(),
                    agent.getName(), agent.getId(), agent.getMapId(), target, inactiveForMs);
            return false;
        }
        if (member.claimNpcRallyRetry(
                inactiveForMs, nowMs, recovery(9).rallyRetryMs())) {
            ACTIONS.refreshNavigation(entry, agent);
        }
        ACTIONS.navigate(entry, target, true);
        return false;
    }

    private static void exitAfterRewardWarpSettles(AgentLpqSession session, long nowMs) {
        boolean agentStillInEventMap = session.members().stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .anyMatch(agent -> AgentLpqDefinition.isEventMap(agent.getMapId()));
        if (agentStillInEventMap || nowMs - session.phaseEnteredAtMs() < 2_000L) return;
        AgentLpqTerminationService.complete(session, nowMs);
    }

    static Set<Integer> bonusDropIds(Collection<MapItem> drops) {
        if (drops == null || drops.isEmpty()) return Set.of();
        return drops.stream().filter(java.util.Objects::nonNull)
                .filter(drop -> !drop.isPickedUp())
                .map(MapItem::getItemId).filter(id -> id > 0)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean bonusMapDrained(Character agent) {
        return activeReactors(agent).isEmpty()
                && agent.getMap().getDroppedItems().stream().noneMatch(drop -> !drop.isPickedUp());
    }

    private static boolean hitNearestReactor(AgentRuntimeEntry entry, Character agent,
                                             long nowMs, AgentLpqMemberState member) {
        return hitNearestReactor(entry, agent, nowMs, member, false);
    }

    private static boolean hitNearestReactor(AgentRuntimeEntry entry, Character agent,
                                             long nowMs, AgentLpqMemberState member,
                                             boolean waitForSpawnCleanup) {
        return hitNearestReactor(entry, agent, nowMs, member, waitForSpawnCleanup, Set.of());
    }

    private static boolean hitNearestReactor(AgentRuntimeEntry entry, Character agent,
                                             long nowMs, AgentLpqMemberState member,
                                             boolean waitForSpawnCleanup,
                                             Set<Integer> claimedByParty) {
        return hitReactorFromCandidates(entry, agent, nowMs, member, waitForSpawnCleanup,
                claimedByParty, activeReactors(agent), true);
    }

    private static boolean hitNearestReactorWithoutRecovery(
            AgentRuntimeEntry entry, Character agent, long nowMs,
            AgentLpqMemberState member, Set<Integer> claimedByParty) {
        return hitReactorFromCandidates(entry, agent, nowMs, member, false,
                claimedByParty, activeReactors(agent), false);
    }

    private static boolean hitStageTwoTrapReactor(AgentRuntimeEntry entry, Character agent,
                                                  long nowMs, AgentLpqMemberState member) {
        List<Reactor> trapReactors = stageTwoTrapReactors(activeReactors(agent));
        return hitReactorFromCandidates(
                entry, agent, nowMs, member, false, Set.of(), trapReactors, true);
    }

    private static boolean hitReactorFromCandidates(AgentRuntimeEntry entry, Character agent,
                                                     long nowMs, AgentLpqMemberState member,
                                                     boolean waitForSpawnCleanup,
                                                     Set<Integer> claimedByParty,
                                                     List<Reactor> candidates,
                                                     boolean allowRemoteRecovery) {
        if (nowMs < member.nextActionAtMs()) return false;
        Reactor reactor = selectCommittedReactor(member, agent.getMapId(), agent.getPosition(),
                candidates, waitForSpawnCleanup, claimedByParty);
        if (reactor == null) return false;
        if (member.reactorTargetHitOnce()) {
            // Once a box has been reached and struck, hold that authored interaction
            // instead of letting the movement planner climb away and rediscover it
            // between every reactor state hit.
            if (!reactorInteractionReady(agent, reactor)) {
                ACTIONS.navigate(entry, reactor.getPosition(), true);
                return true;
            }
            ACTIONS.stop(entry);
            if (ACTIONS.hitReactor(agent, reactor.getObjectId())) {
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
            }
            return true;
        }
        if (!reactorInteractionReady(agent, reactor)) {
            int stage = AgentLpqDefinition.stageNumber(agent.getMapId());
            if (allowRemoteRecovery && stage >= 1 && stage <= 9
                    && member.reactorTargetCommittedAtMs() > 0L
                    && nowMs - member.reactorTargetCommittedAtMs()
                    >= recovery(stage).reactorMs()) {
                ACTIONS.stop(entry);
                Point grounded = ACTIONS.groundPoint(agent.getMap(), reactor.getPosition());
                Point recoveryTarget = grounded == null ? reactor.getPosition() : grounded;
                ACTIONS.stagePosition(entry, agent, recoveryTarget);
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
                log.warn("LPQ reactor approach recovery placed member beside committed reactor: "
                                + "member={}({}) map={} reactor={} committedForMs={}",
                        agent.getName(), agent.getId(), agent.getMapId(), reactor.getObjectId(),
                        nowMs - member.reactorTargetCommittedAtMs());
                return true;
            }
            ACTIONS.navigate(entry, reactor.getPosition(), true);
            return true;
        }
        ACTIONS.stop(entry);
        if (ACTIONS.hitReactor(agent, reactor.getObjectId())) {
            member.markReactorTargetHit();
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
            return true;
        }
        return false;
    }

    private static boolean reactorInteractionReady(Character agent, Reactor reactor) {
        return agent != null && reactor != null
                && reactorInteractionReady(agent.getPosition(), ACTIONS.grounded(agent),
                reactor.getPosition());
    }

    static boolean reactorInteractionReady(
            Point agentPosition, boolean grounded, Point reactorPosition) {
        return grounded && agentPosition != null && reactorPosition != null
                && Math.abs(agentPosition.x - reactorPosition.x) <= INTERACTION_RADIUS
                && Math.abs(agentPosition.y - reactorPosition.y)
                <= REACTOR_PLATFORM_VERTICAL_TOLERANCE_PX;
    }

    static List<Reactor> stageTwoTrapReactors(List<Reactor> active) {
        if (active == null || active.isEmpty()) return List.of();
        return active.stream()
                .filter(java.util.Objects::nonNull)
                .filter(reactor -> reactor.getId() == AgentLpqDefinition.STAGE_2_TRAP_REACTOR)
                .toList();
    }

    static Reactor selectCommittedReactor(AgentLpqMemberState member, int mapId,
                                           Point position, List<Reactor> active,
                                           boolean waitForSpawnCleanup) {
        return selectCommittedReactor(
                member, mapId, position, active, waitForSpawnCleanup, Set.of());
    }

    static Reactor selectCommittedReactor(AgentLpqMemberState member, int mapId,
                                           Point position, List<Reactor> active,
                                           boolean waitForSpawnCleanup,
                                           Set<Integer> claimedByParty) {
        if (member == null || mapId <= 0 || position == null || active == null) return null;
        if (member.reactorTargetObjectId() != 0) {
            if (member.reactorTargetMapId() == mapId) {
                Reactor committed = active.stream()
                        .filter(candidate -> candidate.getObjectId() == member.reactorTargetObjectId())
                        .findFirst().orElse(null);
                if (committed != null) return committed;
            }
            member.markReactorTargetBroken(waitForSpawnCleanup);
            return null;
        }
        if (member.reactorSpawnCleanupPending()) return null;
        Set<Integer> excluded = claimedByParty == null ? Set.of() : claimedByParty;
        List<Reactor> available = active.stream()
                .filter(candidate -> !excluded.contains(candidate.getObjectId()))
                .toList();
        Reactor selected = AgentLpqStageFiveReactorOrder.select(mapId, available);
        if (selected == null) {
            selected = available.stream()
                    .min(Comparator.comparingDouble(candidate -> candidate.getPosition().distance(position)))
                    .orElse(null);
        }
        if (selected != null) member.commitReactorTarget(mapId, selected.getObjectId());
        return selected;
    }

    static Set<Integer> stageCombatTargets(int stage, Set<Integer> configuredTargets) {
        Set<Integer> targets = new LinkedHashSet<>(
                configuredTargets == null ? Set.of() : configuredTargets);
        if (stage == 7) targets.add(AgentLpqDefinition.ROMBARD);
        if (stage == 9) {
            targets.add(AgentLpqDefinition.BOSS_TRIGGER_RATZ);
            targets.add(AgentLpqDefinition.ALISHAR);
        }
        return Set.copyOf(targets);
    }

    private static List<Reactor> activeReactors(Character agent) {
        List<Reactor> reactors = new ArrayList<>();
        EventInstanceManager event = agent.getEventInstance();
        for (Reactor reactor : ACTIONS.reactors(agent)) {
            if (reactor == null || !reactor.isAlive() || !reactor.isActive()) continue;
            if (event != null && event.getProperty(ReactorActionManager.eventReactorActionKey(
                    "lpq", agent.getMapId(), reactor.getObjectId())) != null) {
                continue;
            }
            reactors.add(reactor);
        }
        return reactors;
    }

    private static boolean maintainDarkSight(AgentRuntimeEntry entry, Character agent,
                                             AgentLpqMemberState member, long nowMs) {
        if (!darkSightRefreshDue(agent, nowMs)) return false;
        Point safeSpot = ACTIONS.portalPosition(agent, DARK_SIGHT_SAFE_PORTAL_ID);
        if (safeSpot != null && !near(agent.getPosition(), safeSpot, INTERACTION_RADIUS)) {
            ACTIONS.navigate(entry, safeSpot, true);
            return true;
        }
        ACTIONS.stop(entry);
        if (refreshDarkSight(entry, agent)) {
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
        }
        return true;
    }

    private static boolean refreshDarkSight(AgentRuntimeEntry entry, Character agent) {
        // A normal client may refresh an active buff. Do not cancel the live protection first:
        // readiness can still reject a cast while airborne, climbing, cooling down, or out of MP.
        return AgentCombatBuffRuntime.tryCastExplicitUtilityBuff(entry, agent, Rogue.DARK_SIGHT);
    }

    private static boolean darkSightRefreshDue(Character agent, long nowMs) {
        StatEffect effect = agent.getBuffEffect(BuffStat.DARKSIGHT);
        return darkSightRefreshDue(agent.getBuffedStarttime(BuffStat.DARKSIGHT),
                effect == null ? 0 : effect.getDuration(), nowMs);
    }

    static boolean darkSightRefreshDue(Long startTimeMs, int durationMs, long nowMs) {
        if (startTimeMs == null || durationMs <= 0) return true;
        long remainingMs = startTimeMs + durationMs - nowMs;
        return remainingMs <= DARK_SIGHT_REFRESH_WINDOW_MS;
    }

    private static long traversalInactivity(AgentLpqMemberState member, Character agent,
                                            int destinationMapId, long nowMs) {
        Integer portalId = portalIdTo(agent, destinationMapId);
        Point portal = portalId == null ? null : ACTIONS.portalPosition(agent, portalId);
        long distanceSq = portal == null || agent.getPosition() == null
                ? Long.MAX_VALUE : (long) agent.getPosition().distanceSq(portal);
        return member.observeTraversalProgress(
                agent.getMapId(), destinationMapId, agent.getPosition(), distanceSq, nowMs);
    }

    private static boolean approachWithRecovery(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, int destinationMapId,
            Point target, String context, long nowMs) {
        return approachWithRecovery(session, member, entry, agent, destinationMapId,
                target, INTERACTION_RADIUS, context, nowMs);
    }

    private static boolean approachWithRecovery(
            AgentLpqSession session, AgentLpqMemberState member,
            AgentRuntimeEntry entry, Character agent, int destinationMapId,
            Point target, int arrivalRadius, String context, long nowMs) {
        if (target == null) return false;
        if (near(agent.getPosition(), target, arrivalRadius)) {
            member.clearTraversalProgress();
            return true;
        }
        long distanceSq = agent.getPosition() == null
                ? Long.MAX_VALUE : (long) agent.getPosition().distanceSq(target);
        long inactiveForMs = member.observeTraversalProgress(
                agent.getMapId(), destinationMapId, agent.getPosition(), distanceSq, nowMs);
        int stage = AgentLpqDefinition.stageNumber(agent.getMapId());
        if (stage < 1 || stage > 9 || inactiveForMs < recovery(stage).traversalMs()) {
            ACTIONS.navigate(entry, target, true);
            return false;
        }
        ACTIONS.stop(entry);
        ACTIONS.stagePosition(entry, agent, target);
        member.clearTraversalProgress();
        session.markProgress(nowMs);
        log.warn("LPQ {} position recovery: session={} member={}({}) map={} "
                        + "target={} inactiveForMs={}", context, session.sessionId(),
                agent.getName(), agent.getId(), agent.getMapId(), target, inactiveForMs);
        return true;
    }

    private static Integer portalIdTo(Character agent, int destinationMapId) {
        List<Integer> portalIds = AgentLpqExitRoutePolicy.portalIds(
                agent.getMapId(), destinationMapId);
        Point currentPosition = agent.getPosition();
        Integer nearest = portalIds.stream()
                .filter(portalId -> ACTIONS.portalPosition(agent, portalId) != null)
                .min(Comparator.comparingDouble(portalId ->
                        currentPosition == null ? 0.0
                                : ACTIONS.portalPosition(agent, portalId).distanceSq(currentPosition)))
                .orElse(null);
        return nearest != null ? nearest : ACTIONS.directPortalIdTo(agent, destinationMapId);
    }

    private static boolean enterPortalTo(AgentRuntimeEntry entry, Character agent, int destinationMapId) {
        int sourceMapId = agent.getMapId();
        Integer portalId = portalIdTo(agent, destinationMapId);
        if (portalId == null) return false;
        Point position = ACTIONS.portalPosition(agent, portalId);
        if (position == null) return false;
        Point waypoint = AgentLpqExitRoutePolicy.nextWaypoint(
                sourceMapId, destinationMapId, agent.getPosition(), position);
        if (waypoint != null && !near(agent.getPosition(), waypoint, INTERACTION_RADIUS)) {
            ACTIONS.navigate(entry, waypoint, true);
            return true;
        }
        if (!near(agent.getPosition(), position, INTERACTION_RADIUS)) {
            ACTIONS.navigate(entry, position, true);
            return true;
        }
        ACTIONS.stop(entry);
        return LPQ.enterPortal(agent, portalId);
    }

    private static int stage(AgentLpqSession.Phase phase) {
        return Integer.parseInt(phase.name().substring("STAGE_".length()));
    }
    private static boolean near(Point a, Point b, int radius) {
        return a != null && b != null && Math.abs(a.x - b.x) <= radius && Math.abs(a.y - b.y) <= radius;
    }
    private static boolean isAgent(Character character) { return character != null && entry(character.getId()) != null; }
    private static AgentRuntimeEntry entry(int id) { return AgentRuntimeRegistry.findByAgentCharacterId(id); }
    private static Character character(int id) {
        AgentRuntimeEntry entry = entry(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
}
