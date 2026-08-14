package server.agents.economy.activity;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CalibratedFarmPlanFactoryTest {
    private final EconomyCatalog catalog = new EconomyCatalog() {
        public String version() { return "test"; }
        public Optional<ItemFact> item(int itemId) { return Optional.empty(); }
        public List<MonsterDropFact> monsterDrops(int monsterId) { return List.of(); }
        public Optional<MonsterFact> monster(int monsterId) {
            return monsterId == 100100 ? Optional.of(new MonsterFact(monsterId, 8, 12)) : Optional.empty();
        }
        public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
    };

    @Test
    void turnsObservedRatesIntoExplicitCatalogBackedWorkWithLotConsumption() {
        ActivityCalibration calibration = new ActivityCalibration("observed-build-7", "build-7",
                100000001, 10, "WARRIOR", Instant.EPOCH, 12, 2.5,
                Map.of(100100, 1d), Map.of(2000000, 0.1), 0);

        FarmSessionPlan plan = new CalibratedFarmPlanFactory(catalog).create("session-1", "agent-1",
                Instant.EPOCH, Duration.ofMinutes(20), calibration, 1, Set.of(),
                List.of(new CalibratedFarmPlanFactory.AvailableLot(2000000, 5, "lot-1")));

        assertEquals("observed-build-7", plan.calibrationId());
        assertEquals(List.of(new FarmSessionPlan.MonsterWork(100100, 50, 12)), plan.monsters());
        assertEquals(List.of(new FarmSessionPlan.ItemConsumption(2000000, 2, "lot-1")),
                plan.consumedItems());
    }

    @Test
    void failsClosedWhenCalibrationNeedsUnownedConsumables() {
        ActivityCalibration calibration = new ActivityCalibration("observed-build-7", "build-7",
                100000001, 10, "WARRIOR", Instant.EPOCH, 12, 1,
                Map.of(100100, 1d), Map.of(2000000, 1d), 0);

        assertThrows(IllegalStateException.class, () -> new CalibratedFarmPlanFactory(catalog).create(
                "session-1", "agent-1", Instant.EPOCH, Duration.ofMinutes(2), calibration,
                1, Set.of(), List.of()));
    }
}
