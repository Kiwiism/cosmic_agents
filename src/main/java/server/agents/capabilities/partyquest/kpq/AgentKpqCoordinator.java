package server.agents.capabilities.partyquest.kpq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Party-level KPQ state machine. It uses ordinary NPC scripts, portals, combat, drops, and loot. */
final class AgentKpqCoordinator {
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway KPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final Set<Integer> STAGE_1_MOBS = Set.of(9_300_001);
    private static final long FORMATION_STABLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.FORMATION_STABLE_MS");
    private static final long PHASE_TIMEOUT_MS = 8 * 60_000L;
    private static final int NEAR_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.kpq.AgentKpqCoordinator.NEAR_PX");

    private AgentKpqCoordinator() {
    }

    static void tick(AgentKpqSession session, long nowMs) {
        if (terminal(session.phase())) return;
        if (nowMs - session.lastProgressAtMs() > PHASE_TIMEOUT_MS) {
            fail(session, "No KPQ progress for eight minutes", nowMs);
            return;
        }
        Character leader = character(session.eventLeaderId());
        if (leader == null) {
            fail(session, "The event leader is no longer active", nowMs);
            return;
        }
        switch (session.phase()) {
            case RECRUITING -> { }
            case PREPARING -> prepare(session, leader, nowMs);
            case ENTERING -> enter(session, leader, nowMs);
            case STAGE_1 -> stage1(session, leader, nowMs);
            case STAGE_2 -> combinationStage(session, leader, 2, nowMs);
            case STAGE_3 -> combinationStage(session, leader, 3, nowMs);
            case STAGE_4 -> combinationStage(session, leader, 4, nowMs);
            case STAGE_5 -> stage5(session, leader, nowMs);
            case CLAIMING_REWARDS -> claimRewards(session, nowMs);
            case EXITING -> exit(session, nowMs);
            default -> { }
        }
    }

    private static void prepare(AgentKpqSession session, Character leader, long nowMs) {
        if (session.memberCount() != session.requestedPartySize()) return;
        if (!allAgentsOnMap(session, AgentKpqDefinition.RECRUIT_MAP)) return;
        if (session.readyAtMs() == 0L) {
            session.setReadyAtMs(nowMs + AgentKpqRecruitmentPolicy.preparationDelayMs(
                    session.memberCount(), session.seed()));
            narrate(session, leader, "ready", "Party ready. Starting KPQ shortly.");
        }
        if (nowMs >= session.readyAtMs()) transition(session, AgentKpqSession.Phase.ENTERING, nowMs);
    }

