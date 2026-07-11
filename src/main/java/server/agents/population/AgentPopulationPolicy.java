package server.agents.population;

/** Bounds administrative input and work performed by a single reconciliation. */
public final class AgentPopulationPolicy {
    public static final int DEFAULT_MAX_ACTIONS_PER_SWEEP = 20;
    public static final int MAX_LIST_LINES = 30;
    public static final double MAX_MULTIPLIER = 100.0;

    private final int maxActionsPerSweep;

    public AgentPopulationPolicy() {
        this(DEFAULT_MAX_ACTIONS_PER_SWEEP);
    }

    public AgentPopulationPolicy(int maxActionsPerSweep) {
        if (maxActionsPerSweep <= 0) {
            throw new IllegalArgumentException("maxActionsPerSweep must be positive");
        }
        this.maxActionsPerSweep = maxActionsPerSweep;
    }

    public int maxActionsPerSweep() {
        return maxActionsPerSweep;
    }

    public static double requireMultiplier(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0.0 || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException("multiplier must be finite and between 0 and " + MAX_MULTIPLIER);
        }
        return multiplier;
    }
}
