package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.economy.activity.ActivityCalibration;
import server.agents.economy.activity.ActivityCalibrationRepository;
import server.agents.economy.activity.FarmSessionPlan;
import server.agents.economy.activity.VictoriaActivityMapCatalog;
import server.agents.economy.catalog.*;
import server.agents.economy.session.CommerceParticipant;
import server.agents.economy.scenario.EconomyEngineConfig;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalibratedCosmicActivityPlannerTest {
    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void scalesOnlyMatchingRealCalibrationIntoExplicitMonsterWork() {
        Character agent = mock(Character.class);
        when(agent.getLevel()).thenReturn(10); when(agent.getStartedQuests()).thenReturn(List.of());
        ActivityCalibration calibration = new ActivityCalibration("observed:build:100010000:warrior",
                "build", 100010000, 10, "warrior", AT, 8, 2,
                Map.of(100100, .25, 1210100, .75), Map.of(), .01);
        ActivityCalibrationRepository repository = (build, map, level, job, samples) ->
                map == 100010000 && build.equals("build") && samples == 5
                        ? Optional.of(calibration) : Optional.empty();
        CalibratedCosmicActivityPlanner planner = new CalibratedCosmicActivityPlanner(config(), repository,
                new VictoriaActivityMapCatalog("/agents/catalogs/adaptive/victoria-map-facts.json"), catalog());

        FarmSessionPlan plan = planner.plan(agent, profile(), AT);

        assertEquals(calibration.calibrationId(), plan.calibrationId());
        assertEquals(100010000, plan.mapId()); assertEquals(60, plan.duration().toMinutes());
        assertEquals(List.of(new FarmSessionPlan.MonsterWork(100100, 30, 8),
                new FarmSessionPlan.MonsterWork(1210100, 90, 20)), plan.monsters());
        assertEquals(1, plan.dropRateMultiplier());
    }

    @Test
    void failsClosedWhenNoRealCalibrationMatches() {
        Character agent = mock(Character.class); when(agent.getLevel()).thenReturn(10);
        CalibratedCosmicActivityPlanner planner = new CalibratedCosmicActivityPlanner(config(),
                (build, map, level, job, samples) -> Optional.empty(),
                new VictoriaActivityMapCatalog("/agents/catalogs/adaptive/victoria-map-facts.json"), catalog());
        assertThrows(CalibratedCosmicActivityPlanner.MissingActivityCalibrationException.class,
                () -> planner.plan(agent, profile(), AT));
    }

    @Test
    void shortensWorkToActualCalibratedConsumableRunway() {
        Character agent = mock(Character.class); Inventory use = mock(Inventory.class);
        when(agent.getLevel()).thenReturn(10); when(agent.getStartedQuests()).thenReturn(List.of());
        when(agent.getInventory(InventoryType.USE)).thenReturn(use);
        when(use.countById(2000000)).thenReturn(30);
        ActivityCalibration calibration = new ActivityCalibration("observed:resource-bound", "build",
                100010000, 10, "warrior", AT, 8, 2, Map.of(100100, 1d),
                Map.of(2000000, 1d), .01);
        CalibratedCosmicActivityPlanner planner = new CalibratedCosmicActivityPlanner(config(),
                (build, map, level, job, samples) -> map == 100010000
                        ? Optional.of(calibration) : Optional.empty(),
                new VictoriaActivityMapCatalog("/agents/catalogs/adaptive/victoria-map-facts.json"), catalog());

        FarmSessionPlan plan = planner.plan(agent, profile(), AT);

        assertEquals(30, plan.duration().toMinutes());
        assertEquals(60, plan.monsters().getFirst().kills());
        assertEquals(30, plan.consumedItems().getFirst().quantity());
    }

    private static EconomyEngineConfig.Activity config() {
        EconomyEngineConfig.Activity config = new EconomyEngineConfig.Activity();
        config.agentBuild = "build"; config.minimumCalibrationSamples = 5;
        config.medianSessionMinutes = 60; config.maximumSessionMinutes = 120;
        config.objectiveAware = true; config.consumeAmmunition = true;
        config.consumeHpPotions = true; config.consumeMpPotions = true;
        return config;
    }

    private static CommerceParticipant profile() {
        return new CommerceParticipant("agent-1", "warrior", .5, .5, .5, .5,
                .5, .5, 24, .5, .5);
    }

    private static EconomyCatalog catalog() {
        return new EconomyCatalog() {
            @Override public String version() { return "test"; }
            @Override public Optional<ItemFact> item(int itemId) { return Optional.empty(); }
            @Override public List<MonsterDropFact> monsterDrops(int monsterId) { return List.of(); }
            @Override public Optional<MonsterFact> monster(int monsterId) {
                return Optional.of(new MonsterFact(monsterId, 10, monsterId == 100100 ? 8 : 20));
            }
            @Override public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
        };
    }
}
