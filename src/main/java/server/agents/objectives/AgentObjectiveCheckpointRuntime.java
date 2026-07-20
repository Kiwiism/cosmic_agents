package server.agents.objectives;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.io.IOException;

/** Best-effort persistence adapter; a disk failure must not corrupt the live objective state. */
public final class AgentObjectiveCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentObjectiveCheckpointRuntime.class);
    private static final AgentObjectiveCheckpointStore STORE = FileAgentObjectiveCheckpointStore.runtimeDefault();

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
            STORE.save(state.checkpoint(agent.getId(), nowMs));
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist objective checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    public static void delete(int characterId) throws IOException {
        STORE.delete(characterId);
    }
}
