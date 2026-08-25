package server.agents.capabilities.partyquest.lpq;

import client.BuffStat;
import client.Character;
import client.inventory.InventoryType;
import constants.skills.Rogue;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.Portal;
import server.maps.Reactor;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** LPQ-only coordinator that advances the authored NPC, portal, reactor, combat, and loot flow. */
final class AgentLpqCoordinator {
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway LPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final long PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.PHASE_TIMEOUT_MS");
    private static final long INTERACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.INTERACTION_RETRY_MS");
    private static final long ROOM_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.ROOM_LEASE_MS");
    private static final int INTERACTION_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.lpq.AgentLpqCoordinator.INTERACTION_RADIUS_PX");
    private static final List<Integer> MAGIC_ROOMS = List.of(922_010_401, 922_010_402, 922_010_403);
    private static final List<Integer> PHYSICAL_ROOMS = List.of(922_010_404, 922_010_405);

    private AgentLpqCoordinator() { }

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
                case EXITING -> AgentLpqTerminationService.complete(session, nowMs);
                default -> { }
            }
        }
    }

    private static void synchronizePhase(AgentLpqSession session, Character leader, long nowMs) {
        int mapId = leader.getMapId();
        if (mapId == AgentLpqDefinition.CLEAR_MAP) {
            session.transition(AgentLpqSession.Phase.BONUS, nowMs);
            return;
        }
        if (mapId == AgentLpqDefinition.BONUS_MAP) {
            session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, nowMs);
            return;
        }
        int stage = AgentLpqDefinition.stageNumber(mapId);
        if (stage >= 1 && stage <= 9) {
            AgentLpqSession.Phase expected = AgentLpqSession.Phase.valueOf("STAGE_" + stage);
            if (session.phase() != expected) session.transition(expected, nowMs);
        }
    }

    private static void prepare(AgentLpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentLpqDefinition.stage(1).mapId() && LPQ.event(leader) != null) {
            session.transition(AgentLpqSession.Phase.ENTERING, nowMs);
            return;
        }
        if (leader.getMapId() != AgentLpqDefinition.RECRUIT_MAP || !isAgent(leader)) return;
        AgentRuntimeEntry entry = entry(leader.getId());
        Point npc = ACTIONS.npcPosition(leader, AgentLpqDefinition.ENTRY_NPC);
        if (entry == null || npc == null) return;
        if (!near(leader.getPosition(), npc, INTERACTION_RADIUS)) { ACTIONS.navigate(entry, npc, true); return; }
        if (LPQ.runNpc(leader, AgentLpqDefinition.ENTRY_NPC, 0)) session.markProgress(nowMs);
    }

    private static void enter(AgentLpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() != AgentLpqDefinition.stage(1).mapId() || LPQ.event(leader) == null) return;
        session.bindEventInstance(LPQ.event(leader));
        if (session.members().stream().allMatch(member -> {
            Character participant = character(member.characterId());
            return participant != null && LPQ.sameEvent(leader, participant);
        })) session.transition(AgentLpqSession.Phase.STAGE_1, nowMs);
    }

    private static void collectionStage(AgentLpqSession session, Character leader, long nowMs) {
        int stage = stage(session.phase());
        AgentLpqDefinition.Stage contract = AgentLpqDefinition.stage(stage);
        if (stage == 4 || stage == 5) splitRooms(session, leader, stage, nowMs);
        else ordinaryObjectives(session, leader, stage, nowMs);
        consolidatePasses(session, leader, nowMs);
        if (leader.getItemQuantity(AgentLpqDefinition.PASS, false) >= contract.submissionCount()) {
            submit(session, leader, contract, nowMs);
        }
        if (LPQ.property(leader, stage + "stageclear") != null) {
            movePartyToNextStage(session, stage, nowMs);
        }
    }

    private static void ordinaryObjectives(AgentLpqSession session, Character leader, int stage, long nowMs) {
        int mapId = AgentLpqDefinition.stage(stage).mapId();
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            if (stage == 2 && agent.getMapId() == 922_010_201) {
                enterPortalTo(entry, agent, mapId);
                continue;
            }
            if (agent.getMapId() != mapId) continue;
            LPQ.lootNearby(agent, Set.of(AgentLpqDefinition.PASS));
            if ((stage != 2 && stage != 3) || !hitNearestReactor(entry, agent, nowMs, member)) {
                ACTIONS.grind(entry, ACTIONS.configuredMonsterSpawnIds(agent));
            }
        }
    }

    private static void splitRooms(AgentLpqSession session, Character leader, int stage, long nowMs) {
        int mainMap = AgentLpqDefinition.stage(stage).mapId();
        List<Integer> rooms = AgentLpqDefinition.roomMaps(stage);
        session.rooms().releaseExpired(nowMs, ROOM_LEASE_MS);
        for (AgentLpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant != null && rooms.contains(participant.getMapId())) {
                Integer owner = session.rooms().owner(participant.getMapId());
                if (member.memberType() == AgentLpqMemberState.MemberType.HUMAN
                        && suitable(member.characterId(), stage, participant.getMapId())
                        && owner != null && owner != participant.getId()) {
                    session.rooms().release(participant.getMapId());
                    AgentLpqMemberState displaced = session.member(owner);
                    if (displaced != null) displaced.assign(AgentLpqMemberState.Role.GENERAL, 0);
                }
                session.rooms().reserve(participant.getMapId(), participant.getId(), nowMs);
            }
        }
        assignRooms(session, stage, rooms, nowMs);
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null) continue;
            LPQ.lootNearby(agent, Set.of(AgentLpqDefinition.PASS));
            int assigned = member.assignedMapId();
            if (agent.getMapId() == mainMap && assigned != 0) {
                enterPortalTo(entry, agent, assigned);
                continue;
            }
            if (!rooms.contains(agent.getMapId())) continue;
            Integer roomOwner = session.rooms().owner(agent.getMapId());
            if (roomOwner != null && roomOwner != agent.getId()) {
                member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                enterPortalTo(entry, agent, mainMap);
                continue;
            }
            if (stage == 5 && agent.getMapId() == 922_010_506) ensureDarkSight(entry, agent);
            boolean acted = hitNearestReactor(entry, agent, nowMs, member);
            Set<Integer> mobs = ACTIONS.configuredMonsterSpawnIds(agent);
            boolean darkSightRoom = stage == 5 && agent.getMapId() == 922_010_506;
            if (!acted && !mobs.isEmpty() && !darkSightRoom) ACTIONS.grind(entry, mobs);
            boolean exhausted = activeReactors(agent).isEmpty()
                    && (darkSightRoom || ACTIONS.liveMonsterCount(agent, mobs) == 0)
                    && AgentMapPerception.items(agent.getMap()).stream()
                    .noneMatch(item -> !item.isPickedUp() && item.getItemId() == AgentLpqDefinition.PASS);
            if (exhausted) {
                session.rooms().complete(agent.getMapId());
                member.assign(AgentLpqMemberState.Role.GENERAL, 0);
                enterPortalTo(entry, agent, mainMap);
                session.markProgress(nowMs);
            }
        }
    }

    private static void assignRooms(AgentLpqSession session, int stage, List<Integer> rooms, long nowMs) {
        List<AgentLpqMemberState> agents = session.members().stream()
                .filter(member -> member.memberType() == AgentLpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(member -> member.characterId() == session.eventLeaderId() ? 1 : 0))
                .toList();
        List<Integer> assignmentOrder = stage == 5
                ? List.of(922_010_501, 922_010_506, 922_010_502,
                922_010_503, 922_010_504, 922_010_505)
                : rooms;
        for (int room : assignmentOrder) {
            if (session.rooms().completed(room) || session.rooms().owner(room) != null) continue;
            AgentLpqMemberState chosen = agents.stream().filter(member -> member.assignedMapId() == 0)
                    .filter(member -> suitable(member.characterId(), stage, room)).findFirst().orElse(null);
            boolean specialized = stage == 4 || room == 922_010_501 || room == 922_010_506;
            if (chosen == null && !specialized) {
                chosen = agents.stream().filter(member -> member.assignedMapId() == 0).findFirst().orElse(null);
            }
            if (chosen == null) continue;
            if (!session.rooms().reserve(room, chosen.characterId(), nowMs)) continue;
            AgentLpqMemberState.Role role = stage == 5 && room == 922_010_501
                    ? AgentLpqMemberState.Role.TELEPORT_RUNNER
                    : stage == 5 && room == 922_010_506
                    ? AgentLpqMemberState.Role.DARK_SIGHT_RUNNER
                    : MAGIC_ROOMS.contains(room) ? AgentLpqMemberState.Role.MAGIC_ATTACKER
                    : PHYSICAL_ROOMS.contains(room) ? AgentLpqMemberState.Role.PHYSICAL_ATTACKER
                    : AgentLpqMemberState.Role.GENERAL;
            chosen.assign(role, room);
        }
    }

    private static boolean suitable(int id, int stage, int room) {
        Character member = character(id);
        if (stage == 4 && MAGIC_ROOMS.contains(room)) return AgentLpqRosterRequirementPolicy.teleportMagic(member);
        if (stage == 4 && PHYSICAL_ROOMS.contains(room)) return AgentLpqRosterRequirementPolicy.physicalAttack(member);
        if (stage == 5 && room == 922_010_501) return AgentLpqRosterRequirementPolicy.teleportMagic(member);
        if (stage == 5 && room == 922_010_506) return AgentLpqRosterRequirementPolicy.darkSight(member);
        return true;
    }

    private static void consolidatePasses(AgentLpqSession session, Character leader, long nowMs) {
        if (isAgent(leader)) LPQ.lootNearby(leader, Set.of(AgentLpqDefinition.PASS, AgentLpqDefinition.BOSS_KEY));
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT || member.characterId() == leader.getId()) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != leader.getMapId()) continue;
            int count = agent.getItemQuantity(AgentLpqDefinition.PASS, false);
            if (count <= 0) continue;
            if (!near(agent.getPosition(), leader.getPosition(), INTERACTION_RADIUS)) {
                ACTIONS.navigate(entry, leader.getPosition(), true);
                continue;
            }
            ACTIONS.stop(entry);
            if (AgentScriptItemActionService.dropItem(entry, InventoryType.ETC,
                    AgentLpqDefinition.PASS, (short) Math.min(Short.MAX_VALUE, count))) {
                member.deferUntil(nowMs + INTERACTION_RETRY_MS);
                session.markProgress(nowMs);
                if (!isAgent(leader)) leader.dropMessage(6, "LPQ passes are ready beside you; pick them up for submission.");
            }
        }
    }

    private static void submit(AgentLpqSession session, Character leader,
                               AgentLpqDefinition.Stage stage, long nowMs) {
        if (!isAgent(leader)) {
            AgentLpqMemberState state = session.member(leader.getId());
            if (state != null && nowMs >= state.nextActionAtMs()) {
                leader.dropMessage(6, "LPQ Stage " + stage.number() + " is ready. Talk to the balloon to submit.");
                state.deferUntil(nowMs + 5_000L);
            }
            return;
        }
        AgentRuntimeEntry entry = entry(leader.getId());
        AgentLpqMemberState leaderState = session.member(leader.getId());
        if (leaderState != null && nowMs < leaderState.nextActionAtMs()) return;
        Point npc = ACTIONS.npcPosition(leader, stage.npcId());
        if (entry == null || npc == null) return;
        if (!near(leader.getPosition(), npc, INTERACTION_RADIUS)) { ACTIONS.navigate(entry, npc, true); return; }
        if (LPQ.runNpc(leader, stage.npcId(), 1)) {
            if (leaderState != null) leaderState.deferUntil(nowMs + INTERACTION_RETRY_MS);
            session.markProgress(nowMs);
        }
    }

    private static void movePartyToNextStage(AgentLpqSession session, int stage, long nowMs) {
        int destination = stage == 9 ? AgentLpqDefinition.CLEAR_MAP : AgentLpqDefinition.stage(stage + 1).mapId();
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent != null && entry != null && AgentLpqDefinition.stageNumber(agent.getMapId()) == stage) {
                enterPortalTo(entry, agent, destination);
            }
        }
        session.markProgress(nowMs);
    }

    private static void portalMaze(AgentLpqSession session, Character leader, long nowMs) {
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != 922_010_600) continue;
            if (agent.getPosition().y < -3_050) {
                enterPortalTo(entry, agent, 922_010_700);
                continue;
            }
            int row = mazeRow(agent);
            if (row < 0) continue;
            Integer known = session.maze().successfulPortal(row);
            int portalId = known == null ? 2 + row * 3 + session.maze().nextCandidateOffset(row) : known;
            Portal portal = agent.getMap().getPortal(portalId);
            if (portal == null) continue;
            if (!near(agent.getPosition(), portal.getPosition(), INTERACTION_RADIUS)) {
                ACTIONS.navigate(entry, portal.getPosition(), true);
                continue;
            }
            int beforeY = agent.getPosition().y;
            if (!LPQ.enterPortal(agent, portalId)) continue;
            if (agent.getMapId() == 922_010_700) {
                session.markProgress(nowMs);
            } else if (agent.getPosition().y < beforeY - 80) {
                session.maze().recordSuccess(row, portalId);
                session.markProgress(nowMs);
            } else if (known == null) {
                session.maze().recordFailure(row);
            }
        }
        if (leader.getMapId() == 922_010_700) session.transition(AgentLpqSession.Phase.STAGE_7, nowMs);
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
        return best <= 130.0d ? bestRow : -1;
    }

    private static void platformPuzzle(AgentLpqSession session, Character leader, long nowMs) {
        if ("-1".equals(LPQ.property(leader, "statusStg8"))) {
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
            if (!near(leader.getPosition(), balloon, INTERACTION_RADIUS)) {
                ACTIONS.navigate(leaderEntry, balloon, true);
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
            if (area.contains(participant.getPosition())) continue;
            positioned = false;
            if (member.memberType() == AgentLpqMemberState.MemberType.AGENT) {
                AgentRuntimeEntry entry = entry(member.characterId());
                if (entry != null) ACTIONS.navigate(entry,
                        new Point((int) area.getCenterX(), (int) area.getCenterY()), true);
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
        if (!near(leader.getPosition(), npc, INTERACTION_RADIUS)) { ACTIONS.navigate(entry, npc, true); return; }
        if (LPQ.runNpc(leader, AgentLpqDefinition.stage(8).npcId(), 1)) {
            if (leaderState != null) leaderState.deferUntil(nowMs + INTERACTION_RETRY_MS);
            session.advanceStage8(nowMs);
        }
        if (LPQ.property(leader, "8stageclear") != null) movePartyToNextStage(session, 8, nowMs);
    }

    private static void boss(AgentLpqSession session, Character leader, long nowMs) {
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != 922_010_900) continue;
            LPQ.lootNearby(agent, Set.of(AgentLpqDefinition.BOSS_KEY));
            ACTIONS.grind(entry, ACTIONS.configuredMonsterSpawnIds(agent));
        }
        consolidateBossKey(session, leader, nowMs);
        if (leader.getItemQuantity(AgentLpqDefinition.BOSS_KEY, false) > 0) {
            if (isAgent(leader)) submit(session, leader, AgentLpqDefinition.stage(9), nowMs);
            else leader.dropMessage(6, "Alishar's key is ready. Talk to the balloon to finish LPQ.");
        }
    }

    private static void consolidateBossKey(AgentLpqSession session, Character leader, long nowMs) {
        if (isAgent(leader)) LPQ.lootNearby(leader, Set.of(AgentLpqDefinition.BOSS_KEY));
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
        boolean any = false;
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = entry(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != AgentLpqDefinition.CLEAR_MAP) continue;
            any = true;
            if (session.bonusMode() == AgentLpqSession.BonusMode.SKIP
                    || nowMs - session.phaseEnteredAtMs() >= 50_000L) {
                LPQ.runNpc(agent, 2_040_045, 1);
            } else hitNearestReactor(entry, agent, nowMs, member);
        }
        if (!any) session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, nowMs);
    }

    private static void claim(AgentLpqSession session, long nowMs) {
        boolean remaining = false;
        for (AgentLpqMemberState member : session.members()) {
            if (member.memberType() != AgentLpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            if (agent != null && agent.getMapId() == AgentLpqDefinition.BONUS_MAP) {
                remaining = true;
                LPQ.runNpc(agent, 2_040_035, 1);
            }
        }
        if (!remaining) session.transition(AgentLpqSession.Phase.EXITING, nowMs);
    }

    private static boolean hitNearestReactor(AgentRuntimeEntry entry, Character agent,
                                             long nowMs, AgentLpqMemberState member) {
        if (nowMs < member.nextActionAtMs()) return false;
        Reactor reactor = activeReactors(agent).stream()
                .min(Comparator.comparingDouble(candidate -> candidate.getPosition().distance(agent.getPosition())))
                .orElse(null);
        if (reactor == null) return false;
        if (!near(agent.getPosition(), reactor.getPosition(), INTERACTION_RADIUS)) {
            ACTIONS.navigate(entry, reactor.getPosition(), true);
            return true;
        }
        ACTIONS.stop(entry);
        if (ACTIONS.hitReactor(agent, reactor.getObjectId())) {
            member.deferUntil(nowMs + INTERACTION_RETRY_MS);
            return true;
        }
        return false;
    }

    private static List<Reactor> activeReactors(Character agent) {
        List<Reactor> reactors = new ArrayList<>();
        for (Reactor reactor : ACTIONS.reactors(agent)) {
            if (reactor != null && reactor.isAlive() && reactor.isActive()) reactors.add(reactor);
        }
        return reactors;
    }

    private static void ensureDarkSight(AgentRuntimeEntry entry, Character agent) {
        if (agent.getBuffedValue(BuffStat.DARKSIGHT) == null) {
            AgentCombatBuffRuntime.tryCastExplicitUtilityBuff(entry, agent, Rogue.DARK_SIGHT);
        }
    }

    private static boolean enterPortalTo(AgentRuntimeEntry entry, Character agent, int destinationMapId) {
        Integer portalId = ACTIONS.directPortalIdTo(agent, destinationMapId);
        if (portalId == null) return false;
        Point position = ACTIONS.portalPosition(agent, portalId);
        if (position == null) return false;
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
