package server.agents.progression;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.io.IOException;

public final class AgentCareerProgressionCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentCareerProgressionCheckpointRuntime.class);
    private static final AgentCareerProgressionCheckpointStore STORE =
            FileAgentCareerProgressionCheckpointStore.runtimeDefault();

    private AgentCareerProgressionCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry, AgentCareerBuildBundle bundle) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0 || bundle == null) {
            return false;
        }
        try {
            AgentCareerProgressionCheckpoint checkpoint = STORE.load(agent.getId()).orElse(null);
            if (checkpoint == null) {
                return false;
            }
            if (!bundle.bundleId().equals(checkpoint.bundleId())
                    || bundle.bundleVersion() != checkpoint.bundleVersion()) {
                log.warn("Ignoring incompatible career checkpoint for {} ({}): {} v{} != {} v{}",
                        agent.getName(), agent.getId(), checkpoint.bundleId(), checkpoint.bundleVersion(),
                        bundle.bundleId(), bundle.bundleVersion());
                return false;
            }
            entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY)
                    .restore(bundle, checkpoint);
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore career checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
            return false;
        }
    }

    public static void persistIfDirty(AgentRuntimeEntry entry, long nowMs) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0
                || !AgentRuntimeRegistry.hasActiveAgentCharacterId(agent.getId())) {
            return;
        }
        AgentCareerProgressionState state =
                entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY);
        AgentCareerProgressionCheckpoint checkpoint = state.pendingCheckpoint(agent.getId(), nowMs);
        if (checkpoint == null) {
            return;
        }
        try {
            STORE.save(checkpoint);
            state.markPersisted(checkpoint.stateRevision());
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist career checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    public static void delete(int characterId) throws IOException {
        STORE.delete(characterId);
    }
}
