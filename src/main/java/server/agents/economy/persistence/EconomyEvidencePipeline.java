package server.agents.economy.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One operational evidence cycle: relay, exact ingestion, rebuildable projections, then audit. */
public final class EconomyEvidencePipeline {
    private final EconomyOutboxRelay relay;
    private final JdbcCosmicEconomicEventIngestor ingestor;
    private final JdbcEconomyProjectionService projections;
    private final JdbcEconomyInvariantAuditor auditor;

    public EconomyEvidencePipeline(EconomyOutboxRelay relay, JdbcCosmicEconomicEventIngestor ingestor,
                                   JdbcEconomyProjectionService projections,
                                   JdbcEconomyInvariantAuditor auditor) {
        this.relay = Objects.requireNonNull(relay); this.ingestor = Objects.requireNonNull(ingestor);
        this.projections = Objects.requireNonNull(projections); this.auditor = Objects.requireNonNull(auditor);
    }

    public Result process(UUID runId, Instant logicalAt, int batchSize) {
        if (batchSize <= 0) throw new IllegalArgumentException("batch size must be positive");
        int delivered = 0;
        int relayFailures = 0;
        while (true) {
            EconomyOutboxRelay.Result batch = relay.relay(batchSize);
            delivered = Math.addExact(delivered, batch.delivered());
            relayFailures = Math.addExact(relayFailures, batch.failed());
            if (batch.failed() > 0 || batch.delivered() == 0) break;
        }
        int ingested = 0;
        int quarantined = 0;
        UUID failedOutboxId = null;
        while (true) {
            JdbcCosmicEconomicEventIngestor.Result batch = ingestor.ingest(batchSize);
            ingested = Math.addExact(ingested, batch.ingested());
            quarantined = Math.addExact(quarantined, batch.quarantined());
            if (batch.failedOutboxId() != null) failedOutboxId = batch.failedOutboxId();
            if (batch.quarantined() > 0 || batch.ingested() == 0) break;
        }
        JdbcEconomyProjectionService.Result projected = projections.rebuild(runId);
        JdbcEconomyInvariantAuditor.Audit audit = auditor.audit(runId, logicalAt);
        return new Result(new EconomyOutboxRelay.Result(delivered, relayFailures),
                new JdbcCosmicEconomicEventIngestor.Result(ingested, quarantined, failedOutboxId),
                projected, audit);
    }

    public record Result(EconomyOutboxRelay.Result relay,
                         JdbcCosmicEconomicEventIngestor.Result ingestion,
                         JdbcEconomyProjectionService.Result projections,
                         JdbcEconomyInvariantAuditor.Audit audit) { }
}
