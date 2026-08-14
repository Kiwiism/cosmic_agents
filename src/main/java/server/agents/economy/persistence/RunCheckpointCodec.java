package server.agents.economy.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.clock.ScheduledEconomyEvent;
import server.agents.economy.scenario.SimulationRunEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Explicit JSON codec avoids a hidden dependency on Jackson wall-clock/time modules. */
public final class RunCheckpointCodec {
    private static final ObjectMapper JSON = new ObjectMapper();

    public Encoded encode(SimulationRunEngine.RunCheckpoint checkpoint) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("runId", checkpoint.runId().toString());
        root.put("logicalTime", checkpoint.logicalTime().toString());
        root.put("configHash", checkpoint.configHash());
        root.put("catalogVersion", checkpoint.catalogVersion());
        root.put("randomStates", checkpoint.randomStates());
        root.put("domainState", checkpoint.domainState());
        root.put("queue", checkpoint.queue().stream().map(event -> Map.of(
                "dueAt", event.dueAt().toString(), "sequence", event.sequence(),
                "kind", event.kind(), "subjectId", event.subjectId(),
                "parameters", event.parameters())).toList());
        try {
            String json = JSON.writeValueAsString(root);
            return new Encoded(json, sha256(json));
        } catch (JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not encode run checkpoint", failure);
        }
    }

    @SuppressWarnings("unchecked")
    public SimulationRunEngine.RunCheckpoint decode(String json, String expectedHash) {
        if (!sha256(json).equals(expectedHash)) throw new IllegalStateException("Checkpoint hash mismatch");
        try {
            JsonNode root = JSON.readTree(json);
            List<ScheduledEconomyEvent> queue = new ArrayList<>();
            for (JsonNode event : root.path("queue")) {
                queue.add(new ScheduledEconomyEvent(Instant.parse(event.path("dueAt").asText()),
                        event.path("sequence").asLong(), event.path("kind").asText(),
                        event.path("subjectId").asText(),
                        JSON.convertValue(event.path("parameters"), Map.class)));
            }
            return new SimulationRunEngine.RunCheckpoint(
                    UUID.fromString(root.path("runId").asText()),
                    Instant.parse(root.path("logicalTime").asText()),
                    root.path("configHash").asText(), root.path("catalogVersion").asText(), queue,
                    JSON.convertValue(root.path("randomStates"), Map.class),
                    JSON.convertValue(root.path("domainState"), Map.class));
        } catch (JsonProcessingException failure) {
            throw new EconomyPersistenceException("Could not decode run checkpoint", failure);
        }
    }

    private static String sha256(String json) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Encoded(String json, String sha256) { }
}
