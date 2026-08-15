package server.agents.economy.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EconomySessionPortContractTest {
    @Test
    void scheduledEntryIdentityIsDeterministicAndBounded() {
        UUID run = UUID.randomUUID();
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        var first = EconomySessionPort.EntryRequest.scheduled(run, "agent-1", at,
                Duration.ofMinutes(30), Duration.ofMinutes(5));
        var repeated = EconomySessionPort.EntryRequest.scheduled(run, "agent-1", at,
                Duration.ofMinutes(30), Duration.ofMinutes(5));

        assertEquals(first.requestId(), repeated.requestId());
        assertEquals("SCHEDULED_SCENARIO_ENTRY", first.reason());
    }

    @Test
    void rejectsImpossibleEntryAndDirectiveStates() {
        assertThrows(IllegalArgumentException.class, () -> new EconomySessionPort.EntryRequest(
                UUID.randomUUID(), "entry", Duration.ofMinutes(1), Duration.ofMinutes(2), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new EconomySessionPort.EntryResult(
                EconomySessionPort.EntryResult.Status.ACCEPTED, null, "bad", null, Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new EconomySessionPort.Directive(
                java.util.Optional.of(Instant.now()), true, java.util.Optional.empty(), false, "bad"));
        assertThrows(IllegalArgumentException.class, () -> new EconomySessionPort.ReleaseResult(
                EconomySessionPort.ReleaseResult.Status.DEFERRED, "bad", null));
    }
}
