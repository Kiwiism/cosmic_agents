package server.agents.capabilities.partyquest.epq;

import client.Character;
import client.inventory.Item;
import client.inventory.InventoryType;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentPartyQuestGatewayRuntime;
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
    private static final int PORTAL_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.PORTAL_RADIUS");
    private static final int NPC_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.NPC_RADIUS");
    private static final int REACTOR_RADIUS = config.AgentTuning.intValue(
            "server.agents.capabilities.partyquest.epq.AgentEpqCoordinator.REACTOR_RADIUS");

    private AgentEpqCoordinator() { }

    public static void tickSession(AgentEpqSession session, long nowMs) {
        Character leader = character(session.eventLeaderId());
        if (leader == null) return;
        if (session.eventInstance() == null && leader.getEventInstance() != null
                && AgentEpqDefinition.isEventMap(leader.getMapId())) {
            session.bindEventInstance(leader.getEventInstance());
            session.markProgress(nowMs);
        }
        if (nowMs - session.startedAtMs() >= EVENT_TIMEOUT_MS && !session.terminal()) {
            session.fail("EPQ event timer expired", nowMs);
            return;
        }
        AgentEpqSession.Phase observed = phaseForMap(leader.getMapId());
        if (observed != null) session.transition(observed, nowMs);
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
        ACTIONS.lootNearby(agent, Set.of(AgentEpqDefinition.POISON, AgentEpqDefinition.PURIFIED_POISON));
        Reactor spine = agent.getMap().getReactorById(AgentEpqDefinition.SPINE_REACTOR);
        if (spine != null && spine.getState() >= 4) {
            enterPortal(entry, agent, 3, member, nowMs);
            return;
        }
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.PURIFIED_POISON) > 0 && spine != null) {
            dropAt(entry, agent, InventoryType.ETC, AgentEpqDefinition.PURIFIED_POISON,
                    spine.getPosition(), member, nowMs);
            return;
        }
        Reactor pond = agent.getMap().getReactorById(AgentEpqDefinition.POND_REACTOR);
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.POISON) > 0 && pond != null) {
            dropAt(entry, agent, InventoryType.ETC, AgentEpqDefinition.POISON,
                    pond.getPosition(), member, nowMs);
            return;
        }
        if (ACTIONS.liveMonsterCount(agent, Set.of(AgentEpqDefinition.STAGE_TWO_MOB)) > 0) {
            ACTIONS.grind(entry, Set.of(AgentEpqDefinition.STAGE_TWO_MOB));
        } else {
            ACTIONS.stop(entry);
        }
    }

    private static void stageThree(AgentRuntimeEntry entry, Character agent,
                                   AgentEpqMemberState member, long nowMs) {
        Point coordinator = ACTIONS.npcPosition(agent, AgentEpqDefinition.STAGE_NPC);
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
        if (agent.getId() != captureAgentId(session)) { ACTIONS.stop(entry); return; }
        if (agent.getId() != session.eventLeaderId() && monsterMarbles >= 20) {
            dropStackNearNpc(entry, agent, AgentEpqDefinition.MONSTER_MARBLE,
                    AgentEpqDefinition.STAGE_NPC, member, nowMs);
            return;
        }
        if (ACTIONS.itemCount(agent, AgentEpqDefinition.PURIFICATION_MARBLE) == 0) {
            if (runNearbyNpc(entry, agent, AgentEpqDefinition.STAGE_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        Monster ready = flowers(agent).stream().filter(AgentEpqCaptureRuntime::ready)
                .min(Comparator.comparingDouble(mob -> mob.getPosition().distanceSq(agent.getPosition())))
                .orElse(null);
        if (ready != null) {
            if (!near(agent.getPosition(), ready.getPosition(), 220)) {
                ACTIONS.navigate(entry, ready.getPosition(), true);
            } else {
                ACTIONS.stop(entry);
                if (AgentEpqCaptureRuntime.capture(agent, ready)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            }
            return;
        }
        ACTIONS.grind(entry, Set.of(AgentEpqDefinition.POISON_FLOWER));
    }

    private static void stageFive(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                                  AgentEpqMemberState member, long nowMs) {
        ACTIONS.lootNearby(agent, Set.of(AgentEpqDefinition.MAGIC_STONE));
        if (agent.getId() == session.eventLeaderId()
                && ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE) > 0) {
            if (runNearbyNpc(entry, agent, AgentEpqDefinition.STONE_NPC)) member.deferUntil(nowMs + ACTION_RETRY_MS);
            return;
        }
        if (agent.getId() != workAgentId(session)) { ACTIONS.stop(entry); return; }
        if (agent.getId() != session.eventLeaderId()
                && ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE) > 0) {
            dropStackNearNpc(entry, agent, AgentEpqDefinition.MAGIC_STONE,
                    AgentEpqDefinition.STONE_NPC, member, nowMs);
            return;
        }
        Reactor target = nearestActiveReactor(agent,
                Set.of(AgentEpqDefinition.STONE_BOX, AgentEpqDefinition.EMPTY_BOX));
        if (target == null) { ACTIONS.stop(entry); return; }
        hitReactor(entry, agent, target, member, nowMs);
    }

    private static void boss(AgentEpqSession session, AgentRuntimeEntry entry, Character agent,
                             AgentEpqMemberState member, long nowMs) {
        ACTIONS.lootNearby(agent, Set.of(AgentEpqDefinition.MAGIC_STONE, AgentEpqDefinition.ALTAIRE_FRAGMENT));
        if (ACTIONS.liveMonsterCount(agent, AgentEpqDefinition.BOSS_MOBS) > 0) {
            ACTIONS.grind(entry, AgentEpqDefinition.BOSS_MOBS);
            return;
        }
        if (session.eventInstance() == null || !session.eventInstance().isEventCleared()) {
            if (agent.getId() == session.eventLeaderId()
                    && ACTIONS.itemCount(agent, AgentEpqDefinition.MAGIC_STONE) > 0) {
                Reactor altar = agent.getMap().getReactorById(AgentEpqDefinition.ALTAR_REACTOR);
                if (altar != null) dropAt(entry, agent, InventoryType.ETC,
                        AgentEpqDefinition.MAGIC_STONE, altar.getPosition(), member, nowMs);
            } else {
                ACTIONS.stop(entry);
            }
            return;
        }
        if (nowMs - session.observeBossCleared(nowMs) < BOSS_LOOT_SETTLE_MS) { ACTIONS.stop(entry); return; }
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

    private static int captureAgentId(AgentEpqSession session) {
        if (session.member(session.eventLeaderId()).memberType() == AgentEpqMemberState.MemberType.AGENT) {
            return session.eventLeaderId();
        }
        return workAgentId(session);
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
        Point npc = ACTIONS.npcPosition(agent, npcId);
        if (!near(agent.getPosition(), npc, NPC_RADIUS)) {
            if (npc != null) ACTIONS.navigate(entry, npc, true);
            return false;
        }
        ACTIONS.stop(entry);
        return EPQ.runNpc(agent, npcId, selections);
    }

    private static void dropAt(AgentRuntimeEntry entry, Character agent, InventoryType type, int itemId,
                               Point target, AgentEpqMemberState member, long nowMs) {
        if (!near(agent.getPosition(), target, 90)) {
            Point ground = ACTIONS.groundPoint(agent.getMap(), target);
            if (ground != null) ACTIONS.navigate(entry, ground, true);
            return;
        }
        ACTIONS.stop(entry);
        if (dropItem(entry, agent, type, itemId, (short) 1)) {
            member.deferUntil(nowMs + ACTION_RETRY_MS);
        }
    }

    private static void dropStackNearNpc(
            AgentRuntimeEntry entry, Character agent, int itemId, int npcId,
            AgentEpqMemberState member, long nowMs) {
        Point npc = ACTIONS.npcPosition(agent, npcId);
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

    private static boolean dropItem(
            AgentRuntimeEntry entry, Character agent, InventoryType type, int itemId, short quantity) {
        var inventory = agent == null ? null : agent.getInventory(type);
        Item item = inventory == null ? null : inventory.findById(itemId);
        if (item == null || item.getQuantity() <= 0
                || !AgentInventoryReservationRuntime.mayConsume(
                entry, item, System.currentTimeMillis())) return false;
        AgentInventoryGatewayRuntime.inventory().dropItem(
                agent, type, item.getPosition(),
                (short) Math.min(item.getQuantity(), Math.max(1, quantity)));
        return true;
    }

    private static boolean near(Point first, Point second, int radius) {
        return first != null && second != null && first.distanceSq(second) <= (long) radius * radius;
    }

    public static Character character(int id) {
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(id);
        Character agent = entry == null ? null : AgentRuntimeIdentityRuntime.bot(entry);
        return agent != null ? agent : AgentCharacterGatewayRuntime.characters().findOnlineCharacterById(id);
    }
}
