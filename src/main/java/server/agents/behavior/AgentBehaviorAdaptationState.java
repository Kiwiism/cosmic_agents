package server.agents.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Bounded live modifiers; never replaces the durable personality profile. */
public final class AgentBehaviorAdaptationState {
    public static final AgentCapabilityStateKey<AgentBehaviorAdaptationState> STATE_KEY =
            new AgentCapabilityStateKey<>("behavior.adaptation", AgentBehaviorAdaptationState.class,
                    AgentBehaviorAdaptationState::new);

    private int energy = 75;
    private int confidence = 50;
    private int frustration;
    private int restDebt;
    private int consecutiveMisses;

    public synchronized void targetLost() {
        frustration = clamp(frustration + 4);
        confidence = clamp(confidence - 2);
    }

    public synchronized void attackResolved(int hits, int misses) {
        if (hits > 0) {
            consecutiveMisses = 0;
            confidence = clamp(confidence + 2);
            frustration = clamp(frustration - 3);
        } else if (misses > 0) {
            consecutiveMisses += misses;
            confidence = clamp(confidence - 2);
            frustration = clamp(frustration + 4);
        }
        energy = clamp(energy - 1);
        restDebt = clamp(restDebt + 1);
    }

    public synchronized void mobKilled() {
        confidence = clamp(confidence + 5);
        frustration = clamp(frustration - 6);
        restDebt = clamp(restDebt + 2);
    }

    public synchronized void rested() {
        energy = clamp(energy + 20);
        restDebt = clamp(restDebt - 25);
        frustration = clamp(frustration - 8);
    }

    public synchronized int combatDrive() {
        return clamp((energy + confidence + (100 - frustration) + (100 - restDebt)) / 4);
    }

    public synchronized int consecutiveMisses() { return consecutiveMisses; }
    public synchronized int frustration() { return frustration; }
    public synchronized int restDebt() { return restDebt; }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }
}
