package server.agents.capabilities.presentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentPresentationTelemetryTest {
    @Test
    void countersRemainFixedCardinalityAndTrackIntentKinds() {
        AgentPresentationTelemetry.resetForTests();

        AgentPresentationTelemetry.recordTrigger();
        AgentPresentationTelemetry.recordScheduled();
        AgentPresentationTelemetry.recordExecuted(AgentPresentationIntent.HOP);
        AgentPresentationTelemetry.recordObserverSuppressed();
        AgentPresentationTelemetry.recordUnsafeBlocked();
        AgentPresentationTelemetry.recordCoalesced();

        AgentPresentationTelemetry.Snapshot snapshot = AgentPresentationTelemetry.snapshot();
        assertEquals(1L, snapshot.triggers());
        assertEquals(1L, snapshot.scheduled());
        assertEquals(1L, snapshot.executed());
        assertEquals(1L, snapshot.observerSuppressed());
        assertEquals(1L, snapshot.unsafeBlocked());
        assertEquals(1L, snapshot.coalesced());
        assertEquals(1L, snapshot.executedByIntent().get(AgentPresentationIntent.HOP));
        assertEquals(AgentPresentationIntent.values().length, snapshot.executedByIntent().size());
    }
}
