package server.agents.capabilities.partyquest.hpq;

import client.Character;
import client.inventory.InventoryType;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.plans.AgentScriptItemActionService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.maps.Reactor;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
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
        AgentRuntimeEntry leaderEntry = AgentRuntimeRegistry.findByAgentCharacterId(leader.getId());
        if (leaderEntry != null) {
            Point npc = ACTIONS.npcPosition(leader, AgentHpqDefinition.ENTRY_NPC);
            if (npc != null && !near(leader.getPosition(), npc, 80)) {
                ACTIONS.navigate(leaderEntry, npc, true);
                return;
            }
            if (HPQ.runNpc(leader, AgentHpqDefinition.ENTRY_NPC, 0)) {
                session.transition(AgentHpqSession.Phase.ENTERING, nowMs);
            }
        }
    }

    private static void enter(AgentHpqSession session, Character leader, long nowMs) {
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

    private static void seeds(AgentHpqSession session, Character leader, long nowMs) {
        if (eventStage(leader) >= AgentHpqDefinition.seedBeds().size()) {
            assignDefenseRoles(session);
            session.transition(AgentHpqSession.Phase.DEFENDING_BUNNY, nowMs);
            return;
        }
        // MapleMap activates type-100 item reactors five seconds after a drop. During
        // that window nobody may run the broad PQ seed-loot scan or the planted item
        // will be picked back up before the flower consumes it.
        if (session.members().stream().anyMatch(member ->
                member.role() == AgentHpqMemberState.Role.SEED_PLANTER
                        && member.nextActionAtMs() > nowMs)) {
            return;
        }
        Set<Integer> seedIds = AgentHpqDefinition.seedBeds().stream()
                .map(AgentHpqDefinition.SeedBed::seedItemId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        boolean hasSeed = false;
        int collectorIndex = 0;
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null || agent.getMapId() != AgentHpqDefinition.STAGE_MAP) continue;
            HPQ.lootNearby(agent, seedIds);
            int carriedSeed = seedIds.stream()
                    .filter(seedId -> agent.getItemQuantity(seedId, false) > 0)
                    .findFirst().orElse(0);
            if (carriedSeed > 0) {
                hasSeed = true;
                member.assign(AgentHpqMemberState.Role.SEED_PLANTER, carriedSeed);
                plant(entry, agent, member, carriedSeed, nowMs);
            } else {
                member.assign(AgentHpqMemberState.Role.SEED_COLLECTOR, member.assignedSeedItemId());
                collectSeed(entry, agent, member, collectorIndex++, nowMs);
            }
        }
        if (hasSeed && session.phase() == AgentHpqSession.Phase.COLLECTING_SEEDS) {
            session.transition(AgentHpqSession.Phase.PLANTING_SEEDS, nowMs);
        }
    }

    private static void collectSeed(AgentRuntimeEntry entry, Character agent,
                                    AgentHpqMemberState member, int collectorIndex, long nowMs) {
        if (nowMs < member.nextActionAtMs()) return;
        List<Reactor> sources = new ArrayList<>();
        for (Reactor reactor : ACTIONS.reactors(agent)) {
            if (reactor != null && reactor.isAlive() && reactor.isActive()
                    && AgentHpqDefinition.SEED_SOURCE_REACTORS.contains(reactor.getId())) {
                sources.add(reactor);
            }
        }
        if (sources.isEmpty()) return;
        sources.sort(Comparator.comparingInt(Reactor::getObjectId));
        Reactor target = sources.get(Math.floorMod(collectorIndex, sources.size()));
        if (!near(agent.getPosition(), target.getPosition(), REACTOR_DROP_RADIUS_PX)) {
            ACTIONS.navigate(entry, target.getPosition(), true);
            return;
        }
        ACTIONS.stop(entry);
        if (ACTIONS.hitReactor(agent, target.getObjectId())) {
            member.deferUntil(nowMs + Math.max(500L, INTERACTION_RETRY_MS));
        }
    }

    private static void plant(AgentRuntimeEntry entry, Character agent,
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
            member.deferUntil(nowMs + FLOWER_ACTIVATION_WAIT_MS);
        }
    }

    private static void assignDefenseRoles(AgentHpqSession session) {
        for (AgentHpqMemberState member : session.members()) {
            if (member.characterId() == session.eventLeaderId()) {
                member.assign(AgentHpqMemberState.Role.EVENT_LEADER, 0);
            } else {
                member.assign(AgentHpqMemberState.Role.BUNNY_GUARD, 0);
            }
        }
    }

    private static void defend(AgentHpqSession session, Character leader, long nowMs) {
        Set<Integer> monsters = stageMonsters(leader);
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(member.characterId());
            if (agent == null || entry == null) continue;
            if (member.characterId() == session.eventLeaderId()) {
                HPQ.lootNearby(agent, Set.of(AgentHpqDefinition.RICE_CAKE));
            } else {
                ACTIONS.grind(entry, monsters);
            }
        }
        if (leader.getItemQuantity(AgentHpqDefinition.RICE_CAKE, false)
                >= AgentHpqDefinition.REQUIRED_RICE_CAKES) {
            session.transition(AgentHpqSession.Phase.DELIVERING_CAKES, nowMs);
        }
    }

    private static void deliver(AgentHpqSession session, Character leader, long nowMs) {
        if (leader.getMapId() == AgentHpqDefinition.CLEAR_MAP) {
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
        boolean agentRemaining = false;
        for (AgentHpqMemberState member : session.members()) {
            if (member.memberType() != AgentHpqMemberState.MemberType.AGENT) continue;
            Character agent = character(member.characterId());
            if (agent == null) continue;
            if (agent.getMapId() == AgentHpqDefinition.CLEAR_MAP
                    || agent.getMapId() == AgentHpqDefinition.REWARD_EXIT_MAP) {
                agentRemaining = true;
                HPQ.runNpc(agent, AgentHpqDefinition.ENTRY_NPC);
            }
        }
        if (!agentRemaining) session.transition(AgentHpqSession.Phase.EXITING, nowMs);
    }

    private static void exit(AgentHpqSession session, long nowMs) {
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
