package server.agents.plans;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.io.IOException;

/** Best-effort persistence for the universal plan cursor. */
public final class AgentPlanCheckpointRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentPlanCheckpointRuntime.class);
    private static final AgentPlanCheckpointStore STORE =
            FileAgentPlanCheckpointStore.runtimeDefault();

    private AgentPlanCheckpointRuntime() {
    }

    public static boolean restore(AgentRuntimeEntry entry) {
        Character agent = entry == null ? null : entry.bot();
        if (agent == null || agent.getId() <= 0) {
            return false;
        }
        try {
            AgentPlanCheckpoint checkpoint = STORE.load(agent.getId()).orElse(null);
            if (checkpoint == null) {
                return false;
            }
            AgentPlanDefinition definition =
                    AgentPlanRepository.defaultRepository().require(checkpoint.planId());
            if (!definition.planVersion().equals(checkpoint.planVersion())) {
                log.warn("Ignoring incompatible plan checkpoint for {} ({}): {} v{} != v{}",
                        agent.getName(), agent.getId(), checkpoint.planId(),
                        checkpoint.planVersion(), definition.planVersion());
                return false;
            }
            if (checkpoint.stepIndex() > definition.steps().size()) {
                log.warn("Ignoring invalid plan checkpoint cursor for {} ({}): {} step {}",
                        agent.getName(), agent.getId(), checkpoint.planId(), checkpoint.stepIndex());
                return false;
            }
            entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY).restore(checkpoint);
            entry.capabilityStates().remove(AgentPlanAttachmentState.STATE_KEY);
            return true;
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not restore universal plan checkpoint for {} ({})",
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
        AgentPlanSessionState state =
                entry.capabilityStates().require(AgentPlanSessionState.STATE_KEY);
        AgentPlanCheckpoint checkpoint = state.pendingCheckpoint(agent.getId(), nowMs);
        if (checkpoint == null) {
            return;
        }
        try {
            STORE.save(checkpoint);
            state.markPersisted(checkpoint.stateRevision());
        } catch (IOException | RuntimeException failure) {
            log.warn("Could not persist universal plan checkpoint for {} ({})",
                    agent.getName(), agent.getId(), failure);
        }
    }

    public static void delete(int characterId) throws IOException {
        STORE.delete(characterId);
    }
}
