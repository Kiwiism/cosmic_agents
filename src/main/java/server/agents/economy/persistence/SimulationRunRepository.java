package server.agents.economy.persistence;

import server.agents.economy.catalog.CatalogBundleDescriptor;
import server.agents.economy.scenario.LoadedEconomyConfig;
import server.agents.economy.scenario.SimulationRunEngine;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRunRepository {
    void create(UUID runId, LoadedEconomyConfig config, CatalogBundleDescriptor catalog);
    void updateLogicalTime(UUID runId, Instant logicalTime, String status);
    default void updateStatus(UUID runId, Instant logicalTime, String status, String failureReason) {
        updateLogicalTime(runId, logicalTime, status);
    }
    void saveCheckpoint(SimulationRunEngine.RunCheckpoint checkpoint);
    Optional<SimulationRunEngine.RunCheckpoint> latestCheckpoint(UUID runId);
    default Optional<RunRecord> find(UUID runId) { return Optional.empty(); }
    default void saveDayClose(DayCloseRecord close) { }

    record RunRecord(UUID runId, String status, Instant logicalStartedAt,
                     Instant logicalCurrentAt, Instant targetLogicalAt,
                     String configHash, String catalogVersion, String failureReason) { }

    record DayCloseRecord(UUID runId, int dayIndex, Instant dayStartedAt, Instant dayClosedAt,
                          String checkpointHash, int relayed, int relayFailures, int ingested,
                          int quarantined, boolean auditClean, int violationCount) { }
}
