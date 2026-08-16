package server.agents.runtime.commerce;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.economy.session.CommerceParticipant;
import server.agents.runtime.activity.session.AgentActivityPhase;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFileCommerceSessionStoreTest {
    @TempDir Path temporaryDirectory;

    @Test
    void roundTripsAndDeletesACommerceCheckpoint() {
        AgentFileCommerceSessionStore store =
                new AgentFileCommerceSessionStore(temporaryDirectory);
        AgentCommerceVisitRequest request = new AgentCommerceVisitRequest(
                "visit-file", "world-director",
                new CommerceParticipant("agent-file", "magician", .5, .5, .5,
                        .5, .5, .5, 24, .5, .5),
                AgentCommerceVisitRequest.Purpose.SELL_INVENTORY,
                30_000L, 5_000L, Map.of("source", "inventory"));
        AgentCommerceSessionCheckpoint checkpoint = new AgentCommerceSessionCheckpoint(
                AgentCommerceSessionCheckpoint.SCHEMA_VERSION, request,
                java.util.UUID.randomUUID().toString(), AgentActivityPhase.ACTIVE,
                1_000L, 1_500L, 2_000L, "browsing");

        store.save(checkpoint);

        assertEquals(checkpoint, store.load("agent-file").orElseThrow());
        store.delete("agent-file");
        assertTrue(store.load("agent-file").isEmpty());
    }
}
