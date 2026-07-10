package server.agents.runtime;

public final class AgentSchedulerConfig {
    private AgentSchedulerConfig() {
    }

    public static boolean centralEnabled() {
        return Boolean.getBoolean("agents.scheduler.central.enabled");
    }

    public static long baseTickMs() {
        return Math.max(10L, Long.getLong("agents.scheduler.baseTickMs", 50L));
    }

    public static boolean logSlowTicks() {
        return Boolean.parseBoolean(System.getProperty("agents.scheduler.logSlowTicks", "true"));
    }

    public static long slowTickMs() {
        return Math.max(1L, Long.getLong("agents.scheduler.slowTickMs", 250L));
    }

    public static int maxAgentsPerTick() {
        return Math.max(0, Integer.getInteger("agents.scheduler.maxAgentsPerTick", 0));
    }

    public static AgentSchedulerMode mode() {
        return centralEnabled() ? AgentSchedulerMode.CENTRAL : AgentSchedulerMode.LEGACY_PER_AGENT;
    }
}
