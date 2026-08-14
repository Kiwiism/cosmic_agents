package server.agents.economy.persistence;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EconomyEvidencePipelineTest {
    @Test
    void drainsAllRelayAndIngestionBatchesBeforeProjecting() {
        EconomyOutboxRelay relay = mock(EconomyOutboxRelay.class);
        JdbcCosmicEconomicEventIngestor ingestor = mock(JdbcCosmicEconomicEventIngestor.class);
        JdbcEconomyProjectionService projections = mock(JdbcEconomyProjectionService.class);
        JdbcEconomyInvariantAuditor auditor = mock(JdbcEconomyInvariantAuditor.class);
        UUID runId = UUID.randomUUID();
        Instant logicalAt = Instant.parse("2026-01-02T00:00:00Z");

        when(relay.relay(2)).thenReturn(new EconomyOutboxRelay.Result(2, 0),
                new EconomyOutboxRelay.Result(1, 0), new EconomyOutboxRelay.Result(0, 0));
        when(ingestor.ingest(2)).thenReturn(new JdbcCosmicEconomicEventIngestor.Result(2, 0, null),
                new JdbcCosmicEconomicEventIngestor.Result(1, 0, null),
                new JdbcCosmicEconomicEventIngestor.Result(0, 0, null));
        JdbcEconomyProjectionService.Result projection =
                new JdbcEconomyProjectionService.Result(1, 2, 3, 4);
        when(projections.rebuild(runId)).thenReturn(projection);
        when(auditor.audit(runId, logicalAt))
                .thenReturn(new JdbcEconomyInvariantAuditor.Audit(true, List.of()));

        EconomyEvidencePipeline.Result result = new EconomyEvidencePipeline(
                relay, ingestor, projections, auditor).process(runId, logicalAt, 2);

        assertEquals(3, result.relay().delivered());
        assertEquals(3, result.ingestion().ingested());
        assertEquals(projection, result.projections());
        verify(relay, times(3)).relay(2);
        verify(ingestor, times(3)).ingest(2);
        verify(projections).rebuild(runId);
        verify(auditor).audit(runId, logicalAt);
    }
}
