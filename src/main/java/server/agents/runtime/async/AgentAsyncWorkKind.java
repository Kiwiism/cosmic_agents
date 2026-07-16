package server.agents.runtime.async;

/** Workload lanes kept separate so one slow external system cannot starve another. */
public enum AgentAsyncWorkKind {
    NAVIGATION_GRAPH("navigation", "bot-nav-graph-warmup", 1, 64, Thread.MIN_PRIORITY,
            "agents.async.navigation.threads", "agents.async.navigation.queueCapacity"),
    NAVIGATION_GRAPH_FAST("navigation-fast", "bot-nav-graph-warmup-fast", 1, 64, Thread.MIN_PRIORITY,
            "agents.async.navigation.fastThreads", "agents.async.navigation.fastQueueCapacity"),
    PERSISTENCE("persistence", "agent-persistence", 1, 256, Thread.NORM_PRIORITY,
            "agents.async.persistence.threads", "agents.async.persistence.queueCapacity"),
    POPULATION_LIFECYCLE("population", "agent-population-lifecycle", 1, 1, Thread.NORM_PRIORITY,
            "agents.async.population.threads", "agents.async.population.queueCapacity"),
    MAPLE_ISLAND_COHORT("maple-island-cohort", "maple-island-cohort", 1, 64, Thread.NORM_PRIORITY,
            "agents.async.mapleIslandCohort.threads", "agents.async.mapleIslandCohort.queueCapacity"),
    LLM_NETWORK("llm", "bot-llm", 2, 64, Thread.NORM_PRIORITY,
            "agents.async.llm.threads", "agents.async.llm.queueCapacity"),
    CATALOG_REBUILD("catalog", "agent-catalog", 1, 32, Thread.MIN_PRIORITY,
            "agents.async.catalog.threads", "agents.async.catalog.queueCapacity"),
    ECONOMY_ANALYSIS("trade", "bot-trade-command", 2, 128, Thread.NORM_PRIORITY,
            "agents.async.trade.threads", "agents.async.trade.queueCapacity");

    private final String metricName;
    private final String threadName;
    private final int defaultThreads;
    private final int defaultQueueCapacity;
    private final int threadPriority;
    private final String threadsProperty;
    private final String queueCapacityProperty;

    AgentAsyncWorkKind(String metricName,
                       String threadName,
                       int defaultThreads,
                       int defaultQueueCapacity,
                       int threadPriority,
                       String threadsProperty,
                       String queueCapacityProperty) {
        this.metricName = metricName;
        this.threadName = threadName;
        this.defaultThreads = defaultThreads;
        this.defaultQueueCapacity = defaultQueueCapacity;
        this.threadPriority = threadPriority;
        this.threadsProperty = threadsProperty;
        this.queueCapacityProperty = queueCapacityProperty;
    }

    public String metricName() {
        return metricName;
    }

    String threadName() {
        return threadName;
    }

    int configuredThreads() {
        return positiveProperty(threadsProperty, defaultThreads);
    }

    int configuredQueueCapacity() {
        return positiveProperty(queueCapacityProperty, defaultQueueCapacity);
    }

    int threadPriority() {
        return threadPriority;
    }

    private static int positiveProperty(String property, int fallback) {
        return Math.max(1, Integer.getInteger(property, fallback));
    }
}
