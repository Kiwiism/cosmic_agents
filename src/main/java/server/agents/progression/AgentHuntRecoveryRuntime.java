package server.agents.progression;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.LinkedHashSet;
import java.util.Set;

final class AgentHuntRecoveryRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentHuntRecoveryRuntime.class);
    private static final long MAP_ARRIVAL_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.MAP_ARRIVAL_GRACE_MS");
    private static final long ZERO_TARGET_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.ZERO_TARGET_GRACE_MS");
    private static final long NO_PROGRESS_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.NO_PROGRESS_GRACE_MS");
    private static final long DAMAGE_HEARTBEAT_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.DAMAGE_HEARTBEAT_GRACE_MS");
    private static final long HARD_KILL_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.HARD_KILL_GRACE_MS");
    private static final long FAILED_MAP_COOLDOWN_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.FAILED_MAP_COOLDOWN_MS");
    private static final int MAX_INSTANCE_REENTRIES = config.AgentTuning.intValue(
            "server.agents.progression.AgentHuntRecoveryRuntime.MAX_INSTANCE_REENTRIES");

    private AgentHuntRecoveryRuntime() {
    }

    static Observation observe(
            AgentRuntimeEntry entry,
            String objectiveKey,
            int mapId,
            int progress,
            int liveTargets,
            boolean instanceMap,
            long nowMs) {
        AgentHuntRecoveryState.Frame frame = entry.capabilityStates()
                .require(AgentHuntRecoveryState.STATE_KEY).frame(objectiveKey, progress, nowMs);
        frame.enterMap(mapId, nowMs);
        boolean advanced = frame.observeProgress(progress, nowMs);
        frame.observeTargets(liveTargets, nowMs);
        if (advanced || !frame.arrivalGraceElapsed(nowMs, MAP_ARRIVAL_GRACE_MS)) {
            return Observation.STAY;
        }
        boolean exhausted = frame.zeroTargetGraceElapsed(nowMs, ZERO_TARGET_GRACE_MS);
        boolean damageHeartbeat = frame.recentRelevantDamage(nowMs, DAMAGE_HEARTBEAT_GRACE_MS);
        boolean hardKillStalled = frame.hardKillGraceElapsed(nowMs, HARD_KILL_GRACE_MS);
        boolean stalled = liveTargets > 0
                && frame.progressGraceElapsed(nowMs, NO_PROGRESS_GRACE_MS)
                && (!damageHeartbeat || hardKillStalled);
        if (!exhausted && !stalled) {
            return Observation.STAY;
        }
        if (instanceMap && frame.reentryAttempts() < MAX_INSTANCE_REENTRIES) {
            frame.recordReentry(nowMs);
            log.info("Agent hunt recovery objective={} map={} action=reenter reason={}",
                    objectiveKey, mapId, exhausted ? "zero-targets" : "no-progress");
            return Observation.REENTER_INSTANCE;
        }
        frame.activateFallback();
        frame.failMaps(Set.of(mapId), nowMs + FAILED_MAP_COOLDOWN_MS);
        log.warn("Agent hunt recovery objective={} map={} action=reselect reason={}",
                objectiveKey, mapId, exhausted ? "zero-targets" : "no-progress");
        return Observation.RESELECT;
    }

    static boolean fallbackActive(AgentRuntimeEntry entry, String objectiveKey, int progress,
                                  long nowMs) {
        return entry.capabilityStates().require(AgentHuntRecoveryState.STATE_KEY)
                .frame(objectiveKey, progress, nowMs).fallbackActive();
    }

    static Set<Integer> failedMaps(AgentRuntimeEntry entry, String objectiveKey, int progress,
                                   long nowMs) {
        return entry.capabilityStates().require(AgentHuntRecoveryState.STATE_KEY)
                .frame(objectiveKey, progress, nowMs).failedMaps(nowMs);
    }

    static void failMaps(AgentRuntimeEntry entry, String objectiveKey, int progress,
                         Set<Integer> mapIds, long nowMs) {
        Set<Integer> bounded = new LinkedHashSet<>(mapIds == null ? Set.of() : mapIds);
        if (bounded.isEmpty()) {
            return;
        }
        entry.capabilityStates().require(AgentHuntRecoveryState.STATE_KEY)
                .frame(objectiveKey, progress, nowMs)
                .failMaps(Set.copyOf(bounded), nowMs + FAILED_MAP_COOLDOWN_MS);
    }

    static void clear(AgentRuntimeEntry entry, String objectiveKey) {
        entry.capabilityStates().find(AgentHuntRecoveryState.STATE_KEY)
                .ifPresent(state -> state.clear(objectiveKey));
    }

    static void recordRelevantDamage(AgentRuntimeEntry entry, int mapId, long nowMs) {
        entry.capabilityStates().find(AgentHuntRecoveryState.STATE_KEY)
                .ifPresent(state -> state.observeRelevantDamage(mapId, nowMs));
    }

    enum Observation {
        STAY,
        REENTER_INSTANCE,
        RESELECT
    }
}
