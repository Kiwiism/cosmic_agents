package server.agents.progression;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.ItemInformationProvider;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentSpawnFallService;
import server.agents.capabilities.navigation.AgentLithHarborArrivalRouteRuntime;
import server.agents.capabilities.quest.AmherstTestRuntimeResetService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.objectives.AgentObjectiveState;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.quest.Quest;

import java.awt.Point;
import java.io.IOException;
import java.util.Locale;

/** Guarded reusable fixture for the Lith Harbor to level-15 first-job slice. */
public final class VictoriaFirstJobMvpTestService {
    public static final int LITH_HARBOR_MAP_ID = 104_000_000;
    private static final long START_DELAY_MS = config.AgentTuning.longValue("server.agents.progression.VictoriaFirstJobMvpTestService.START_DELAY_MS");
    private static final int VICTORIA_SHOWCASE_HAT_ID = 1_002_068;
    private static final int VICTORIA_SHOWCASE_TOP_ID = 1_041_010;
    private static final int VICTORIA_SHOWCASE_BOTTOM_ID = 1_061_008;
    private static final int VICTORIA_SHOWCASE_SHOES_ID = 1_072_005;
    private static final int VICTORIA_THIEF_DAGGER_WEAPON_ID = 1_332_005;
    private VictoriaFirstJobMvpTestService() {
    }

    public static AgentCareerBuildBundle resetAndStart(AgentRuntimeEntry entry,
                                                       String requestedCareer,
                                                       long nowMs) throws IOException {
        return resetAndStart(entry, requestedCareer, "lv10", nowMs);
    }

    public static AgentCareerBuildBundle resetAndStart(AgentRuntimeEntry entry,
                                                       String requestedCareer,
                                                       String requestedVariant,
                                                       long nowMs) throws IOException {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null) {
            throw new IllegalArgumentException("a spawned Agent is required");
        }
        AgentVictoriaLevel15StageContractRepository.defaultContract();
        AgentCareerBuildBundle bundle = resolveBundle(requestedCareer);
        AgentVictoriaLevel15CatalogRepository catalogRepository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();
        catalogRepository.careerFor(bundle);
        AgentVictoriaLevel15Catalog.StartVariant startVariant = resolveStartVariant(requestedVariant);

