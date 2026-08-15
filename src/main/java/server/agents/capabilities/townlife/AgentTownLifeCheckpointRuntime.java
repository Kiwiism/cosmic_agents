package server.agents.capabilities.townlife;

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

/** Best-effort persistence for TownLife intent; a failure never corrupts the live session. */
public final class AgentTownLifeCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentTownLifeCheckpointRuntime.class);
    private static final Path DIRECTORY = Path.of(
            ".runtime", "agents", "plans", "town-life-checkpoints").toAbsolutePath().normalize();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentTownLifeCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getId() <= 0) {
            return false;
        }
        Path path = path(agent.getId());
        if (!Files.exists(path)) {
            return false;
        }
        try {
            AgentTownLifeCheckpoint checkpoint = MAPPER.readValue(
                    path.toFile(), AgentTownLifeCheckpoint.class);
            if (checkpoint.characterId() != agent.getId()
                    || checkpoint.townMapId() != agent.getMapId()) {
                return false;
            }
            AgentTownLifeVisitRequest request = new AgentTownLifeVisitRequest(
                    checkpoint.townMapId(), checkpoint.purpose(), checkpoint.reason(),
                    checkpoint.remainingFreeTimeMs());
            return AgentTownLifeLifecycleRuntime.start(
                    entry, agent, request, AgentTownLifeAdmissionMode.MANUAL_ONLY,
                    nowMs, agent.getId()).started();
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore TownLife checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    static void persist(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getId() <= 0) {
            return;
        }
        AgentTownLifeState state = entry.capabilityStates().require(AgentTownLifeState.STATE_KEY);
        if (!state.enabled()) {
            return;
        }
        AgentTownLifeCheckpoint checkpoint = new AgentTownLifeCheckpoint(
                1, agent.getId(), state.townMapId(), state.visitPurpose(), state.visitReason(),
                state.remainingFreeTimeMs(nowMs), nowMs);
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
            log.warn("Could not persist TownLife checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    static void delete(Character agent) {
        if (agent == null || agent.getId() <= 0) {
            return;
        }
        try {
            Files.deleteIfExists(path(agent.getId()));
        } catch (IOException failure) {
            log.warn("Could not delete TownLife checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    private static Path path(int characterId) {
        return DIRECTORY.resolve(characterId + ".json");
    }
}
