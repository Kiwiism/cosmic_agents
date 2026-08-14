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
                List.of(new FarmSessionPlan.MonsterWork(100100, 2, 3)), Set.of(), Map.of(2000000, 1));

        FarmSessionOutcome outcome = new RuleExactFarmResolver(catalog)
                .resolve(plan, new NamedRandomStreams(7));

        assertEquals(8, outcome.mesos());
        assertEquals(6, outcome.experience());
        assertEquals(2, outcome.itemDrops().size());
        assertTrue(outcome.itemDrops().stream().noneMatch(drop -> drop.itemId() == 4030000));
        assertEquals(Map.of(2000000, 1), outcome.consumedItems());
    }

    private record StubCatalog(List<MonsterDropFact> drops) implements EconomyCatalog {
        public String version() { return "test"; }
        public Optional<ItemFact> item(int itemId) { return Optional.empty(); }
        public List<MonsterDropFact> monsterDrops(int monsterId) { return drops; }
        public Optional<NpcShopFact> npcShop(int npcId) { return Optional.empty(); }
    }
}