        AmherstTestRuntimeResetService.reset(entry, agent, nowMs);
        if (AgentTownLifeRuntime.active(entry)) {
            AgentTownLifeRuntime.stop(entry, agent);
        }
        if (!AgentAmherstPlanRuntime.clearSession(entry)) {
            throw new IllegalStateException("the previous Agent capability is still closing");
        }
        AgentShopService.cancelShopVisit(entry);
        AgentMovementCommandRuntime.stop(entry);
        entry.capabilityStates().remove(AgentObjectiveState.STATE_KEY);
        entry.capabilityStates().remove(AgentResourcePlanningState.STATE_KEY);
        entry.capabilityStates().remove(AgentSupplyProcurementState.STATE_KEY);
        entry.capabilityStates().remove(AgentCareerProgressionState.STATE_KEY);
        entry.capabilityStates().remove(AgentVictoriaQuestSchedulerState.STATE_KEY);
        entry.capabilityStates().remove(AgentVictoriaTrainingState.STATE_KEY);
        entry.capabilityStates().remove(AgentVictoriaPlanSessionState.STATE_KEY);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());

        agent.resetVictoriaFirstJobTestBaseline(
                bundle.firstJobId(), startVariant.level(), startVariant.exp());
        applyVictoriaShowcaseEquipment(agent);
        AgentVictoriaLevel15Catalog catalog = catalogRepository.catalog();
        Quest.getInstance(catalog.islandHandoff().biggsQuestId()).reset(agent);
        Quest.getInstance(catalog.islandHandoff().olafLessonQuestId()).reset(agent);
        for (AgentVictoriaLevel15Catalog.Career career : catalog.careers()) {
            Quest.getInstance(career.olafPathQuestId()).reset(agent);
        }
        for (int questId : bundle.instructorTrainingQuestIds()) {
            Quest.getInstance(questId).reset(agent);
        }
        for (AgentVictoriaLevel15Catalog.QuestPack pack : catalog.questPacks()) {
            for (int questId : pack.questIds()) {
                Quest.getInstance(questId).reset(agent);
            }
        }
        AgentVictoriaProgressionDiagnostics.deleteMilestones(agent.getId());
        Quest.getInstance(catalog.islandHandoff().biggsQuestId())
                .forceStart(agent, catalog.islandHandoff().biggsNpcId());
        bundle = AgentCareerBuildBundleService.assignForTest(entry, bundle.bundleId(), nowMs);
        moveToLithHarbor(entry, agent, nowMs);
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).reset(
                bundle,
                AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                startVariant.variantId(),
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF,
                nowMs + START_DELAY_MS);
        if (!AgentUniversalPlanRuntime.start(entry, agent, "victoria-level15-mvp",
                AgentPlanStartRequest.EMPTY, nowMs)) {
            throw new IllegalStateException("universal Victoria level-15 plan rejected the reset state");
        }
        AgentUniversalPlanRuntime.tick(entry, agent, nowMs);
        AgentCareerProgressionCheckpointRuntime.persistIfDirty(entry, nowMs);
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return bundle;
    }

    public static AgentCareerBuildBundle resolveBundle(String requestedCareer) {
        String alias = requestedCareer == null ? "" : requestedCareer.trim().toLowerCase(Locale.ROOT);
        String bundleId = switch (alias) {
            case "warrior", "warrior-standard-v1" -> "warrior-standard-v1";
            case "bowman", "archer", "bowman-standard-v1" -> "bowman-standard-v1";
            case "magician", "mage", "magician-standard-v1" -> "magician-standard-v1";
            case "thief", "thief-claw", "thief-claw-standard-v1" -> "thief-claw-standard-v1";
            case "thief-dagger", "thief-dagger-standard-v1" -> "thief-dagger-standard-v1";
            case "pirate", "pirate-gun", "pirate-gun-standard-v1" -> "pirate-gun-standard-v1";
            case "pirate-knuckle", "pirate-knuckle-standard-v1" -> "pirate-knuckle-standard-v1";
            default -> throw new IllegalArgumentException("unknown career '" + requestedCareer + "'");
        };
        return AgentCareerBuildBundleRepository.defaultRepository().find(bundleId).orElseThrow();
    }

    public static AgentVictoriaLevel15Catalog.StartVariant resolveStartVariant(String requestedVariant) {
        String alias = requestedVariant == null ? "" : requestedVariant.trim().toLowerCase(Locale.ROOT);
        String variantId = switch (alias) {
            case "", "lv10", "level10" -> "lv10";
            case "lv9-olaf", "level9-olaf", "olaf" -> "lv9-olaf";
            case "lv9-grind", "level9-grind", "grind" -> "lv9-grind";
            default -> throw new IllegalArgumentException("unknown start variant '" + requestedVariant + "'");
        };
        return AgentVictoriaLevel15CatalogRepository.defaultRepository().startVariant(variantId);
    }

    private static void moveToLithHarbor(AgentRuntimeEntry entry, Character agent, long nowMs) {
        var maps = AgentMapGatewayRuntime.map();
        var clients = AgentClientGatewayRuntime.clients();
        var map = maps.resolveMap(
                clients.world(agent), clients.channel(agent), LITH_HARBOR_MAP_ID);
        Point spawn = lithHarborArrivalPosition(map, agent.getName().hashCode());
        if (agent.getMapId() != LITH_HARBOR_MAP_ID) {
            maps.changeMap(agent, map, spawn);
        } else {
            AgentMovementPoseService.teleportTo(entry, agent, spawn);
        }
        Point ground = AgentGroundingService.findGroundPoint(map,
                new Point(agent.getPosition().x, agent.getPosition().y - 1));
        if (ground == null || AgentSpawnFallService.shouldFall(agent.getPosition(), ground)) {
            AgentSpawnFallService.beginFall(entry, agent);
        } else {
            AgentMovementPoseService.teleportTo(entry, agent, ground);
        }
        AgentMovementBroadcastService.broadcastMovement(entry);
        AgentLithHarborArrivalRouteRuntime.prepareNavigation(entry, agent);
    }

    static Point lithHarborArrivalPosition(server.maps.MapleMap map) {
        return lithHarborArrivalPosition(map, 0);
    }

    static Point lithHarborArrivalPosition(server.maps.MapleMap map, int selector) {
        return AgentLithHarborArrivalRouteRuntime.victoriaArrivalPosition(map, selector);
    }

    private static void applyVictoriaShowcaseEquipment(Character agent) {
        Inventory current = agent.getInventory(InventoryType.EQUIPPED);
        Inventory equipped = new Inventory(agent, InventoryType.EQUIPPED, current.getSlotLimit());
        addEquip(equipped, VICTORIA_SHOWCASE_HAT_ID, (byte) -1);
        addEquip(equipped, VICTORIA_SHOWCASE_TOP_ID, (byte) -5);
        addEquip(equipped, VICTORIA_SHOWCASE_BOTTOM_ID, (byte) -6);
        addEquip(equipped, VICTORIA_SHOWCASE_SHOES_ID, (byte) -7);
        addEquip(equipped, VICTORIA_THIEF_DAGGER_WEAPON_ID, (byte) -11);
        agent.setInventory(InventoryType.EQUIPPED, equipped);
        agent.recalcLocalStats();
    }

    private static void addEquip(Inventory equipped, int itemId, byte position) {
        Item item = ItemInformationProvider.getInstance().getEquipById(itemId);
        if (item == null) {
            throw new IllegalStateException("Missing Victoria showcase equipment " + itemId);
        }
        item.setPosition(position);
        equipped.addItemFromDB(item);
    }
}
