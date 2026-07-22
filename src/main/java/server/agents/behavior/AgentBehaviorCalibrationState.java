package server.agents.behavior;

import server.agents.runtime.state.AgentCapabilityStateKey;

/** Per-session projection of stable, durable-seed behavior calibration. */
public final class AgentBehaviorCalibrationState {
    public static final AgentCapabilityStateKey<AgentBehaviorCalibrationState> STATE_KEY =
            new AgentCapabilityStateKey<>("behavior.calibration", AgentBehaviorCalibrationState.class,
                    AgentBehaviorCalibrationState::new);

    private AgentBehaviorPolicyProfile policy;
    private long seed;
    private int responseBaselineMs;
    private boolean enabled;
    private long decisionSequence;

    public synchronized void configure(AgentBehaviorPolicyProfile policy, long seed, boolean enabled) {
        this.policy = policy;
        this.seed = seed;
        this.enabled = enabled;
        this.decisionSequence = 0L;
        int width = policy.response().maxMs() - policy.response().minMs() + 1;
        this.responseBaselineMs = policy.response().minMs()
                + Math.floorMod(mix(seed), width);
    }

    public synchronized AgentBehaviorPolicyProfile policy() { return policy; }
    public synchronized long seed() { return seed; }
    public synchronized int responseBaselineMs() { return responseBaselineMs; }
    public synchronized boolean enabled() { return enabled && policy != null; }

    public synchronized int nextPercent(String channel) {
        long salt = channel == null ? 0L : channel.hashCode();
        return Math.floorMod(mix(seed ^ salt ^ ++decisionSequence), 100);
    }

    public synchronized int stablePercent(String channel, long stimulus) {
        long salt = channel == null ? 0L : channel.hashCode();
        return Math.floorMod(mix(seed ^ salt ^ stimulus), 100);
    }

    private static int mix(long value) {
        long mixed = value ^ (value >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) mixed;
    }
}
