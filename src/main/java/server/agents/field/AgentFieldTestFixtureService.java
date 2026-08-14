package server.agents.field;

import client.Character;
import server.agents.capabilities.build.profiles.AgentApBuildProfileService;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.movement.AgentMovementBroadcastService;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementStateResetService;
import server.agents.integration.AgentCharacterGatewayRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.VictoriaFirstJobMvpTestService;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.capabilities.supplies.AgentResourcePlanningState;
import server.agents.capabilities.supplies.AgentSupplyMaintenanceEvaluationState;
import server.agents.capabilities.supplies.AgentSupplyProcurementState;
import server.agents.runtime.maintenance.AgentRemediationState;

import java.awt.Point;
import java.io.IOException;

/** Guarded deterministic level-15 fixture for manual field-scaling exercises. */
final class AgentFieldTestFixtureService {
    private static final int TEST_LEVEL = 15;
    private static final short BENCHMARK_SUPPLY_QUANTITY = 200;

    private AgentFieldTestFixtureService() {
    }

    static Prepared prepare(
            Character operator, AgentRuntimeEntry entry, String career, long nowMs) throws IOException {
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (operator == null || operator.getMap() == null || agent == null) {
            throw new IllegalArgumentException("a live operator map and spawned Agent are required");
        }
        if (AgentFieldRuntime.snapshot(operator, nowMs) != null) {
            throw new IllegalStateException("stop the active field session before resetting a fixture");
        }

        AgentCareerBuildBundle bundle = VictoriaFirstJobMvpTestService.resetAndStart(
                entry, career, "lv10", VictoriaFirstJobMvpTestService.Checkpoint.CHECKPOINT_2, nowMs);
        AgentUniversalPlanRuntime.cancel(entry, agent, "field exercise fixture", nowMs);
        AgentUniversalPlanRuntime.clearCheckpoint(entry, agent.getId());
        AgentMovementCommandRuntime.stop(entry);
        clearSupplyMaintenance(entry);

        if (agent.getExp() > 0) {
            agent.loseExp(agent.getExp(), false, false);
        }
        while (agent.getLevel() < TEST_LEVEL) {
            agent.levelUp(false);
            AgentApBuildProfileService.autoAssign(entry, agent);
            AgentSpBuildProfileService.autoAssign(entry, agent);
        }
        if (agent.getLevel() != TEST_LEVEL) {
            throw new IllegalStateException("fixture baseline is already above level " + TEST_LEVEL);
        }
        AgentApBuildProfileService.autoAssign(entry, agent);
        AgentSpBuildProfileService.autoAssign(entry, agent);
        grantBenchmarkSupplies(agent);
        agent.healHpMp();

        Point spawn = operator.getMap().getPortal(0) == null
                ? new Point(operator.getPosition())
                : new Point(operator.getMap().getPortal(0).getPosition());
        AgentMapGatewayRuntime.map().changeMap(agent, operator.getMap(), spawn);
        AgentMovementStateResetService.resetEntryState(entry);
        AgentMovementBroadcastService.broadcastMovement(entry);
        agent.equipChanged();
        AgentCharacterGatewayRuntime.characters().save(agent, false);
        return new Prepared(agent.getName(), bundle.bundleId(), agent.getJob().getId(),
                agent.getLevel(), agent.getExp(), agent.getMapId());
    }

    private static void clearSupplyMaintenance(AgentRuntimeEntry entry) {
        entry.capabilityStates().remove(AgentSupplyProcurementState.STATE_KEY);
        entry.capabilityStates().remove(AgentResourcePlanningState.STATE_KEY);
        entry.capabilityStates().remove(AgentSupplyMaintenanceEvaluationState.STATE_KEY);
        entry.capabilityStates().remove(AgentRemediationState.STATE_KEY);
    }

    private static void grantBenchmarkSupplies(Character agent) {
        var inventory = AgentInventoryGatewayRuntime.inventory();
        requireItem(inventory.addItem(agent, 2000002, BENCHMARK_SUPPLY_QUANTITY), "White Potions");
        requireItem(inventory.addItem(agent, 2000003, BENCHMARK_SUPPLY_QUANTITY), "Blue Potions");
        if (agent.getJob().getId() == 300) {
            requireItem(inventory.addItem(agent, 2060000, BENCHMARK_SUPPLY_QUANTITY), "Bow Arrows");
        } else if (agent.getJob().getId() == 500) {
            requireItem(inventory.addItem(agent, 2330000, BENCHMARK_SUPPLY_QUANTITY), "Bullets");
        }
    }

    private static void requireItem(boolean added, String itemName) {
        if (!added) {
            throw new IllegalStateException("could not provision benchmark " + itemName);
        }
    }

    record Prepared(String name, String bundleId, int jobId, int level, int exp, int mapId) {
    }
}
