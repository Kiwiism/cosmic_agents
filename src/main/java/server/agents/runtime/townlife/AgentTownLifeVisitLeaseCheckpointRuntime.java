package server.agents.runtime.townlife;

import client.Character;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.capabilities.townlife.AgentTownLifeRuntime;
import server.agents.capabilities.townlife.AgentTownLifeSessionHandle;
import server.agents.capabilities.townlife.AgentTownLifeState;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class AgentTownLifeVisitLeaseCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(
            AgentTownLifeVisitLeaseCheckpointRuntime.class);
    private static final Path DIRECTORY = Path.of(
            ".runtime", "agents", "plans", "town-life-visit-leases")
            .toAbsolutePath().normalize();
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private AgentTownLifeVisitLeaseCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getId() <= 0) {
            return false;
        }
        Path path = path(agent.getId());
        if (!Files.exists(path)) {
            return false;
        }
        try {
            AgentTownLifeVisitLeaseCheckpoint checkpoint = MAPPER.readValue(
                    path.toFile(), AgentTownLifeVisitLeaseCheckpoint.class);
            AgentTownLifeState townState = entry.capabilityStates()
                    .require(AgentTownLifeState.STATE_KEY);
            if (!AgentTownLifeRuntime.active(entry)
                    || checkpoint.characterId() != agent.getId()
                    || checkpoint.townMapId() != agent.getMapId()
                    || !checkpoint.sessionId().equals(townState.sessionId())) {
                delete(agent);
                return false;
            }
            AgentTownLifeSessionHandle handle = new AgentTownLifeSessionHandle(
                    checkpoint.sessionId(), checkpoint.requestId(), checkpoint.callerId(),
                    checkpoint.characterId(), checkpoint.townMapId(), checkpoint.startedAtMs());
            AgentTownLifeVisitLeaseRuntime.restore(entry, agent,
                    new AgentTownLifeVisitLeaseRuntime.AgentTownLifeSessionHandleRestored(
                            handle, checkpoint.exitAtMs(), checkpoint.gracefulTimeoutMs(),
                            checkpoint.exitReason()));
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore TownLife visit lease for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    static void persist(AgentRuntimeEntry entry, Character agent) {
        if (entry == null || agent == null || agent.getId() <= 0) {
            return;
        }
        AgentTownLifeVisitLeaseState lease = entry.capabilityStates()
                .require(AgentTownLifeVisitLeaseState.STATE_KEY);
        if (!lease.active()) {
            return;
        }
        AgentTownLifeSessionHandle handle = lease.handle();
        AgentTownLifeVisitLeaseCheckpoint checkpoint = new AgentTownLifeVisitLeaseCheckpoint(
                1, agent.getId(), handle.sessionId(), handle.requestId(), handle.callerId(),
                handle.townMapId(), handle.startedAtMs(), lease.exitAtMs(),
                lease.gracefulTimeoutMs(), lease.exitReason());
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
            log.warn("Could not persist TownLife visit lease for {} ({})",
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
            log.warn("Could not delete TownLife visit lease for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    private static Path path(int characterId) {
        return DIRECTORY.resolve(characterId + ".json");
    }
}
