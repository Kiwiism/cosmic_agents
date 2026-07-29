package server.agents.objectives;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Best-effort persistence adapter; a disk failure must not corrupt the live objective state. */
public final class AgentObjectiveCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentObjectiveCheckpointRuntime.class);
    private static final AgentObjectiveCheckpointStore STORE = FileAgentObjectiveCheckpointStore.runtimeDefault();
    private static final AtomicLong NEXT_WRITE_REVISION = new AtomicLong();
    private static final Map<Integer, Long> LATEST_WRITE_REVISIONS = new ConcurrentHashMap<>();

    private AgentObjectiveCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0) {
            return false;
        }
        try {
            AgentObjectiveCheckpoint checkpoint = STORE.load(agent.getId()).orElse(null);
            if (checkpoint == null) {
                return false;
            }
            entry.capabilityStates().require(AgentObjectiveState.STATE_KEY).restore(checkpoint);
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore objective checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    public static void persist(AgentRuntimeEntry entry, long nowMs) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0
                || !AgentRuntimeRegistry.hasActiveAgentCharacterId(agent.getId())) {
            return;
        }
        try {
            AgentObjectiveState state = entry.capabilityStates().require(AgentObjectiveState.STATE_KEY);
            AgentObjectiveCheckpoint checkpoint = state.checkpoint(agent.getId(), nowMs);
            long writeRevision = NEXT_WRITE_REVISION.incrementAndGet();
            LATEST_WRITE_REVISIONS.put(agent.getId(), writeRevision);
            persistAsync(entry, checkpoint, writeRevision, 1);
        } catch (RuntimeException failure) {
            log.warn("Could not persist objective checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    public static void delete(int characterId) throws IOException {
        LATEST_WRITE_REVISIONS.remove(characterId);
        STORE.delete(characterId);
    }

    private static void persistAsync(AgentRuntimeEntry entry,
                                     AgentObjectiveCheckpoint checkpoint,
                                     long writeRevision,
                                     int attempt) {
        AgentAsyncTaskGateway.Submission submission = AgentAsyncTaskGateway.runtime().submit(
                entry,
                AgentAsyncWorkKind.PERSISTENCE,
                "objective-checkpoint",
                () -> saveLatest(checkpoint, writeRevision),
                (currentEntry, completion) -> {
                    if (completion.succeeded()) {
                        LATEST_WRITE_REVISIONS.remove(checkpoint.characterId(), writeRevision);
                        return;
                    }
                    Throwable failure = unwrap(completion.failure());
                    int maxAttempts = config.AgentTuning.intValue(
                            "server.agents.objectives.FileAgentObjectiveCheckpointStore.REPLACE_ATTEMPTS");
                    if (failure instanceof AccessDeniedException && attempt < maxAttempts) {
                        long retryDelayMs = config.AgentTuning.longValue(
                                "server.agents.objectives.FileAgentObjectiveCheckpointStore.REPLACE_RETRY_DELAY_MS");
                        AgentSchedulerRuntime.afterDelay(
                                currentEntry,
                                retryDelayMs * attempt,
                                () -> persistAsync(
                                        currentEntry, checkpoint, writeRevision, attempt + 1));
                        return;
                    }
                    log.warn("Could not persist objective checkpoint for {} ({})",
                            currentEntry.bot().getName(), checkpoint.characterId(), failure);
                });
        if (!submission.accepted()) {
            LATEST_WRITE_REVISIONS.remove(checkpoint.characterId(), writeRevision);
            log.warn("Could not queue objective checkpoint persistence for {} ({})",
                    entry.bot().getName(), checkpoint.characterId());
        }
    }

    private static AgentObjectiveCheckpoint saveLatest(AgentObjectiveCheckpoint checkpoint,
                                                       long writeRevision) {
        if (LATEST_WRITE_REVISIONS.getOrDefault(checkpoint.characterId(), -1L) != writeRevision) {
            return checkpoint;
        }
        try {
            STORE.save(checkpoint);
            return checkpoint;
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        if (failure instanceof UncheckedIOException unchecked && unchecked.getCause() != null) {
            return unchecked.getCause();
        }
        return failure;
    }
}
