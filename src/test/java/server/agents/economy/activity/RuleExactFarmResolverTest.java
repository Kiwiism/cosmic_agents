package server.agents.economy.activity;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.*;
import server.agents.economy.scenario.NamedRandomStreams;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RuleExactFarmResolverTest {
    @Test
    void usesAuthoritativeDropsAndSuppressesInactiveQuestDrops() {
        EconomyCatalog catalog = new StubCatalog(List.of(
                new MonsterDropFact(100100, 0, 1_000_000, 4, 4, 0),
                new MonsterDropFact(100100, 4000019, 1_000_000, 1, 1, 0),
                new MonsterDropFact(100100, 4030000, 1_000_000, 1, 1, 99)));
        FarmSessionPlan plan = new FarmSessionPlan("farm-1", "agent-1", 100000000,
                Instant.EPOCH, Duration.ofHours(2), 1,
                List.of(new FarmSessionPlan.MonsterWork(100100, 2, 3)), Set.of(),
                List.of(new FarmSessionPlan.ItemConsumption(2000000, 1, "pot-lot")));

        FarmSessionOutcome outcome = new RuleExactFarmResolver(catalog)
                .resolve(plan, new NamedRandomStreams(7));

        assertEquals(8, outcome.mesos());
        assertEquals(6, outcome.experience());
        assertEquals(2, outcome.itemDrops().size());
        assertTrue(outcome.itemDrops().stream().noneMatch(drop -> drop.itemId() == 4030000));
        assertEquals(1, outcome.consumedItems().getFirst().quantity());
    }

    @Test
    void appliesRealGlobalRowsWithoutLocalDropMultiplierAndSeparatesEquipmentInstances() {
        EconomyCatalog catalog = new StubCatalog(List.of(
                new MonsterDropFact(100100, 1002000, 1_000_000, 2, 2, 0))) {
            @Override public Optional<ItemFact> item(int itemId) {
                return Optional.of(new ItemFact(itemId, "equip", 1, 1, 1,
                        Set.of(ItemCategory.EQUIPMENT), Map.of()));
            }
            @Override public List<GlobalDropFact> globalDrops(int mapId) {
                return List.of(new GlobalDropFact(1002001, 1_000_000, 1, 1, 1, 0),
                        new GlobalDropFact(1002002, 1_000_000, 1, 1, 1, 99));
            }
            @Override public Optional<EquipmentRollFact> rollEquipment(
                    int itemId, java.util.function.DoubleSupplier random) {
                return Optional.of(new EquipmentRollFact(itemId, Map.of("DEX", 2)));
            }
        };
        FarmSessionPlan plan = new FarmSessionPlan("farm-2", "agent-1", 100000000,
                Instant.EPOCH, Duration.ofMinutes(1), 3,
                List.of(new FarmSessionPlan.MonsterWork(100100, 1, 3)), Set.of(), List.of());

        FarmSessionOutcome outcome = new RuleExactFarmResolver(catalog)
                .resolve(plan, new NamedRandomStreams(9));

        assertEquals(3, outcome.itemDrops().size());
        assertEquals(2, outcome.itemDrops().stream().filter(drop -> drop.itemId() == 1002000).count());
        assertTrue(outcome.itemDrops().stream().noneMatch(drop -> drop.itemId() == 1002002));
        assertTrue(outcome.itemDrops().stream().allMatch(drop -> drop.quantity() == 1));
    }

    @Test
    void calibratedDeathEndsWorkAndAddsConfiguredRespawnDowntime() {
        FarmSessionPlan plan = new FarmSessionPlan("fatal-farm", "observed:death", "agent-1",
                100000001, Instant.EPOCH, Duration.ofHours(2), 1, 1d, Duration.ofSeconds(10),
                List.of(new FarmSessionPlan.MonsterWork(100100, 120, 3)), Set.of(),
                List.of(new FarmSessionPlan.ItemConsumption(2000000, 120, "pot-lot")));

        FarmSessionOutcome outcome = new RuleExactFarmResolver(new StubCatalog(List.of()))
                .resolve(plan, new NamedRandomStreams(17));

        assertTrue(outcome.death().died());
        assertEquals(10_000, outcome.death().downtimeMillis());
        assertEquals(outcome.death().occurredAt().plusSeconds(10), outcome.completedAt());
        assertTrue(outcome.killCounts().get(100100) < 120);
        assertTrue(outcome.consumedItems().isEmpty()
                || outcome.consumedItems().getFirst().quantity() < 120);
        assertEquals(outcome.killCounts().get(100100) * 3L, outcome.experience());
    }

    private static class StubCatalog implements EconomyCatalog {
        private final List<MonsterDropFact> drops;
        private StubCatalog(List<MonsterDropFact> drops) { this.drops = drops; }
        public String version() { return "test"; }
        public Optional<ItemFact> item(int itemId) { return itemId <= 0 ? Optional.empty() : Optional.of(
                new ItemFact(itemId, "item", 1, null, 100, Set.of(ItemCategory.OTHER), Map.of())); }
        public List<MonsterDropFact> monsterDrops(int monsterId) { return drops; }
        public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
    }
}
