package server.agents.behavior;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Session hydration and throttled local persistence for behavior energy. */
public final class AgentBehaviorAdaptationPersistenceRuntime {
    private static final Logger log = LoggerFactory.getLogger(
            AgentBehaviorAdaptationPersistenceRuntime.class);
    private static final long CHECKPOINT_INTERVAL_MS = config.AgentTuning.longValue(
            "server.agents.behavior.AgentBehaviorAdaptationPersistenceRuntime.CHECKPOINT_INTERVAL_MS");
    private static final AgentBehaviorAdaptationFileStore STORE =
            AgentBehaviorAdaptationFileStore.runtimeDefault();
    private static final Set<Integer> HYDRATED = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Long> LAST_SAVED_AT = new ConcurrentHashMap<>();

    private AgentBehaviorAdaptationPersistenceRuntime() { }

    public static void hydrateSession(AgentRuntimeEntry entry, int agentId, long nowMs) {
        if (entry == null || agentId <= 0) return;
        hydrate(entry.capabilityStates().require(
                AgentBehaviorAdaptationState.STATE_KEY), agentId, nowMs);
    }

    public static AgentBehaviorAdaptationSnapshot observe(
            AgentRuntimeEntry entry, int agentId, AgentActivityKind activity, long nowMs) {
        hydrateSession(entry, agentId, nowMs);
        AgentBehaviorAdaptationState state = entry.capabilityStates()
                .require(AgentBehaviorAdaptationState.STATE_KEY);
        AgentBehaviorAdaptationSnapshot snapshot = state.observe(activity, nowMs);
        long lastSaved = LAST_SAVED_AT.getOrDefault(agentId, 0L);
        if (nowMs - lastSaved >= CHECKPOINT_INTERVAL_MS) {
            save(agentId, snapshot);
            LAST_SAVED_AT.put(agentId, nowMs);
        }
        return snapshot;
    }

    public static void checkpointAndClear(
            AgentRuntimeEntry entry, int agentId, long nowMs) {
        if (entry != null && agentId > 0) {
            try {
                AgentBehaviorAdaptationState state = entry.capabilityStates()
                        .require(AgentBehaviorAdaptationState.STATE_KEY);
                save(agentId, state.snapshot(nowMs));
            } catch (RuntimeException failure) {
                log.warn("Could not checkpoint Agent {} energy: {}", agentId, failure.toString());
            }
        }
        HYDRATED.remove(agentId);
        LAST_SAVED_AT.remove(agentId);
    }

    private static void hydrate(
            AgentBehaviorAdaptationState state, int agentId, long nowMs) {
        if (!HYDRATED.add(agentId)) return;
        try {
            STORE.load(agentId).ifPresent(snapshot -> state.restoreOffline(snapshot, nowMs));
        } catch (RuntimeException failure) {
            log.warn("Could not hydrate Agent {} energy; using bounded defaults: {}",
                    agentId, failure.toString());
        }
    }

    private static void save(int agentId, AgentBehaviorAdaptationSnapshot snapshot) {
        try {
            STORE.save(agentId, snapshot);
        } catch (RuntimeException failure) {
            log.warn("Could not persist Agent {} energy: {}", agentId, failure.toString());
        }
    }
}
