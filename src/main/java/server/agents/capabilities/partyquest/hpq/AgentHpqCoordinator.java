package server.agents.capabilities.partyquest.hpq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.capabilities.inventory.AgentInventoryStateRuntime;
import server.agents.capabilities.looting.AgentGrindLootStateRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.perception.AgentMapPerception;
import server.life.Monster;
import server.maps.MapItem;
import server.maps.Reactor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** HPQ-only stage coordinator using ordinary NPC, combat, loot, and reactor paths. */
final class AgentHpqCoordinator {
    private static final PrimitiveCapabilityGateway ACTIONS =
            AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway HPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final long PHASE_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.PHASE_TIMEOUT_MS");
    private static final long INTERACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.INTERACTION_RETRY_MS");
    private static final int REACTOR_DROP_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.REACTOR_DROP_RADIUS_PX");
    private static final long FLOWER_ACTIVATION_WAIT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.FLOWER_ACTIVATION_WAIT_MS");
    private static final long PREPARATION_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.PREPARATION_DELAY_MS");
    private static final int NPC_APPROACH_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.NPC_APPROACH_PX");
    private static final int GATHER_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.GATHER_RADIUS_PX");
    private static final long DEFENSE_REACTION_MAX_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.DEFENSE_REACTION_MAX_MS");
    private static final int DEFENSE_WAKE_RADIUS_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.DEFENSE_WAKE_RADIUS_PX");
    private static final int DEFENSE_GUARD_ARRIVAL_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.hpq.AgentHpqCoordinator.DEFENSE_GUARD_ARRIVAL_PX");

    private AgentHpqCoordinator() {
    }

    static void tick(AgentHpqSession session, long nowMs) {
        synchronized (session) {
            if (session.terminal()) return;
            if (nowMs - session.lastProgressAtMs() > PHASE_TIMEOUT_MS) {
                AgentHpqTerminationService.fail(session, "No HPQ progress before the phase timeout", nowMs);
                return;
            }
            Character leader = character(session.eventLeaderId());
            if (leader == null || leader.getHp() <= 0) {
                AgentHpqTerminationService.fail(session, "The HPQ event leader is unavailable", nowMs);
                return;
            }
            if (session.eventInstance() == null && HPQ.event(leader) != null) {
                session.bindEventInstance(HPQ.event(leader));
            }
            if (insideEvent(session.phase()) && session.eventInstance() != null
                    && HPQ.event(leader) != session.eventInstance()) {
                AgentHpqTerminationService.fail(
                        session, "The HPQ event ended or the leader left its instance", nowMs);
                return;
            }
            switch (session.phase()) {
                case PREPARING -> prepare(session, leader, nowMs);
                case ENTERING -> enter(session, leader, nowMs);
                case COLLECTING_SEEDS, PLANTING_SEEDS -> seeds(session, leader, nowMs);
                case DEFENDING_BUNNY -> defend(session, leader, nowMs);
                case DELIVERING_CAKES -> deliver(session, leader, nowMs);
                case BONUS_DECISION -> bonusDecision(session, leader, nowMs);
                case BONUS_FARMING -> bonus(session, leader, nowMs);
                case CLAIMING_REWARD -> claim(session, nowMs);
                case EXITING -> exit(session, nowMs);
                default -> { }
            }
        }
    }

