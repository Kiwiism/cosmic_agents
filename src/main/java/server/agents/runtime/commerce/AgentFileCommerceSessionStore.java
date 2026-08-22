package server.agents.runtime.commerce;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Atomic JSON persistence for per-Agent Commerce visits. */
public final class AgentFileCommerceSessionStore implements AgentCommerceSessionStore {
    private static final AgentFileCommerceSessionStore RUNTIME_DEFAULT =
            new AgentFileCommerceSessionStore(Path.of(".runtime", "agents", "commerce-visits"));
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileCommerceSessionStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("Commerce session directory is required");
        }
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileCommerceSessionStore runtimeDefault() {
        return RUNTIME_DEFAULT;
    }

    @Override
    public void save(AgentCommerceSessionCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("Commerce session checkpoint is required");
        }
        Path target = path(checkpoint.request().participant().agentId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            mapper.writeValue(temporary.toFile(), checkpoint);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist Commerce session", failure);
        }
    }

    @Override
    public Optional<AgentCommerceSessionCheckpoint> load(String agentId) {
        Path source = path(agentId);
        if (!Files.isRegularFile(source)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(
                    source.toFile(), AgentCommerceSessionCheckpoint.class));
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore Commerce session", failure);
        }
    }

    @Override
    public void delete(String agentId) {
        try {
            Files.deleteIfExists(path(agentId));
        } catch (IOException failure) {
            throw new IllegalStateException("could not delete Commerce session", failure);
        }
    }

    private Path path(String agentId) {
        String normalized = agentId == null ? "" : agentId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Commerce agent id is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return directory.resolve(HexFormat.of().formatHex(digest) + ".json");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
