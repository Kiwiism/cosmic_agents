package server.agents.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;
import server.agents.runtime.activity.session.AgentActivityKind;

/** Bounded live modifiers; never replaces the durable personality profile. */
public final class AgentBehaviorAdaptationState {
    private static final String TUNING_PREFIX =
            "server.agents.behavior.AgentBehaviorAdaptationState.";
    private static final int INITIAL_ENERGY = tuningInt("INITIAL_ENERGY");
    private static final int INITIAL_CONFIDENCE = tuningInt("INITIAL_CONFIDENCE");
    private static final int TARGET_LOST_FRUSTRATION_DELTA =
            tuningInt("TARGET_LOST_FRUSTRATION_DELTA");
    private static final int TARGET_LOST_CONFIDENCE_DELTA =
            tuningInt("TARGET_LOST_CONFIDENCE_DELTA");
    private static final int HIT_CONFIDENCE_DELTA = tuningInt("HIT_CONFIDENCE_DELTA");
    private static final int HIT_FRUSTRATION_DELTA = tuningInt("HIT_FRUSTRATION_DELTA");
    private static final int MISS_CONFIDENCE_DELTA = tuningInt("MISS_CONFIDENCE_DELTA");
    private static final int MISS_FRUSTRATION_DELTA = tuningInt("MISS_FRUSTRATION_DELTA");
    private static final int ATTACK_ENERGY_DELTA = tuningInt("ATTACK_ENERGY_DELTA");
    private static final int ATTACK_REST_DEBT_DELTA = tuningInt("ATTACK_REST_DEBT_DELTA");
    private static final int KILL_CONFIDENCE_DELTA = tuningInt("KILL_CONFIDENCE_DELTA");
    private static final int KILL_FRUSTRATION_DELTA = tuningInt("KILL_FRUSTRATION_DELTA");
    private static final int KILL_REST_DEBT_DELTA = tuningInt("KILL_REST_DEBT_DELTA");
    private static final int REST_ENERGY_DELTA = tuningInt("REST_ENERGY_DELTA");
    private static final int REST_DEBT_DELTA = tuningInt("REST_DEBT_DELTA");
    private static final int REST_FRUSTRATION_DELTA = tuningInt("REST_FRUSTRATION_DELTA");
    private static final int MIN_STATE_VALUE = tuningInt("MIN_STATE_VALUE");
    private static final int MAX_STATE_VALUE = tuningInt("MAX_STATE_VALUE");
    private static final int COMBAT_DRIVE_COMPONENTS = tuningInt("COMBAT_DRIVE_COMPONENTS");

    public static final AgentCapabilityStateKey<AgentBehaviorAdaptationState> STATE_KEY =
            new AgentCapabilityStateKey<>("behavior.adaptation", AgentBehaviorAdaptationState.class,
                    AgentBehaviorAdaptationState::new);

    private int energy = INITIAL_ENERGY;
    private int confidence = INITIAL_CONFIDENCE;
    private int frustration;
    private int restDebt;
    private int consecutiveMisses;
    private long lastEnergyObservationAtMs;
    private AgentActivityKind lastObservedActivity;

    public synchronized void targetLost() {
        frustration = clamp(frustration + TARGET_LOST_FRUSTRATION_DELTA);
        confidence = clamp(confidence - TARGET_LOST_CONFIDENCE_DELTA);
    }

    public synchronized void attackResolved(int hits, int misses) {
        if (hits > 0) {
            consecutiveMisses = 0;
            confidence = clamp(confidence + HIT_CONFIDENCE_DELTA);
            frustration = clamp(frustration - HIT_FRUSTRATION_DELTA);
        } else if (misses > 0) {
            consecutiveMisses += misses;
            confidence = clamp(confidence - MISS_CONFIDENCE_DELTA);
            frustration = clamp(frustration + MISS_FRUSTRATION_DELTA);
        }
        energy = clamp(energy - ATTACK_ENERGY_DELTA);
        restDebt = clamp(restDebt + ATTACK_REST_DEBT_DELTA);
    }

