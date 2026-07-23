package server.agents.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

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

    private static int clamp(int value) {
        return Math.max(MIN_STATE_VALUE, Math.min(MAX_STATE_VALUE, value));
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
