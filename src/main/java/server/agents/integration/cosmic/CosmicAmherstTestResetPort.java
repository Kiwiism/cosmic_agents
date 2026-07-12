package server.agents.integration.cosmic;

import client.Character;
import client.QuestStatus;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.ItemConstants;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentSpawnFallService;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstQuestSegment;
import server.agents.capabilities.quest.AmherstTestRuntimeResetService;
import server.agents.capabilities.quest.AmherstTestResetPlan;
import server.agents.capabilities.quest.AmherstTestResetPlanner;
import server.agents.capabilities.quest.AmherstTestResetMode;
import server.agents.capabilities.quest.AmherstTestResetPort;
import server.agents.capabilities.quest.AmherstTestResetRequest;
import server.agents.capabilities.quest.AmherstTestResetResult;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.quest.Quest;

import java.awt.Point;

public enum CosmicAmherstTestResetPort implements AmherstTestResetPort {
    INSTANCE;

    private final AmherstTestResetPlanner planner = new AmherstTestResetPlanner();

    @Override
    public AmherstTestResetResult reset(AmherstTestResetRequest request) {
        Character agent = CosmicCharacterGateway.INSTANCE.findOnlineCharacterById(request.characterId());
        if (agent == null || !CosmicCharacterGateway.INSTANCE.isAgentCharacter(agent)) {
            return AmherstTestResetResult.blocked(
                    server.agents.capabilities.AgentCapabilityStatus.MISSING_REQUIREMENT,
                    "allowlisted character is not an online Agent");
        }
        if (request.characterName() != null && !request.characterName().isBlank()
                && !request.characterName().equals(agent.getName())) {
            return AmherstTestResetResult.blocked(
                    server.agents.capabilities.AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "character id and name do not identify the same Agent");
        }

        AmherstTestResetPlan plan = planner.plan(request);
        AgentRuntimeEntry entry = runtimeEntry(agent);
        clearRuntime(entry, agent);
        clearMapFixtureState(agent);
        if (plan.resetCharacterBaseline()) {
            agent.resetAmherstTestBaseline();
        }
        if (plan.resetAllAmherstQuests()) {
            resetAmherstQuests(agent);
        } else if (plan.selectedQuestId() > 0) {
            resetQuest(agent, plan.selectedQuestId());
            clearKnownQuestItems(agent, plan.selectedQuestId());
        }
        if (plan.seedAmherstPrerequisites()) {
            seedAmherstPrerequisites(agent);
        }
        moveTo(entry, agent, plan.targetMapId());
        clearMapFixtureState(agent);
        agent.saveCharToDB(false);
        return AmherstTestResetResult.allowed("Amherst test fixture reset complete");
    }

    private static AgentRuntimeEntry runtimeEntry(Character agent) {
        Character leader = AgentRuntimeRegistry.activeLeaderByAgentCharacterId(agent.getId());
        if (leader == null) {
            return null;
        }
        return AgentRuntimeRegistry.findByCharacterId(leader.getId(), agent.getId());
    }

    private static void clearRuntime(AgentRuntimeEntry entry, Character agent) {
        if (entry != null) {
            AmherstTestRuntimeResetService.reset(entry, agent, System.currentTimeMillis());
        }
    }

    private static void clearMapFixtureState(Character agent) {
        if (agent.getMap() != null) {
            agent.getMap().clearDropsOwnedBy(agent.getId());
            agent.getMap().resetReactors();
        }
    }

    private static void seedAmherstPrerequisites(Character agent) {
        for (var definition : AmherstQuestCatalog.allRequiredQuests()) {
            if (definition.segment() != AmherstQuestSegment.AMHERST && definition.questId() != 1037) {
                agent.updateQuestStatus(new QuestStatus(
                        Quest.getInstance(definition.questId()), QuestStatus.Status.COMPLETED));
            }
        }
        QuestStatus snailHunt = new QuestStatus(Quest.getInstance(1037), QuestStatus.Status.STARTED, 2005);
        snailHunt.setProgress(100100, "10");
        agent.updateQuestStatus(snailHunt);
    }

    private static void clearKnownQuestItems(Character agent, int questId) {
        int[] itemIds = switch (questId) {
            case 1000, 1001 -> new int[]{4031003};
            case 1005 -> new int[]{4031000};
            case 1006 -> new int[]{4031001};
            case 1021 -> new int[]{2010007};
            case 1025 -> new int[]{4000004, 4000011};
            case 1034 -> new int[]{4031792};
            case 1035 -> new int[]{4031802};
            case 1038 -> new int[]{4031800};
            case 1008 -> new int[]{4031161, 4031162};
            default -> new int[0];
        };
        for (int itemId : itemIds) {
            Inventory inventory = agent.getInventory(ItemConstants.getInventoryType(itemId));
            for (Item item : inventory.listById(itemId)) {
                inventory.removeItem(item.getPosition(), item.getQuantity(), false);
            }
        }
    }

    private static void resetAmherstQuests(Character agent) {
        for (Integer questId : AmherstQuestCatalog.requiredQuestIdSet()) {
            resetQuest(agent, questId);
        }
    }

    private static void resetQuest(Character agent, int questId) {
        Quest.getInstance(questId).reset(agent);
    }

    private static void moveTo(AgentRuntimeEntry entry, Character agent, int mapId) {
        var map = CosmicMapGateway.INSTANCE.resolveMap(agent.getWorld(), agent.getClient().getChannel(), mapId);
        Point spawn = new Point(map.getRandomPlayerSpawnpoint().getPosition());
        if (agent.getMapId() != mapId) {
            agent.changeMap(map, spawn);
        } else if (entry != null) {
            AgentMovementPoseService.teleportTo(entry, agent, spawn);
        } else {
            agent.setPosition(spawn);
        }
        if (entry == null) {
            return;
        }
        Point ground = AgentGroundingService.findGroundPoint(map,
                new Point(agent.getPosition().x, agent.getPosition().y - 1));
        if (ground == null || AgentSpawnFallService.shouldFall(agent.getPosition(), ground)) {
            AgentSpawnFallService.beginFall(entry, agent);
        } else {
            AgentMovementPoseService.teleportTo(entry, agent, ground);
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
    }
}
