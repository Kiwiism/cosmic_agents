package server.agents.capabilities.partyquest.opq;

import client.Character;
import client.inventory.InventoryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.capabilities.looting.AgentLootEligibility;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.perception.AgentMapPerception;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.Reactor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

/**
 * OPQ room workers. Every action uses ordinary movement, portal, combat, NPC,
 * pickup, drop, and reactor paths. There is deliberately no stagePosition,
 * changeMap, remote damage, forced reactor state, item grant, or drop vacuum.
 */
final class AgentOpqCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentOpqCoordinator.class);
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway OPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final long ACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqCoordinator.ACTION_RETRY_MS");
    private static final long PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqCoordinator.PHASE_TIMEOUT_MS");
    private static final long ROOM_LEASE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqCoordinator.ROOM_LEASE_MS");
    private static final long NAVIGATION_STALL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqCoordinator.NAVIGATION_STALL_MS");
    private static final int NPC_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.opq.AgentOpqCoordinator.NPC_RADIUS");
    private static final int LOOT_RADIUS = server.agents.runtime.AgentRuntimeConfig.cfg.LOOT_RADIUS;
    private static final Point ENTRANCE_HANDOFF = new Point(170, 96);
    private static final Point ENTRANCE_EAK = new Point(377, 66);
    private static final Point WALKWAY_EAK = new Point(552, -138);
    private static final Point LOUNGE_EAK = new Point(97, -737);
    private static final Point MUSIC_PLAYER = new Point(-1706, -240);
    private static final Point ROOT_POT = new Point(-755, 19);
    private static final Point MINERVA_BASE = new Point(-48, -916);
    private static final List<int[]> SEALED_COMBINATIONS = sealedCombinations();

    private AgentOpqCoordinator() { }

    static void tickSession(AgentOpqSession session, long nowMs) {
        synchronized (session) {
            if (session.terminal()) return;
            if (nowMs - session.lastProgressAtMs() > PHASE_TIMEOUT_MS) {
                AgentOpqTerminationService.release(session, "No legal OPQ progress before timeout", nowMs, true);
                return;
            }
            session.rooms().releaseExpired(nowMs, ROOM_LEASE_MS);
            Character leader = character(session.eventLeaderId());
            if (leader == null || leader.getHp() <= 0) {
                AgentOpqTerminationService.release(session, "OPQ leader unavailable", nowMs, true);
                return;
            }
            if (session.eventInstance() == null && OPQ.event(leader) != null) session.bindEventInstance(OPQ.event(leader));
            synchronizePhase(session, leader, nowMs);
            switch (session.phase()) {
                case PREPARING -> session.transition(AgentOpqSession.Phase.ENTERING, nowMs);
                case ENTERING -> tickEntry(session, leader, nowMs);
                case ENTRANCE -> assignEntrance(session);
                case SPLIT_ROOMS -> {
                    assignSplitRooms(session, nowMs);
                    synchronizeRoomCompletion(session, leader, nowMs);
                }
                case RESTORING_STATUE -> tickStatueRestoration(session, leader, nowMs);
                case GARDEN -> assignGarden(session, nowMs);
                case RESTORING_MINERVA -> tickFinalRoot(session, leader, nowMs);
                case CLAIMING_REWARD -> tickReward(session, nowMs);
                default -> { }
            }
        }
    }

    static void tickMember(AgentOpqSession session, AgentRuntimeEntry entry,
                           Character agent, long nowMs) {
        if (session == null || entry == null || agent == null || session.terminal()) return;
        AgentOpqMemberState member = session.member(agent.getId());
        if (member == null || member.memberType() != AgentOpqMemberState.MemberType.AGENT
                || nowMs < member.nextActionAtMs()) return;
        member.observeMovement(agent.getPosition(), nowMs);
        if (member.movementStalledFor(nowMs) >= NAVIGATION_STALL_MS) {
            ACTIONS.refreshNavigation(entry, agent);
            member.clearLocalWork();
            member.deferUntil(nowMs + ACTION_RETRY_MS);
            log.warn("OPQ legal navigation route refreshed after local stall: session={} member={} map={}",
                    session.sessionId(), agent.getId(), agent.getMapId());
            return;
        }
        switch (session.phase()) {
            case ENTRANCE -> tickEntranceWorker(session, entry, agent, member, nowMs);
            case SPLIT_ROOMS -> tickRoomWorker(session, entry, agent, member, nowMs);
            case RESTORING_STATUE -> tickPieceCarrier(session, entry, agent, member, nowMs);
            case GARDEN -> tickGardenWorker(session, entry, agent, member, nowMs);
            case RESTORING_MINERVA -> tickRootCarrier(session, entry, agent, member, nowMs);
            case CLAIMING_REWARD -> tickRewardMember(session, entry, agent, nowMs);
            default -> { }
        }
    }

    private static void synchronizePhase(AgentOpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentOpqDefinition.ENTRANCE_MAP && OPQ.event(leader) != null
                && session.phase().ordinal() <= AgentOpqSession.Phase.ENTRANCE.ordinal()) {
            session.bindEventInstance(OPQ.event(leader));
            session.transition(AgentOpqSession.Phase.ENTRANCE, nowMs);
        }
        if (leader.getMapId() == AgentOpqDefinition.CENTER_MAP
                && session.phase() == AgentOpqSession.Phase.ENTRANCE) {
            session.transition(AgentOpqSession.Phase.SPLIT_ROOMS, nowMs);
        }
        if (session.phase() == AgentOpqSession.Phase.SPLIT_ROOMS && session.rooms().allComplete()) {
            session.transition(AgentOpqSession.Phase.RESTORING_STATUE, nowMs);
        }
        if (leader.getMapId() == AgentOpqDefinition.GARDEN_MAP) {
            session.transition(AgentOpqSession.Phase.GARDEN, nowMs);
        }
        if (leader.getMapId() == AgentOpqDefinition.CENTER_MAP
                && session.phase() == AgentOpqSession.Phase.GARDEN
                && leader.getItemQuantity(AgentOpqDefinition.ROOT_OF_LIFE, false) > 0) {
            session.transition(AgentOpqSession.Phase.RESTORING_MINERVA, nowMs);
        }
        if (leader.getMapId() == AgentOpqDefinition.CLEAR_MAP) {
            session.transition(AgentOpqSession.Phase.CLAIMING_REWARD, nowMs);
        }
    }

    private static void tickEntry(AgentOpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() != AgentOpqDefinition.RECRUIT_MAP) return;
        Point npc = ACTIONS.npcPosition(leader, AgentOpqDefinition.ENTRY_NPC);
        AgentRuntimeEntry entry = entry(leader.getId());
        if (entry == null || !near(leader.getPosition(), npc, NPC_RADIUS)) {
            if (entry != null && npc != null) ACTIONS.navigate(entry, npc, true);
            return;
        }
        ACTIONS.stop(entry);
        if (OPQ.runNpc(leader, AgentOpqDefinition.ENTRY_NPC, 0)) session.markProgress(nowMs);
    }

    private static void assignEntrance(AgentOpqSession session) {
        session.members().forEach(member -> member.assign(
                AgentOpqMemberState.Role.ENTRANCE_COLLECTOR, null, 0));
    }

    private static void tickEntranceWorker(AgentOpqSession session, AgentRuntimeEntry entry,
                                           Character agent, AgentOpqMemberState member, long nowMs) {
        if (agent.getMapId() != AgentOpqDefinition.ENTRANCE_MAP) return;
        if (collectReservedDrop(session, entry, agent, Set.of(AgentOpqDefinition.CLOUD_PIECE), nowMs)) return;
        List<Reactor> clouds = activeReactors(agent).stream().filter(r -> r.getId() == 2_002_001).toList();
        if (!clouds.isEmpty()) {
            hitNearestLegalReactor(entry, agent, member, clouds, nowMs);
            return;
        }
        int held = agent.getItemQuantity(AgentOpqDefinition.CLOUD_PIECE, false);
        if (agent.getId() != session.eventLeaderId() && held > 0) {
            if (!moveTo(entry, agent, ENTRANCE_HANDOFF, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
            if (dropItem(entry, InventoryType.ETC,
                    AgentOpqDefinition.CLOUD_PIECE, (short) held)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        Character leader = character(session.eventLeaderId());
        if (agent.getId() == session.eventLeaderId()) {
            if (collectGroundItem(entry, agent, AgentOpqDefinition.CLOUD_PIECE, false, nowMs)) return;
            if (held >= 20) {
                if (!moveTo(entry, agent, ENTRANCE_EAK, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
                if (dropItem(entry, InventoryType.ETC,
                        AgentOpqDefinition.CLOUD_PIECE, (short) 20)) member.deferUntil(nowMs + ACTION_RETRY_MS);
                return;
            }
            boolean cloudDropRemaining = AgentMapPerception.items(agent.getMap()).stream()
                    .anyMatch(drop -> !drop.isPickedUp() && drop.getItemId() == AgentOpqDefinition.CLOUD_PIECE);
            if (leader != null && propertyInt(leader, "statusStg0") != 1
                    && partyItemCount(session, AgentOpqDefinition.CLOUD_PIECE) == 0
                    && !cloudDropRemaining) {
                runNearbyNpc(entry, leader, AgentOpqDefinition.EAK_NPC);
            }
        }
    }

    private static void assignSplitRooms(AgentOpqSession session, long nowMs) {
        List<AgentOpqMemberState> agents = session.members().stream()
                .filter(m -> m.memberType() == AgentOpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentOpqMemberState::characterId)).toList();
        if (agents.size() < 6) return;
        AgentOpqMemberState leader = session.member(session.eventLeaderId());
        List<AgentOpqMemberState> others = agents.stream()
                .filter(m -> m.characterId() != session.eventLeaderId()).toList();
        if (!session.rooms().complete(AgentOpqDefinition.Room.SEALED)) {
            session.rooms().claim(AgentOpqDefinition.Room.SEALED, leader.characterId(), nowMs);
            leader.assign(AgentOpqMemberState.Role.LEADER, AgentOpqDefinition.Room.SEALED, 0);
            for (int i = 0; i < 3; i++) {
                others.get(i).assign(AgentOpqMemberState.Role.SEALED_PLATFORM, AgentOpqDefinition.Room.SEALED, 0);
            }
            assignSealedPlatforms(session, others.subList(0, 3));
            assignRoom(session, others.get(3), AgentOpqDefinition.Room.ON_WAY_UP,
                    AgentOpqMemberState.Role.WAY_UP_RUNNER, nowMs);
            assignRoom(session, others.get(4), AgentOpqDefinition.Room.LOUNGE,
                    AgentOpqMemberState.Role.LOUNGE_RUNNER, nowMs);
            return;
        }
        assignRoom(session, leader, AgentOpqDefinition.Room.LOBBY, AgentOpqMemberState.Role.LOBBY_RUNNER, nowMs);
        assignRoom(session, others.get(0), AgentOpqDefinition.Room.WALKWAY, AgentOpqMemberState.Role.WALKWAY_COLLECTOR, nowMs);
        assignRoom(session, others.get(1), AgentOpqDefinition.Room.STORAGE, AgentOpqMemberState.Role.STORAGE_RUNNER, nowMs);
        assignRoom(session, others.get(2), AgentOpqDefinition.Room.LOUNGE, AgentOpqMemberState.Role.LOUNGE_RUNNER, nowMs);
        assignRoom(session, others.get(3), AgentOpqDefinition.Room.ON_WAY_UP, AgentOpqMemberState.Role.WAY_UP_RUNNER, nowMs);
        assignRoom(session, others.get(4), AgentOpqDefinition.Room.LOUNGE, AgentOpqMemberState.Role.LOUNGE_RUNNER, nowMs);
        if (session.rooms().complete(AgentOpqDefinition.Room.WALKWAY)) {
            assignRoom(session, others.get(0), AgentOpqDefinition.Room.LOUNGE, AgentOpqMemberState.Role.LOUNGE_RUNNER, nowMs);
        }
        if (session.rooms().complete(AgentOpqDefinition.Room.STORAGE)) {
            assignRoom(session, others.get(1), AgentOpqDefinition.Room.LOUNGE, AgentOpqMemberState.Role.LOUNGE_RUNNER, nowMs);
        }
        if (session.rooms().complete(AgentOpqDefinition.Room.LOBBY)) {
            int walkwayFragments = partyItemCount(session, AgentOpqDefinition.WALKWAY_FRAGMENT);
            int loungeFragments = partyItemCount(session, AgentOpqDefinition.LOUNGE_FRAGMENT);
            boolean wayRunnerAtTop = session.members().stream()
                    .filter(m -> m.assignedRoom() == AgentOpqDefinition.Room.ON_WAY_UP)
                    .map(m -> character(m.characterId())).filter(java.util.Objects::nonNull)
                    .anyMatch(c -> c.getMapId() == AgentOpqDefinition.ON_WAY_UP_MAP && c.getPosition().y < -4_900);
            if (!session.rooms().complete(AgentOpqDefinition.Room.WALKWAY) && walkwayFragments >= 30) {
                leader.assign(AgentOpqMemberState.Role.LEADER, AgentOpqDefinition.Room.WALKWAY, 0);
            } else if (!session.rooms().complete(AgentOpqDefinition.Room.LOUNGE) && loungeFragments >= 40) {
                leader.assign(AgentOpqMemberState.Role.LEADER, AgentOpqDefinition.Room.LOUNGE, 0);
            } else if (!session.rooms().complete(AgentOpqDefinition.Room.ON_WAY_UP) && wayRunnerAtTop) {
                leader.assign(AgentOpqMemberState.Role.LEADER, AgentOpqDefinition.Room.ON_WAY_UP, 0);
            }
        }
        assignLoungeSubrooms(session);
    }

    private static void assignRoom(AgentOpqSession session, AgentOpqMemberState member,
                                   AgentOpqDefinition.Room room, AgentOpqMemberState.Role role, long nowMs) {
        if (session.rooms().complete(room)) return;
        AgentOpqRoomLedger.Lease lease = session.rooms().lease(room);
        if (lease == null) session.rooms().claim(room, member.characterId(), nowMs);
        member.assign(role, room, member.assignedSubroomMapId());
    }

    private static void assignLoungeSubrooms(AgentOpqSession session) {
        List<AgentOpqMemberState> runners = session.members().stream()
                .filter(m -> m.role() == AgentOpqMemberState.Role.LOUNGE_RUNNER).toList();
        Set<Integer> occupied = runners.stream().map(AgentOpqMemberState::assignedSubroomMapId)
                .filter(id -> id > 0 && !session.loungeSubroomComplete(id))
                .collect(java.util.stream.Collectors.toSet());
        for (AgentOpqMemberState runner : runners) {
            int current = runner.assignedSubroomMapId();
            if (current > 0 && !session.loungeSubroomComplete(current)) continue;
            int next = AgentOpqDefinition.LOUNGE_ROOM_MAPS.stream()
                    .filter(id -> !session.loungeSubroomComplete(id) && !occupied.contains(id))
                    .findFirst().orElse(0);
            runner.assign(AgentOpqMemberState.Role.LOUNGE_RUNNER, AgentOpqDefinition.Room.LOUNGE, next);
            if (next > 0) occupied.add(next);
        }
    }

    private static void assignSealedPlatforms(AgentOpqSession session, List<AgentOpqMemberState> holders) {
        int[] combination = SEALED_COMBINATIONS.get(Math.min(session.sealedAttempt(), SEALED_COMBINATIONS.size() - 1));
        int holder = 0;
        for (int platform = 0; platform < 3; platform++) {
            for (int count = 0; count < combination[platform]; count++) holders.get(holder++).assignPlatform(platform);
        }
    }

    private static void tickRoomWorker(AgentOpqSession session, AgentRuntimeEntry entry,
                                       Character agent, AgentOpqMemberState member, long nowMs) {
        AgentOpqDefinition.Room room = member.assignedRoom();
        if (room == null || session.rooms().complete(room)) {
            tickPieceCarrier(session, entry, agent, member, nowMs);
            return;
        }
        AgentOpqDefinition.Room currentRoom = AgentOpqDefinition.roomForMap(agent.getMapId());
        if (currentRoom != null && currentRoom != room && session.rooms().complete(currentRoom)) {
            exitRoom(entry, agent, currentRoom, nowMs, member);
            return;
        }
        if (agent.getMapId() == AgentOpqDefinition.CENTER_MAP) {
            enterRoom(entry, agent, room, nowMs, member);
            return;
        }
        if (AgentOpqDefinition.roomForMap(agent.getMapId()) != room) return;
        session.rooms().heartbeat(room, session.rooms().lease(room) == null
                ? member.characterId() : session.rooms().lease(room).ownerId(), nowMs);
        switch (room) {
            case WALKWAY -> tickWalkway(session, entry, agent, member, nowMs);
            case STORAGE -> tickStorage(session, entry, agent, member, nowMs);
            case LOBBY -> tickLobby(session, entry, agent, member, nowMs);
            case SEALED -> tickSealed(session, entry, agent, member, nowMs);
            case LOUNGE -> tickLounge(session, entry, agent, member, nowMs);
            case ON_WAY_UP -> tickWayUp(session, entry, agent, member, nowMs);
        }
    }

    private static void enterRoom(AgentRuntimeEntry entry, Character agent,
                                  AgentOpqDefinition.Room room, long nowMs, AgentOpqMemberState member) {
        Integer portalId = AgentOpqDefinition.CENTER_PORTALS.get(room);
        if (portalId == null) return;
        enterAuthoredPortal(entry, agent, portalId, nowMs, member);
    }

    private static void tickWalkway(AgentOpqSession session, AgentRuntimeEntry entry,
                                    Character agent, AgentOpqMemberState member, long nowMs) {
        if (collectReservedDrop(session, entry, agent, Set.of(AgentOpqDefinition.WALKWAY_FRAGMENT), nowMs)) return;
        if (ACTIONS.liveMonsterCount(agent, AgentOpqDefinition.WALKWAY_MOBS) > 0) {
            ACTIONS.grind(entry, AgentOpqDefinition.WALKWAY_MOBS); return;
        }
        int held = agent.getItemQuantity(AgentOpqDefinition.WALKWAY_FRAGMENT, false);
        Character leader = character(session.eventLeaderId());
        if (agent.getId() == session.eventLeaderId() && held >= 30) {
            runNearbyNpc(entry, agent, AgentOpqDefinition.EAK_NPC);
        } else if (held >= 30 && leader != null && leader.getMapId() == AgentOpqDefinition.WALKWAY_MAP) {
            if (!moveTo(entry, agent, WALKWAY_EAK, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
            dropItem(entry, InventoryType.ETC,
                    AgentOpqDefinition.WALKWAY_FRAGMENT, (short) held);
            member.deferUntil(nowMs + ACTION_RETRY_MS);
        } else if (held >= 30) {
            ACTIONS.stop(entry);
        }
    }

    private static void tickStorage(AgentOpqSession session, AgentRuntimeEntry entry,
                                    Character agent, AgentOpqMemberState member, long nowMs) {
        reserve(session, AgentOpqDefinition.STATUE_PIECES.get(1), agent, AgentOpqDefinition.STORAGE_MAP, nowMs);
        if (collectReservedDrop(session, entry, agent, Set.of(AgentOpqDefinition.STATUE_PIECES.get(1)), nowMs)) return;
        if (agent.getItemQuantity(AgentOpqDefinition.STATUE_PIECES.get(1), false) > 0) {
            session.loot().pickedUp(AgentOpqDefinition.STATUE_PIECES.get(1), agent.getId(), nowMs);
            exitRoom(entry, agent, AgentOpqDefinition.Room.STORAGE, nowMs, member); return;
        }
        hitNearestLegalReactor(entry, agent, member, activeReactors(agent), nowMs);
    }

    private static void tickLobby(AgentOpqSession session, AgentRuntimeEntry entry,
                                  Character agent, AgentOpqMemberState member, long nowMs) {
        int day = new GregorianCalendar().get(GregorianCalendar.DAY_OF_WEEK);
        int lpItem = 4_001_055 + day;
        int piece = AgentOpqDefinition.STATUE_PIECES.get(2);
        reserve(session, lpItem, agent, AgentOpqDefinition.LOBBY_MAP, nowMs);
        reserve(session, piece, agent, AgentOpqDefinition.LOBBY_MAP, nowMs);
        if (collectReservedDrop(session, entry, agent, Set.of(lpItem, piece), nowMs)) return;
        if (agent.getItemQuantity(piece, false) > 0) {
            session.loot().pickedUp(piece, agent.getId(), nowMs);
            exitRoom(entry, agent, AgentOpqDefinition.Room.LOBBY, nowMs, member); return;
        }
        if (agent.getItemQuantity(lpItem, false) > 0) {
            if (!moveTo(entry, agent, MUSIC_PLAYER, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
            dropItem(entry, InventoryType.ETC, lpItem, (short) 1);
            member.deferUntil(nowMs + ACTION_RETRY_MS); return;
        }
        if (propertyInt(agent, "statusStg3") == 0 && agent.getId() == session.eventLeaderId()) {
            if (runNearbyNpc(entry, agent, AgentOpqDefinition.EAK_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        List<Reactor> target = activeReactors(agent).stream()
                .filter(r -> r.getId() == 2_002_004 + day - 1 || r.getId() == 2_002_011).toList();
        hitNearestLegalReactor(entry, agent, member, target, nowMs);
    }

    private static void tickSealed(AgentOpqSession session, AgentRuntimeEntry entry,
                                   Character agent, AgentOpqMemberState member, long nowMs) {
        int piece = AgentOpqDefinition.STATUE_PIECES.get(3);
        if (propertyInt(agent, "statusStg4") == 1) {
            if (member.role() == AgentOpqMemberState.Role.SEALED_PLATFORM) {
                reserve(session, piece, agent, AgentOpqDefinition.SEALED_MAP, nowMs);
                AgentOpqLootLedger.Reservation custody = session.loot().reservation(piece);
                if (custody != null && custody.ownerId() == agent.getId()) {
                    if (collectReservedDrop(session, entry, agent, Set.of(piece), nowMs)) return;
                    if (agent.getItemQuantity(piece, false) > 0) {
                        session.loot().pickedUp(piece, agent.getId(), nowMs);
                    } else {
                        List<Reactor> box = activeReactors(agent).stream()
                                .filter(r -> r.getId() == AgentOpqDefinition.SEALED_REWARD_BOX).toList();
                        if (!box.isEmpty()) hitNearestLegalReactor(entry, agent, member, box, nowMs);
                        return;
                    }
                }
            }
            exitRoom(entry, agent, AgentOpqDefinition.Room.SEALED, nowMs, member); return;
        }
        if (member.role() == AgentOpqMemberState.Role.SEALED_PLATFORM) {
            Point target = sealedPlatform(member.assignedPlatform());
            if (!near(agent.getPosition(), target, 28)) ACTIONS.navigate(entry, target, true);
            else ACTIONS.stop(entry);
            return;
        }
        if (agent.getId() != session.eventLeaderId()) return;
        List<Character> holders = session.members().stream()
                .filter(m -> m.role() == AgentOpqMemberState.Role.SEALED_PLATFORM)
                .map(m -> character(m.characterId())).filter(java.util.Objects::nonNull).toList();
        if (holders.size() != 3 || !sealedFormationReady(session, holders)) return;
        if (nowMs - session.sealedCheckedAtMs() < 1_500L) return;
        if (runNearbyNpc(entry, agent, AgentOpqDefinition.EAK_NPC)) {
            if (propertyInt(agent, "statusStg4") != 1) session.advanceSealedAttempt(nowMs);
            else session.markProgress(nowMs);
            session.markSealedChecked(nowMs);
        }
    }

    private static void tickLounge(AgentOpqSession session, AgentRuntimeEntry entry,
                                   Character agent, AgentOpqMemberState member, long nowMs) {
        if (AgentOpqDefinition.LOUNGE_ROOM_MAPS.contains(agent.getMapId())) {
            member.beginLoungeCollection(agent.getMapId(),
                    agent.getItemQuantity(AgentOpqDefinition.LOUNGE_FRAGMENT, false));
        }
        if (collectReservedDrop(session, entry, agent, Set.of(AgentOpqDefinition.LOUNGE_FRAGMENT), nowMs)) return;
        int piece = AgentOpqDefinition.STATUE_PIECES.get(4);
        if (agent.getId() == session.eventLeaderId() && agent.getItemQuantity(piece, false) > 0) {
            exitRoom(entry, agent, AgentOpqDefinition.Room.LOUNGE, nowMs, member); return;
        }
        if (AgentOpqDefinition.LOUNGE_ROOM_MAPS.contains(agent.getMapId())) {
            int currentCount = agent.getItemQuantity(AgentOpqDefinition.LOUNGE_FRAGMENT, false);
            if (member.loungeCollected(agent.getMapId(), currentCount) >= 10) {
                session.completeLoungeSubroom(agent.getMapId(), nowMs);
                enterAuthoredPortal(entry, agent, AgentOpqDefinition.LOUNGE_SUBROOM_EXIT_PORTAL, nowMs, member);
                return;
            }
            if (ACTIONS.liveMonsterCount(agent, AgentOpqDefinition.LOUNGE_MOBS) > 0) {
                ACTIONS.grind(entry, AgentOpqDefinition.LOUNGE_MOBS); return;
            }
            List<Reactor> boxes = activeReactors(agent).stream().filter(r -> r.getId() == 2_002_002).toList();
            if (!boxes.isEmpty()) { hitNearestLegalReactor(entry, agent, member, boxes, nowMs); return; }
            enterAuthoredPortal(entry, agent, AgentOpqDefinition.LOUNGE_SUBROOM_EXIT_PORTAL, nowMs, member);
            return;
        }
        int partyFragments = session.members().stream().map(m -> character(m.characterId()))
                .filter(java.util.Objects::nonNull)
                .mapToInt(c -> c.getItemQuantity(AgentOpqDefinition.LOUNGE_FRAGMENT, false)).sum();
        if (partyFragments >= 40) {
            Character leader = character(session.eventLeaderId());
            if (agent.getId() != session.eventLeaderId()) {
                int held = agent.getItemQuantity(AgentOpqDefinition.LOUNGE_FRAGMENT, false);
                if (held > 0 && leader != null && leader.getMapId() == AgentOpqDefinition.LOUNGE_MAP
                        && moveTo(entry, agent, LOUNGE_EAK, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) {
                    dropItem(entry, InventoryType.ETC,
                            AgentOpqDefinition.LOUNGE_FRAGMENT, (short) held);
                }
            } else if (collectGroundItem(entry, agent, AgentOpqDefinition.LOUNGE_FRAGMENT, false, nowMs)) return;
            else if (agent.getItemQuantity(AgentOpqDefinition.LOUNGE_FRAGMENT, false) >= 40) {
                runNearbyNpc(entry, agent, AgentOpqDefinition.EAK_NPC);
            }
            return;
        }
        int targetRoom = member.assignedSubroomMapId();
        Integer portal = AgentOpqDefinition.LOUNGE_SUBROOM_ENTRY_PORTALS.get(targetRoom);
        if (portal != null) {
            if (portal != 6 && agent.getPosition().y > -1_760) {
                tickLoungePortalDiscovery(session, entry, agent, member, nowMs);
            } else enterAuthoredPortal(entry, agent, portal, nowMs, member);
        }
    }

    private static void tickLoungePortalDiscovery(AgentOpqSession session, AgentRuntimeEntry entry,
                                                   Character agent, AgentOpqMemberState member, long nowMs) {
        if (member.portalObservationPending(agent.getMapId())) {
            boolean advanced = agent.getPosition().y < member.pendingPortalSourceY() - 70;
            session.loungeRoute().observe(member.pendingPortalRow(), member.pendingPortalChoice(), advanced);
            member.clearPortalObservation();
        }
        int row = agent.getPosition().y > -1_560 ? 0 : 1;
        int choice = session.loungeRoute().choice(row);
        int portalId = (row == 0 ? 11 : 14) + choice;
        Point portal = ACTIONS.portalPosition(agent, portalId);
        if (!AgentOpqInteractionPolicy.mayEnterPortal(agent.getPosition(), portal)) {
            if (portal != null) ACTIONS.navigate(entry, portal, true);
            return;
        }
        member.beginPortalObservation(agent.getMapId(), row, choice, agent.getPosition().y);
        if (OPQ.enterPortal(agent, portalId)) member.deferUntil(nowMs + ACTION_RETRY_MS);
        else member.clearPortalObservation();
    }

    private static void tickWayUp(AgentOpqSession session, AgentRuntimeEntry entry,
                                  Character agent, AgentOpqMemberState member, long nowMs) {
        int piece = AgentOpqDefinition.STATUE_PIECES.get(5);
        reserve(session, piece, agent, AgentOpqDefinition.ON_WAY_UP_MAP, nowMs);
        if (collectReservedDrop(session, entry, agent, Set.of(piece), nowMs)) return;
        if (agent.getItemQuantity(piece, false) > 0) {
            session.loot().pickedUp(piece, agent.getId(), nowMs);
            exitRoom(entry, agent, AgentOpqDefinition.Room.ON_WAY_UP, nowMs, member); return;
        }
        if (propertyInt(agent, "statusStg6") == 1) {
            List<Reactor> box = activeReactors(agent).stream()
                    .filter(r -> r.getId() == AgentOpqDefinition.WAY_UP_REWARD_BOX).toList();
            if (!box.isEmpty()) hitNearestLegalReactor(entry, agent, member, box, nowMs);
            return;
        }
        if (agent.getPosition().y > -4_900) {
            tickWayUpPortalDiscovery(session, entry, agent, member, nowMs); return;
        }
        if (agent.getId() == session.eventLeaderId()) {
            configureWayUpLevers(session, entry, agent, member, nowMs);
        } else ACTIONS.stop(entry);
    }

    private static void tickWayUpPortalDiscovery(AgentOpqSession session, AgentRuntimeEntry entry,
                                                  Character agent, AgentOpqMemberState member, long nowMs) {
        if (member.portalObservationPending(agent.getMapId())) {
            boolean advanced = agent.getPosition().y < member.pendingPortalSourceY() - 70;
            session.wayUpRoute().observe(member.pendingPortalRow(), member.pendingPortalChoice(), advanced);
            member.clearPortalObservation();
        }
        int row = wayUpRow(agent.getPosition().y);
        if (row < 0 || row >= 16) return;
        int choice = session.wayUpRoute().choice(row);
        int portalId = 24 + row * 4 + choice;
        Point portal = ACTIONS.portalPosition(agent, portalId);
        if (!AgentOpqInteractionPolicy.mayEnterPortal(agent.getPosition(), portal)) {
            if (portal != null) ACTIONS.navigate(entry, portal, true);
            return;
        }
        member.beginPortalObservation(agent.getMapId(), row, choice, agent.getPosition().y);
        if (OPQ.enterPortal(agent, portalId)) member.deferUntil(nowMs + ACTION_RETRY_MS);
        else member.clearPortalObservation();
    }

    private static void configureWayUpLevers(AgentOpqSession session, AgentRuntimeEntry entry,
                                             Character leader, AgentOpqMemberState member, long nowMs) {
        int attempt = session.wayUpLeverAttempt();
        int first = 0, second = 1, cursor = 0;
        outer: for (int a = 0; a < 5; a++) for (int b = a + 1; b < 5; b++) {
            if (cursor++ == attempt) { first = a; second = b; break outer; }
        }
        List<Reactor> levers = activeReactors(leader).stream()
                .filter(r -> r.getId() == 2_008_007).sorted(Comparator.comparing(Reactor::getName)).toList();
        for (int i = 0; i < levers.size(); i++) {
            boolean desired = i == first || i == second;
            if ((levers.get(i).getState() > 0) != desired) {
                hitNearestLegalReactor(entry, leader, member, List.of(levers.get(i)), nowMs); return;
            }
        }
        if (runNearbyNpc(entry, leader, AgentOpqDefinition.EAK_NPC)) session.advanceWayUpLeverAttempt(nowMs);
    }

    private static void synchronizeRoomCompletion(AgentOpqSession session, Character leader, long nowMs) {
        for (AgentOpqDefinition.Room room : AgentOpqDefinition.Room.values()) {
            int status = propertyInt(leader, AgentOpqDefinition.stageProperty(room));
            boolean solved = room == AgentOpqDefinition.Room.LOBBY ? status >= 1 : status == 1;
            boolean pieceSecured = partyItemCount(session, AgentOpqDefinition.statuePiece(room)) > 0;
            boolean complete = solved && pieceSecured;
            AgentOpqRoomLedger.Lease lease = session.rooms().lease(room);
            if (complete && lease != null) session.rooms().advance(room, lease.ownerId(), AgentOpqRoomLedger.State.COMPLETE, nowMs);
        }
    }

    private static void tickStatueRestoration(AgentOpqSession session, Character leader, long nowMs) {
        boolean complete = true;
        if (leader.getMapId() == AgentOpqDefinition.CENTER_MAP) {
            for (String scar : AgentOpqDefinition.STATUE_SCAR_BY_ITEM.values()) {
                Reactor reactor = leader.getMap().getReactorByName(scar);
                if (reactor == null || reactor.getState() < 1) { complete = false; break; }
            }
        } else complete = false;
        if (complete && runNearbyNpc(entry(leader.getId()), leader, AgentOpqDefinition.EAK_NPC)) {
            session.markProgress(nowMs);
        }
    }

    private static void tickPieceCarrier(AgentOpqSession session, AgentRuntimeEntry entry,
                                         Character agent, AgentOpqMemberState member, long nowMs) {
        if (agent.getMapId() != AgentOpqDefinition.CENTER_MAP) {
            AgentOpqDefinition.Room room = AgentOpqDefinition.roomForMap(agent.getMapId());
            if (room != null && session.rooms().complete(room)) exitRoom(entry, agent, room, nowMs, member);
            return;
        }
        for (int piece : AgentOpqDefinition.STATUE_PIECES) {
            if (agent.getItemQuantity(piece, false) <= 0) continue;
            String scar = AgentOpqDefinition.STATUE_SCAR_BY_ITEM.get(piece);
            Point target = reactorPosition(agent, scar, AgentOpqDefinition.STATUE_SCAR_POSITION.get(scar));
            if (!moveTo(entry, agent, target, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
            if (dropItem(entry, InventoryType.ETC, piece, (short) 1)) {
                session.loot().delivered(piece, agent.getId(), nowMs);
                member.deferUntil(nowMs + ACTION_RETRY_MS);
            }
            return;
        }
        ACTIONS.stop(entry);
    }

    private static void assignGarden(AgentOpqSession session, long nowMs) {
        List<AgentOpqMemberState> agents = session.members().stream()
                .filter(m -> m.memberType() == AgentOpqMemberState.MemberType.AGENT)
                .sorted(Comparator.comparingInt(AgentOpqMemberState::characterId)).toList();
        boolean papaPixieAlive = agents.stream().map(agent -> character(agent.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(agent -> agent.getMapId() == AgentOpqDefinition.GARDEN_MAP)
                .anyMatch(agent -> papaPixie(agent) != null);
        session.observePapaPixie(papaPixieAlive, nowMs);
        for (AgentOpqMemberState agent : agents) agent.assign(
                papaPixieAlive ? AgentOpqMemberState.Role.BOSS_ATTACKER
                        : agent.characterId() == session.eventLeaderId()
                        ? AgentOpqMemberState.Role.GARDEN_SEEDER
                        : AgentOpqMemberState.Role.BOSS_ATTACKER,
                null, 0);
    }

    private static void tickGardenWorker(AgentOpqSession session, AgentRuntimeEntry entry,
                                         Character agent, AgentOpqMemberState member, long nowMs) {
        if (agent.getMapId() != AgentOpqDefinition.GARDEN_MAP) return;
        Monster papaPixie = papaPixie(agent);
        session.observePapaPixie(papaPixie != null, nowMs);
        if (papaPixie != null) {
            tickPapaPixieCombat(session, entry, agent, member, papaPixie, nowMs);
            return;
        }
        member.clearBossCombat();
        if (member.role() == AgentOpqMemberState.Role.GARDEN_SEEDER) {
            List<Integer> lootPriority = List.of(AgentOpqDefinition.ROOT_OF_LIFE,
                    AgentOpqDefinition.EVEN_STRANGER_SEED, AgentOpqDefinition.TRANSPARENT_TRIGGER,
                    AgentOpqDefinition.STRANGE_SEED);
            for (int item : lootPriority) {
                reserve(session, item, agent, AgentOpqDefinition.GARDEN_MAP, nowMs);
            }
            for (int item : lootPriority) {
                if (collectReservedDrop(session, entry, agent, Set.of(item), nowMs)) return;
            }
            if (agent.getItemQuantity(AgentOpqDefinition.ROOT_OF_LIFE, false) > 0) {
                if (ACTIONS.liveMonsterCount(agent, AgentOpqDefinition.GARDEN_SETUP_MOBS) > 0) {
                    ACTIONS.grind(entry, AgentOpqDefinition.GARDEN_SETUP_MOBS);
                } else if (collectGardenCleanupDrop(session, entry, agent, nowMs)) {
                    session.markProgress(nowMs);
                } else if (gardenStageDrained(agent) && agent.getId() == session.eventLeaderId()) {
                    enterAuthoredPortal(entry, agent, 1, nowMs, member);
                } else {
                    ACTIONS.stop(entry);
                }
                return;
            }
            if (agent.getItemQuantity(AgentOpqDefinition.EVEN_STRANGER_SEED, false) > 0) {
                dropAt(entry, agent, AgentOpqDefinition.EVEN_STRANGER_SEED, ROOT_POT, nowMs, member); return;
            }
            if (!session.papaPixieDefeated()
                    && agent.getItemQuantity(AgentOpqDefinition.TRANSPARENT_TRIGGER, false) > 0) {
                Reactor trigger = activeReactors(agent).stream().filter(r -> r.getId() == 2_001_016)
                        .min(Comparator.comparingDouble(r -> r.getPosition().distanceSq(agent.getPosition()))).orElse(null);
                if (trigger != null) dropAt(entry, agent, AgentOpqDefinition.TRANSPARENT_TRIGGER,
                        trigger.getPosition(), nowMs, member);
                return;
            }
            if (!session.papaPixieDefeated()
                    && agent.getItemQuantity(AgentOpqDefinition.STRANGE_SEED, false) > 0) {
                Reactor pot = activeReactors(agent).stream().filter(r -> r.getId() == 2_001_000 || r.getId() == 2_001_001)
                        .min(Comparator.comparingDouble(r -> r.getPosition().distanceSq(agent.getPosition()))).orElse(null);
                if (pot != null) dropAt(entry, agent, AgentOpqDefinition.STRANGE_SEED, pot.getPosition(), nowMs, member);
                return;
            }
        }
        if (ACTIONS.liveMonsterCount(agent, AgentOpqDefinition.GARDEN_SETUP_MOBS) > 0) {
            ACTIONS.grind(entry, AgentOpqDefinition.GARDEN_SETUP_MOBS);
        } else if (session.papaPixieDefeated()
                && collectGardenCleanupDrop(session, entry, agent, nowMs)) {
            session.markProgress(nowMs);
        } else if (session.papaPixieDefeated()) {
            ACTIONS.stop(entry);
        }
    }

    private static void tickPapaPixieCombat(AgentOpqSession session, AgentRuntimeEntry entry,
                                             Character agent, AgentOpqMemberState member,
                                             Monster papaPixie, long nowMs) {
        if (member.role() != AgentOpqMemberState.Role.BOSS_ATTACKER) {
            ACTIONS.stop(entry);
            member.assign(AgentOpqMemberState.Role.BOSS_ATTACKER, null, 0);
            log.info("OPQ Papa Pixie combat scheduled: session={} member={}({}) position={} bossPosition={}",
                    session.sessionId(), agent.getName(), agent.getId(),
                    agent.getPosition(), papaPixie.getPosition());
        }
        if (member.observeBossCombat(papaPixie.getObjectId(), papaPixie.getHp())) {
            session.markProgress(nowMs);
        }
        ACTIONS.grind(entry, Set.of(AgentOpqDefinition.PAPA_PIXIE));
    }

    private static Monster papaPixie(Character agent) {
        if (agent == null || agent.getMap() == null) return null;
        return agent.getMap().getAllMonsters().stream()
                .filter(monster -> monster.getId() == AgentOpqDefinition.PAPA_PIXIE)
                .filter(monster -> monster.getHp() > 0)
                .findFirst().orElse(null);
    }

    private static boolean collectGardenCleanupDrop(AgentOpqSession session, AgentRuntimeEntry entry,
                                                     Character agent, long nowMs) {
        if (agent.getMap() == null || agent.getPosition() == null) return false;
        MapItem target = AgentMapPerception.items(agent.getMap()).stream()
                .filter(drop -> !drop.isPickedUp())
                .filter(drop -> !AgentOpqDefinition.EXCLUSIVE_ITEMS.contains(drop.getItemId()))
                .filter(drop -> AgentLootEligibility.canBotTargetLoot(
                        entry, agent, agent.getMap(), drop, nowMs))
                .filter(drop -> nearestEligibleGardenCollector(session, drop, nowMs) == agent.getId())
                .min(Comparator.comparingDouble(drop ->
                        drop.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null) return false;
        if (near(agent.getPosition(), target.getPosition(), LOOT_RADIUS)) {
            ACTIONS.stop(entry);
            agent.pickupItem(target);
        } else {
            ACTIONS.navigate(entry, target.getPosition(), true);
        }
        return true;
    }

    private static int nearestEligibleGardenCollector(AgentOpqSession session, MapItem drop, long nowMs) {
        return session.members().stream()
                .filter(member -> member.memberType() == AgentOpqMemberState.MemberType.AGENT)
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> candidate.getMapId() == AgentOpqDefinition.GARDEN_MAP
                        && candidate.getPosition() != null)
                .filter(candidate -> {
                    AgentRuntimeEntry candidateEntry = entry(candidate.getId());
                    return candidateEntry != null && AgentLootEligibility.canBotTargetLoot(
                            candidateEntry, candidate, candidate.getMap(), drop, nowMs);
                })
                .min(Comparator.comparingDouble((Character candidate) ->
                                candidate.getPosition().distanceSq(drop.getPosition()))
                        .thenComparingInt(Character::getId))
                .map(Character::getId)
                .orElse(0);
    }

    private static boolean gardenStageDrained(Character agent) {
        return agent != null && agent.getMap() != null
                && agent.getMap().getAllMonsters().isEmpty()
                && AgentMapPerception.items(agent.getMap()).stream().noneMatch(drop -> !drop.isPickedUp());
    }

    private static void tickFinalRoot(AgentOpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() != AgentOpqDefinition.CENTER_MAP) return;
        AgentRuntimeEntry entry = entry(leader.getId());
        if (entry == null) return;
        tickRootCarrier(session, entry, leader, session.member(leader.getId()), nowMs);
    }

    private static void tickRootCarrier(AgentOpqSession session, AgentRuntimeEntry entry,
                                        Character agent, AgentOpqMemberState member, long nowMs) {
        if (agent.getMapId() != AgentOpqDefinition.CENTER_MAP) return;
        if (agent.getId() == session.eventLeaderId()
                && agent.getItemQuantity(AgentOpqDefinition.ROOT_OF_LIFE, false) > 0) {
            dropAt(entry, agent, AgentOpqDefinition.ROOT_OF_LIFE, MINERVA_BASE, nowMs, member);
            return;
        }
        if (propertyInt(agent, "statusStg8") == 1) {
            runNearbyNpc(entry, agent, AgentOpqDefinition.MINERVA_NPC);
        }
    }

    private static void tickReward(AgentOpqSession session, long nowMs) {
        boolean allOutside = session.members().stream().map(m -> character(m.characterId()))
                .filter(java.util.Objects::nonNull).allMatch(c -> c.getMapId() == AgentOpqDefinition.RECRUIT_MAP);
        if (allOutside) session.complete(nowMs);
    }

    private static void tickRewardMember(AgentOpqSession session, AgentRuntimeEntry entry,
                                         Character agent, long nowMs) {
        if (agent.getMapId() == AgentOpqDefinition.CENTER_MAP && propertyInt(agent, "statusStg8") == 1) {
            if (runNearbyNpc(entry, agent, AgentOpqDefinition.MINERVA_NPC)) session.markProgress(nowMs);
            return;
        }
        if (agent.getMapId() == AgentOpqDefinition.CLEAR_MAP
                && runNearbyNpc(entry, agent, AgentOpqDefinition.MINERVA_NPC)) session.markProgress(nowMs);
    }

    private static boolean collectReservedDrop(AgentOpqSession session, AgentRuntimeEntry entry,
                                               Character agent, Set<Integer> items, long nowMs) {
        MapItem target = AgentMapPerception.items(agent.getMap()).stream()
                .filter(drop -> !drop.isPickedUp() && items.contains(drop.getItemId()))
                .filter(drop -> AgentOpqSessionRegistry.canLootExclusive(agent, drop.getItemId()))
                .min(Comparator.comparingDouble(drop -> drop.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null) return false;
        if (near(agent.getPosition(), target.getPosition(), LOOT_RADIUS)) {
            ACTIONS.stop(entry); agent.pickupItem(target);
            if (agent.getItemQuantity(target.getItemId(), false) > 0) session.loot().pickedUp(target.getItemId(), agent.getId(), nowMs);
        } else ACTIONS.navigate(entry, target.getPosition(), true);
        return true;
    }

    private static boolean collectGroundItem(AgentRuntimeEntry entry, Character agent,
                                             int itemId, boolean reservationRequired, long nowMs) {
        MapItem target = AgentMapPerception.items(agent.getMap()).stream()
                .filter(drop -> !drop.isPickedUp() && drop.getItemId() == itemId)
                .filter(drop -> !reservationRequired || AgentOpqSessionRegistry.canLootExclusive(agent, itemId))
                .min(Comparator.comparingDouble(drop -> drop.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null) return false;
        if (near(agent.getPosition(), target.getPosition(), LOOT_RADIUS)) { ACTIONS.stop(entry); agent.pickupItem(target); }
        else ACTIONS.navigate(entry, target.getPosition(), true);
        return true;
    }

    private static boolean hitNearestLegalReactor(AgentRuntimeEntry entry, Character agent,
                                                  AgentOpqMemberState member, List<Reactor> candidates, long nowMs) {
        if (candidates == null || candidates.isEmpty()) return false;
        Reactor committed = candidates.stream().filter(r -> r.getObjectId() == member.committedReactorObjectId()
                && agent.getMapId() == member.committedReactorMapId()).findFirst().orElse(null);
        if (committed == null) {
            committed = candidates.stream().filter(Reactor::isAlive).filter(Reactor::isActive)
                    .min(Comparator.comparingDouble(r -> r.getPosition().distanceSq(agent.getPosition()))).orElse(null);
            if (committed == null) { member.clearReactor(); return false; }
            member.commitReactor(agent.getMapId(), committed.getObjectId());
        }
        if (!AgentOpqInteractionPolicy.mayHitReactor(agent.getMapId(), agent.getPosition(), ACTIONS.grounded(agent), committed)) {
            Point target = ACTIONS.groundPoint(agent.getMap(), committed.getPosition());
            if (target != null) ACTIONS.navigate(entry, target, true);
            return true;
        }
        ACTIONS.stop(entry);
        if (ACTIONS.hitReactor(agent, committed.getObjectId())) member.deferUntil(nowMs + ACTION_RETRY_MS);
        if (!committed.isAlive() || !committed.isActive()) member.clearReactor();
        return true;
    }

    private static boolean enterAuthoredPortal(AgentRuntimeEntry entry, Character agent, int portalId,
                                               long nowMs, AgentOpqMemberState member) {
        Point portal = ACTIONS.portalPosition(agent, portalId);
        if (!AgentOpqInteractionPolicy.mayEnterPortal(agent.getPosition(), portal)) {
            if (portal != null) ACTIONS.navigate(entry, portal, true);
            return false;
        }
        ACTIONS.stop(entry);
        if (!OPQ.enterPortal(agent, portalId)) return false;
        member.deferUntil(nowMs + ACTION_RETRY_MS);
        return true;
    }

    private static void exitRoom(AgentRuntimeEntry entry, Character agent, AgentOpqDefinition.Room room,
                                 long nowMs, AgentOpqMemberState member) {
        Integer portal = AgentOpqDefinition.ROOM_EXIT_PORTALS.get(room);
        if (portal != null) enterAuthoredPortal(entry, agent, portal, nowMs, member);
    }

    private static boolean runNearbyNpc(AgentRuntimeEntry entry, Character agent, int npcId) {
        if (entry == null || agent == null) return false;
        Point npc = ACTIONS.npcPosition(agent, npcId);
        if (!near(agent.getPosition(), npc, NPC_RADIUS)) {
            if (npc != null) ACTIONS.navigate(entry, npc, true);
            return false;
        }
        ACTIONS.stop(entry);
        return OPQ.runNpc(agent, npcId);
    }

    private static boolean moveTo(AgentRuntimeEntry entry, Character agent, Point target, int radius) {
        if (target == null) return false;
        if (near(agent.getPosition(), target, radius)) { ACTIONS.stop(entry); return true; }
        ACTIONS.navigate(entry, target, true); return false;
    }

    private static void dropAt(AgentRuntimeEntry entry, Character agent, int itemId, Point target,
                               long nowMs, AgentOpqMemberState member) {
        if (!moveTo(entry, agent, target, AgentOpqInteractionPolicy.ITEM_DROP_RADIUS_PX)) return;
        if (!AgentOpqInteractionPolicy.mayDropTrigger(agent.getPosition(), target)) return;
        if (dropItem(entry, InventoryType.ETC, itemId, (short) 1)) {
            member.deferUntil(nowMs + ACTION_RETRY_MS);
        }
    }

    private static void reserve(AgentOpqSession session, int item, Character agent, int mapId, long nowMs) {
        AgentOpqLootLedger.Reservation current = session.loot().reservation(item);
        if (current == null || current.state() == AgentOpqLootLedger.State.DELIVERED) {
            session.loot().reserve(item, agent.getId(), mapId, nowMs);
        }
    }

    private static boolean dropItem(AgentRuntimeEntry entry, InventoryType type, int itemId, short quantity) {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent == null || type == null) return false;
        var inventory = agent.getInventory(type);
        var item = inventory == null ? null : inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0
                || !AgentInventoryReservationRuntime.mayConsume(entry, item, System.currentTimeMillis())) return false;
        short amount = quantity <= 0 ? item.getQuantity() : (short) Math.min(quantity, item.getQuantity());
        AgentInventoryGatewayRuntime.inventory().dropItem(agent, type, item.getPosition(), amount);
        return true;
    }

    private static Point reactorPosition(Character agent, String name, Point fallback) {
        Reactor reactor = agent == null || agent.getMap() == null ? null : agent.getMap().getReactorByName(name);
        return reactor == null ? fallback : reactor.getPosition();
    }

    private static List<Reactor> activeReactors(Character agent) {
        List<Reactor> result = new ArrayList<>();
        for (Reactor reactor : ACTIONS.reactors(agent)) if (reactor != null && reactor.isAlive() && reactor.isActive()) result.add(reactor);
        return result;
    }

    private static int propertyInt(Character character, String property) {
        String value = OPQ.property(character, property);
        if (value == null) return -1;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { return -1; }
    }

    private static int partyItemCount(AgentOpqSession session, int itemId) {
        return session.members().stream().map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .mapToInt(character -> character.getItemQuantity(itemId, false)).sum();
    }

    private static Point sealedPlatform(int platform) {
        return switch (platform) {
            case 0 -> new Point(-162, -808);
            case 1 -> new Point(-40, -919);
            case 2 -> new Point(78, -813);
            default -> throw new IllegalArgumentException("invalid sealed platform");
        };
    }

    private static boolean sealedFormationReady(AgentOpqSession session, List<Character> holders) {
        for (Character holder : holders) {
            AgentOpqMemberState member = session.member(holder.getId());
            if (member == null || holder.getMapId() != AgentOpqDefinition.SEALED_MAP
                    || !near(holder.getPosition(), sealedPlatform(member.assignedPlatform()), 40)) return false;
        }
        return true;
    }

    private static int wayUpRow(int y) {
        int[] ys = {-2092,-2212,-2328,-2444,-2874,-2994,-3113,-3230,
                -3641,-3762,-3881,-4005,-4423,-4547,-4667,-4794};
        int best = -1, distance = Integer.MAX_VALUE;
        for (int i = 0; i < ys.length; i++) if (Math.abs(y - ys[i]) < distance) { distance = Math.abs(y - ys[i]); best = i; }
        return distance <= 150 ? best : -1;
    }

    private static List<int[]> sealedCombinations() {
        List<int[]> result = new ArrayList<>();
        for (int left = 0; left <= 3; left++) for (int middle = 0; middle <= 3 - left; middle++) {
            result.add(new int[]{left, middle, 3 - left - middle});
        }
        return List.copyOf(result);
    }

    private static boolean near(Point first, Point second, int radius) {
        return first != null && second != null && Math.abs(first.x - second.x) <= radius
                && Math.abs(first.y - second.y) <= radius;
    }

    static Character character(int id) {
        AgentRuntimeEntry entry = entry(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
    private static AgentRuntimeEntry entry(int id) { return AgentRuntimeRegistry.findByAgentCharacterId(id); }
}
