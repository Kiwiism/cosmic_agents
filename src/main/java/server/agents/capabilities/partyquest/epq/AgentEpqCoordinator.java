package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.inventory.Item;
import client.inventory.InventoryType;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.capabilities.combat.AgentAttackDamageProfileService;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackTransactionResult;
import server.agents.capabilities.combat.AgentBasicAttackPlanRuntime;
import server.agents.capabilities.combat.AgentCombatAttackRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PartyQuestGateway;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.perception.AgentMapPerception;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.life.Monster;
import server.maps.Portal;
import server.maps.Reactor;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** EPQ stage coordinator. Every action follows the authored field interaction. */
public final class AgentEpqCoordinator {
    private static final PrimitiveCapabilityGateway ACTIONS = AgentPrimitiveCapabilityGatewayRuntime.gateway();
    private static final PartyQuestGateway EPQ = AgentPartyQuestGatewayRuntime.partyQuest();
    private static final long ACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.ACTION_RETRY_MS");
    private static final long EVENT_TIMEOUT_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.EVENT_TIMEOUT_MS");
    private static final long BOSS_LOOT_SETTLE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.BOSS_LOOT_SETTLE_MS");
    private static final long ITEM_REACTOR_SETTLE_MS = 5_500L;
    private static final int ITEM_REACTOR_DROP_RADIUS = 40;
    private static final int PORTAL_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.PORTAL_RADIUS");
    private static final int NPC_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.NPC_RADIUS");
    private static final int REACTOR_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.REACTOR_RADIUS");

    private AgentEpqCoordinator() { }

    public static void tickSession(AgentEpqSession session, long nowMs) {
        if (nowMs - session.startedAtMs() >= EVENT_TIMEOUT_MS && !session.terminal()) {
            session.fail("EPQ event timer expired", nowMs);
            return;
        }
        Character leader = character(session.eventLeaderId());
        if (leader == null) {
            AgentEpqWatchdogRuntime.tick(session, nowMs);
            return;
        }
        if (session.eventInstance() == null && leader.getEventInstance() != null
                && AgentEpqDefinition.isEventMap(leader.getMapId())) {
            session.bindEventInstance(leader.getEventInstance());
            session.markProgress(nowMs);
        }
        AgentEpqSession.Phase observed = phaseForMap(leader.getMapId());
        if (observed != null) session.transition(observed, nowMs);
        session.observeProgressSignature(progressSignature(session, leader), nowMs);
        AgentEpqWatchdogRuntime.tick(session, nowMs);
        if (session.phase() == AgentEpqSession.Phase.EXITING
                && session.members().stream().map(member -> character(member.characterId()))
                .filter(java.util.Objects::nonNull)
                .noneMatch(member -> AgentEpqDefinition.isEventMap(member.getMapId()))) {
            session.complete(nowMs);
        }
    }

    public static void tickMember(
            AgentEpqSession session, AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentEpqMemberState member = session.member(agent.getId());
        if (member == null || member.memberType() != AgentEpqMemberState.MemberType.AGENT
                || nowMs < member.nextActionAtMs()) return;
        if (session.eventInstance() != null && AgentEpqDefinition.isEventMap(agent.getMapId())
                && agent.getEventInstance() != session.eventInstance()) {
            session.fail("EPQ member entered a different event instance", nowMs);
            return;
        }

        switch (agent.getMapId()) {
            case AgentEpqDefinition.RECRUIT_MAP -> enterEvent(session, entry, agent, member, nowMs);
            case AgentEpqDefinition.ENTRANCE_MAP -> enterPortal(entry, agent, 4, member, nowMs);
            case AgentEpqDefinition.STAGE_ONE_MAP -> stageOne(entry, agent, member, nowMs);
            case AgentEpqDefinition.STAGE_TWO_MAP -> stageTwo(session, entry, agent, member, nowMs);
            case AgentEpqDefinition.STAGE_THREE_MAP -> stageThree(entry, agent, member, nowMs);
            case AgentEpqDefinition.STAGE_FOUR_MAP -> stageFour(session, entry, agent, member, nowMs);
            case AgentEpqDefinition.STAGE_FIVE_MAP -> stageFive(session, entry, agent, member, nowMs);
            case AgentEpqDefinition.BOSS_MAP -> boss(session, entry, agent, member, nowMs);
            case AgentEpqDefinition.REWARD_MAP -> reward(session, entry, agent, member, nowMs);
            default -> {
                ACTIONS.stop(entry);
                if (session.phase() == AgentEpqSession.Phase.EXITING) session.markProgress(nowMs);
            }
        }
    }

