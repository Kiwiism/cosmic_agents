package server.agents.runtime.field;

import client.Character;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AgentFieldVisitLeaseCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(
            AgentFieldVisitLeaseCheckpointRuntime.class);
    private static final Path DIRECTORY = Path.of(
            ".runtime", "agents", "plans", "field-visit-leases")
            .toAbsolutePath().normalize();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentFieldVisitLeaseCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getId() <= 0) return false;
        Path path = path(agent.getId());
        if (!Files.exists(path)) return false;
        try {
            AgentFieldVisitLeaseCheckpoint checkpoint = MAPPER.readValue(
                    path.toFile(), AgentFieldVisitLeaseCheckpoint.class);
            AgentFieldActivityState.Snapshot field = entry.capabilityStates()
                    .require(AgentFieldActivityState.STATE_KEY).snapshot();
            if (!field.active() || checkpoint.characterId() != agent.getId()
                    || checkpoint.mapId() != agent.getMapId()
                    || !checkpoint.sessionId().equals(field.handle().sessionId())) {
                delete(agent);
                return false;
            }
            AgentFieldSessionHandle handle = new AgentFieldSessionHandle(
                    checkpoint.sessionId(), checkpoint.requestId(), checkpoint.callerId(),
                    checkpoint.characterId(), checkpoint.mapId(), checkpoint.startedAtMs());
            AgentFieldVisitLeaseRuntime.restore(entry, new AgentFieldVisitLeaseRuntime.Restored(
                    handle, checkpoint.exitAtMs(), checkpoint.gracefulTimeoutMs(),
                    checkpoint.exitReason(), checkpoint.exitRequested()));
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore field visit lease for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    static void persist(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getId() <= 0) return;
        AgentFieldVisitLeaseState state = entry.capabilityStates()
                .require(AgentFieldVisitLeaseState.STATE_KEY);
        if (!state.active()) return;
        AgentFieldSessionHandle handle = state.handle();
        AgentFieldVisitLeaseCheckpoint checkpoint = new AgentFieldVisitLeaseCheckpoint(
                1, agent.getId(), handle.sessionId(), handle.requestId(), handle.callerId(),
                handle.mapId(), handle.startedAtMs(), state.exitAtMs(),
                state.gracefulTimeoutMs(), state.exitReason(), state.exitRequested());
        try {
            Files.createDirectories(DIRECTORY);
            Path target = path(agent.getId());
            Path temporary = Files.createTempFile(DIRECTORY, target.getFileName().toString(), ".tmp");
            try {
                MAPPER.writeValue(temporary.toFile(), checkpoint);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException | AccessDeniedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist field visit lease for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    static void delete(Character agent) {
        if (agent == null || agent.getId() <= 0) return;
        try {
            Files.deleteIfExists(path(agent.getId()));
        } catch (IOException failure) {
            log.warn("Could not delete field visit lease for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    private static Path path(int characterId) {
        return DIRECTORY.resolve(characterId + ".json");
    }
}
