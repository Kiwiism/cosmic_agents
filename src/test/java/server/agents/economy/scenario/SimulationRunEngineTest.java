package server.agents.economy.scenario;

import org.junit.jupiter.api.Test;
import server.agents.economy.catalog.CatalogBundleLoader;
import server.agents.economy.clock.ScheduledEconomyEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import server.agents.economy.persistence.RunCheckpointCodec;

import static org.junit.jupiter.api.Assertions.*;

class SimulationRunEngineTest {
    @Test
    void fastForwardsThirtyDaysWithoutWallClockWaiting() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        var handled = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog, handled::add);
        AtomicInteger durableCheckpoints = new AtomicInteger();
        engine.onCheckpoint(durableCheckpoints::incrementAndGet);

        var result = engine.advanceDays(30);

        assertEquals(Instant.parse(loaded.config().clock.logicalStart).plusSeconds(30L * 86_400),
                result.reachedAt());
        assertEquals(200, handled.stream().filter(event -> event.kind().equals(SimulationRunEngine.ADMIT_AGENT)).count());
        assertEquals(120, handled.stream().filter(event -> event.kind().equals(SimulationRunEngine.CHECKPOINT)).count());
        assertEquals(120, durableCheckpoints.get());
        assertTrue(result.processedEvents() >= 320);
        assertEquals(loaded.sha256(), engine.checkpoint(java.util.Map.of()).configHash());
    }

    @Test
    void checkpointRoundTripsAndRefusesMismatchedResume() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog, ignored -> { });
        engine.advanceDays(2);
        var encoded = new RunCheckpointCodec().encode(engine.checkpoint(java.util.Map.of("agents", 60)));
        var checkpoint = new RunCheckpointCodec().decode(encoded.json(), encoded.sha256());
        SimulationRunEngine restored = SimulationRunEngine.restore(checkpoint, loaded, catalog, ignored -> { });
        assertEquals(engine.now(), restored.now());
        assertThrows(IllegalStateException.class, () -> new RunCheckpointCodec()
                .decode(encoded.json(), "bad"));
    }

    @Test
    void physicalCapabilityStopsLogicalClockUntilCallerResumes() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        AtomicReference<SimulationRunEngine> reference = new AtomicReference<>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog,
                event -> reference.get().pauseAfterCurrentEvent("walking through FM portal"));
        reference.set(engine);

        var result = engine.advanceDays(30);

        assertTrue(result.waitingExternalAction());
        assertEquals("walking through FM portal", result.waitReason());
        assertTrue(result.reachedAt().isBefore(Instant.parse(loaded.config().clock.logicalStart)
                .plusSeconds(30L * 86_400)));
        assertEquals(1, result.processedEvents());
    }

    @Test
    void restartProducesTheSameEventOrderAndRandomStateAsAnUninterruptedRun() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        UUID runId = UUID.randomUUID();
        var uninterruptedEvents = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine uninterrupted = new SimulationRunEngine(
                runId, loaded, catalog, uninterruptedEvents::add);
        uninterrupted.advanceDays(30);

        var restartedEvents = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine first = new SimulationRunEngine(runId, loaded, catalog, restartedEvents::add);
        first.advanceDays(10);
        RunCheckpointCodec codec = new RunCheckpointCodec();
        RunCheckpointCodec.Encoded encoded = codec.encode(first.checkpoint(java.util.Map.of()));
        SimulationRunEngine restored = SimulationRunEngine.restore(
                codec.decode(encoded.json(), encoded.sha256()), loaded, catalog, restartedEvents::add);
        restored.advanceTo(Instant.parse(loaded.config().clock.logicalStart).plusSeconds(30L * 86_400));

        assertEquals(uninterruptedEvents, restartedEvents);
        assertEquals(uninterrupted.checkpoint(java.util.Map.of()).randomStates(),
                restored.checkpoint(java.util.Map.of()).randomStates());
        assertEquals(uninterrupted.checkpoint(java.util.Map.of()).queue(),
                restored.checkpoint(java.util.Map.of()).queue());
    }

    @Test
    void recurringCheckpointsContinueBeyondTheDefaultReportingHorizon() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        var events = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog, events::add);

        engine.advanceDays(31);

        assertEquals(124, events.stream().filter(event ->
                event.kind().equals(SimulationRunEngine.CHECKPOINT)).count());
        assertTrue(engine.checkpoint(java.util.Map.of()).queue().stream().anyMatch(event ->
                event.kind().equals(SimulationRunEngine.CHECKPOINT)
                        && event.dueAt().equals(engine.now().plusSeconds(6 * 3_600L))));
    }

    @Test
    void schedulerScalesToOneThousandProfilesWithoutWallClockTicks() {
        LoadedEconomyConfig loaded = new EconomyConfigLoader().load();
        loaded.config().population.maximumAgents = 1_000;
        loaded.config().population.growth.amount = 50;
        var catalog = new CatalogBundleLoader().load(loaded.config().catalog);
        var events = new ArrayList<ScheduledEconomyEvent>();
        SimulationRunEngine engine = new SimulationRunEngine(UUID.randomUUID(), loaded, catalog, events::add);

        var result = assertTimeout(java.time.Duration.ofSeconds(5), () -> engine.advanceDays(90));

        assertEquals(1_000, events.stream().filter(event ->
                event.kind().equals(SimulationRunEngine.ADMIT_AGENT)).count());
        assertEquals(360, events.stream().filter(event ->
                event.kind().equals(SimulationRunEngine.CHECKPOINT)).count());
        assertEquals(Instant.parse(loaded.config().clock.logicalStart).plusSeconds(90L * 86_400),
                result.reachedAt());
    }
}