    private static void prepare(AgentHpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentHpqDefinition.STAGE_MAP && HPQ.event(leader) != null) {
            session.transition(AgentHpqSession.Phase.ENTERING, nowMs);
            return;
        }
        if (leader.getMapId() != AgentHpqDefinition.RECRUIT_MAP) return;
        Point npc = ACTIONS.npcPosition(leader, AgentHpqDefinition.ENTRY_NPC);
        if (npc == null) return;
        boolean allGathered = true;
        for (AgentHpqMemberState member : session.members()) {
            Character participant = character(member.characterId());
            if (participant == null || participant.getMapId() != AgentHpqDefinition.RECRUIT_MAP) {
                allGathered = false;
                continue;
            }
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry == null) {
                if (!near(participant.getPosition(), npc, GATHER_RADIUS_PX)) allGathered = false;
                continue;
            }
            Point target = lobbyApproachPoint(session, member, participant, npc);
            if (!near(participant.getPosition(), target, NPC_APPROACH_PX)) {
                ACTIONS.navigate(entry, target, true);
                allGathered = false;
            }
        }
        long previousReadyAtMs = session.readyAtMs();
        long readyAtMs = preparationReadyAtMs(
                allGathered, previousReadyAtMs, nowMs, PREPARATION_DELAY_MS);
        session.setReadyAtMs(readyAtMs);
        if (!allGathered) return;
        if (previousReadyAtMs == 0L) announceReady(session);
        if (nowMs >= readyAtMs) session.transition(AgentHpqSession.Phase.ENTERING, nowMs);
    }

    private static void enter(AgentHpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentHpqDefinition.RECRUIT_MAP) {
            AgentRuntimeEntry leaderEntry = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId());
            if (leaderEntry == null) return;
            Point npc = ACTIONS.npcPosition(leader, AgentHpqDefinition.ENTRY_NPC);
            if (npc != null && !near(leader.getPosition(), npc, NPC_APPROACH_PX)) {
                ACTIONS.navigate(leaderEntry, npc, true);
                return;
            }
            if (HPQ.runNpc(leader, AgentHpqDefinition.ENTRY_NPC, 0)) {
                session.markProgress(nowMs);
            }
            return;
        }
        if (leader.getMapId() != AgentHpqDefinition.STAGE_MAP || HPQ.event(leader) == null) return;
        boolean together = session.members().stream().allMatch(member -> {
            Character character = character(member.characterId());
            return character != null && character.getMapId() == AgentHpqDefinition.STAGE_MAP
                    && HPQ.sameEvent(leader, character);
        });
        if (!together) return;
        session.bindEventInstance(HPQ.event(leader));
        session.transition(AgentHpqSession.Phase.COLLECTING_SEEDS, nowMs);
    }

    static long preparationReadyAtMs(
            boolean allGathered, long currentReadyAtMs, long nowMs, long delayMs) {
        if (!allGathered) return 0L;
        return currentReadyAtMs == 0L ? nowMs + Math.max(0L, delayMs) : currentReadyAtMs;
    }

    private static Point lobbyApproachPoint(
            AgentHpqSession session, AgentHpqMemberState member, Character character, Point npc) {
        long mixed = session.seed() + member.characterId() * 131L;
        int offset = 24 + Math.floorMod(mixed, Math.max(1, NPC_APPROACH_PX));
        Point target = new Point(npc.x - offset, npc.y);
        Point grounded = ACTIONS.groundPoint(character.getMap(), target);
        return grounded == null ? target : grounded;
    }

    private static void announceReady(AgentHpqSession session) {
        Character narrator = character(session.executionAgentId());
        if (narrator != null) {
            AgentPacketGatewayRuntime.packets().broadcastChatText(
                    narrator, "Party ready. Everyone is here. Starting HPQ in 5 seconds.", false, 0);
        }
    }

    private static void seeds(AgentHpqSession session, Character leader, long nowMs) {
        if (eventStage(leader) >= AgentHpqDefinition.seedBeds().size()) {
            assignDefenseRoles(session);
            session.transition(AgentHpqSession.Phase.DEFENDING_BUNNY, nowMs);
            return;
        }
        Set<Integer> seedIds = AgentHpqDefinition.seedBeds().stream()
                .map(AgentHpqDefinition.SeedBed::seedItemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<MapItem> floorSeeds = naturalSeedDrops(leader.getMap().getDroppedItems());
        Set<Integer> assignedDropIds = new HashSet<>();
        boolean hasSeed = false;
        int collectorIndex = 0;
        int seedlessAgents = (int) session.members().stream()
                .filter(member -> member.memberType() == AgentHpqMemberState.MemberType.AGENT)
                .map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .filter(agent -> agent.getMapId() == AgentHpqDefinition.STAGE_MAP)
                .filter(agent -> seedIds.stream().noneMatch(
                        seedId -> agent.getItemQuantity(seedId, false) > 0))
                .count();
        int sourceCollectorCount = Math.max(1,
                seedlessAgents - Math.min(seedlessAgents, floorSeeds.size()));
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != AgentHpqDefinition.STAGE_MAP) continue;
            int carriedSeed = seedIds.stream()
                    .filter(seedId -> agent.getItemQuantity(seedId, false) > 0)
                    .findFirst().orElse(0);
            if (carriedSeed > 0) {
                hasSeed = true;
                AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry);
                member.assign(AgentHpqMemberState.Role.SEED_PLANTER, carriedSeed);
                plant(session, entry, agent, member, carriedSeed, nowMs);
            } else {
                member.assign(AgentHpqMemberState.Role.SEED_COLLECTOR, member.assignedSeedItemId());
                MapItem floorSeed = nearestUnassignedDrop(
                        agent, floorSeeds, assignedDropIds);
                if (floorSeed != null) {
                    assignedDropIds.add(floorSeed.getObjectId());
                    pursueLoot(entry, floorSeed);
                } else {
                    AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry);
                    collectSeed(entry, agent, member, collectorIndex++, sourceCollectorCount, nowMs);
                }
            }
        }
        if (hasSeed && session.phase() == AgentHpqSession.Phase.COLLECTING_SEEDS) {
            session.transition(AgentHpqSession.Phase.PLANTING_SEEDS, nowMs);
        }
    }

    private static void collectSeed(AgentRuntimeEntry entry, Character agent,
                                    AgentHpqMemberState member, int collectorIndex,
                                    int collectorCount, long nowMs) {
        if (nowMs < member.nextActionAtMs()) return;
        List<Reactor> sources = new ArrayList<>();
        for (Reactor reactor : ACTIONS.reactors(agent)) {
            if (reactor != null && reactor.isAlive() && reactor.isActive()
                    && AgentHpqDefinition.SEED_SOURCE_REACTORS.contains(reactor.getId())) {
                sources.add(reactor);
            }
        }
        if (sources.isEmpty()) return;
        sources.sort(Comparator.comparingInt((Reactor reactor) -> reactor.getPosition().x)
                .thenComparingInt(Reactor::getObjectId));
        Reactor target = sources.get(distributedSourceIndex(
                collectorIndex, collectorCount, sources.size()));
        if (!near(agent.getPosition(), target.getPosition(), REACTOR_DROP_RADIUS_PX)) {
            ACTIONS.navigate(entry, target.getPosition(), true);
            return;
        }
        ACTIONS.stop(entry);
        if (ACTIONS.hitReactor(agent, target.getObjectId())) {
            member.deferUntil(nowMs + Math.max(500L, INTERACTION_RETRY_MS));
        }
    }

    static int distributedSourceIndex(int collectorIndex, int collectorCount, int sourceCount) {
        if (collectorCount <= 0 || sourceCount <= 0) {
            throw new IllegalArgumentException("positive HPQ collector and source counts are required");
        }
        int ordinal = Math.floorMod(collectorIndex, collectorCount);
        long numerator = (2L * ordinal + 1L) * sourceCount;
        return Math.min(sourceCount - 1, (int) (numerator / (2L * collectorCount)));
    }

    private static void plant(AgentHpqSession session, AgentRuntimeEntry entry, Character agent,
                              AgentHpqMemberState member, int seedItemId, long nowMs) {
        if (nowMs < member.nextActionAtMs()) return;
        AgentHpqDefinition.SeedBed bed = AgentHpqDefinition.seedBed(seedItemId);
        Point reactor = ACTIONS.reactors(agent).stream()
                .filter(Reactor::isAlive)
                .filter(candidate -> candidate.getId() == bed.reactorId())
                .filter(candidate -> bed.reactorName().equalsIgnoreCase(candidate.getName()))
                .min(Comparator.comparingDouble(candidate ->
                        candidate.getPosition().distance(agent.getPosition())))
                .map(candidate -> new Point(candidate.getPosition()))
                .orElse(null);
        if (reactor == null) return;
        // Reactor.wz item bounds are x [-19,20], y [-42,16]; stay just inside them.
        if (!near(agent.getPosition(), reactor,
                AgentHpqDefinition.FLOWER_DROP_X_PX, AgentHpqDefinition.FLOWER_DROP_Y_PX)) {
            ACTIONS.navigate(entry, reactor, true);
            return;
        }
        ACTIONS.stop(entry);
        if (AgentScriptItemActionService.dropItem(
                entry, InventoryType.ETC, seedItemId, (short) 1)) {
            inhibitSeedPickupDuringFlowerActivation(session);
            member.deferUntil(nowMs + Math.max(500L, INTERACTION_RETRY_MS));
        }
    }

    static List<MapItem> naturalSeedDrops(Collection<MapItem> drops) {
        if (drops == null || drops.isEmpty()) return List.of();
        return drops.stream()
                .filter(java.util.Objects::nonNull)
                .filter(drop -> !drop.isPickedUp() && !drop.isPlayerDrop())
                .filter(drop -> drop.getMeso() <= 0 && AgentHpqDefinition.isSeed(drop.getItemId()))
                .sorted(Comparator.comparingInt(MapItem::getObjectId))
                .toList();
    }

    private static MapItem nearestUnassignedDrop(
            Character agent, List<MapItem> drops, Set<Integer> assignedDropIds) {
        if (agent == null || agent.getPosition() == null) return null;
        return drops.stream()
                .filter(drop -> !assignedDropIds.contains(drop.getObjectId()))
                .min(Comparator
                        .comparingDouble((MapItem drop) ->
                                drop.getPosition().distanceSq(agent.getPosition()))
                        .thenComparingInt(MapItem::getObjectId))
                .orElse(null);
    }

    private static void pursueLoot(AgentRuntimeEntry entry, MapItem drop) {
        ACTIONS.grind(entry, Set.of());
        AgentGrindLootStateRuntime.setObjectiveLootTarget(entry, drop);
    }

    private static void inhibitSeedPickupDuringFlowerActivation(AgentHpqSession session) {
        int inhibitMs = (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, FLOWER_ACTIVATION_WAIT_MS));
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry != null) {
                AgentInventoryStateRuntime.setLootInhibitMs(entry,
                        Math.max(AgentInventoryStateRuntime.lootInhibitMs(entry), inhibitMs));
            }
        }
    }

    private static void assignDefenseRoles(AgentHpqSession session) {
        for (AgentHpqMemberState member : session.members()) {
            AgentGrindLootStateRuntime.clearObjectiveLootTarget(
                    AgentRuntimeRegistry.findByAgentCharacterId(member.characterId()));
            if (member.characterId() == session.eventLeaderId()) {
                member.assign(AgentHpqMemberState.Role.EVENT_LEADER, 0);
            } else {
                member.assign(AgentHpqMemberState.Role.BUNNY_GUARD, 0);
            }
            if (member.memberType() == AgentHpqMemberState.MemberType.AGENT) {
                member.deferUntil(0L);
            }
        }
    }

    private static void defend(AgentHpqSession session, Character leader, long nowMs) {
        Set<Integer> monsters = stageMonsters(leader);
        List<Monster> liveHostiles = AgentMapPerception.monsters(leader.getMap()).stream()
                .filter(java.util.Objects::nonNull)
                .filter(Monster::isAlive)
                .filter(monster -> monsters.contains(monster.getId()))
                .toList();
        boolean hostilesPresent = !liveHostiles.isEmpty();
        if (session.observeDefenseHostiles(hostilesPresent)) {
            assignDefenseReactionDelays(session, nowMs);
        }
        List<MapItem> cakes = leader.getMap().getDroppedItems().stream()
                .filter(drop -> drop != null && !drop.isPickedUp() && drop.getMeso() <= 0
                        && drop.getItemId() == AgentHpqDefinition.RICE_CAKE)
                .toList();
        List<AgentHpqMemberState> agents = session.members().stream()
                .filter(member -> member.memberType() == AgentHpqMemberState.MemberType.AGENT)
                .toList();
        boolean centerReserved = agents.stream().anyMatch(
                member -> member.characterId() == session.eventLeaderId());
        int ordinaryCount = agents.size() - (centerReserved ? 1 : 0);
        int ordinaryOrdinal = 0;
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null) continue;

            boolean eventLeader = centerReserved
                    && member.characterId() == session.eventLeaderId();
            Point authoredPost = eventLeader
                    ? AgentHpqDefinition.defenseGuardPoints().get(2)
                    : defenseGuardPoint(ordinaryOrdinal++, ordinaryCount, centerReserved);
            Point guardPost = ACTIONS.groundPoint(agent.getMap(), authoredPost);
            if (guardPost == null) guardPost = authoredPost;
            boolean hostileClose = hostileNear(agent, liveHostiles, DEFENSE_WAKE_RADIUS_PX);
            if (!near(agent.getPosition(), guardPost, DEFENSE_GUARD_ARRIVAL_PX)
                    && !hostileClose) {
                AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry);
                ACTIONS.navigate(entry, guardPost, true);
                continue;
            }
            if (hostilesPresent && nowMs < member.nextActionAtMs() && !hostileClose) {
                ACTIONS.stop(entry);
                continue;
            }

            MapItem cake = member.characterId() == session.eventLeaderId()
                    ? nearestUnassignedDrop(agent, cakes, Set.of()) : null;
            if (hostilesPresent || cake != null) {
                ACTIONS.grind(entry, hostilesPresent ? monsters : Set.of());
                if (cake != null) AgentGrindLootStateRuntime.setObjectiveLootTarget(entry, cake);
                else AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry);
            } else {
                AgentGrindLootStateRuntime.clearObjectiveLootTarget(entry);
                ACTIONS.stop(entry);
            }
        }
        if (leader.getItemQuantity(AgentHpqDefinition.RICE_CAKE, false)
                >= AgentHpqDefinition.REQUIRED_RICE_CAKES) {
            session.transition(AgentHpqSession.Phase.DELIVERING_CAKES, nowMs);
        }
    }

    private static void assignDefenseReactionDelays(AgentHpqSession session, long nowMs) {
        int agentOrdinal = 0;
        long waveSeed = session.seed()
                ^ (session.defenseWaveOrdinal() * 0x94D049BB133111EBL);
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            member.deferUntil(nowMs + defenseReactionDelayMs(
                    waveSeed, member.characterId(), agentOrdinal++, DEFENSE_REACTION_MAX_MS));
        }
    }

    static Point defenseGuardPoint(
            int ordinal, int participantCount, boolean centerReserved) {
        List<Point> points = new ArrayList<>(AgentHpqDefinition.defenseGuardPoints());
        if (centerReserved) points.remove(2);
        int index = distributedSourceIndex(ordinal, Math.max(1, participantCount), points.size());
        return new Point(points.get(index));
    }

    static long defenseReactionDelayMs(
            long seed, int characterId, int ordinal, long maximumDelayMs) {
        if (maximumDelayMs <= 0L || Math.floorMod(ordinal, 3) == 0) return 0L;
        long minimumDelayMs = Math.min(450L, maximumDelayMs);
        long span = maximumDelayMs - minimumDelayMs + 1L;
        long mixed = seed ^ (characterId * 0x9E3779B97F4A7C15L)
                ^ (ordinal * 0xBF58476D1CE4E5B9L);
        return minimumDelayMs + Math.floorMod(mixed, span);
    }

    private static boolean hostileNear(
            Character agent, List<Monster> hostiles, int radiusPx) {
        if (agent == null || agent.getPosition() == null || radiusPx < 0) return false;
        long radiusSquared = (long) radiusPx * radiusPx;
        for (Monster monster : hostiles) {
            if (monster != null && monster.getPosition() != null
                    && monster.getPosition().distanceSq(agent.getPosition()) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    private static void deliver(AgentHpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentHpqDefinition.CLEAR_MAP) {
            session.freezeRewardEligibility();
            session.transition(AgentHpqSession.Phase.BONUS_DECISION, nowMs);
            return;
        }
        AgentRuntimeEntry leaderEntry = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId());
        if (leaderEntry == null) return;
        Point growlie = ACTIONS.npcPosition(leader, AgentHpqDefinition.GROWLIE_NPC);
        if (growlie != null && !near(leader.getPosition(), growlie, 80)) {
            ACTIONS.navigate(leaderEntry, growlie, true);
            return;
        }
        HPQ.runNpc(leader, AgentHpqDefinition.GROWLIE_NPC, 1);
    }

    private static void bonusDecision(AgentHpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentHpqDefinition.BONUS_MAP) {
            session.transition(AgentHpqSession.Phase.BONUS_FARMING, nowMs);
            return;
        }
        boolean agentLeader = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId()) != null;
        if (!agentLeader) {
            boolean waiting = leader.getMapId() == AgentHpqDefinition.CLEAR_MAP
                    && nowMs - session.phaseEnteredAtMs() < AgentHpqBonusPolicy.humanDecisionMs();
            if (!waiting) session.transition(AgentHpqSession.Phase.CLAIMING_REWARD, nowMs);
            return;
        }
        if (session.bonusMode() == AgentHpqSession.BonusMode.ENTER) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId());
            Point tommy = ACTIONS.npcPosition(leader, AgentHpqDefinition.TOMMY_NPC);
            if (entry != null && tommy != null && !near(leader.getPosition(), tommy, 80)) {
                ACTIONS.navigate(entry, tommy, true);
                return;
            }
            HPQ.runNpc(leader, AgentHpqDefinition.TOMMY_NPC);
            return;
        }
        session.transition(AgentHpqSession.Phase.CLAIMING_REWARD, nowMs);
    }

    private static void bonus(AgentHpqSession session, Character leader, long nowMs) {
        boolean exit = nowMs - session.phaseEnteredAtMs() >= AgentHpqBonusPolicy.dwellMs()
                || leader.getMapId() != AgentHpqDefinition.BONUS_MAP;
        boolean agentRemaining = false;
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != AgentHpqDefinition.BONUS_MAP) continue;
            agentRemaining = true;
            if (exit) HPQ.runNpc(agent, AgentHpqDefinition.TOMMY_NPC);
            else ACTIONS.grind(entry, ACTIONS.configuredMonsterSpawnIds(agent));
        }
        if (!agentRemaining) session.transition(AgentHpqSession.Phase.CLAIMING_REWARD, nowMs);
    }

    private static void claim(AgentHpqSession session, long nowMs) {
        session.freezeRewardEligibility();
        boolean nonLeaderRewardsResolved = session.members().stream()
                .filter(member -> member.characterId() != session.eventLeaderId())
                .allMatch(AgentHpqMemberState::rewardResolved);
        for (AgentHpqMemberState member : session.members()) {
            if (member.rewardResolved()) continue;
            Character participant = character(member.characterId());
            if (participant == null) continue;
            if (participant.getMapId() == AgentHpqDefinition.CLEAR_MAP
                    || participant.getMapId() == AgentHpqDefinition.REWARD_EXIT_MAP) {
                if (member.memberType() == AgentHpqMemberState.MemberType.HUMAN) {
                    if (nowMs >= member.nextActionAtMs()) {
                        participant.dropMessage(6, "Talk to Tory to claim your HPQ reward and return to Henesys.");
                        member.deferUntil(nowMs + 5_000L);
                    }
                    continue;
                }
                if (member.characterId() == session.eventLeaderId() && !nonLeaderRewardsResolved) {
                    continue;
                }
                AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(
                        member.characterId());
                Point tory = ACTIONS.npcPosition(participant, AgentHpqDefinition.ENTRY_NPC);
                if (entry != null && tory != null
                        && !near(participant.getPosition(), tory, NPC_APPROACH_PX)) {
                    ACTIONS.navigate(entry, tory, true);
                } else if (tory != null) {
                    HPQ.runNpc(participant, AgentHpqDefinition.ENTRY_NPC);
                }
            }
        }
        if (session.allRewardsResolved()) session.transition(AgentHpqSession.Phase.EXITING, nowMs);
    }

    private static void exit(AgentHpqSession session, long nowMs) {
        boolean allGathered = true;
        Character narrator = null;
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != AgentHpqDefinition.RECRUIT_MAP) {
                allGathered = false;
                continue;
            }
            if (narrator == null) narrator = agent;
            Point tory = ACTIONS.npcPosition(agent, AgentHpqDefinition.ENTRY_NPC);
            if (tory == null) {
                allGathered = false;
                continue;
            }
            Point target = lobbyApproachPoint(session, member, agent, tory);
            if (!near(agent.getPosition(), target, NPC_APPROACH_PX)) {
                ACTIONS.navigate(entry, target, true);
                allGathered = false;
            }
        }
        if (!allGathered) return;
        for (AgentHpqMemberState member : session.members()) {
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (entry != null) ACTIONS.stop(entry);
        }
        if (narrator != null) {
            AgentPacketGatewayRuntime.packets().broadcastChatText(
                    narrator, "HPQ complete. Waiting at Tory for the next run.", false, 0);
        }
        AgentHpqTerminationService.complete(session, nowMs);
    }

    private static int eventStage(Character character) {
        String value = HPQ.property(character, "stage");
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static Set<Integer> stageMonsters(Character character) {
        Set<Integer> monsters = new LinkedHashSet<>(ACTIONS.configuredMonsterSpawnIds(character));
        monsters.remove(AgentHpqDefinition.MOON_BUNNY);
        return Set.copyOf(monsters);
    }

    private static boolean near(Point first, Point second, int radius) {
        return near(first, second, radius, radius);
    }

    private static boolean near(Point first, Point second, int horizontalRadius, int verticalRadius) {
        return first != null && second != null
                && Math.abs(first.x - second.x) <= horizontalRadius
                && Math.abs(first.y - second.y) <= verticalRadius;
    }

    private static boolean insideEvent(AgentHpqSession.Phase phase) {
        return switch (phase) {
            case COLLECTING_SEEDS, PLANTING_SEEDS, DEFENDING_BUNNY,
                    DELIVERING_CAKES -> true;
            default -> false;
        };
    }

    private static Character character(int characterId) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(characterId);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent
                : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(characterId);
    }
}
