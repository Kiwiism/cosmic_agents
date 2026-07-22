package server.agents.capabilities.behavior;

import java.util.concurrent.atomic.LongAdder;

/** Fixed-cardinality rollout counters; never labels by Agent, map, or target. */
public final class AgentBehaviorTelemetry {
    private static final LongAdder responseDeferred = new LongAdder();
    private static final LongAdder claimAlternatives = new LongAdder();
    private static final LongAdder crowdRestStarted = new LongAdder();
    private static final LongAdder crowdRestResumed = new LongAdder();
    private static final LongAdder idlePresentation = new LongAdder();
    private static final LongAdder expressionsShown = new LongAdder();
    private static final LongAdder expressionsSuppressed = new LongAdder();

    private AgentBehaviorTelemetry() {
    }

    public static void responseDeferred() { responseDeferred.increment(); }
    public static void claimAlternative() { claimAlternatives.increment(); }
    public static void crowdRestStarted() { crowdRestStarted.increment(); }
    public static void crowdRestResumed() { crowdRestResumed.increment(); }
    public static void idlePresentation() { idlePresentation.increment(); }
    public static void expressionShown() { expressionsShown.increment(); }
    public static void expressionSuppressed() { expressionsSuppressed.increment(); }

    public static Snapshot snapshot() {
        return new Snapshot(responseDeferred.sum(), claimAlternatives.sum(), crowdRestStarted.sum(),
                crowdRestResumed.sum(), idlePresentation.sum(), expressionsShown.sum(), expressionsSuppressed.sum());
    }

    public record Snapshot(long responseDeferred, long claimAlternatives, long crowdRestStarted,
                           long crowdRestResumed, long idlePresentation, long expressionsShown,
                           long expressionsSuppressed) { }
}
