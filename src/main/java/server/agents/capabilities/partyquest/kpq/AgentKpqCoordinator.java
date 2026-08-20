package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagement;
import server.agents.capabilities.partyquest.AgentPartyQuestEngagementRegistry;
import server.agents.capabilities.partyquest.AgentPartyQuestLifecycleRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.field.AgentFieldObservationState;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import scripting.event.EventInstanceManager;
import server.maps.MapItem;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Party-level KPQ state machine. It uses ordinary NPC scripts, portals, combat, drops, and loot. */
final class AgentKpqCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentKpqCoordinator.class);
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway KPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final Set<Integer> STAGE_1_MOBS = Set.of(9_300_001);
    static final Set<Integer> STAGE_5_NORMAL_MOBS = Set.of(9_300_000, 9_300_002, 210_100);
    static final Set<Integer> STAGE_5_BOSS_MOBS = Set.of(9_300_003);
    private static final long KING_SLIME_REVIVE_GRACE_MS = 2_000L;
    private static final long FORMATION_STABLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.FORMATION_STABLE_MS");
    private static final long KPQ_PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.KPQ_PHASE_TIMEOUT_MS");
    private static final long COUPON_SWEEP_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.COUPON_SWEEP_INTERVAL_MS");
    private static final long COUPON_SWEEP_MAXIMUM_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.COUPON_SWEEP_MAXIMUM_MS");
    private static final long MISSING_PASS_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.MISSING_PASS_GRACE_MS");
    private static final long STAGE1_SUBMIT_RECOVERY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.STAGE1_SUBMIT_RECOVERY_MS");
    private static final long LOCAL_RECOVERY_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.LOCAL_RECOVERY_TIMEOUT_MS");
    private static final long INTERACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.INTERACTION_RETRY_MS");
    private static final long PUZZLE_CHECK_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.PUZZLE_CHECK_DELAY_MS");
    private static final long PUZZLE_CHECK_VARIANCE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.PUZZLE_CHECK_VARIANCE_MS");
    private static final long STAGE5_MISSING_PASS_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.STAGE5_MISSING_PASS_GRACE_MS");
    private static final long SQUISHY_SHOES_HUMAN_PRIORITY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.SQUISHY_SHOES_HUMAN_PRIORITY_MS");
    private static final long PREPARATION_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.PREPARATION_DELAY_MS");
    private static final int NEAR_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.NEAR_PX");
    private static final int NPC_APPROACH_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.NPC_APPROACH_PX");
    private static final int GATHER_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.GATHER_RADIUS_PX");

    private AgentKpqCoordinator() {
    }

    static void tick(AgentKpqSession session, long nowMs) {
        synchronized (session) {
            tickSession(session, nowMs);
        }
    }

    private static void tickSession(AgentKpqSession session, long nowMs) {
        if (terminal(session.phase())) return;
        Character leader = eventLeader(session);
        Character narrator = narrator(session);
        if (nowMs - session.lastProgressAtMs() > KPQ_PHASE_TIMEOUT_MS) {
            if (leader == null) {
                fail(session, "No KPQ progress for " + (KPQ_PHASE_TIMEOUT_MS / 60_000L) + " minutes", nowMs);
                return;
            }
            if (session.phase() == AgentKpqSession.Phase.STAGE_1) {
                endStageOneForTimeout(session, leader, nowMs);
            } else {
                fail(session, "No KPQ progress for " + (KPQ_PHASE_TIMEOUT_MS / 60_000L) + " minutes", nowMs);
            }
            return;
        }
        if (leader == null) {
            fail(session, "The event leader is no longer active", nowMs);
            return;
        }
        if (leader.getHp() <= 0) {
            fail(session, "The event leader was defeated or left the KPQ", nowMs);
            return;
        }
        bindKnownEvent(session, leader);
        if (!reconcileLiveParty(session, leader, nowMs)) return;
        if (!membersAlive(session, leader, nowMs)) return;
        if (insideEventPhase(session.phase()) && session.eventInstance() != null
                && KPQ.event(leader) != session.eventInstance()) {
            fail(session, "The KPQ event instance disappeared or the leader left it", nowMs);
            return;
        }
        if (narrator == null) {
            fail(session, "The Agent coordinator is no longer active", nowMs);
            return;
        }
        switch (session.phase()) {
            case PREPARING -> prepare(session, leader, narrator, nowMs);
            case ENTERING -> enter(session, leader, narrator, nowMs);
            case STAGE_1 -> stage1(session, leader, narrator, nowMs);
            case STAGE_2 -> combinationStage(session, leader, narrator, 2, nowMs);
            case STAGE_3 -> combinationStage(session, leader, narrator, 3, nowMs);
            case STAGE_4 -> combinationStage(session, leader, narrator, 4, nowMs);
            case STAGE_5 -> stage5(session, leader, narrator, nowMs);
            case CLAIMING_REWARDS -> claimRewards(session, nowMs);
            case EXITING -> exit(session, nowMs);
            default -> { }
        }
    }

    private static void endStageOneForTimeout(AgentKpqSession session, Character leader, long nowMs) {
        narrate(session, narrator(session), "stage1-timeout",
                "No KPQ progress in stage 1 for " + (KPQ_PHASE_TIMEOUT_MS / 60_000L)
                        + " minutes. Ending KPQ and returning to Kerning.");
        fail(session, "No KPQ progress for " + (KPQ_PHASE_TIMEOUT_MS / 60_000L)
                + " minutes in stage 1", nowMs);
    }

    private static void prepare(
            AgentKpqSession session, Character leader, Character narrator, long nowMs) {
        if (session.memberCount() != session.requestedPartySize()) {
            if (blockedTooLong(session, "prepare-party-size", nowMs)) {
                fail(session, "KPQ party membership changed during preparation", nowMs);
            }
            return;
        }
        if (!allMembersOnMap(session, AgentKpqDefinition.RECRUIT_MAP, leader)) {
            if (blockedTooLong(session, "prepare-member-map", nowMs)) {
                fail(session, "A party member did not reach the Kerning entrance", nowMs);
            }
            return;
        }
        Point entryNpc = ACTIONS.npcPosition(leader, AgentKpqDefinition.ENTRY_NPC);
        if (entryNpc == null) {
            if (blockedTooLong(session, "prepare-entry-npc", nowMs)) {
                fail(session, "The Kerning entry NPC could not be located", nowMs);
            }
            return;
        }
        boolean allGathered = true;
        for (AgentKpqMemberState member : session.members()) {
            Character memberCharacter = memberCharacter(member.characterId(), leader);
            if (memberCharacter == null || memberCharacter.getMap() == null) {
                allGathered = false;
                continue;
            }
            if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                Point target = npcApproachPoint(session, member, memberCharacter, entryNpc, 11);
                if (!walkToPoint(memberCharacter, target, entry(member.characterId()), NPC_APPROACH_PX)) {
                    allGathered = false;
                }
            } else if (!near(memberCharacter.getPosition(), entryNpc, GATHER_RADIUS_PX)) {
                allGathered = false;
            }
        }
        if (!allGathered) {
            if (session.readyAtMs() != 0L) session.setReadyAtMs(0L);
            if (blockedTooLong(session, "prepare-gather", nowMs)) {
                fail(session, "The party could not gather at Lakelis", nowMs);
            }
            return;
        }
        session.clearBlocker();
        if (session.readyAtMs() == 0L) {
            session.setReadyAtMs(nowMs + PREPARATION_DELAY_MS);
            AgentKpqDialogue.sayMapNow(
                    narrator, "Party ready. Everyone is here. Starting KPQ in 5 seconds.");
        }
        if (nowMs >= session.readyAtMs()) transition(session, AgentKpqSession.Phase.ENTERING, nowMs);
    }

    private static void enter(
            AgentKpqSession session, Character leader, Character narrator, long nowMs) {
        if (leader.getMapId() == AgentKpqDefinition.STAGE_1_MAP && KPQ.event(leader) != null) {
            session.bindEventInstance(KPQ.event(leader));
            if (allMembersSameEvent(session, leader)) {
                session.clearBlocker();
                if (session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION
                        && session.requestedCheckpointStage() > 1) {
                    AgentKpqCheckpointService.apply(session, leader, session.requestedCheckpointStage(), nowMs);
                    return;
                }
                narrate(session, narrator, "entered", "We're in. Ask Cloto for your coupon number.");
                initializeStageMovementDelays(session, 1, nowMs);
                transition(session, AgentKpqSession.Phase.STAGE_1, nowMs);
            } else if (blockedTooLong(session, "enter-partial-party", nowMs)) {
                fail(session, "Only part of the party entered the KPQ instance", nowMs);
            }
            return;
        }
        if (leader.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
            if (blockedTooLong(session, "enter-leader-map", nowMs)) {
                fail(session, "The event leader left the KPQ entrance during admission", nowMs);
            }
            return;
        }
        if (isAgent(session, leader.getId())) {
            AgentKpqMemberState leaderState = session.member(leader.getId());
            if (nowMs < leaderState.nextRetryAtMs()) return;
            leaderState.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
            boolean invoked = KPQ.runNpc(leader, AgentKpqDefinition.ENTRY_NPC, 0);
            if (leader.getMapId() == AgentKpqDefinition.RECRUIT_MAP
                    && blockedTooLong(session, invoked ? "enter-lobby-wait" : "enter-npc", nowMs)) {
                fail(session, invoked
                        ? "No KPQ lobby became available before the admission deadline"
                        : "Lakelis repeatedly rejected or failed the KPQ entry request", nowMs);
            }
        } else {
            narrate(session, narrator, "human-enter",
                    leader.getName() + ", please talk to Lakelis to start KPQ.");
        }
    }

    private static void stage1(
            AgentKpqSession session, Character leader, Character narrator, long nowMs) {
        if ("true".equals(KPQ.property(leader, "1stageclear"))) {
            clearCouponLootObjectives(session);
            enterNextPortal(session, 2, nowMs);
            return;
        }
        boolean allDelivered = true;
        AgentRuntimeEntry leaderEntry = entry(leader.getId());
        Point cloto = ACTIONS.npcPosition(leader, AgentKpqDefinition.CLOTO_NPC);
        int needed = session.memberCount() - 1;
        boolean agentLeader = isAgent(session, leader.getId());
        List<MapItem> floorCoupons = liveCouponDrops(leader.getMap());
        int couponsOnGround = couponQuantityOnGround(floorCoupons);
        int remainingPartyNeed = remainingPartyCouponNeed(session, leader);
        boolean knownCouponNeedsCovered = allKnownCouponNeedsCovered(session, leader);
        AgentKpqCouponCollectionPolicy.Decision collection =
                AgentKpqCouponCollectionPolicy.decide(
                        nowMs,
                        session.phaseEnteredAtMs(),
                        session.couponSweepStartedAtMs(),
                        session.nextCouponSweepAtMs(),
                        couponsOnGround,
                        remainingPartyNeed,
                        COUPON_SWEEP_INTERVAL_MS,
                        COUPON_SWEEP_MAXIMUM_MS);
        session.setCouponSweepStartedAtMs(collection.sweepStartedAtMs());
        session.setNextCouponSweepAtMs(collection.nextSweepAtMs());
        int sweepCollectorId = session.couponSweepCollectorId();
        if (!collection.sweepActive()) {
            sweepCollectorId = 0;
        } else if (collection.rotateCollector()
                || !couponSweepCollectorEligible(session, leader, sweepCollectorId)) {
            sweepCollectorId = nextCouponSweepCollectorId(session, leader, sweepCollectorId);
            if (sweepCollectorId > 0) {
                session.setCouponSweepStartedAtMs(nowMs);
            } else {
                session.setCouponSweepStartedAtMs(0L);
                session.setNextCouponSweepAtMs(nowMs + COUPON_SWEEP_INTERVAL_MS);
            }
        }
        session.setCouponSweepCollectorId(sweepCollectorId);
        syncCouponLootObjectives(session, leader, floorCoupons, sweepCollectorId);
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), leader);
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || agent.getMap() == null
                    || agent.getMapId() != AgentKpqDefinition.STAGE_1_MAP) {
                if (member.characterId() != session.eventLeaderId()) allDelivered = false;
                continue;
            }
            if (member.memberType() == AgentKpqMemberState.MemberType.HUMAN) {
                if (member.characterId() != session.eventLeaderId()) {
                    if (KPQ.playerGrid(agent) == 0) member.markPassCreated();
                    if (member.passCreated()
                            && agent.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) == 0) {
                        member.markPassDelivered();
                    }
                    if (!member.passDelivered()) allDelivered = false;
                    narrate(session, narrator, "human-stage1-" + member.characterId(),
                            agent.getName() + ", ask Cloto for your coupon number, collect them, "
                                    + "then drop your pass beside " + leader.getName() + '.');
                }
                continue;
            }
            if (entry == null) {
                if (member.characterId() != session.eventLeaderId()) allDelivered = false;
                continue;
            }
            if (stageMovementDelayed(session, member, entry, 1, nowMs)) {
                if (member.characterId() != session.eventLeaderId()) allDelivered = false;
                continue;
            }
            if (member.characterId() == session.eventLeaderId()) {
                member.setRole(AgentKpqMemberState.Role.EVENT_LEADER);
                if (!livePassDrops(agent.getMap()).isEmpty()) {
                    ACTIONS.stop(entry);
                    KPQ.lootNearby(agent, Set.of(AgentKpqDefinition.PASS_ITEM));
                } else if (knownCouponNeedsCovered) {
                    moveTowardStageOneExit(session, member, agent, entry);
                } else {
                    ACTIONS.grind(entry, stage1GrindTargets(agent));
                }
                continue;
            }
            if (!member.questionRequested()) {
                long questionAt = session.phaseEnteredAtMs() + 300L * member.partyNumber()
                        + Math.floorMod(session.seed() + member.characterId(), 350L);
                if (nowMs < questionAt) {
                    allDelivered = false;
                    continue;
                }
                Point approach = npcApproachPoint(session, member, agent,
                        ACTIONS.npcPosition(agent, AgentKpqDefinition.CLOTO_NPC), 101);
                if (!walkToPoint(agent, approach, entry, NPC_APPROACH_PX)) {
                    allDelivered = false;
                    continue;
                }
                if (nowMs < member.nextRetryAtMs()) {
                    allDelivered = false;
                    continue;
                }
                member.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                boolean invoked = KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                int target = AgentKpqDefinition.couponTarget(KPQ.playerGrid(agent));
                if (target > 0) {
                    member.clearBlocker();
                    member.markQuestionRequested();
                    member.setCouponTarget(target);
                    member.setRole(AgentKpqMemberState.Role.COUPON_COLLECTOR);
                    narrate(session, agent, "coupon-target-" + agent.getId(),
                            "I need " + target + " coupons.");
                } else {
                    member.observeBlocker(invoked ? "stage1-question-state" : "stage1-question-npc", nowMs);
                    if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                        fail(session, "Cloto could not assign a coupon question to " + agent.getName(), nowMs);
                        return;
                    }
                }
            }
            if (!member.passCreated()) {
                member.setRole(AgentKpqMemberState.Role.COUPON_COLLECTOR);
                allDelivered = false;
                int have = agent.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false);
                reportCouponProgress(session, agent, member, have);
                if (member.couponTarget() > 0 && have >= member.couponTarget()) {
                    ACTIONS.stop(entry);
                    if (have > member.couponTarget()) {
                        AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                                AgentKpqDefinition.COUPON_ITEM, (short) (have - member.couponTarget()));
                        continue;
                    }
                    Point approach = npcApproachPoint(session, member, agent,
                            ACTIONS.npcPosition(agent, AgentKpqDefinition.CLOTO_NPC), 151);
                    if (!walkToPoint(agent, approach, entry, NPC_APPROACH_PX)) {
                        continue;
                    }
                    if (nowMs < member.nextRetryAtMs()) continue;
                    member.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                    KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                    if (agent.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) > 0) {
                        member.clearBlocker();
                        member.markPassCreated();
                        member.setRole(AgentKpqMemberState.Role.PASS_DELIVERER);
                        session.markProgress(nowMs);
                    } else {
                        member.observeBlocker("stage1-pass-create", nowMs);
                        if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                            fail(session, agent.getName()
                                    + " could not create a Stage 1 pass; check ETC inventory capacity", nowMs);
                            return;
                        }
                    }
                } else {
                    ACTIONS.grind(entry, stage1GrindTargets(agent));
                }
            }
            if (member.passCreated() && !member.passDelivered()) {
                allDelivered = false;
                if (near(agent, leader, NEAR_PX)) {
                    ACTIONS.stop(entry);
                    int passesBeforeDrop = agent.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
                    boolean dropRequested = AgentScriptItemActionService.dropItem(
                            entry, InventoryType.ETC, AgentKpqDefinition.PASS_ITEM, (short) 1);
                    int passesAfterDrop = agent.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
                    if (dropRequested && passDropConfirmed(passesBeforeDrop, passesAfterDrop)) {
                        member.markPassDelivered();
                        member.setRole(AgentKpqMemberState.Role.COMBAT_HELPER);
                        session.markProgress(nowMs);
                    }
                } else {
                    ACTIONS.navigate(entry, leader.getPosition(), false);
                }
            } else if (member.passDelivered()) {
                if (knownCouponNeedsCovered) moveTowardStageOneExit(session, member, agent, entry);
                else ACTIONS.grind(entry, stage1GrindTargets(agent));
            }
        }
        List<MapItem> floorPasses = livePassDrops(leader.getMap());
        if (agentLeader && !floorPasses.isEmpty()) {
            ACTIONS.stop(leaderEntry);
            KPQ.lootNearby(leader, Set.of(AgentKpqDefinition.PASS_ITEM));
            floorPasses = livePassDrops(leader.getMap());
        }
        AgentKpqMemberState leaderState = session.member(session.eventLeaderId());
        int leaderPasses = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        reportLeaderPassProgress(session, narrator, leaderState, leaderPasses, needed);
        int floorPassQuantity = passQuantityOnGround(floorPasses);
        if (!agentLeader && floorPassQuantity > 0 && leaderPasses < needed) {
            narrate(session, narrator,
                    "human-stage1-loot-" + leaderPasses + '-' + floorPassQuantity,
                    leader.getName() + ", pick up the pass beside you.");
        }
        if (leaderPasses >= needed) {
            session.setMissingPassSinceMs(0L);
            if (!agentLeader) {
                narrate(session, narrator, "human-stage1-submit",
                        leader.getName() + ", you have " + leaderPasses + "/" + needed
                                + " passes. Please talk to Cloto.");
                return;
            }
            Point approach = npcApproachPoint(session, leaderState, leader, cloto, 191);
            if (walkToPoint(leader, approach, leaderEntry, NPC_APPROACH_PX)) {
                ACTIONS.stop(leaderEntry);
                if (nowMs >= leaderState.nextRetryAtMs()) {
                    leaderState.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                    if (submitReadyStageOne(session, leader, nowMs, "npc-approach")) {
                        leaderState.clearBlocker();
                    } else {
                        leaderState.observeBlocker("stage1-submit-rejected", nowMs);
                        if (nowMs - leaderState.blockerSinceMs() >= STAGE1_SUBMIT_RECOVERY_MS) {
                            forceReadyStageOne(session, leader, nowMs, "npc-rejection-timeout");
                        }
                    }
                }
            } else {
                leaderState.observeBlocker("stage1-submit-walk", nowMs);
                if (nowMs - leaderState.blockerSinceMs() >= STAGE1_SUBMIT_RECOVERY_MS
                        && nowMs >= leaderState.nextRetryAtMs()) {
                    leaderState.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                    submitReadyStageOne(session, leader, nowMs, "navigation-timeout");
                }
            }
            return;
        }
        if (!allDelivered || floorPassQuantity > 0) {
            session.setMissingPassSinceMs(0L);
            return;
        }
        if (session.missingPassSinceMs() == 0L) {
            session.setMissingPassSinceMs(nowMs);
            return;
        }
        if (shouldBypassMissingPasses(allDelivered, leaderPasses, needed,
                floorPassQuantity, session.missingPassSinceMs(), nowMs,
                MISSING_PASS_GRACE_MS)) {
            bypassMissingStageOnePasses(
                    session, leader, leaderPasses, needed, "grace-timeout", nowMs);
        }
    }

    private static void combinationStage(AgentKpqSession session,
                                         Character leader,
                                         Character narrator,
                                         int stage,
                                         long nowMs) {
        AgentKpqDefinition.CombinationStage definition = AgentKpqDefinition.combinationStage(stage);
        if ("true".equals(KPQ.property(leader, definition.clearProperty()))) {
            enterNextPortal(session, stage + 1, nowMs);
            return;
        }
        if (KPQ.property(leader, definition.answerProperty()) == null) {
            if (isAgent(session, leader.getId())) {
                KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
            } else {
                narrate(session, narrator, "human-stage" + stage + "-initialize",
                        leader.getName() + ", please talk to Cloto to begin Stage " + stage + '.');
            }
            return;
        }
        List<AgentKpqMemberState> participants = participants(session);
        if (participants.size() < 3) {
            fail(session, "Stage " + stage + " needs three controllable puzzle participants", nowMs);
            return;
        }
        List<List<Integer>> order = AgentKpqCombinationOrder.forPositionCount(definition.positions().size());
        if (session.attemptIndex() >= order.size()) {
            failExhaustedPuzzle(session, leader, definition, nowMs);
            return;
        }
        int attempt = session.attemptIndex();
        List<Integer> combination = order.get(attempt);
        if (!combination.equals(session.combination())) {
            int attemptId = session.nextAttemptId();
            List<AgentKpqMemberState> movers = assignFormation(
                    participants, combination, session.seed(), attemptId, nowMs);
            session.setCombination(combination);
            session.setPuzzleCheckAtMs(0L);
            if (!movers.isEmpty()) {
                narrate(session, narrator, "s" + stage + "-a" + attempt,
                        formationInstruction(movers, leader));
            }
        }
        boolean stable = true;
        for (AgentKpqMemberState participant : participants.subList(0, 3)) {
            Character agent = memberCharacter(participant.characterId(), leader);
            AgentRuntimeEntry entry = entry(participant.characterId());
            if (agent == null || agent.getMapId() != definition.mapId()) {
                stable = false;
                continue;
            }
            if (nowMs < participant.stageMovementNotBeforeMs()) {
                if (entry != null) ACTIONS.stop(entry);
                stable = false;
                continue;
            }
            if (nowMs < participant.actionNotBeforeMs()) {
                stable = false;
                continue;
            }
            boolean grounded = definition.holdMode() != AgentKpqDefinition.HoldMode.GROUNDED
                    || ACTIONS.grounded(agent);
            if (!puzzlePositionReady(definition, participant.assignedPosition(),
                    agent.getPosition(), grounded)) {
                participant.clearFidget();
                participant.observeBlocker("puzzle-position", nowMs);
                participant.setStableSinceMs(0L);
                if (entry != null) {
                    ACTIONS.navigate(entry, formationTarget(
                            definition, participant,
                            session.seed() + participant.blockerAttempts() * 17L), true);
                    if (nowMs - participant.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS * 2L) {
                        fail(session, "Stage " + stage + " formation movement exhausted for member "
                                + participant.characterId(), nowMs);
                        return;
                    }
                } else if (nowMs - participant.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                    narrate(session, narrator,
                            "human-stage" + stage + "-position-" + participant.characterId(),
                            agent.getName() + ", please move to " + participant.assignedPosition() + '.');
                }
                stable = false;
            } else if (participant.stableSinceMs() == 0L) {
                participant.clearBlocker();
                if (entry != null) ACTIONS.stop(entry);
                participant.setStableSinceMs(nowMs);
                stable = false;
            } else if (nowMs - participant.stableSinceMs() < FORMATION_STABLE_MS) {
                stable = false;
            }
            if (puzzlePositionReady(definition, participant.assignedPosition(),
                    agent.getPosition(), grounded)) {
                AgentKpqPuzzleFidgetBehavior.tick(
                        participant, agent, entry, definition, nowMs);
            }
        }
        if (!stable) return;
        AgentKpqMemberState fidgeter = AgentKpqPuzzleFidgetBehavior.select(
                session, participants.subList(0, 3));
        if (fidgeter != null && fidgeter.fidgetedAttemptId() != session.attemptId()) {
            Character agent = memberCharacter(fidgeter.characterId(), leader);
            AgentRuntimeEntry entry = entry(fidgeter.characterId());
            if (agent != null && entry != null) {
                AgentKpqPuzzleFidgetBehavior.begin(fidgeter, definition, agent.getPosition(),
                        session.seed(), session.attemptId(), nowMs, LOCAL_RECOVERY_TIMEOUT_MS);
            }
        }
        if (isAgent(session, leader.getId())) {
            if (session.puzzleCheckAtMs() == 0L) {
                long delayMs = puzzleCheckDelayMs(session.seed(), stage, attempt);
                session.setPuzzleCheckAtMs(nowMs + delayMs);
                if (session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION) {
                    log.info("KPQ puzzle check scheduled: session={} stage={} attempt={} attemptId={} "
                                    + "delayMs={} fidgeters={}",
                            session.sessionId(), stage, attempt, session.attemptId(), delayMs,
                            participants.subList(0, 3).stream()
                                    .filter(member -> member.fidgetTarget() != null).count());
                }
                return;
            }
            if (nowMs < session.puzzleCheckAtMs()) return;
            if (session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION
                    && session.markPuzzleCheckLogged(session.attemptId())) {
                log.info("KPQ puzzle check executing: session={} stage={} attempt={} attemptId={} "
                                + "latenessMs={} fidgeters={} positions={}",
                        session.sessionId(), stage, attempt, session.attemptId(),
                        Math.max(0L, nowMs - session.puzzleCheckAtMs()),
                        participants.subList(0, 3).stream()
                                .filter(member -> member.fidgetTarget() != null).count(),
                        participants.subList(0, 3).stream()
                                .map(member -> member.characterId() + "->" + member.assignedPosition())
                                .toList());
            }
        }
        if (!isAgent(session, leader.getId())) {
            AgentKpqSession.PuzzleValidation validation = session.consumeHumanPuzzleValidation(stage);
            if (validation == null) {
                narrate(session, narrator, "human-stage" + stage + "-check-" + attempt,
                        leader.getName() + ", please check this formation with Cloto.");
                return;
            }
            if (validation.accepted()) return;
            advancePuzzleAttempt(session, leader, definition, order.size(), attempt, nowMs);
            return;
        }
        if (!KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC)) {
            if (blockedTooLong(session, "stage" + stage + "-npc-check", nowMs)) {
                fail(session, "Stage " + stage + " Cloto check repeatedly failed", nowMs);
            }
            return;
        }
        session.clearBlocker();
        if (!"true".equals(KPQ.property(leader, definition.clearProperty()))) {
            advancePuzzleAttempt(session, leader, definition, order.size(), attempt, nowMs);
        }
    }

    private static void stage5(
            AgentKpqSession session, Character leader, Character narrator, long nowMs) {
        if ("true".equals(KPQ.property(leader, "5stageclear"))) {
            stopAll(session);
            transition(session, AgentKpqSession.Phase.CLAIMING_REWARDS, nowMs);
            return;
        }
        narrate(session, narrator, "stage5-enter",
                "Clearing the monsters and collecting 10 passes.");
        boolean shoesPending = handleSquishyShoes(session, leader, nowMs);
        boolean agentLeader = isAgent(session, leader.getId());
        if (agentLeader) KPQ.lootNearby(leader, Set.of(AgentKpqDefinition.PASS_ITEM));
        else narrate(session, narrator, "human-stage5-collect",
                leader.getName() + ", please collect the 10 passes while we clear the monsters.");
        int leaderPasses = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        int normalAlive = ACTIONS.liveMonsterCount(leader, STAGE_5_NORMAL_MOBS);
        int bossAlive = ACTIONS.liveMonsterCount(leader, STAGE_5_BOSS_MOBS);
        observeStageFiveBossCombat(session, normalAlive, bossAlive, nowMs);
        int stage5MobCount = normalAlive + bossAlive;
        boolean reviveGraceActive = session.stage5ReviveGraceActive(
                bossAlive, nowMs, KING_SLIME_REVIVE_GRACE_MS);
        int floorPasses = passQuantityOnGround(livePassDrops(leader.getMap()));
        if (session.observeStage5Progress(stage5MobCount, leaderPasses)) session.markProgress(nowMs);
        boolean returningToCloto = stageFiveReadyToReturn(
                leaderPasses, normalAlive, bossAlive, reviveGraceActive);
        for (AgentKpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = entry(member.characterId());
            Character agent = memberCharacter(member.characterId(), leader);
            if (agent == null || agent.getMapId() != AgentKpqDefinition.STAGE_5_MAP) continue;
            member.setRole(member.characterId() == session.eventLeaderId()
                    ? AgentKpqMemberState.Role.STAGE5_PASS_COLLECTOR
                    : AgentKpqMemberState.Role.COMBAT_HELPER);
            if (member.memberType() == AgentKpqMemberState.MemberType.AGENT && entry != null) {
                if (nowMs < member.stageMovementNotBeforeMs()) {
                    ACTIONS.stop(entry);
                } else if (shoesPending && member.characterId() == session.squishyShoesWinnerId()) {
                    member.setRole(AgentKpqMemberState.Role.SQUISHY_SHOES_COLLECTOR);
                    ACTIONS.stop(entry);
                } else if (returningToCloto) {
                    Point cloto = ACTIONS.npcPosition(agent, AgentKpqDefinition.CLOTO_NPC);
                    Point approach = npcApproachPoint(session, member, agent, cloto, 501);
                    walkToPoint(agent, approach, entry, NPC_APPROACH_PX);
                } else {
                    grindStageFive(entry, agent);
                }
            }
        }
        if (reviveGraceActive && stage5MobCount == 0) {
            session.setMissingPassSinceMs(0L);
            return;
        }
        if (!returningToCloto && stage5MobCount == 0 && floorPasses == 0) {
            if (session.missingPassSinceMs() == 0L) session.setMissingPassSinceMs(nowMs);
            if (nowMs - session.missingPassSinceMs() >= STAGE5_MISSING_PASS_GRACE_MS) {
                bypassMissingStageFivePasses(session, leader, leaderPasses, nowMs);
            }
            return;
        }
        session.setMissingPassSinceMs(0L);
        if (returningToCloto) {
            if (!agentLeader) {
                narrate(session, narrator, "human-stage5-submit",
                        leader.getName() + ", you have 10/10 passes. Please talk to Cloto.");
                return;
            }
            AgentKpqMemberState leaderState = session.member(session.eventLeaderId());
            Point approach = npcApproachPoint(session, leaderState, leader,
                    ACTIONS.npcPosition(leader, AgentKpqDefinition.CLOTO_NPC), 501);
            if (walkToPoint(leader, approach, entry(leader.getId()), NPC_APPROACH_PX)) {
                KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
            }
        }
    }

    static boolean stageFiveReadyToReturn(int leaderPasses,
                                          int normalAlive,
                                          int bossAlive,
                                          boolean reviveGraceActive) {
        return leaderPasses >= 10 && normalAlive == 0 && bossAlive == 0 && !reviveGraceActive;
    }

    private static void observeStageFiveBossCombat(
            AgentKpqSession session, int normalAlive, int bossAlive, long nowMs) {
        if (normalAlive == 0 && bossAlive > 0 && session.beginStage5BossCombat(nowMs)) {
            for (AgentKpqMemberState member : session.members()) {
                if (member.memberType() != AgentKpqMemberState.MemberType.AGENT) continue;
                AgentRuntimeEntry entry = entry(member.characterId());
                if (entry == null) continue;
                AgentFieldObservationState.Snapshot snapshot = entry.capabilityStates()
                        .require(AgentFieldObservationState.STATE_KEY).snapshot(nowMs);
                member.beginStage5BossCombat(snapshot.attacks(), snapshot.hitLines(),
                        snapshot.missLines(), snapshot.damage());
            }
            log.info("KPQ King Slime combat started: session={} agents={}",
                    session.sessionId(), session.members().stream()
                            .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                            .map(AgentKpqMemberState::characterId).toList());
            return;
        }
        if (bossAlive > 0 || !session.claimStage5BossCombatReport()) return;
        reportStageFiveBossCombat(session, nowMs);
    }

    private static void reportStageFiveBossCombat(AgentKpqSession session, long nowMs) {
        long durationMs = Math.max(1L, nowMs - session.stage5BossCombatStartedAtMs());
        record Result(String name, AgentKpqMemberState.BossCombatDelta delta,
                      AgentFieldObservationState.Snapshot snapshot) { }
        List<Result> results = session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .map(member -> {
                    Character agent = memberCharacter(member.characterId(), firstCharacter(session));
                    AgentRuntimeEntry entry = entry(member.characterId());
                    if (entry == null) return null;
                    AgentFieldObservationState.Snapshot snapshot = entry.capabilityStates()
                            .require(AgentFieldObservationState.STATE_KEY).snapshot(nowMs);
                    return new Result(agent == null ? "#" + member.characterId() : agent.getName(),
                            member.stage5BossCombatDelta(snapshot.attacks(), snapshot.hitLines(),
                                    snapshot.missLines(), snapshot.damage()), snapshot);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        long maximumDamage = results.stream().map(Result::delta)
                .mapToLong(AgentKpqMemberState.BossCombatDelta::damage).max().orElse(0L);
        for (Result result : results) {
            AgentKpqMemberState.BossCombatDelta delta = result.delta();
            String concern = bossCombatConcern(delta, maximumDamage);
            String message = "KPQ King Slime combat: session=" + session.sessionId()
                    + " agent=" + result.name() + " durationMs=" + durationMs
                    + " attacks=" + delta.attacks() + " hits=" + delta.hitLines()
                    + " misses=" + delta.missLines() + " damage=" + delta.damage()
                    + " dps=" + (delta.damage() * 1000L / durationMs)
                    + " posture=" + result.snapshot().posture()
                    + " target=" + result.snapshot().targetMobId()
                    + " assessment=" + concern;
            if ("ok".equals(concern)) log.info(message);
            else log.warn(message);
        }
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forOperator(session.operatorId());
        if (engagement != null) {
            String summary = results.stream().map(result -> result.name() + ':'
                            + result.delta().damage() + '/' + result.delta().attacks())
                    .collect(java.util.stream.Collectors.joining(","));
            engagement.addDiagnostic("King Slime " + durationMs + "ms damage/attacks " + summary, nowMs);
        }
    }

    static String bossCombatConcern(
            AgentKpqMemberState.BossCombatDelta delta, long maximumPartyDamage) {
        if (delta == null || delta.attacks() == 0L) return "no-attacks";
        if (delta.damage() == 0L && delta.missLines() > 0L) return "all-misses";
        if (delta.damage() == 0L) return "no-damage";
        long attemptedLines = delta.hitLines() + delta.missLines();
        if (attemptedLines >= 5L && delta.missLines() * 5L >= attemptedLines * 3L) {
            return "accuracy-limited";
        }
        if (maximumPartyDamage > 0L && delta.damage() * 10L < maximumPartyDamage) {
            return "below-10%-of-party-maximum";
        }
        return "ok";
    }

    private static void claimRewards(AgentKpqSession session, long nowMs) {
        Character anchor = firstCharacter(session);
        boolean shoesPending = anchor != null && handleSquishyShoes(session, anchor, nowMs);
        boolean allInBonus = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), firstCharacter(session));
            if (agent == null || agent.getMap() == null) {
                allInBonus = false;
                continue;
            }
            if (agent.getMapId() == AgentKpqDefinition.STAGE_5_MAP) {
                allInBonus = false;
                if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                    if (shoesPending && member.characterId() == session.squishyShoesWinnerId()) {
                        AgentRuntimeEntry winnerEntry = entry(member.characterId());
                        if (winnerEntry != null) ACTIONS.stop(winnerEntry);
                        continue;
                    }
                    boolean rewardCapacity = agent.getInventory(InventoryType.USE).getNextFreeSlot() > -1
                            && agent.getInventory(InventoryType.ETC).getNextFreeSlot() > -1;
                    if (!rewardCapacity) {
                        member.observeBlocker("reward-inventory-full", nowMs);
                        if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                            log.warn("KPQ reward abandoned after inventory deadline: session={} member={} map={}",
                                    session.sessionId(), member.characterId(), agent.getMapId());
                            EventInstanceManager event = session.eventInstance();
                            if (event != null) event.exitPlayer(agent);
                            member.markRewardClaimed();
                        }
                        continue;
                    }
                    Point cloto = ACTIONS.npcPosition(agent, AgentKpqDefinition.CLOTO_NPC);
                    Point approach = npcApproachPoint(session, member, agent, cloto, 551);
                    if (walkToPoint(agent, approach, entry(member.characterId()), NPC_APPROACH_PX)) {
                        if (nowMs >= member.nextRetryAtMs()) {
                            member.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                            KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                        }
                    }
                } else {
                    narrate(session, narrator(session), "human-claim-" + member.characterId(),
                            agent.getName() + ", please talk to Cloto to claim your reward.");
                }
            } else if (agent.getMapId() == AgentKpqDefinition.BONUS_MAP) {
                member.clearBlocker();
                member.markRewardClaimed();
            } else if (member.rewardClaimed()
                    && agent.getMapId() == AgentKpqDefinition.RECRUIT_MAP) {
                // Bounded Agent recovery may deliberately leave without a reward.
            } else {
                allInBonus = false;
            }
        }
        if (allInBonus) transition(session, AgentKpqSession.Phase.EXITING, nowMs);
    }

    private static void exit(AgentKpqSession session, long nowMs) {
        boolean allOutside = true;
        Character leader = leader(session);
        Point entryNpc = leader == null || leader.getMap() == null
                || leader.getMapId() != AgentKpqDefinition.RECRUIT_MAP
                ? null : ACTIONS.npcPosition(leader, AgentKpqDefinition.ENTRY_NPC);
        boolean allGathered = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), firstCharacter(session));
            if (agent == null || agent.getMap() == null) {
                log.warn("KPQ exit treats missing member as outside: session={} member={}",
                        session.sessionId(), member.characterId());
                continue;
            }
            if (agent.getMapId() == AgentKpqDefinition.BONUS_MAP
                    || agent.getMapId() == AgentKpqDefinition.EXIT_MAP) {
                allOutside = false;
                if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                    member.observeBlocker("exit-npc", nowMs);
                    if (nowMs >= member.nextRetryAtMs()) {
                        member.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                        KPQ.runNpc(agent, AgentKpqDefinition.EXIT_NPC);
                    }
                    if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                        EventInstanceManager event = session.eventInstance();
                        if (event != null) event.exitPlayer(agent);
                    }
                } else {
                    narrate(session, narrator(session), "human-exit-" + member.characterId(),
                            agent.getName() + ", please use the exit NPC when you're ready.");
                }
                allGathered = false;
            } else if (agent.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
                allOutside = false;
            } else if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry agentEntry = entry(member.characterId());
                Point target = npcApproachPoint(session, member, agent, entryNpc, 601);
                if (!walkToPoint(agent, target, agentEntry, NPC_APPROACH_PX)) {
                    allGathered = false;
                }
            }
        }
        if (!allOutside || !allGathered) return;
        stopAll(session);
        narrate(session, narrator(session), "wait-outside",
                "Party is back in Kerning and waiting for the next command.");
        AgentKpqTerminationService.complete(session, nowMs);
    }

    private static Character leader(AgentKpqSession session) {
        return eventLeader(session);
    }

    private static void enterNextPortal(AgentKpqSession session, int nextStage, long nowMs) {
        int expectedMap = AgentKpqDefinition.STAGE_1_MAP + nextStage - 1;
        boolean allThere = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), leader(session));
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || agent.getMap() == null) {
                allThere = false;
                member.observeBlocker("portal-member-missing", nowMs);
                if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                    fail(session, "Party member " + member.characterId()
                            + " disappeared during the Stage " + nextStage + " transition", nowMs);
                    return;
                }
                continue;
            }
            if (agent.getMapId() != expectedMap) {
                allThere = false;
                member.observeBlocker("portal-stage-" + nextStage, nowMs);
                if (member.memberType() == AgentKpqMemberState.MemberType.AGENT && entry != null) {
                    Point portal = ACTIONS.portalPosition(agent, AgentKpqDefinition.NEXT_PORTAL_ID);
                    if (portal != null && near(agent.getPosition(), portal, 50)) {
                        if (nowMs >= member.nextRetryAtMs()) {
                            member.setNextRetryAtMs(nowMs + INTERACTION_RETRY_MS);
                            KPQ.enterPortal(agent, AgentKpqDefinition.NEXT_PORTAL_ID);
                        }
                    } else if (portal != null) {
                        ACTIONS.navigate(entry, portal, true);
                    }
                    if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS
                            && recoverAgentPortal(session, member, agent, nextStage, nowMs)) {
                        continue;
                    }
                    if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS * 2L) {
                        fail(session, "Portal recovery exhausted for " + agent.getName()
                                + " entering Stage " + nextStage, nowMs);
                        return;
                    }
                } else if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                    narrate(session, narrator(session),
                            "human-portal-" + nextStage + '-' + member.characterId(),
                            agent.getName() + ", please enter the next portal.");
                }
            } else if (member.memberType() == AgentKpqMemberState.MemberType.AGENT && entry != null) {
                member.clearBlocker();
                prePositionAfterEntry(session, member, agent, entry, nextStage, nowMs);
            }
        }
        if (allThere) transition(session, phaseForStage(nextStage), nowMs);
    }

    private static boolean recoverAgentPortal(AgentKpqSession session,
                                              AgentKpqMemberState member,
                                              Character agent,
                                              int nextStage,
                                              long nowMs) {
        EventInstanceManager event = session.eventInstance();
        int clearedStage = nextStage - 1;
        if (event == null || !"true".equals(event.getProperty(clearedStage + "stageclear"))) return false;
        MapleMap destination = event.getMapInstance(AgentKpqDefinition.STAGE_1_MAP + nextStage - 1);
        var spawnPortal = destination == null ? null : destination.getRandomPlayerSpawnpoint();
        if (destination == null || spawnPortal == null) return false;
        log.warn("KPQ portal recovery: session={} member={} stage={} attempts={} sourceMap={}",
                session.sessionId(), member.characterId(), nextStage,
                member.blockerAttempts(), agent.getMapId());
        AgentMapGatewayRuntime.map().changeMapNear(agent, destination, spawnPortal.getPosition());
        member.clearBlocker();
        session.markProgress(nowMs);
        return true;
    }

    private static List<AgentKpqMemberState> participants(AgentKpqSession session) {
        List<AgentKpqMemberState> participants = session.members().stream()
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (participants.size() >= 4) {
            participants.removeIf(member -> member.characterId() == session.eventLeaderId());
        }
        return AgentKpqPuzzleParticipantOrder.order(participants, session.seed());
    }

    static List<AgentKpqMemberState> assignFormation(List<AgentKpqMemberState> participants,
                                                     List<Integer> combination,
                                                     long seed,
                                                     int attemptId,
                                                     long nowMs) {
        List<AgentKpqMemberState> firstThree = participants.subList(0, 3);
        Set<Integer> retained = new LinkedHashSet<>();
        List<AgentKpqMemberState> movers = new ArrayList<>();
        for (AgentKpqMemberState member : firstThree) {
            if (combination.contains(member.assignedPosition())) retained.add(member.assignedPosition());
        }
        List<Integer> open = combination.stream().filter(position -> !retained.contains(position)).toList();
        int openIndex = 0;
        for (AgentKpqMemberState member : firstThree) {
            member.setRole(AgentKpqMemberState.Role.PUZZLE_PARTICIPANT);
            member.setStableSinceMs(0L);
            member.clearFidget();
            if (!retained.contains(member.assignedPosition())) {
                member.setAssignedPosition(open.get(openIndex++));
                movers.add(member);
                member.setActionNotBeforeMs(nowMs + 180L * member.partyNumber()
                        + Math.floorMod(seed + attemptId * 31L + member.characterId(), 220L));
            } else {
                member.setActionNotBeforeMs(nowMs);
            }
        }
        return List.copyOf(movers);
    }

    static String formationInstruction(List<AgentKpqMemberState> movers, Character eventAnchor) {
        List<AgentKpqMemberState> speakingOrder = movers.stream()
                .sorted(Comparator
                        .comparing((AgentKpqMemberState member) ->
                                member.memberType() != AgentKpqMemberState.MemberType.HUMAN)
                        .thenComparingInt(AgentKpqMemberState::partyNumber))
                .toList();
        List<String> assignments = new ArrayList<>();
        for (AgentKpqMemberState member : speakingOrder) {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            String name = character == null ? "Party member " + member.partyNumber() : character.getName();
            assignments.add(name + " -> " + member.assignedPosition());
        }
        return String.join("; ", assignments) + '.';
    }

    private static void transition(AgentKpqSession session, AgentKpqSession.Phase phase, long nowMs) {
        session.transition(phase, nowMs);
    }

    private static void advancePuzzleAttempt(AgentKpqSession session,
                                             Character leader,
                                             AgentKpqDefinition.CombinationStage definition,
                                             int attemptCount,
                                             int attempt,
                                             long nowMs) {
        int next = attempt + 1;
        session.setPuzzleCheckAtMs(0L);
        if (next >= attemptCount) {
            failExhaustedPuzzle(session, leader, definition, nowMs);
            return;
        }
        session.setAttemptIndex(next);
        session.markProgress(nowMs);
    }

    private static void failExhaustedPuzzle(AgentKpqSession session,
                                            Character leader,
                                            AgentKpqDefinition.CombinationStage definition,
                                            long nowMs) {
        List<String> positions = participants(session).stream().limit(3).map(member -> {
            Character character = memberCharacter(member.characterId(), leader);
            return member.characterId() + "->" + member.assignedPosition()
                    + "@" + (character == null ? "offline" : character.getPosition())
                    + ":grounded=" + (character != null && ACTIONS.grounded(character));
        }).toList();
        log.error("KPQ puzzle combinations exhausted: session={} stage={} answer={} attempt={} positions={}",
                session.sessionId(), definition.stageNumber(),
                KPQ.property(leader, definition.answerProperty()), session.attemptIndex(), positions);
        fail(session, "Stage " + definition.stageNumber()
                + " rejected every authored puzzle formation", nowMs);
    }

    static long puzzleCheckDelayMs(long seed, int stage, int attempt) {
        long variance = Math.max(0L, PUZZLE_CHECK_VARIANCE_MS);
        long offset = variance == 0L ? 0L
                : Math.floorMod(seed + stage * 997L + attempt * 131L, variance * 2L + 1L) - variance;
        return Math.max(0L, PUZZLE_CHECK_DELAY_MS + offset);
    }

    private static boolean blockedTooLong(AgentKpqSession session, String key, long nowMs) {
        session.observeBlocker(key, nowMs);
        return nowMs - session.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS;
    }

    private static void fail(AgentKpqSession session, String reason, long nowMs) {
        Character narrator = narrator(session);
        if (narrator != null) narrate(session, narrator, "failed", "KPQ stopped: " + reason + '.');
        log.error("KPQ session failure: session={} phase={} reason={} blocker={} blockerMs={} diagnostics={}",
                session.sessionId(), session.phase(), reason, session.blockerKey(),
                session.blockerSinceMs(), sessionDiagnostics(session));
        AgentKpqTerminationService.fail(session, reason, nowMs);
    }

    private static String sessionDiagnostics(AgentKpqSession session) {
        Character anchor = firstCharacter(session);
        List<String> members = session.members().stream().map(member -> {
            Character character = memberCharacter(member.characterId(), anchor);
            if (character == null) return member.characterId() + ":offline:" + member.blocker();
            int channel = AgentClientGatewayRuntime.clients().hasClient(character)
                    ? AgentClientGatewayRuntime.clients().channel(character) : -1;
            return character.getName() + '(' + member.characterId() + ")"
                    + ":type=" + member.memberType()
                    + ":channel=" + channel
                    + ":map=" + character.getMapId()
                    + ":pos=" + character.getPosition()
                    + ":hp=" + character.getHp()
                    + ":coupons=" + character.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false)
                    + ":passes=" + character.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false)
                    + ":shoes=" + character.getItemQuantity(AgentKpqDefinition.SQUISHY_SHOES, false)
                    + ":blocker=" + member.blocker();
        }).toList();
        EventInstanceManager event = session.eventInstance();
        return "event=" + (event == null ? "none" : event.getName()) + ",members=" + members;
    }

    private static void stopAll(AgentKpqSession session) {
        session.members().forEach(member -> {
            AgentRuntimeEntry entry = entry(member.characterId());
            if (entry != null) ACTIONS.stop(entry);
        });
    }

    private static void narrate(AgentKpqSession session, Character speaker, String key, String message) {
        if (speaker == null || !session.narrateOnce(key)) return;
        // Required KPQ narration is deliberately map-local. Cosmic's party-chat
        // broadcast excludes the sender and anyone observing outside the party,
        // which made successful Stage 1 target/progress reports look silent.
        AgentKpqDialogue.sayMapNow(speaker, message);
    }

    private static boolean allMembersOnMap(AgentKpqSession session, int mapId, Character eventAnchor) {
        return session.members().stream().map(member -> memberCharacter(member.characterId(), eventAnchor))
                .allMatch(character -> character != null && character.getMap() != null
                        && character.getMapId() == mapId);
    }

    private static boolean allMembersSameEvent(AgentKpqSession session, Character leader) {
        return session.members().stream()
                .map(member -> memberCharacter(member.characterId(), leader))
                .allMatch(character -> character != null && KPQ.sameEvent(leader, character));
    }

    private static Character character(int id) {
        AgentRuntimeEntry entry = entry(id);
        return entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
    }

    private static Character memberCharacter(int id, Character eventAnchor) {
        Character agent = character(id);
        if (agent != null) return agent;
        Character online = AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
        if (online != null) return online;
        if (eventAnchor == null) return null;
        return KPQ.eventMembers(eventAnchor).stream()
                .filter(member -> member.getId() == id).findFirst().orElse(null);
    }

    private static Character firstCharacter(AgentKpqSession session) {
        for (AgentKpqMemberState member : session.members()) {
            Character character = character(member.characterId());
            if (character != null) return character;
        }
        return null;
    }

    private static Character eventLeader(AgentKpqSession session) {
        return memberCharacter(session.eventLeaderId(), firstCharacter(session));
    }

    private static void bindKnownEvent(AgentKpqSession session, Character eventAnchor) {
        EventInstanceManager event = session.eventInstance();
        if (event == null) {
            for (AgentKpqMemberState member : session.members()) {
                Character character = memberCharacter(member.characterId(), eventAnchor);
                event = character == null ? null : KPQ.event(character);
                if (event != null) {
                    session.bindEventInstance(event);
                    break;
                }
            }
        }
    }

    private static boolean reconcileLiveParty(
            AgentKpqSession session, Character eventAnchor, long nowMs) {
        if (!insideEventPhase(session.phase()) || session.eventInstance() == null) return true;
        Character partyAnchor = null;
        server.agents.integration.AgentPartySnapshot party = null;
        for (AgentKpqMemberState member : session.members()) {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            var candidate = character == null ? null : AgentPartyGatewayRuntime.party().snapshot(character);
            if (candidate != null) {
                partyAnchor = character;
                party = candidate;
                break;
            }
        }
        if (partyAnchor == null || party == null) {
            fail(session, "The KPQ party was disbanded; no rewards were awarded", nowMs);
            return false;
        }
        Set<Integer> livePartyIds = party.members().stream()
                .filter(java.util.Objects::nonNull)
                .map(server.agents.integration.AgentPartyMemberSnapshot::id)
                .collect(java.util.stream.Collectors.toSet());
        List<AgentKpqMemberState> departed = session.members().stream()
                .filter(member -> !livePartyIds.contains(member.characterId()))
                .toList();
        EventInstanceManager event = session.eventInstance();
        AgentPartyQuestEngagement engagement =
                AgentPartyQuestEngagementRegistry.forOperator(session.operatorId());
        for (AgentKpqMemberState member : departed) {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            log.warn("KPQ member left the live party and was ejected: session={} member={} type={} "
                            + "phase={} remainingBefore={}",
                    session.sessionId(), member.characterId(), member.memberType(),
                    session.phase(), session.memberCount());
            if (character != null && KPQ.event(character) == event) event.exitPlayer(character);
            session.removeMember(member.characterId());
            AgentKpqSessionRegistry.unindexMember(session, member.characterId());
            if (engagement != null) {
                AgentPartyQuestEngagementRegistry.unindexMember(engagement, member.characterId());
            }
            if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                AgentPartyQuestLifecycleRuntime.recoverDetachedMember(member.characterId(), nowMs);
            }
        }
        if (session.memberCount() < 3) {
            fail(session, "The KPQ party fell below three members; no rewards were awarded", nowMs);
            return false;
        }
        int liveLeaderId = party.members().stream()
                .filter(java.util.Objects::nonNull)
                .filter(server.agents.integration.AgentPartyMemberSnapshot::leader)
                .mapToInt(server.agents.integration.AgentPartyMemberSnapshot::id)
                .findFirst().orElse(0);
        if (liveLeaderId <= 0 || session.member(liveLeaderId) == null) {
            fail(session, "The live KPQ party leader is not an event member; no rewards were awarded", nowMs);
            return false;
        }
        int coordinatorId = session.member(session.coordinatorAgentId()) != null
                ? session.coordinatorAgentId()
                : session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .mapToInt(AgentKpqMemberState::characterId).findFirst().orElse(0);
        if (liveLeaderId != session.eventLeaderId()
                || coordinatorId != session.coordinatorAgentId()) {
            if (coordinatorId <= 0) {
                fail(session, "No Agent coordinator remained after the KPQ leader changed", nowMs);
                return false;
            }
            Character newLeader = memberCharacter(liveLeaderId, partyAnchor);
            session.setLeadership(liveLeaderId, coordinatorId);
            if (newLeader != null) event.setLeader(newLeader);
            session.markProgress(nowMs);
            log.info("KPQ leadership reconciled to live party: session={} leader={} coordinator={} members={}",
                    session.sessionId(), liveLeaderId, coordinatorId, session.memberCount());
            return false;
        }
        return true;
    }

    private static boolean membersAlive(AgentKpqSession session, Character eventAnchor, long nowMs) {
        for (AgentKpqMemberState member : session.members()) {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            if (character == null) {
                member.observeBlocker("member-missing", nowMs);
                if (nowMs - member.blockerSinceMs() >= LOCAL_RECOVERY_TIMEOUT_MS) {
                    fail(session, "Party member " + member.characterId()
                            + " disconnected or left the channel", nowMs);
                    return false;
                }
            } else if (character.getHp() <= 0) {
                fail(session, character.getName() + " was defeated during KPQ", nowMs);
                return false;
            } else if ("member-missing".equals(member.blocker())) {
                member.clearBlocker();
            }
        }
        return true;
    }

    private static Character narrator(AgentKpqSession session) {
        Character caller = character(session.formationCallerId());
        return caller != null ? caller : character(session.coordinatorAgentId());
    }

    private static boolean isAgent(AgentKpqSession session, int characterId) {
        AgentKpqMemberState member = session.member(characterId);
        return member != null && member.memberType() == AgentKpqMemberState.MemberType.AGENT;
    }

    private static AgentRuntimeEntry entry(int id) {
        return AgentRuntimeRegistry.findByAgentCharacterId(id);
    }

    private static boolean near(Character first, Character second, int px) {
        return first != null && second != null && first.getMap() == second.getMap()
                && near(first.getPosition(), second.getPosition(), px);
    }

    private static boolean near(Point first, Point second, int px) {
        return first != null && second != null
                && Math.abs(first.x - second.x) <= px && Math.abs(first.y - second.y) <= px;
    }

    private static AgentKpqSession.Phase phaseForStage(int stage) {
        return switch (stage) {
            case 2 -> AgentKpqSession.Phase.STAGE_2;
            case 3 -> AgentKpqSession.Phase.STAGE_3;
            case 4 -> AgentKpqSession.Phase.STAGE_4;
            case 5 -> AgentKpqSession.Phase.STAGE_5;
            default -> throw new IllegalArgumentException("Invalid KPQ stage " + stage);
        };
    }

    private static boolean terminal(AgentKpqSession.Phase phase) {
        return phase == AgentKpqSession.Phase.COMPLETED
                || phase == AgentKpqSession.Phase.FAILED;
    }

    private static boolean insideEventPhase(AgentKpqSession.Phase phase) {
        return switch (phase) {
            case STAGE_1, STAGE_2, STAGE_3, STAGE_4, STAGE_5, CLAIMING_REWARDS -> true;
            default -> false;
        };
    }

    private static boolean walkToPoint(Character character, Point target, AgentRuntimeEntry entry, int radiusPx) {
        if (character == null || entry == null || target == null) {
            return false;
        }
        if (near(character.getPosition(), target, radiusPx)) {
            return true;
        }
        ACTIONS.navigate(entry, target, true);
        return false;
    }

    static Point npcApproachPoint(AgentKpqSession session,
                                  AgentKpqMemberState member,
                                  Character character,
                                  Point npcPoint,
                                  int purposeSalt) {
        if (session == null || member == null || character == null || npcPoint == null) return npcPoint;
        long mixed = session.seed() + member.characterId() * 131L
                + member.partyNumber() * 37L + purposeSalt * 977L;
        int leftOffset = 24 + Math.floorMod(mixed, 58);
        int x = npcPoint.x - leftOffset;
        // A minority of leader approaches may be just right of the NPC; members remain mostly left.
        if (member.characterId() == session.eventLeaderId() && Math.floorMod(mixed, 5L) == 0L) {
            x = npcPoint.x + 14 + Math.floorMod(mixed >>> 3, 20);
        }
        MapleMap map = character.getMap();
        Point grounded = ACTIONS.groundPoint(map, new Point(x, npcPoint.y));
        return grounded == null ? new Point(x, npcPoint.y) : grounded;
    }

    private static Set<Integer> stage1GrindTargets(Character agent) {
        Set<Integer> configured = ACTIONS.configuredMonsterSpawnIds(agent);
        return configured.isEmpty() ? STAGE_1_MOBS : configured;
    }

    private static List<MapItem> liveCouponDrops(MapleMap map) {
        if (map == null) return List.of();
        return map.getDroppedItems().stream()
                .filter(drop -> drop != null && !drop.isPickedUp()
                        && drop.getMeso() <= 0
                        && drop.getItemId() == AgentKpqDefinition.COUPON_ITEM)
                .toList();
    }

    private static List<MapItem> livePassDrops(MapleMap map) {
        if (map == null) return List.of();
        return map.getDroppedItems().stream()
                .filter(drop -> drop != null && !drop.isPickedUp()
                        && drop.getMeso() <= 0
                        && drop.getItemId() == AgentKpqDefinition.PASS_ITEM)
                .toList();
    }

    private static List<MapItem> liveSquishyShoesDrops(MapleMap map) {
        if (map == null) return List.of();
        return map.getDroppedItems().stream()
                .filter(drop -> drop != null && !drop.isPickedUp()
                        && drop.getMeso() <= 0
                        && drop.getItemId() == AgentKpqDefinition.SQUISHY_SHOES)
                .toList();
    }

    private static boolean handleSquishyShoes(
            AgentKpqSession session, Character eventAnchor, long nowMs) {
        if (session.squishyShoesResolved()) return false;
        Character stageFiveMember = session.members().stream()
                .map(member -> memberCharacter(member.characterId(), eventAnchor))
                .filter(java.util.Objects::nonNull)
                .filter(character -> character.getMapId() == AgentKpqDefinition.STAGE_5_MAP)
                .findFirst().orElse(null);
        MapleMap stageFiveMap = stageFiveMember == null ? null : stageFiveMember.getMap();
        List<MapItem> shoes = liveSquishyShoesDrops(stageFiveMap);
        if (shoes.isEmpty()) {
            if (session.squishyShoesSeenAtMs() > 0L) session.markSquishyShoesResolved();
            return false;
        }
        if (session.squishyShoesSeenAtMs() == 0L) session.setSquishyShoesSeenAtMs(nowMs);
        Character winner = memberCharacter(session.squishyShoesWinnerId(), eventAnchor);
        if (winner == null || winner.getMapId() != AgentKpqDefinition.STAGE_5_MAP) {
            int winnerId = chooseSquishyShoesWinner(session, eventAnchor);
            session.setSquishyShoesWinnerId(winnerId);
            winner = memberCharacter(winnerId, eventAnchor);
        }
        if (winner == null) return true;
        if (winner.getItemQuantity(AgentKpqDefinition.SQUISHY_SHOES, false) > 0) {
            session.markSquishyShoesResolved();
            return false;
        }
        boolean humanPriority = hasHumanPartyMember(session);
        if (squishyShoesHumanWindowActive(humanPriority,
                session.squishyShoesSeenAtMs(), nowMs, SQUISHY_SHOES_HUMAN_PRIORITY_MS)) {
            return true;
        }
        KPQ.lootNearby(winner, Set.of(AgentKpqDefinition.SQUISHY_SHOES));
        if (winner.getItemQuantity(AgentKpqDefinition.SQUISHY_SHOES, false) > 0
                || liveSquishyShoesDrops(stageFiveMap).isEmpty()) {
            session.markSquishyShoesResolved();
            return false;
        }
        if (nowMs - session.squishyShoesSeenAtMs()
                >= SQUISHY_SHOES_HUMAN_PRIORITY_MS + LOCAL_RECOVERY_TIMEOUT_MS) {
            log.warn("KPQ Squishy Shoes collection abandoned after bounded priority window: "
                            + "session={} winner={} map={} floorDrops={}",
                    session.sessionId(), winner.getId(), winner.getMapId(),
                    liveSquishyShoesDrops(stageFiveMap).size());
            session.markSquishyShoesResolved();
            return false;
        }
        return true;
    }

    static int chooseSquishyShoesWinner(AgentKpqSession session, Character eventAnchor) {
        List<AgentKpqMemberState> candidates = session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .filter(member -> {
                    Character character = memberCharacter(member.characterId(), eventAnchor);
                    return character != null && character.getMapId() == AgentKpqDefinition.STAGE_5_MAP;
                })
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .toList();
        if (candidates.size() > 1) {
            List<AgentKpqMemberState> nonLeaders = candidates.stream()
                    .filter(member -> member.characterId() != session.eventLeaderId()).toList();
            if (!nonLeaders.isEmpty()) candidates = nonLeaders;
        }
        List<AgentKpqMemberState> withoutShoes = candidates.stream().filter(member -> {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            return character != null
                    && character.getItemQuantity(AgentKpqDefinition.SQUISHY_SHOES, false) == 0
                    && character.hasEmptySlot(AgentKpqDefinition.SQUISHY_SHOES);
        }).toList();
        if (!withoutShoes.isEmpty()) candidates = withoutShoes;
        if (candidates.isEmpty()) return 0;
        int choice = Math.floorMod(session.seed() + session.attemptId() * 31L, candidates.size());
        return candidates.get(choice).characterId();
    }

    static boolean squishyShoesHumanWindowActive(
            boolean humanPresent, long dropSeenAtMs, long nowMs, long graceMs) {
        return humanPresent && dropSeenAtMs > 0L
                && nowMs - dropSeenAtMs < Math.max(0L, graceMs);
    }

    static boolean hasHumanPartyMember(AgentKpqSession session) {
        return session != null && session.members().stream()
                .anyMatch(member -> member.memberType() == AgentKpqMemberState.MemberType.HUMAN);
    }

    static int couponQuantityOnGround(List<MapItem> drops) {
        long total = 0L;
        if (drops == null) return 0;
        for (MapItem drop : drops) {
            if (drop == null || drop.isPickedUp() || drop.getMeso() > 0
                    || drop.getItemId() != AgentKpqDefinition.COUPON_ITEM) {
                continue;
            }
            total += drop.getItem() == null ? 1 : Math.max(1, drop.getItem().getQuantity());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static int passQuantityOnGround(List<MapItem> drops) {
        long total = 0L;
        if (drops == null) return 0;
        for (MapItem drop : drops) {
            if (drop == null || drop.isPickedUp() || drop.getMeso() > 0
                    || drop.getItemId() != AgentKpqDefinition.PASS_ITEM) {
                continue;
            }
            total += drop.getItem() == null ? 1 : Math.max(1, drop.getItem().getQuantity());
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    static boolean passDropConfirmed(int passesBeforeDrop, int passesAfterDrop) {
        return passesBeforeDrop > 0 && passesAfterDrop < passesBeforeDrop;
    }

    static boolean shouldBypassMissingPasses(
            boolean allDelivered,
            int leaderPasses,
            int needed,
            int floorPasses,
            long missingPassSinceMs,
            long nowMs,
            long graceMs) {
        return allDelivered
                && leaderPasses < needed
                && floorPasses == 0
                && missingPassSinceMs > 0L
                && nowMs - missingPassSinceMs >= graceMs;
    }

    static boolean recoverMissingStageOnePassesForTestCommand(
            AgentKpqSession session, Character leader, long nowMs) {
        if (session == null || leader == null) return false;
        int needed = session.memberCount() - 1;
        int leaderPasses = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        boolean allDelivered = session.members().stream()
                .filter(member -> member.characterId() != session.eventLeaderId())
                .allMatch(AgentKpqMemberState::passDelivered);
        if (!allDelivered || leaderPasses >= needed || !livePassDrops(leader.getMap()).isEmpty()) {
            return false;
        }
        bypassMissingStageOnePasses(
                session, leader, leaderPasses, needed, "test-complete-command", nowMs);
        return true;
    }

    static boolean submitReadyStageOne(
            AgentKpqSession session, Character leader, long nowMs, String trigger) {
        if (session == null || leader == null) return false;
        int needed = session.memberCount() - 1;
        int leaderPasses = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        if (leaderPasses < needed) return false;
        EventInstanceManager event = KPQ.event(leader);
        if (event != null && event.getLeaderId() != leader.getId()) {
            log.warn("KPQ event leader mismatch repaired before Stage 1 submit: session={} "
                            + "sessionLeader={} eventLeader={} partySize={} eventPlayers={}",
                    session.sessionId(), leader.getId(), event.getLeaderId(),
                    session.memberCount(), event.getPlayerCount());
            event.setLeader(leader);
        }
        boolean invoked = KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
        boolean cleared = "true".equals(KPQ.property(leader, "1stageclear"));
        if (cleared) {
            clearCouponLootObjectives(session);
            session.markProgress(nowMs);
        } else {
            log.warn("KPQ Stage 1 submit did not clear: session={} leader={}({}) passes={}/{} "
                            + "invoked={} trigger={} map={} eventLeader={} eventPlayers={} sessionMembers={}",
                    session.sessionId(), leader.getName(), leader.getId(), leaderPasses, needed,
                    invoked, trigger, leader.getMapId(), event == null ? -1 : event.getLeaderId(),
                    event == null ? -1 : event.getPlayerCount(), session.memberCount());
        }
        return cleared;
    }

    static boolean forceReadyStageOne(
            AgentKpqSession session, Character leader, long nowMs, String trigger) {
        if (session == null || leader == null) return false;
        EventInstanceManager event = KPQ.event(leader);
        int needed = session.memberCount() - 1;
        int leaderPasses = leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
        if (event == null || leaderPasses < needed) return false;
        log.warn("KPQ Stage 1 forced clear after authoritative NPC rejection: session={} "
                        + "leader={}({}) passes={}/{} trigger={} eventLeader={} eventPlayers={} sessionMembers={}",
                session.sessionId(), leader.getName(), leader.getId(), leaderPasses, needed, trigger,
                event.getLeaderId(), event.getPlayerCount(), session.memberCount());
        AgentInventoryGatewayRuntime.inventory().removeById(
                leader, InventoryType.ETC, AgentKpqDefinition.PASS_ITEM, leaderPasses, false, false);
        event.setProperty("1stageclear", "true");
        event.showClearEffect(true);
        event.linkToNextStage(1, "kpq", AgentKpqDefinition.STAGE_1_MAP);
        event.gridClear();
        clearCouponLootObjectives(session);
        session.setMissingPassSinceMs(0L);
        session.markProgress(nowMs);
        return true;
    }

    private static void bypassMissingStageOnePasses(
            AgentKpqSession session,
            Character leader,
            int leaderPasses,
            int needed,
            String trigger,
            long nowMs) {
        EventInstanceManager event = KPQ.event(leader);
        if (event == null) {
            log.error("KPQ Stage 1 missing-pass recovery failed: session={} leader={}({}) map={} "
                            + "leaderPasses={}/{} reason=no-event members={}",
                    session.sessionId(), leader.getName(), leader.getId(), leader.getMapId(),
                    leaderPasses, needed, stageOnePassDiagnostics(session, leader));
            return;
        }
        log.warn("KPQ Stage 1 missing-pass recovery: session={} leader={}({}) map={} "
                        + "leaderPasses={}/{} floorPasses=0 trigger={} graceMs={} members={}",
                session.sessionId(), leader.getName(), leader.getId(), leader.getMapId(),
                leaderPasses, needed, trigger, MISSING_PASS_GRACE_MS,
                stageOnePassDiagnostics(session, leader));
        event.setProperty("1stageclear", "true");
        event.showClearEffect(true);
        event.linkToNextStage(1, "kpq", AgentKpqDefinition.STAGE_1_MAP);
        event.gridClear();
        session.setMissingPassSinceMs(0L);
        session.markProgress(nowMs);
    }

    private static void bypassMissingStageFivePasses(
            AgentKpqSession session, Character leader, int leaderPasses, long nowMs) {
        EventInstanceManager event = session.eventInstance();
        if (event == null) {
            fail(session, "Stage 5 passes disappeared and the event instance is unavailable", nowMs);
            return;
        }
        log.warn("KPQ Stage 5 missing-pass recovery: session={} leader={}({}) map={} "
                        + "leaderPasses={}/10 floorPasses=0 mobs=0 graceMs={} members={}",
                session.sessionId(), leader.getName(), leader.getId(), leader.getMapId(),
                leaderPasses, STAGE5_MISSING_PASS_GRACE_MS,
                stageOnePassDiagnostics(session, leader));
        event.setProperty("5stageclear", "true");
        event.showClearEffect(true);
        event.linkToNextStage(5, "kpq", AgentKpqDefinition.STAGE_5_MAP);
        event.clearPQ();
        session.setMissingPassSinceMs(0L);
        session.markProgress(nowMs);
    }

    private static String stageOnePassDiagnostics(AgentKpqSession session, Character eventAnchor) {
        List<String> members = new ArrayList<>();
        for (AgentKpqMemberState member : session.members()) {
            Character character = memberCharacter(member.characterId(), eventAnchor);
            int inventoryPasses = character == null
                    ? -1 : character.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false);
            int grid = character == null ? -2 : KPQ.playerGrid(character);
            members.add(member.characterId() + ":" + member.memberType()
                    + ":created=" + member.passCreated()
                    + ":delivered=" + member.passDelivered()
                    + ":inventory=" + inventoryPasses
                    + ":grid=" + grid);
        }
        return members.toString();
    }

    private static int remainingPartyCouponNeed(AgentKpqSession session, Character eventAnchor) {
        long required = 0L;
        long held = 0L;
        for (AgentKpqMemberState member : session.members()) {
            if (member.passCreated() || member.couponTarget() <= 0) continue;
            Character character = memberCharacter(member.characterId(), eventAnchor);
            if (character == null || character.getMapId() != AgentKpqDefinition.STAGE_1_MAP) continue;
            required += member.couponTarget();
            held += Math.max(0,
                    character.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false));
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, required - held));
    }

    private static boolean allKnownCouponNeedsCovered(AgentKpqSession session, Character eventAnchor) {
        for (AgentKpqMemberState member : session.members()) {
            if (member.characterId() == session.eventLeaderId()) continue;
            Character character = memberCharacter(member.characterId(), eventAnchor);
            if (member.memberType() == AgentKpqMemberState.MemberType.HUMAN) {
                if (!member.passCreated()) return false;
                continue;
            }
            if (!member.questionRequested() || member.couponTarget() <= 0 || character == null) return false;
            if (!member.passCreated()
                    && character.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false)
                    < member.couponTarget()) return false;
        }
        return true;
    }

    private static boolean couponSweepCollectorEligible(
            AgentKpqSession session, Character eventAnchor, int characterId) {
        AgentKpqMemberState member = session.member(characterId);
        if (member == null || member.memberType() != AgentKpqMemberState.MemberType.AGENT
                || member.characterId() == session.eventLeaderId()
                || member.passCreated() || member.couponTarget() <= 0) {
            return false;
        }
        Character collector = memberCharacter(characterId, eventAnchor);
        return collector != null
                && collector.getMapId() == AgentKpqDefinition.STAGE_1_MAP
                && collector.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false)
                < member.couponTarget();
    }

    private static int nextCouponSweepCollectorId(
            AgentKpqSession session, Character eventAnchor, int currentCollectorId) {
        int currentPartyNumber = session.member(currentCollectorId) == null
                ? 0 : session.member(currentCollectorId).partyNumber();
        List<AgentKpqMemberState> collectors = session.members().stream()
                .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .filter(member -> member.characterId() != session.eventLeaderId())
                .filter(member -> couponSweepCollectorEligible(
                        session, eventAnchor, member.characterId()))
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .toList();
        if (collectors.isEmpty()) return 0;
        for (AgentKpqMemberState member : collectors) {
            if (member.partyNumber() > currentPartyNumber) return member.characterId();
        }
        return collectors.get(0).characterId();
    }

    private static void syncCouponLootObjectives(
            AgentKpqSession session,
            Character eventAnchor,
            List<MapItem> floorCoupons,
            int sweepCollectorId) {
        for (AgentKpqMemberState member : session.members()) {
            if (member.memberType() != AgentKpqMemberState.MemberType.AGENT) continue;
            AgentRuntimeEntry memberEntry = entry(member.characterId());
            if (memberEntry == null) continue;
            if (member.characterId() != sweepCollectorId) {
                AgentGrindLootStateRuntime.clearObjectiveLootTarget(memberEntry);
                continue;
            }
            Character collector = memberCharacter(member.characterId(), eventAnchor);
            if (collector == null) continue;
            MapItem current = AgentGrindLootStateRuntime.grindLootTarget(memberEntry);
            if (AgentGrindLootStateRuntime.hasObjectiveLootTarget(memberEntry)
                    && floorCoupons.contains(current)) {
                continue;
            }
            MapItem closest = floorCoupons.stream()
                    .min(Comparator
                            .comparingDouble((MapItem drop) ->
                                    drop.getPosition().distanceSq(collector.getPosition()))
                            .thenComparingInt(MapItem::getObjectId))
                    .orElse(null);
            AgentGrindLootStateRuntime.setObjectiveLootTarget(memberEntry, closest);
        }
    }

    private static void clearCouponLootObjectives(AgentKpqSession session) {
        if (session == null) return;
        for (AgentKpqMemberState member : session.members()) {
            AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry(member.characterId()));
        }
        session.setCouponSweepCollectorId(0);
        session.setCouponSweepStartedAtMs(0L);
    }

    private static void grindStageFive(AgentRuntimeEntry entry, Character agent) {
        Set<Integer> configured = ACTIONS.configuredMonsterSpawnIds(agent);
        if (configured.isEmpty()) {
            configured = new LinkedHashSet<>(STAGE_5_NORMAL_MOBS);
            configured.addAll(STAGE_5_BOSS_MOBS);
        }
        int normalAlive = ACTIONS.liveMonsterCount(agent, STAGE_5_NORMAL_MOBS);
        int bossAlive = ACTIONS.liveMonsterCount(agent, STAGE_5_BOSS_MOBS);
        if (normalAlive > 0) {
            ACTIONS.grind(entry, STAGE_5_NORMAL_MOBS, configured);
        } else if (bossAlive > 0) {
            ACTIONS.grind(entry, STAGE_5_BOSS_MOBS, configured);
        } else {
            ACTIONS.grind(entry, configured);
        }
    }

    private static Point formationTarget(AgentKpqDefinition.CombinationStage definition,
                                         AgentKpqMemberState member,
                                         long seed) {
        Rectangle area = definition.positions().get(member.assignedPosition() - 1);
        long mixed = seed + definition.stageNumber() * 997L
                + member.partyNumber() * 131L + member.assignedPosition() * 43L;
        if (definition.holdMode() == AgentKpqDefinition.HoldMode.ROPE) {
            int margin = Math.min(28, Math.max(8, area.height / 5));
            int range = Math.max(1, area.height - margin * 2);
            return new Point((int) area.getCenterX(), area.y + margin + Math.floorMod(mixed, range));
        }
        int margin = definition.stageNumber() == 4 ? 6 : 24;
        int range = Math.max(1, area.width - margin * 2);
        return new Point(area.x + margin + Math.floorMod(mixed, range), (int) area.getCenterY());
    }

    static boolean puzzlePositionReady(AgentKpqDefinition.CombinationStage definition,
                                       int assignedPosition,
                                       Point current,
                                       boolean grounded) {
        return definition.contains(assignedPosition, current)
                && (definition.holdMode() != AgentKpqDefinition.HoldMode.GROUNDED || grounded);
    }

    private static void prePositionAfterEntry(AgentKpqSession session,
                                              AgentKpqMemberState member,
                                              Character agent,
                                              AgentRuntimeEntry entry,
                                              int nextStage,
                                              long nowMs) {
        member.beginStageMovement(nextStage,
                nowMs + stageMovementDelayMs(session.seed(), member, nextStage));
        if (nowMs < member.stageMovementNotBeforeMs()) {
            ACTIONS.stop(entry);
            return;
        }
        if (nextStage == 5) {
            grindStageFive(entry, agent);
            return;
        }
        AgentKpqDefinition.CombinationStage definition = AgentKpqDefinition.combinationStage(nextStage);
        List<AgentKpqMemberState> participants = participants(session);
        int participantIndex = participants.subList(0, Math.min(3, participants.size())).indexOf(member);
        if (participantIndex < 0) {
            Point cloto = ACTIONS.npcPosition(agent, AgentKpqDefinition.CLOTO_NPC);
            Point approach = npcApproachPoint(session, member, agent, cloto, 700 + nextStage);
            walkToPoint(agent, approach, entry, NPC_APPROACH_PX);
            return;
        }
        List<Integer> initial = AgentKpqCombinationOrder.forPositionCount(definition.positions().size()).getFirst();
        member.setAssignedPosition(initial.get(participantIndex));
        ACTIONS.navigate(entry, formationTarget(definition, member, session.seed()), true);
    }

    private static void initializeStageMovementDelays(
            AgentKpqSession session, int stage, long nowMs) {
        for (AgentKpqMemberState member : session.members()) {
            if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                member.beginStageMovement(stage,
                        nowMs + stageMovementDelayMs(session.seed(), member, stage));
            }
        }
    }

    private static boolean stageMovementDelayed(
            AgentKpqSession session,
            AgentKpqMemberState member,
            AgentRuntimeEntry entry,
            int stage,
            long nowMs) {
        member.beginStageMovement(stage,
                nowMs + stageMovementDelayMs(session.seed(), member, stage));
        if (nowMs >= member.stageMovementNotBeforeMs()) return false;
        ACTIONS.stop(entry);
        return true;
    }

    static long stageMovementDelayMs(long seed, AgentKpqMemberState member, int stage) {
        return 140L + 170L * Math.max(1, member.partyNumber())
                + Math.floorMod(seed + member.characterId() * 43L + stage * 101L, 180L);
    }

    private static void moveTowardStageOneExit(
            AgentKpqSession session,
            AgentKpqMemberState member,
            Character agent,
            AgentRuntimeEntry entry) {
        Point portal = ACTIONS.portalPosition(agent, AgentKpqDefinition.NEXT_PORTAL_ID);
        if (portal == null) {
            ACTIONS.stop(entry);
            return;
        }
        long mixed = session.seed() + member.characterId() * 71L;
        Point target = ACTIONS.groundPoint(agent.getMap(),
                new Point(portal.x - 24 - (int) Math.floorMod(mixed, 54L), portal.y));
        walkToPoint(agent, target == null ? portal : target, entry, NPC_APPROACH_PX);
    }

    private static void reportCouponProgress(AgentKpqSession session,
                                            Character speaker,
                                            AgentKpqMemberState member,
                                            int have) {
        int target = member.couponTarget();
        if (target <= 0 || speaker == null) return;
        List<Integer> crossed = crossedCouponMilestones(member.couponMilestone(), have, target);
        if (crossed.isEmpty()) return;
        int milestone = crossed.getLast();
        narrate(session, speaker, "coupon-" + speaker.getId() + '-' + milestone,
                "Coupons: " + have + "/" + target + '.');
        member.setCouponMilestone(milestone);
    }

    static List<Integer> crossedCouponMilestones(int previous, int have, int target) {
        if (target <= 0 || have <= 0) return List.of();
        int percent = Math.min(100, (int) ((have * 100L) / target));
        List<Integer> crossed = new ArrayList<>();
        for (int milestone : List.of(20, 50, 90, 100)) {
            if (milestone > previous && percent >= milestone) crossed.add(milestone);
        }
        if (have >= target && previous < 100 && !crossed.contains(100)) crossed.add(100);
        return List.copyOf(crossed);
    }

    private static void reportLeaderPassProgress(AgentKpqSession session,
                                                 Character leader,
                                                 AgentKpqMemberState leaderState,
                                                 int have,
                                                 int needed) {
        if (leader == null || leaderState == null || needed <= 0) return;
        int capped = Math.min(have, needed);
        for (int count = leaderState.reportedPassCount() + 1; count <= capped; count++) {
            narrate(session, leader, "leader-pass-" + count,
                    "Passes: " + count + "/" + needed + (count == needed ? " done" : ""));
        }
        leaderState.setReportedPassCount(capped);
    }
}
