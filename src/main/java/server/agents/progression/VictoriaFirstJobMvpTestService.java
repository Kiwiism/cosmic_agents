package server.agents.progression;

import client.Character;
import server.agents.capabilities.movement.AgentGroundingService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.movement.AgentSpawnFallService;
import server.agents.capabilities.quest.AmherstTestRuntimeResetService;
import server.agents.capabilities.shop.AgentShopService;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.cosmic.CosmicMapleIslandCohortIdentity;
import server.agents.objectives.AgentObjectiveState;
import server.agents.plans.amherst.AgentAmherstPlanRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.quest.Quest;

import java.awt.Point;
import java.io.IOException;
import java.util.Locale;

/** Guarded reusable fixture for the Lith Harbor to level-15 first-job slice. */
public final class VictoriaFirstJobMvpTestService {
    public static final int LITH_HARBOR_MAP_ID = 104_000_000;
    private static final long START_DELAY_MS = 3_000L;

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
        AgentVictoriaLevel15PlanRepository.defaultPlan();
        AgentCareerBuildBundle bundle = resolveBundle(requestedCareer);
        AgentVictoriaLevel15CatalogRepository catalogRepository =
                AgentVictoriaLevel15CatalogRepository.defaultRepository();
        catalogRepository.careerFor(bundle);
        AgentVictoriaLevel15Catalog.StartVariant startVariant = resolveStartVariant(requestedVariant);

        AmherstTestRuntimeResetService.reset(entry, agent, nowMs);
        if (!AgentAmherstPlanRuntime.clearSession(entry)) {
            throw new IllegalStateException("the previous Agent capability is still closing");
        }
        AgentShopService.cancelShopVisit(entry);
        entry.capabilityStates().remove(AgentObjectiveState.STATE_KEY);
        entry.capabilityStates().remove(AgentResourcePlanningState.STATE_KEY);
        entry.capabilityStates().remove(AgentSupplyProcurementState.STATE_KEY);
        entry.capabilityStates().remove(AgentCareerProgressionState.STATE_KEY);
        entry.capabilityStates().remove(AgentVictoriaQuestSchedulerState.STATE_KEY);
        entry.capabilityStates().remove(AgentVictoriaTrainingState.STATE_KEY);

        agent.resetVictoriaFirstJobTestBaseline(
                bundle.firstJobId(), startVariant.level(), startVariant.exp());
        CosmicMapleIslandCohortIdentity.applyDefaultStarterWeapon(agent);
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
        moveToLithHarbor(entry, agent);
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).reset(
                bundle,
                AgentCareerProgressionState.RunMode.LEVEL15_WITH_INITIAL_SHOP,
                startVariant.variantId(),
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF,
                nowMs + START_DELAY_MS);
        AgentCareerProgressionCheckpointRuntime.persistIfDirty(entry, nowMs);
        agent.equipChanged();
        agent.saveCharToDB(false);
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

    private static void moveToLithHarbor(AgentRuntimeEntry entry, Character agent) {
        var maps = AgentMapGatewayRuntime.map();
        var map = maps.resolveMap(agent.getWorld(), agent.getClient().getChannel(), LITH_HARBOR_MAP_ID);
        Point spawn = map.getPortal(0) != null
                ? new Point(map.getPortal(0).getPosition())
                : new Point(map.getRandomPlayerSpawnpoint().getPosition());
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
    }
}
