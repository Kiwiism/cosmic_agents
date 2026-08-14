package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.CatalogBundleLoader;
import server.agents.economy.clock.ScheduledEconomyEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationRunEngineTest {
    @Test
    void fastForwardsThirtyDaysWithoutWallClockWaiting() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        var handled = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog, handled::add);

        var result = engine.advanceDays(30);

        assertEquals(Instant.parse(loaded.config().clock.logicalStart).plusSeconds(30L * 86_400),
                result.reachedAt());
        assertEquals(200, handled.stream().filter(event -> event.kind().equals(SimulationRunEngine.ADMIT_AGENT)).count());
        assertEquals(120, handled.stream().filter(event -> event.kind().equals(SimulationRunEngine.CHECKPOINT)).count());
        assertTrue(result.processedEvents() >= 320);
        assertEquals(loaded.sha256(), engine.checkpoint(java.util.Map.of()).configHash());
    }
}
