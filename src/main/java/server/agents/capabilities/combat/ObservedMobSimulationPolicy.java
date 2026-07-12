package server.agents.capabilities.combat;

public final class ObservedMobSimulationPolicy {
    private ObservedMobSimulationPolicy() {
    }

    public static boolean shouldSimulate(boolean enabled, boolean hasRealObserver) {
        return enabled && hasRealObserver;
    }
}
