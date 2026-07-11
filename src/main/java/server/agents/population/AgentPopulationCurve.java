package server.agents.population;

/** Computes a bounded target from the explicit managed roster. */
public final class AgentPopulationCurve {
    public int target(int managedCount, double multiplier) {
        if (managedCount < 0) {
            throw new IllegalArgumentException("managedCount must be nonnegative");
        }
        AgentPopulationPolicy.requireMultiplier(multiplier);
        return (int) Math.min(managedCount, Math.floor(managedCount * multiplier));
    }
}