    public synchronized void mobKilled() {
        confidence = clamp(confidence + KILL_CONFIDENCE_DELTA);
        frustration = clamp(frustration - KILL_FRUSTRATION_DELTA);
        restDebt = clamp(restDebt + KILL_REST_DEBT_DELTA);
    }

    public synchronized void rested() {
        energy = clamp(energy + REST_ENERGY_DELTA);
        restDebt = clamp(restDebt - REST_DEBT_DELTA);
        frustration = clamp(frustration - REST_FRUSTRATION_DELTA);
    }

    public synchronized int combatDrive() {
        return clamp((energy + confidence
                + (MAX_STATE_VALUE - frustration)
                + (MAX_STATE_VALUE - restDebt)) / COMBAT_DRIVE_COMPONENTS);
    }

    public synchronized int consecutiveMisses() { return consecutiveMisses; }
    public synchronized int frustration() { return frustration; }
    public synchronized int restDebt() { return restDebt; }

    /**
     * Applies coarse human-like activity recovery/drain without putting a timer on the
     * Agent thread. The next observation catches up the previous known activity.
     */
    public synchronized AgentBehaviorAdaptationSnapshot observe(
            AgentActivityKind currentActivity, long nowMs) {
        if (nowMs < 0L) throw new IllegalArgumentException("valid observation time is required");
        if (lastEnergyObservationAtMs > 0L && nowMs > lastEnergyObservationAtMs) {
            long elapsedMinutes = (nowMs - lastEnergyObservationAtMs) / 60_000L;
            if (elapsedMinutes > 0L) {
                long rawDelta = elapsedMinutes * energyRatePerMinute(lastObservedActivity);
                int delta = (int) Math.max(-100L, Math.min(100L, rawDelta));
                energy = clamp(energy + delta);
                restDebt = clamp(restDebt - Math.max(0, delta));
                lastEnergyObservationAtMs += elapsedMinutes * 60_000L;
            }
        } else if (lastEnergyObservationAtMs == 0L) {
            lastEnergyObservationAtMs = nowMs;
        }
        lastObservedActivity = currentActivity;
        return new AgentBehaviorAdaptationSnapshot(
                energy, confidence, frustration, restDebt, consecutiveMisses, nowMs);
    }

    public synchronized AgentBehaviorAdaptationSnapshot snapshot(long nowMs) {
        return new AgentBehaviorAdaptationSnapshot(
                energy, confidence, frustration, restDebt, consecutiveMisses, nowMs);
    }

    /** Restores the last durable checkpoint and applies fastest-rate offline recovery. */
    public synchronized void restoreOffline(
            AgentBehaviorAdaptationSnapshot checkpoint, long nowMs) {
        if (checkpoint == null || nowMs < checkpoint.observedAtMs()) {
            throw new IllegalArgumentException("valid behavior checkpoint is required");
        }
        long offlineMinutes = (nowMs - checkpoint.observedAtMs()) / 60_000L;
        int recovery = (int) Math.min(100L, offlineMinutes * 3L);
        energy = clamp(checkpoint.energyPercent() + recovery);
        confidence = checkpoint.confidencePercent();
        frustration = clamp(checkpoint.frustrationPercent() - recovery);
        restDebt = clamp(checkpoint.restDebtPercent() - recovery);
        consecutiveMisses = checkpoint.consecutiveMisses();
        lastEnergyObservationAtMs = nowMs;
        lastObservedActivity = null;
    }

    private static int energyRatePerMinute(AgentActivityKind activity) {
        if (activity == null) return 2;
        return switch (activity) {
            case HUNTING, QUESTING, PARTY_QUEST -> -1;
            case TOWN_LIFE, COMMERCE -> 1;
        };
    }

    private static int clamp(int value) {
        return Math.max(MIN_STATE_VALUE, Math.min(MAX_STATE_VALUE, value));
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
