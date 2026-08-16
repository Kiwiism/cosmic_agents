package server.agents.observation.commerce;

import server.agents.economy.persistence.EconomyEvidencePipeline;
import server.agents.economy.scenario.ManagedEconomyRun;

import java.nio.file.Path;
import java.util.UUID;

/** External 30-day observation facade; Commerce has no dependency on this package. */
public final class CommerceObservationRuntime {
    public static final Path DEFAULT_CONFIG = Path.of(
            "config/economy/economy-commerce-observe-30day.yaml");

    private CommerceObservationRuntime() {
    }

    public static CommerceScenarioRuntime.Preflight preflight() {
        return CommerceScenarioRuntime.preflight(DEFAULT_CONFIG);
    }

    public static CommerceScenarioRuntime.Status start(UUID runId) {
        return CommerceScenarioRuntime.start(runId, DEFAULT_CONFIG);
    }

    public static CommerceScenarioRuntime.Status resume(UUID runId) {
        return CommerceScenarioRuntime.resume(runId, DEFAULT_CONFIG);
    }

    public static ManagedEconomyRun.AdvanceResult advanceDays(long days) {
        return CommerceScenarioRuntime.advanceDays(days);
    }

    public static CommerceScenarioRuntime.Status status() {
        return CommerceScenarioRuntime.status();
    }

    public static CommerceScenarioRuntime.ObservationSnapshot snapshot() {
        return CommerceScenarioRuntime.observationSnapshot();
    }

    public static EconomyEvidencePipeline.Result audit() {
        return CommerceScenarioRuntime.audit();
    }

    public static void stop() {
        CommerceScenarioRuntime.stop();
    }
}
