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

/** Best-effort durable persistence for externally owned field visits. */
public final class AgentFieldCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentFieldCheckpointRuntime.class);
    private static final Path DIRECTORY = Path.of(
            ".runtime", "agents", "plans", "field-activity-checkpoints")
            .toAbsolutePath().normalize();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentFieldCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getId() <= 0) return false;
        Path path = path(agent.getId());
        if (!Files.exists(path)) return false;
        try {
            AgentFieldCheckpoint checkpoint = MAPPER.readValue(path.toFile(), AgentFieldCheckpoint.class);
            if (checkpoint.characterId() != agent.getId() || checkpoint.mapId() != agent.getMapId()) {
                delete(agent);
                return false;
            }
            AgentFieldSessionResult restored = AgentFieldActivityRuntime.requestSession(
                    entry, agent, checkpoint.entryRequest(),
                    AgentFieldAdmissionMode.CREATE_OR_JOIN, nowMs);
            if (!restored.started()
                    && restored.status() != AgentFieldSessionResult.Status.ALREADY_ACTIVE_SAME_REQUEST) {
                return false;
            }
            if (checkpoint.phase() == AgentFieldActivityState.Phase.SUSPENDED) {
                AgentFieldActivityRuntime.suspend(entry, agent, "restored suspended field visit", nowMs);
            } else if (checkpoint.phase() == AgentFieldActivityState.Phase.DRAINING) {
                AgentFieldActivityRuntime.requestExit(entry, agent, AgentFieldExitRequest.graceful(
                        restored.handle(), checkpoint.exitReason(), nowMs,
                        nowMs + Math.max(1L, checkpoint.remainingExitDeadlineMs())));
            } else if (checkpoint.phase() == AgentFieldActivityState.Phase.RESTING
                    && checkpoint.remainingRestMs() > 0L) {
                AgentFieldActivityRuntime.requestRest(entry, agent, checkpoint.remainingRestMs(),
                        checkpoint.restReason(), nowMs);
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore field checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    static void persist(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getId() <= 0) return;
        AgentFieldActivityState.Snapshot state = entry.capabilityStates()
                .require(AgentFieldActivityState.STATE_KEY).snapshot();
        if (!state.active()) return;
        var intent = state.visit().intent();
        AgentFieldCheckpoint checkpoint = new AgentFieldCheckpoint(
                1, agent.getId(), state.handle().mapId(), state.handle().requestId(),
                state.handle().callerId(), intent.type(), intent.objectiveId(),
                intent.requiredMobIds(), intent.requiredKills(), intent.temporaryVisitor(),
                state.visit().acceptingQuestVisitors(), state.visit().maximumParticipants(),
                state.visit().restAllowed(), state.visit().narrationLevel(), state.phase(),
                state.exitReason(), state.phase() == AgentFieldActivityState.Phase.DRAINING
                        ? Math.max(1L, state.exitDeadlineMs() - nowMs) : 0L,
                state.restReason(), state.phase() == AgentFieldActivityState.Phase.RESTING
                        ? Math.max(1L, state.restUntilMs() - nowMs) : 0L, nowMs);
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
            log.warn("Could not persist field checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    static void delete(Character agent) {
        if (agent == null || agent.getId() <= 0) return;
        try {
            Files.deleteIfExists(path(agent.getId()));
        } catch (IOException failure) {
            log.warn("Could not delete field checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    private static Path path(int characterId) {
        return DIRECTORY.resolve(characterId + ".json");
    }
}
