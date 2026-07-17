package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.capabilities.movement.AgentChairService;
import server.agents.capabilities.movement.AgentMovementStuckStateRuntime;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementBroadcastStateRuntime;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentCombatObjectiveTargetStateRuntime;
import server.agents.capabilities.npc.AgentNpcInteractionType;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.integration.AgentCharacterStateSnapshot;
import server.agents.integration.AgentPacketGatewayRuntime;
import server.agents.runtime.AgentModeService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.life.Monster;
import server.life.NPC;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.Reactor;
import server.quest.Quest;

import java.awt.Point;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import tools.PacketCreator;

public enum CosmicPrimitiveCapabilityGateway implements PrimitiveCapabilityGateway {
    INSTANCE;

    private final Set<Integer> fieldAbsentAgentIds = ConcurrentHashMap.newKeySet();

    @Override
    public int mapId(Character agent) {
        return agent.getMapId();
    }

    @Override
    public Point position(Character agent) {
        return new Point(agent.getPosition());
    }

    @Override
    public boolean alive(Character agent) {
        return agent.isAlive();
    }

    @Override
    public boolean grounded(Character agent) {
        if (agent == null) {
            return false;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        return entry == null || AgentMovementStateRuntime.grounded(entry);
    }

    @Override
    public AgentCharacterStateSnapshot characterState(Character agent) {
        return new AgentCharacterStateSnapshot(
                agent.getJob().getId(),
                agent.getLevel(),
                agent.getHp(),
                agent.getMaxHp(),
                agent.getMp(),
                agent.getMaxMp(),
                agent.isAlive());
    }

    @Override
    public int stuckDurationMs(AgentRuntimeEntry entry) {
        return AgentMovementStuckStateRuntime.stuckMs(entry);
    }

    @Override
    public int questStatus(Character agent, int questId) {
        return agent.getQuestStatus(questId);
    }

    @Override
    public int questProgress(Character agent, int questId, int progressId) {
        QuestStatus status = agent.getQuestNoAdd(Quest.getInstance(questId));
        if (status == null) {
            return 0;
        }
        try {
            return Integer.parseInt(status.getProgress(progressId));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @Override
    public boolean canStartQuest(Character agent, int questId, int npcId) {
        return Quest.getInstance(questId).canStart(agent, npcId);
    }

    @Override
    public boolean canCompleteQuest(Character agent, int questId, int npcId) {
        return Quest.getInstance(questId).canComplete(agent, npcId);
    }

    @Override
    public int itemCount(Character agent, int itemId) {
        InventoryType type = ItemConstants.getInventoryType(itemId);
        Inventory inventory = agent.getInventory(type);
        return inventory == null ? 0 : inventory.countById(itemId);
    }

    @Override
    public int freeSlots(Character agent, int itemId) {
        Inventory inventory = agent.getInventory(ItemConstants.getInventoryType(itemId));
        return inventory == null ? 0 : inventory.getNumFreeSlot();
    }

    @Override
    public boolean questItem(int itemId) {
        return server.ItemInformationProvider.getInstance().isQuestItem(itemId);
    }

    @Override
    public boolean portalPresent(Character agent, int portalId) {
        return agent.getMap() != null && agent.getMap().getPortal(portalId) != null;
    }

    @Override
    public Point portalPosition(Character agent, int portalId) {
        if (agent.getMap() == null) {
            return null;
        }
        var portal = agent.getMap().getPortal(portalId);
        return portal == null ? null : new Point(portal.getPosition());
    }

    @Override
    public Integer directPortalIdTo(Character agent, int destinationMapId) {
        if (agent.getMap() == null) {
            return null;
        }
        return agent.getMap().getPortals().stream()
                .filter(portal -> portal.getPortalStatus() && portal.getTargetMapId() == destinationMapId)
                .map(server.maps.Portal::getId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Point npcPosition(Character agent, int npcId) {
        NPC npc = agent.getMap().getNPCById(npcId);
        return npc == null ? null : new Point(npc.getPosition());
    }

    @Override
    public void facePosition(Character agent, Point targetPosition) {
        if (agent == null || targetPosition == null || agent.getPosition() == null) {
            return;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry == null) {
            return;
        }
        int deltaX = targetPosition.x - agent.getPosition().x;
        if (deltaX != 0) {
            AgentMovementStateRuntime.setFacingDirection(entry, deltaX < 0 ? -1 : 1);
        }
        AgentMovementStateRuntime.clearMoveDirection(entry);
        AgentMovementPoseService.syncCharacterState(entry);
        AgentMovementBroadcastStateRuntime.invalidate(entry);
        AgentMovementBroadcastService.broadcastMovement(entry);
    }

    @Override
    public Collection<Reactor> reactors(Character agent) {
        List<Reactor> reactors = new java.util.ArrayList<>();
        for (MapObject object : agent.getMap().getReactors()) {
            reactors.add((Reactor) object);
        }
        return reactors;
    }

    @Override
    public Point nearestActiveReactorPosition(Character agent, Integer reactorId, String reactorName) {
        Point agentPosition = agent.getPosition();
        return reactors(agent).stream()
                .filter(Reactor::isActive)
                .filter(reactor -> reactorId == null || reactor.getId() == reactorId)
                .filter(reactor -> reactorName == null || reactorName.isBlank()
                        || reactorName.equalsIgnoreCase(reactor.getName()))
                .min(java.util.Comparator.comparingLong(reactor -> {
                    Point position = reactor.getPosition();
                    long dx = agentPosition.x - position.x;
                    long dy = agentPosition.y - position.y;
                    return dx * dx + dy * dy;
                }))
                .map(reactor -> new Point(reactor.getPosition()))
                .orElse(null);
    }

    @Override
    public int liveMonsterCount(Character agent, Set<Integer> mobIds) {
        int count = 0;
        for (Monster monster : server.agents.perception.AgentMapPerception.monsters(agent.getMap())) {
            if (monster.isAlive() && mobIds.contains(monster.getId())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void navigate(AgentRuntimeEntry entry, Point destination, boolean precise) {
        AgentModeService.startMoveTo(entry, destination, precise);
    }

    @Override
    public void grind(AgentRuntimeEntry entry, Set<Integer> allowedMobIds) {
        AgentCombatObjectiveTargetStateRuntime.setAllowedMobIds(entry, allowedMobIds);
        if (!AgentModeStateRuntime.grinding(entry)) {
            AgentModeService.startGrind(entry, AgentMovementStateResetService::clearNavigationState);
        }
    }

    @Override
    public void stop(AgentRuntimeEntry entry) {
        AgentCombatObjectiveTargetStateRuntime.clear(entry);
        AgentModeService.startStop(entry);
    }

    @Override
    public boolean enterPortal(Character agent, int portalId) {
        return CosmicMapGateway.INSTANCE.enterPortal(agent, portalId);
    }

    @Override
    public boolean useItem(Character agent, int itemId) {
        Inventory inventory = agent.getInventory(ItemConstants.getInventoryType(itemId));
        Item item = inventory == null ? null : inventory.findById(itemId);
        return item != null && CosmicInventoryGateway.INSTANCE.consumeUseItem(
                agent, item.getPosition(), itemId);
    }

    @Override
    public boolean interactNpc(Character agent,
                               int npcId,
                               AgentNpcInteractionType type,
                               Integer questId) {
        if (npcPosition(agent, npcId) == null) {
            return false;
        }
        if (type == AgentNpcInteractionType.QUEST_START && questId != null) {
            return startQuest(agent, questId, npcId);
        }
        if (type == AgentNpcInteractionType.QUEST_COMPLETE && questId != null) {
            return completeQuest(agent, questId, npcId);
        }
        return true;
    }

    @Override
    public boolean startQuest(Character agent, int questId, int npcId) {
        Quest quest = Quest.getInstance(questId);
        if (quest.hasScriptRequirement(false)) {
            return CosmicHeadlessQuestScriptGateway.start(agent, questId, npcId);
        }
        quest.start(agent, npcId);
        int status = agent.getQuestStatus(questId);
        return status == QuestStatus.Status.STARTED.getId()
                || status == QuestStatus.Status.COMPLETED.getId();
    }

    @Override
    public boolean completeQuest(Character agent, int questId, int npcId) {
        Quest quest = Quest.getInstance(questId);
        if (quest.hasScriptRequirement(true)) {
            return CosmicHeadlessQuestScriptGateway.complete(agent, questId, npcId);
        }
        quest.complete(agent, npcId);
        return agent.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId();
    }

    @Override
    public boolean forceCompleteQuest(Character agent, int questId, int npcId) {
        Quest.getInstance(questId).forceCompleteWithActions(agent, npcId, null);
        return agent.getQuestStatus(questId) == QuestStatus.Status.COMPLETED.getId();
    }

    @Override
    public boolean beginFieldAbsence(Character agent, long safetyRestoreDelayMs) {
        if (agent == null || agent.getMap() == null) {
            return false;
        }
        if (!fieldAbsentAgentIds.add(agent.getId())) {
            return true;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry != null) {
            AgentModeService.startStop(entry);
        }
        agent.getMap().broadcastMessage(PacketCreator.removePlayerFromMap(agent.getId()));
        AgentSchedulerRuntime.schedule(
                () -> endFieldAbsence(agent), Math.max(1_000L, safetyRestoreDelayMs));
        return true;
    }

    @Override
    public boolean endFieldAbsence(Character agent) {
        if (agent == null) {
            return false;
        }
        if (!fieldAbsentAgentIds.remove(agent.getId())) {
            return true;
        }
        if (agent.getMap() == null) {
            return false;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        if (entry != null) {
            // SPAWN_PLAYER with enteringField=true intentionally renders 42 px above the
            // stored position. Follow it with the authoritative grounded state so a
            // simulated Cash Shop return cannot remain visually airborne.
            AgentMovementPoseService.idleOnGround(entry, agent);
            AgentMovementBroadcastStateRuntime.invalidate(entry);
        }
        agent.getMap().broadcastSpawnPlayerMapObjectMessage(agent, agent, true);
        if (entry != null) {
            AgentMovementBroadcastService.broadcastMovement(entry);
        }
        return true;
    }

    @Override
    public boolean hitReactor(Character agent, int objectId) {
        Reactor reactor = agent.getMap().getReactorByOid(objectId);
        if (reactor == null || !reactor.isAlive()) {
            return false;
        }
        AgentAttackExecutionProvider.BasicAttackData attack =
                AgentAttackExecutionProvider.buildBasicAttackData(agent, reactor.getPosition());
        if (attack.route() == AgentAttackRoute.CLOSE) {
            AgentPacketGatewayRuntime.packets().broadcastCloseRangeAttack(
                    agent, 0, 0, attack.stance(), 0, Map.of(), attack.speed(),
                    attack.direction(), attack.display());
        }
        short reactorStance = (short) (agent.getPosition().x <= reactor.getPosition().x ? 0 : 2);
        reactor.hitReactor(true, agent.getPosition().x, reactorStance, 0, agent.getClient());
        return true;
    }

    @Override
    public boolean lootNearby(Character agent, Set<Integer> itemIds) {
        boolean found = false;
        for (MapItem item : server.agents.perception.AgentMapPerception.items(agent.getMap())) {
            if (!item.isPickedUp() && itemIds.contains(item.getItemId())) {
                found = true;
                agent.pickupItem(item);
            }
        }
        return found;
    }

    @Override
    public boolean sitChair(Character agent, int itemId) {
        if (itemCount(agent, itemId) < 1) {
            return false;
        }
        AgentRuntimeEntry entry = AgentRuntimeRegistry.findByAgentCharacterId(agent.getId());
        return AgentChairService.sit(entry, agent, itemId);
    }

    @Override
    public int chairItemId(Character agent) {
        return agent == null ? -1 : agent.getChair();
    }
}