    private static void enterEvent(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                                   AgentEpqMemberState member, long nowMs) {
        if (agent.getId() != session.eventLeaderId()) { ACTIONS.stop(entry); return; }
        if (runNearbyNpc(entry, agent, AgentEpqDefinition.ENTRY_NPC, 0)) {
            member.deferUntil(nowMs + ACTION_RETRY_MS);
            session.transition(AgentEpqSession.Phase.ENTERING, nowMs);
        }
    }

    private static void stageOne(AgentRuntimeEntry entry, Character agent,
                                 AgentEpqMemberState member, long nowMs) {
        if (ACTIONS.liveMonsterCount(agent, Set.of(AgentEpqDefinition.STAGE_ONE_MOB)) > 0) {
            ACTIONS.grind(entry, Set.of(AgentEpqDefinition.STAGE_ONE_MOB));
        } else {
            enterPortal(entry, agent, 3, member, nowMs);
        }
    }

    private static void stageTwo(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                                 AgentEpqMemberState member, long nowMs) {
        boolean carrier = agent.getId() == workAgentId(session);
        if (carrier) ACTIONS.lootNearby(agent,
                Set.of(AgentEpqDefinition.POISON, AgentEpqDefinition.PURIFIED_POISON));
        Reactor spine = agent.getMap().getReactorById(AgentEpqDefinition.SPINE_REACTOR);
        if (spine != null && spine.getState() >= 4) {
            enterPortal(entry, agent, 3, member, nowMs);
            return;
        }
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.PURIFIED_POISON) > 0 && spine != null) {
            dropAt(session, entry, agent, InventoryType.ETC, AgentEpqDefinition.PURIFIED_POISON,
                    spine.getPosition(), member, nowMs);
            return;
        }
        Reactor pond = agent.getMap().getReactorById(AgentEpqDefinition.POND_REACTOR);
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.POISON) > 0 && pond != null) {
            dropAt(session, entry, agent, InventoryType.ETC, AgentEpqDefinition.POISON,
                    pond.getPosition(), member, nowMs);
            return;
        }
        if (ACTIONS.liveMonsterCount(agent, Set.of(AgentEpqDefinition.STAGE_TWO_MOB)) > 0) {
            ACTIONS.grind(entry, Set.of(AgentEpqDefinition.STAGE_TWO_MOB));
        } else if (carrier) {
            ACTIONS.stop(entry);
            agent.getMap().instanceMapForceRespawn();
            member.deferUntil(nowMs + ACTION_RETRY_MS);
        } else {
            ACTIONS.stop(entry);
        }
    }

    private static void stageThree(AgentRuntimeEntry entry, Character agent,
                                   AgentEpqMemberState member, long nowMs) {
        Point coordinator = npcApproachPoint(agent, AgentEpqDefinition.STAGE_NPC);
        if (near(agent.getPosition(), coordinator, 260)) {
            if (runNearbyNpc(entry, agent, AgentEpqDefinition.STAGE_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        Portal next = agent.getMap().getPortals().stream()
                .filter(portal -> portal.getScriptName() != null
                        && portal.getScriptName().startsWith("party6_stage5"))
                .min(Comparator.comparingDouble(portal -> portal.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (next == null) { ACTIONS.stop(entry); return; }
        enterPortal(entry, agent, next.getId(), member, nowMs);
    }

    private static void stageFour(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                                  AgentEpqMemberState member, long nowMs) {
        int monsterMarbles = ACTIONS.itemCount(agent, AgentEpqDefinition.MONSTER_MARBLE);
        if (agent.getId() == session.eventLeaderId() && monsterMarbles >= 20) {
            if (runNearbyNpc(entry, agent, AgentEpqDefinition.STAGE_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        if (agent.getId() != session.eventLeaderId() && monsterMarbles >= 20) {
            dropStackNearNpc(entry, agent, AgentEpqDefinition.MONSTER_MARBLE,
                    AgentEpqDefinition.STAGE_NPC, member, nowMs);
            if (ACTIONS.itemCount(agent, AgentEpqDefinition.MONSTER_MARBLE) == 0) {
                announce(member, agent, "stage4-human-handoff",
                        "I dropped all 20 Monster Marbles beside the stage NPC for our leader.");
            }
            return;
        }
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.PURIFICATION_MARBLE) == 0) {
            if (runNearbyNpc(entry, agent, AgentEpqDefinition.STAGE_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        Monster target = flowers(agent).stream()
                .min(Comparator.comparingDouble(mob -> mob.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (target == null || agent.getId() != captureAgentId(session, target)) {
            ACTIONS.stop(entry);
            return;
        }
        if (!near(agent.getPosition(), target.getPosition(), 220)) {
            ACTIONS.navigate(entry, target.getPosition(), true);
            return;
        }
        ACTIONS.stop(entry);
        if (AgentEpqCaptureRuntime.ready(target)) {
            if (AgentEpqCaptureRuntime.capture(agent, target)) {
                member.deferUntil(nowMs + ACTION_RETRY_MS);
            }
            return;
        }
        AgentAttackPlan basic = AgentBasicAttackPlanRuntime.planBasicAttack(agent, target);
        if (basic == null || basic.primaryTarget() != target
                || conservativeMaximumDamage(agent, basic) >= target.getHp()) {
            member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        AgentAttackTransactionResult attack = AgentCombatAttackRuntime.attackMonster(entry, agent, basic);
        if (attack.committed()) session.markProgress(nowMs);
    }

    private static void stageFive(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                                  AgentEpqMemberState member, long nowMs) {
        int magicStones = ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE);
        if (magicStones > 0) {
            if (agent.getId() == session.eventLeaderId()) {
                if (runNearbyNpc(entry, agent, AgentEpqDefinition.STONE_NPC)) {
                    member.deferUntil(nowMs + ACTION_RETRY_MS);
                }
            } else {
                dropStackNearNpc(entry, agent, AgentEpqDefinition.MAGIC_STONE,
                        AgentEpqDefinition.STONE_NPC, member, nowMs);
                if (ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE) == 0) {
                    announce(member, agent, "stage5-human-handoff",
                            "I dropped the Magic Stone beside Yuris. Leader, please pick it up and continue.");
                }
            }
            return;
        }
        if (!mayCollectStageFiveStone(session, agent.getId())) { ACTIONS.stop(entry); return; }
        ACTIONS.lootNearby(agent, Set.of(AgentEpqDefinition.MAGIC_STONE));
        Reactor target = nearestActiveReactor(agent,
                Set.of(AgentEpqDefinition.STONE_BOX, AgentEpqDefinition.EMPTY_BOX));
        if (target == null) { ACTIONS.stop(entry); return; }
        hitReactor(entry, agent, target, member, nowMs);
    }

    static boolean mayCollectStageFiveStone(AgentEpqSession session, int characterId) {
        return session != null && characterId == workAgentId(session);
    }

    private static void boss(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                             AgentEpqMemberState member, long nowMs) {
        Set<Integer> bossLoot = agent.getId() == fragmentCollectorId(session)
                ? Set.of(AgentEpqDefinition.MAGIC_STONE, AgentEpqDefinition.ALTAIRE_FRAGMENT)
                : Set.of(AgentEpqDefinition.MAGIC_STONE);
        ACTIONS.lootNearby(agent, bossLoot);
        if (ACTIONS.liveMonsterCount(agent, AgentEpqDefinition.BOSS_MOBS) > 0) {
            ACTIONS.grind(entry, AgentEpqDefinition.BOSS_COMBAT_TARGETS);
            return;
        }
        if (session.eventInstance() == null || !session.eventInstance().isEventCleared()) {
            if (agent.getId() == session.eventLeaderId()
                    && ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE) > 0) {
                Reactor altar = agent.getMap().getReactorById(AgentEpqDefinition.ALTAR_REACTOR);
                if (altar != null) dropAt(session, entry, agent, InventoryType.ETC,
                        AgentEpqDefinition.MAGIC_STONE, altar.getPosition(), member, nowMs);
            } else {
                ACTIONS.stop(entry);
            }
            return;
        }
        AgentMapPerception.monsters(agent.getMap()).stream()
                .filter(Monster::isAlive)
                .filter(monster -> monster.getId() == AgentEpqDefinition.POST_DEATH_DUMMY)
                .findFirst().ifPresent(dummy ->
                        agent.getMap().killMonster(dummy, null, false, (short) 0));
        if (nowMs - session.observeBossCleared(nowMs) < BOSS_LOOT_SETTLE_MS) { ACTIONS.stop(entry); return; }
        if (agent.getId() == workAgentId(session)
                && session.members().stream().anyMatch(state ->
                state.memberType() == AgentEpqMemberState.MemberType.HUMAN)) {
            announce(member, agent, "boss-human-fragment",
                    "The Altair Fragment is reserved for our human party member; loot it before exiting.");
        }
        enterPortal(entry, agent, 1, member, nowMs);
    }

    private static void reward(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                               AgentEpqMemberState member, long nowMs) {
        if (!session.rewardHit() && agent.getId() == workAgentId(session)) {
            Reactor reward = agent.getMap().getReactorById(AgentEpqDefinition.REWARD_REACTOR);
            if (reward != null && reward.isActive()) {
                if (hitReactor(entry, agent, reward, member, nowMs)) session.markRewardHit(nowMs);
                return;
            }
            session.markRewardHit(nowMs);
        }
        if (!session.rewardHit()) { ACTIONS.stop(entry); return; }
        if (enterPortal(entry, agent, 1, member, nowMs)) {
            session.transition(AgentEpqSession.Phase.EXITING, nowMs);
        }
    }

    private static AgentEpqSession.Phase phaseForMap(int mapId) {
        return switch (mapId) {
            case AgentEpqDefinition.RECRUIT_MAP -> AgentEpqSession.Phase.PREPARING;
            case AgentEpqDefinition.ENTRANCE_MAP -> AgentEpqSession.Phase.ENTERING;
            case AgentEpqDefinition.STAGE_ONE_MAP -> AgentEpqSession.Phase.STAGE_ONE;
            case AgentEpqDefinition.STAGE_TWO_MAP -> AgentEpqSession.Phase.STAGE_TWO;
            case AgentEpqDefinition.STAGE_THREE_MAP -> AgentEpqSession.Phase.STAGE_THREE;
            case AgentEpqDefinition.STAGE_FOUR_MAP -> AgentEpqSession.Phase.STAGE_FOUR;
            case AgentEpqDefinition.STAGE_FIVE_MAP -> AgentEpqSession.Phase.STAGE_FIVE;
            case AgentEpqDefinition.BOSS_MAP -> AgentEpqSession.Phase.BOSS;
            case AgentEpqDefinition.REWARD_MAP -> AgentEpqSession.Phase.REWARD;
            default -> null;
        };
    }

    private static List<Monster> flowers(Character agent) {
        return AgentMapPerception.monsters(agent.getMap()).stream()
                .filter(Monster::isAlive).filter(mob -> mob.getId() == AgentEpqDefinition.POISON_FLOWER).toList();
    }

    private static int captureAgentId(AgentEpqSession session, Monster flower) {
        int nearestSafeId = 0;
        double nearestSafeDistance = Double.POSITIVE_INFINITY;
        int selectedId = 0;
        long selectedDamage = Long.MAX_VALUE;
        for (AgentEpqMemberState member : session.members()) {
            if (member.memberType() != AgentEpqMemberState.MemberType.AGENT) continue;
            Character candidate = character(member.characterId());
            if (candidate == null || candidate.getMap() != flower.getMap()) continue;
            AgentAttackPlan plan = AgentBasicAttackPlanRuntime.planBasicAttack(candidate, flower);
            long damage = plan == null || plan.primaryTarget() != flower
                    ? Long.MAX_VALUE : conservativeMaximumDamage(candidate, plan);
            if (damage < selectedDamage) {
                selectedDamage = damage;
                selectedId = member.characterId();
            }
            if (damage < flower.getHp()) {
                double distance = candidate.getPosition().distanceSq(flower.getPosition());
                if (distance < nearestSafeDistance) {
                    nearestSafeDistance = distance;
                    nearestSafeId = member.characterId();
                }
            }
        }
        if (nearestSafeId != 0) return nearestSafeId;
        return selectedId == 0 ? workAgentId(session) : selectedId;
    }

    private static long conservativeMaximumDamage(Character agent, AgentAttackPlan plan) {
        var profile = AgentAttackDamageProfileService.resolve(agent, plan);
        return Math.max(1L, (long) profile.maxDamage() * Math.max(1, plan.numDamage));
    }

    private static int workAgentId(AgentEpqSession session) {
        AgentEpqMemberState leader = session.member(session.eventLeaderId());
        if (leader != null && leader.memberType() == AgentEpqMemberState.MemberType.AGENT) {
            return leader.characterId();
        }
        return session.members().stream()
                .filter(member -> member.memberType() == AgentEpqMemberState.MemberType.AGENT)
                .mapToInt(AgentEpqMemberState::characterId).min().orElse(session.executionAgentId());
    }

    private static int fragmentCollectorId(AgentEpqSession session) {
        return session.members().stream()
                .filter(member -> member.memberType() == AgentEpqMemberState.MemberType.HUMAN)
                .mapToInt(AgentEpqMemberState::characterId).findFirst()
                .orElseGet(() -> workAgentId(session));
    }

    private static long progressSignature(AgentEpqSession session, Character leader) {
        long signature = session.phase().ordinal();
        for (AgentEpqMemberState member : session.members()) {
            Character character = character(member.characterId());
            signature = signature * 31L + (character == null ? -1 : character.getMapId());
            if (character != null) {
                signature = signature * 31L + ACTIONS.itemCount(character, AgentEpqDefinition.POISON);
                signature = signature * 31L + ACTIONS.itemCount(character, AgentEpqDefinition.PURIFIED_POISON);
                signature = signature * 31L + ACTIONS.itemCount(character, AgentEpqDefinition.MONSTER_MARBLE);
                signature = signature * 31L + ACTIONS.itemCount(character, AgentEpqDefinition.MAGIC_STONE);
            }
        }
        if (leader.getMap() != null && AgentEpqDefinition.isEventMap(leader.getMapId())) {
            for (Monster monster : AgentMapPerception.monsters(leader.getMap()).stream()
                    .filter(Monster::isAlive).sorted(Comparator.comparingInt(Monster::getObjectId)).toList()) {
                signature = signature * 31L + monster.getObjectId();
                signature = signature * 31L + monster.getHp();
            }
            for (Reactor reactor : ACTIONS.reactors(leader).stream()
                    .filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparingInt(Reactor::getObjectId)).toList()) {
                signature = signature * 31L + reactor.getObjectId();
                signature = signature * 31L + reactor.getState();
            }
        }
        return signature;
    }

    private static Reactor nearestActiveReactor(Character agent, Set<Integer> reactorIds) {
        return ACTIONS.reactors(agent).stream().filter(java.util.Objects::nonNull)
                .filter(Reactor::isAlive).filter(Reactor::isActive)
                .filter(reactor -> reactorIds.contains(reactor.getId()))
                .min(Comparator.comparingDouble(reactor -> reactor.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
    }

    private static boolean hitReactor(AgentRuntimeEntry entry, Character agent, Reactor reactor,
                                      AgentEpqMemberState member, long nowMs) {
        if (!near(agent.getPosition(), reactor.getPosition(), REACTOR_RADIUS) || !ACTIONS.grounded(agent)) {
            Point target = ACTIONS.groundPoint(agent.getMap(), reactor.getPosition());
            if (target != null) ACTIONS.navigate(entry, target, true);
            return false;
        }
        ACTIONS.stop(entry);
        if (!ACTIONS.hitReactor(agent, reactor.getObjectId())) return false;
        member.deferUntil(nowMs + ACTION_RETRY_MS);
        return true;
    }

    private static boolean enterPortal(AgentRuntimeEntry entry, Character agent, int portalId,
                                       AgentEpqMemberState member, long nowMs) {
        Point portal = ACTIONS.portalPosition(agent, portalId);
        if (!near(agent.getPosition(), portal, PORTAL_RADIUS) || !ACTIONS.grounded(agent)) {
            if (portal != null) ACTIONS.navigate(entry, portal, true);
            return false;
        }
        ACTIONS.stop(entry);
        if (!EPQ.enterPortal(agent, portalId)) return false;
        member.deferUntil(nowMs + ACTION_RETRY_MS);
        return true;
    }

    private static boolean runNearbyNpc(AgentRuntimeEntry entry, Character agent, int npcId, int... selections) {
        Point npc = npcApproachPoint(agent, npcId);
        if (!near(agent.getPosition(), npc, NPC_RADIUS)) {
            if (npc != null) ACTIONS.navigate(entry, npc, true);
            return false;
        }
        ACTIONS.stop(entry);
        return EPQ.runNpc(agent, npcId, selections);
    }

    private static void dropAt(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                               InventoryType type, int itemId,
                               Point target, AgentEpqMemberState member, long nowMs) {
        Point position = agent.getPosition();
        Point levelTarget = new Point(target.x, position == null ? target.y : position.y);
        Point ground = ACTIONS.groundPoint(agent.getMap(), levelTarget);
        Point approach = ground == null ? levelTarget : ground;
        if (!near(position, approach, ITEM_REACTOR_DROP_RADIUS)) {
            ACTIONS.navigate(entry, approach, true);
            return;
        }
        ACTIONS.stop(entry);
        if (dropItem(entry, agent, type, itemId, (short) 1)) {
            long settleUntil = nowMs + ITEM_REACTOR_SETTLE_MS;
            session.members().forEach(state -> state.deferUntil(settleUntil));
        }
    }

    private static void dropStackNearNpc(
            AgentRuntimeEntry entry, Character agent, int itemId, int npcId,
            AgentEpqMemberState member, long nowMs) {
        Point npc = npcApproachPoint(agent, npcId);
        if (!near(agent.getPosition(), npc, NPC_RADIUS)) {
            if (npc != null) ACTIONS.navigate(entry, npc, true);
            return;
        }
        ACTIONS.stop(entry);
        int count = ACTIONS.itemCount(agent, itemId);
        if (count > 0 && dropItem(entry, agent, InventoryType.ETC, itemId,
                (short) Math.min(count, Short.MAX_VALUE))) {
            member.deferUntil(nowMs + ACTION_RETRY_MS);
        }
    }

    private static Point npcApproachPoint(Character agent, int npcId) {
        Point npc = ACTIONS.npcPosition(agent, npcId);
        if (npc == null || agent == null || agent.getMap() == null) return npc;
        Point position = agent.getPosition();
        Point besideNpc = new Point(npc.x - 48, position == null ? npc.y : position.y);
        Point grounded = ACTIONS.groundPoint(agent.getMap(), besideNpc);
        return grounded == null ? besideNpc : grounded;
    }

    private static boolean dropItem(
            AgentRuntimeEntry entry, Character agent, InventoryType type, int itemId, short quantity) {
        var inventory = agent == null ? null : agent.getInventory(type);
        Item item = inventory == null ? null : inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0
                || !AgentInventoryReservationRuntime.mayConsume(
                entry, item, System.currentTimeMillis())) return false;
        short dropQuantity = (short) Math.min(item.getQuantity(), Math.max(1, quantity));
        if (itemId == AgentEpqDefinition.POISON
                && agent.getMapId() == AgentEpqDefinition.STAGE_TWO_MAP) {
            Item dropped = item.copy();
            dropped.setQuantity(dropQuantity);
            AgentInventoryGatewayRuntime.inventory().removeFromSlot(
                    agent, type, item.getPosition(), dropQuantity, true);
            agent.getMap().spawnItemDrop(
                    agent, agent, dropped, new Point(agent.getPosition()), true, true);
            return true;
        }
        AgentInventoryGatewayRuntime.inventory().dropItem(
                agent, type, item.getPosition(), dropQuantity);
        return true;
    }

    private static boolean near(Point first, Point second, int radius) {
        return first != null && second != null && first.distanceSq(second) <= (long) radius * radius;
    }

    private static void announce(AgentEpqMemberState member, Character agent,
                                 String key, String message) {
        if (member.claimAnnouncement(key)) {
            AgentPartyGatewayRuntime.party().sendPartyChat(agent, message);
        }
    }

    public static Character character(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
}
