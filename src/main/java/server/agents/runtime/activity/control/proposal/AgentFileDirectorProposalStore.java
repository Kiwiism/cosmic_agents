package server.agents.runtime.activity.control.proposal;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** Atomic local persistence, separate from Cosmic's database. */
public final class AgentFileDirectorProposalStore implements AgentDirectorProposalStore {
    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    public AgentFileDirectorProposalStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("proposal directory is required");
        this.directory = directory.toAbsolutePath().normalize();
    }

    public static AgentFileDirectorProposalStore runtimeDefault() {
        return new AgentFileDirectorProposalStore(
                Path.of(".runtime", "agents", "world-director", "proposals"));
    }

    @Override
    public synchronized AgentDirectorProposal save(AgentDirectorProposal proposal) {
        if (proposal == null) throw new IllegalArgumentException("proposal is required");
        Path target = path(proposal.agentId(), proposal.proposalId());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            mapper.writeValue(temporary.toFile(), proposal);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return proposal;
        } catch (IOException failure) {
            throw new IllegalStateException("could not persist Director proposal", failure);
        } finally {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }

    @Override
    public synchronized Optional<AgentDirectorProposal> load(int agentId, String proposalId) {
        Path source = path(agentId, proposalId);
        if (!Files.isRegularFile(source)) return Optional.empty();
        AgentDirectorProposal proposal = read(source);
        if (proposal.agentId() != agentId || !proposal.proposalId().equals(proposalId)) {
            throw new IllegalStateException("proposal identity does not match its file");
        }
        return Optional.of(proposal);
    }

    @Override
    public synchronized List<AgentDirectorProposal> list(int agentId) {
        Path agentDirectory = agentDirectory(agentId);
        if (!Files.isDirectory(agentDirectory)) return List.of();
        try (var files = Files.list(agentDirectory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::read)
                    .sorted(Comparator.comparingLong(AgentDirectorProposal::createdAtMs).reversed())
                    .toList();
        } catch (IOException failure) {
            throw new IllegalStateException("could not list Director proposals", failure);
        }
    }

    private AgentDirectorProposal read(Path source) {
        try {
            return mapper.readValue(source.toFile(), AgentDirectorProposal.class);
        } catch (IOException failure) {
            throw new IllegalStateException("could not restore Director proposal", failure);
        }
    }

    private Path path(int agentId, String proposalId) {
        if (proposalId == null || proposalId.isBlank()) {
            throw new IllegalArgumentException("proposal id is required");
        }
        return agentDirectory(agentId).resolve(digest(proposalId) + ".json");
    }

    private Path agentDirectory(int agentId) {
        if (agentId <= 0) throw new IllegalArgumentException("positive Agent id is required");
        return directory.resolve(Integer.toString(agentId));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
