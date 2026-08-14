package server.agents.economy.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import server.agents.economy.scenario.SimulationRunEngine;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunCheckpointCodecJsonbTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void hashSurvivesJsonbKeyReorderingAndWhitespace() throws Exception {
        UUID runId = UUID.randomUUID();
        var checkpoint = new SimulationRunEngine.RunCheckpoint(runId, Instant.EPOCH,
                "config", "catalog", List.of(), Map.of("stream", 12L),
                Map.of("z", Map.of("second", 2, "first", 1), "a", List.of(3, 4)));
        RunCheckpointCodec codec = new RunCheckpointCodec();
        RunCheckpointCodec.Encoded encoded = codec.encode(checkpoint);
        JsonNode root = JSON.readTree(encoded.json());
        String reordered = "{\n  \"queue\":" + root.get("queue")
                + ",\"domainState\":" + root.get("domainState")
                + ",\"randomStates\":" + root.get("randomStates")
                + ",\"catalogVersion\":" + root.get("catalogVersion")
                + ",\"configHash\":" + root.get("configHash")
                + ",\"logicalTime\":" + root.get("logicalTime")
                + ",\"runId\":" + root.get("runId") + "}";

        assertEquals(runId, codec.decode(reordered, encoded.sha256()).runId());
    }

    @Test
    void semanticTamperingStillFailsClosed() {
        UUID runId = UUID.randomUUID();
        var checkpoint = new SimulationRunEngine.RunCheckpoint(runId, Instant.EPOCH,
                "config", "catalog", List.of(), Map.of(), Map.of());
        RunCheckpointCodec codec = new RunCheckpointCodec();
        RunCheckpointCodec.Encoded encoded = codec.encode(checkpoint);

        assertThrows(IllegalStateException.class, () -> codec.decode(
                encoded.json().replace(runId.toString(), UUID.randomUUID().toString()),
                encoded.sha256()));
    }
}