    private static void enter(AgentKpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentKpqDefinition.STAGE_1_MAP && KPQ.event(leader) != null) {
            if (allAgentsSameEvent(session, leader)) {
                if (session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION
                        && session.requestedCheckpointStage() > 1) {
                    AgentKpqCheckpointService.apply(session, leader, session.requestedCheckpointStage(), nowMs);
                    return;
                }
                narrate(session, leader, "entered", "We're in. Ask Cloto for your coupon number.");
                transition(session, AgentKpqSession.Phase.STAGE_1, nowMs);
            }
            return;
        }
        if (leader.getMapId() != AgentKpqDefinition.RECRUIT_MAP) return;
        KPQ.runNpc(leader, AgentKpqDefinition.ENTRY_NPC, 0);
    }

    private static void stage1(AgentKpqSession session, Character leader, long nowMs) {
        if ("true".equals(KPQ.property(leader, "1stageclear"))) {
            enterNextPortal(session, 2, nowMs);
            return;
        }
        boolean allDelivered = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (member.memberType() == AgentKpqMemberState.MemberType.HUMAN) {
                if (member.characterId() != session.eventLeaderId()) allDelivered = false;
                continue;
            }
            if (agent == null || entry == null || agent.getMapId() != AgentKpqDefinition.STAGE_1_MAP) continue;
            if (member.characterId() == session.eventLeaderId()) {
                member.setRole(AgentKpqMemberState.Role.EVENT_LEADER);
                KPQ.lootNearby(agent, Set.of(AgentKpqDefinition.PASS_ITEM));
                ACTIONS.grind(entry, STAGE_1_MOBS);
                continue;
            }
            if (!member.questionRequested()) {
                long questionAt = session.phaseEnteredAtMs() + 300L * member.partyNumber()
                        + Math.floorMod(session.seed() + member.characterId(), 350L);
                if (nowMs < questionAt) {
                    allDelivered = false;
                    continue;
                }
                KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                int target = AgentKpqDefinition.couponTarget(KPQ.playerGrid(agent));
                if (target > 0) {
                    member.markQuestionRequested();
                    member.setCouponTarget(target);
                    member.setRole(AgentKpqMemberState.Role.COUPON_COLLECTOR);
                    narrate(session, agent, "need-" + agent.getId(), "I need " + target + " coupons.");
                }
            }
            if (!member.passCreated()) {
                allDelivered = false;
                int have = agent.getItemQuantity(AgentKpqDefinition.COUPON_ITEM, false);
                if (member.couponTarget() > 0 && have >= member.couponTarget()) {
                    ACTIONS.stop(entry);
                    if (have > member.couponTarget()) {
                        AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                                AgentKpqDefinition.COUPON_ITEM, (short) (have - member.couponTarget()));
                        continue;
                    }
                    KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                    if (agent.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) > 0) {
                        member.markPassCreated();
                        member.setRole(AgentKpqMemberState.Role.PASS_DELIVERER);
                        narrate(session, agent, "pass-" + agent.getId(), "Pass ready; bringing it to leader.");
                        session.markProgress(nowMs);
                    }
                } else {
                    ACTIONS.grind(entry, STAGE_1_MOBS);
                }
            }
            if (member.passCreated() && !member.passDelivered()) {
                allDelivered = false;
                if (near(agent, leader, NEAR_PX)) {
                    ACTIONS.stop(entry);
                    if (AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                            AgentKpqDefinition.PASS_ITEM, (short) 1)) {
                        member.markPassDelivered();
                        member.setRole(AgentKpqMemberState.Role.COMBAT_HELPER);
                        narrate(session, agent, "delivered-" + agent.getId(), "Pass dropped for leader.");
                        session.markProgress(nowMs);
                    }
                } else {
                    ACTIONS.navigate(entry, leader.getPosition(), false);
                }
            } else if (member.passDelivered()) {
                ACTIONS.grind(entry, STAGE_1_MOBS);
            }
        }
        KPQ.lootNearby(leader, Set.of(AgentKpqDefinition.PASS_ITEM));
        int needed = session.memberCount() - 1;
        if (allDelivered || leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) >= needed) {
            ACTIONS.stop(entry(leader.getId()));
            KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
        }
    }

    private static void combinationStage(AgentKpqSession session, Character leader, int stage, long nowMs) {
        AgentKpqDefinition.CombinationStage definition = AgentKpqDefinition.combinationStage(stage);
        if ("true".equals(KPQ.property(leader, definition.clearProperty()))) {
            enterNextPortal(session, stage + 1, nowMs);
            return;
        }
        if (KPQ.property(leader, definition.answerProperty()) == null) {
            KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
            return;
        }
        List<AgentKpqMemberState> participants = participants(session);
        if (participants.size() < 3) {
            fail(session, "Stage " + stage + " needs three controllable puzzle participants", nowMs);
            return;
        }
        List<List<Integer>> order = AgentKpqCombinationOrder.forPositionCount(definition.positions().size());
        int attempt = Math.min(session.attemptIndex(), order.size() - 1);
        List<Integer> combination = order.get(attempt);
        if (!combination.equals(session.combination())) {
            assignFormation(participants, combination, session.seed(), session.attemptId(), nowMs);
            session.setCombination(combination);
            session.nextAttemptId();
            narrate(session, leader, "s" + stage + "-a" + attempt,
                    "Stage " + stage + " try " + (attempt + 1) + ": " + combination + ".");
        }
        boolean stable = true;
        for (AgentKpqMemberState participant : participants.subList(0, 3)) {
            Character agent = memberCharacter(participant.characterId(), leader);
            AgentRuntimeEntry entry = entry(participant.characterId());
            if (agent == null) {
                stable = false;
                continue;
            }
            Point target = definition.center(participant.assignedPosition());
            if (nowMs < participant.actionNotBeforeMs()) {
                stable = false;
                continue;
            }
            if (!definition.contains(participant.assignedPosition(), agent.getPosition())
                    || (definition.holdMode() == AgentKpqDefinition.HoldMode.GROUNDED && !ACTIONS.grounded(agent))) {
                participant.setStableSinceMs(0L);
                if (entry != null) {
                    ACTIONS.navigate(entry, target, true);
                } else {
                    narrate(session, leader, "human-s" + stage + "-a" + attempt + "-p"
                                    + participant.partyNumber(),
                            "Member " + participant.partyNumber() + ": move to position "
                                    + participant.assignedPosition() + ".");
                }
                stable = false;
            } else if (participant.stableSinceMs() == 0L) {
                if (entry != null) ACTIONS.stop(entry);
                participant.setStableSinceMs(nowMs);
                stable = false;
            } else if (nowMs - participant.stableSinceMs() < FORMATION_STABLE_MS) {
                stable = false;
            }
        }
        if (!stable) return;
        KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
        if (!"true".equals(KPQ.property(leader, definition.clearProperty()))) {
            session.setAttemptIndex(attempt + 1);
            session.markProgress(nowMs);
        }
    }

    private static void stage5(AgentKpqSession session, Character leader, long nowMs) {
        if ("true".equals(KPQ.property(leader, "5stageclear"))) {
            stopAll(session);
            transition(session, AgentKpqSession.Phase.CLAIMING_REWARDS, nowMs);
            return;
        }
        if (session.squishyShoesWinnerId() == 0) {
            List<AgentKpqMemberState> members = session.members().stream()
                    .filter(member -> member.memberType() == AgentKpqMemberState.MemberType.AGENT).toList();
            int winner = Math.floorMod(session.seed(), members.size());
            session.setSquishyShoesWinnerId(members.get(winner).characterId());
        }
        for (AgentKpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = entry(member.characterId());
            Character agent = character(member.characterId());
            if (entry == null || agent == null) continue;
            member.setRole(member.characterId() == session.eventLeaderId()
                    ? AgentKpqMemberState.Role.STAGE5_PASS_COLLECTOR
                    : AgentKpqMemberState.Role.COMBAT_HELPER);
            ACTIONS.grind(entry, ACTIONS.configuredMonsterSpawnIds(agent));
            if (member.characterId() == session.squishyShoesWinnerId()) {
                KPQ.lootNearby(agent, Set.of(AgentKpqDefinition.SQUISHY_SHOES));
            }
        }
        KPQ.lootNearby(leader, Set.of(AgentKpqDefinition.PASS_ITEM));
        if (leader.getItemQuantity(AgentKpqDefinition.PASS_ITEM, false) >= 10) {
            KPQ.runNpc(leader, AgentKpqDefinition.CLOTO_NPC);
        }
    }

    private static void claimRewards(AgentKpqSession session, long nowMs) {
        boolean allInBonus = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), firstCharacter(session));
            if (agent == null) continue;
            if (agent.getMapId() == AgentKpqDefinition.STAGE_5_MAP) {
                allInBonus = false;
                if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                    KPQ.runNpc(agent, AgentKpqDefinition.CLOTO_NPC);
                }
            } else if (agent.getMapId() == AgentKpqDefinition.BONUS_MAP) {
                member.markRewardClaimed();
            } else {
                allInBonus = false;
            }
        }
        if (allInBonus) transition(session, AgentKpqSession.Phase.EXITING, nowMs);
    }

    private static void exit(AgentKpqSession session, long nowMs) {
        boolean allOutside = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = memberCharacter(member.characterId(), firstCharacter(session));
            if (agent == null) continue;
            if (agent.getMapId() == AgentKpqDefinition.BONUS_MAP
                    || agent.getMapId() == AgentKpqDefinition.EXIT_MAP) {
                allOutside = false;
                if (member.memberType() == AgentKpqMemberState.MemberType.AGENT) {
                    KPQ.runNpc(agent, AgentKpqDefinition.EXIT_NPC);
                }
            } else if (agent.getMapId() != AgentKpqDefinition.RECRUIT_MAP) {
                allOutside = false;
            }
        }
        if (!allOutside) return;
        stopAll(session);
        if (session.mode() == AgentKpqSession.Mode.TEST_OBSERVATION) {
            transition(session, AgentKpqSession.Phase.WAITING_OUTSIDE_TEST, nowMs);
        } else {
            transition(session, AgentKpqSession.Phase.COMPLETED, nowMs);
            AgentKpqSessionRegistry.remove(session);
        }
    }

    private static void enterNextPortal(AgentKpqSession session, int nextStage, long nowMs) {
        int expectedMap = AgentKpqDefinition.STAGE_1_MAP + nextStage - 1;
        boolean allThere = true;
        for (AgentKpqMemberState member : session.members()) {
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            if (agent.getMapId() != expectedMap) {
                allThere = false;
                Point portal = ACTIONS.portalPosition(agent, AgentKpqDefinition.NEXT_PORTAL_ID);
                if (portal != null && near(agent.getPosition(), portal, 50)) {
                    KPQ.enterPortal(agent, AgentKpqDefinition.NEXT_PORTAL_ID);
                } else if (portal != null) {
                    ACTIONS.navigate(entry, portal, true);
                }
            }
        }
        if (allThere) transition(session, phaseForStage(nextStage), nowMs);
    }

    private static List<AgentKpqMemberState> participants(AgentKpqSession session) {
        List<AgentKpqMemberState> agents = session.members().stream()
                .sorted(Comparator.comparingInt(AgentKpqMemberState::partyNumber))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (agents.size() >= 4) agents.removeIf(member -> member.characterId() == session.eventLeaderId());
        return agents;
    }

    private static void assignFormation(List<AgentKpqMemberState> participants,
                                        List<Integer> combination,
                                        long seed,
                                        int attemptId,
                                        long nowMs) {
        List<AgentKpqMemberState> firstThree = participants.subList(0, 3);
        Set<Integer> retained = new LinkedHashSet<>();
        for (AgentKpqMemberState member : firstThree) {
            if (combination.contains(member.assignedPosition())) retained.add(member.assignedPosition());
        }
        List<Integer> open = combination.stream().filter(position -> !retained.contains(position)).toList();
        int openIndex = 0;
        for (AgentKpqMemberState member : firstThree) {
            member.setRole(AgentKpqMemberState.Role.PUZZLE_PARTICIPANT);
            if (!retained.contains(member.assignedPosition())) {
                member.setAssignedPosition(open.get(openIndex++));
                member.setActionNotBeforeMs(nowMs + 180L * member.partyNumber()
                        + Math.floorMod(seed + attemptId * 31L + member.characterId(), 220L));
            }
        }
    }

    private static void transition(AgentKpqSession session, AgentKpqSession.Phase phase, long nowMs) {
        session.transition(phase, nowMs);
    }

    private static void fail(AgentKpqSession session, String reason, long nowMs) {
        Character leader = character(session.eventLeaderId());
        if (leader != null) narrate(session, leader, "failed", "KPQ stopped: " + reason + '.');
        stopAll(session);
        session.fail(reason, nowMs);
    }

    private static void stopAll(AgentKpqSession session) {
        session.members().forEach(member -> {
            AgentRuntimeEntry entry = entry(member.characterId());
            if (entry != null) ACTIONS.stop(entry);
        });
    }

    private static void narrate(AgentKpqSession session, Character speaker, String key, String message) {
        if (!session.narrateOnce(key)) return;
        if (speaker.getMapId() >= AgentKpqDefinition.STAGE_1_MAP
                && speaker.getMapId() <= AgentKpqDefinition.BONUS_MAP) {
            AgentDialogueTransportRuntime.sayPartyNow(speaker, message);
        } else {
            AgentDialogueTransportRuntime.sayMapNow(speaker, message);
        }
    }

    private static boolean allAgentsOnMap(AgentKpqSession session, int mapId) {
        return session.members().stream().filter(m -> m.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .map(m -> character(m.characterId())).allMatch(c -> c != null && c.getMapId() == mapId);
    }

    private static boolean allAgentsSameEvent(AgentKpqSession session, Character leader) {
        return session.members().stream().filter(m -> m.memberType() == AgentKpqMemberState.MemberType.AGENT)
                .map(m -> character(m.characterId())).allMatch(c -> c != null && KPQ.sameEvent(leader, c));
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
        return phase == AgentKpqSession.Phase.WAITING_OUTSIDE_TEST
                || phase == AgentKpqSession.Phase.COMPLETED
                || phase == AgentKpqSession.Phase.FAILED;
    }
}
