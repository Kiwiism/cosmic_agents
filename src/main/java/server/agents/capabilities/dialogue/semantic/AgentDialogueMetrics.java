package server.agents.capabilities.dialogue.semantic;

import java.util.concurrent.atomic.LongAdder;

/** Low-cost process counters for semantic dialogue and pair sessions. */
public final class AgentDialogueMetrics {
    private static final LongAdder semanticActs = new LongAdder();
    private static final LongAdder projectionRequests = new LongAdder();
    private static final LongAdder projected = new LongAdder();
    private static final LongAdder topicSuppressed = new LongAdder();
    private static final LongAdder noAudienceSuppressed = new LongAdder();
    private static final LongAdder cooldownSuppressed = new LongAdder();
    private static final LongAdder mapBudgetSuppressed = new LongAdder();
    private static final LongAdder sessionsStarted = new LongAdder();
    private static final LongAdder sessionsCompleted = new LongAdder();
    private static final LongAdder sessionsTimedOut = new LongAdder();
    private static final LongAdder coordinationPublished = new LongAdder();
    private static final LongAdder coordinationDelivered = new LongAdder();
    private static final LongAdder failures = new LongAdder();

    private AgentDialogueMetrics() {
    }

    public static void recordSemanticAct() { semanticActs.increment(); }
    public static void recordProjectionRequest() { projectionRequests.increment(); }
    public static void recordProjected() { projected.increment(); }
    public static void recordTopicSuppressed() { topicSuppressed.increment(); }
    public static void recordNoAudienceSuppressed() { noAudienceSuppressed.increment(); }
    public static void recordCooldownSuppressed() { cooldownSuppressed.increment(); }
    public static void recordMapBudgetSuppressed() { mapBudgetSuppressed.increment(); }
    public static void recordSessionStarted() { sessionsStarted.increment(); }
    public static void recordSessionCompleted() { sessionsCompleted.increment(); }
    public static void recordSessionTimedOut() { sessionsTimedOut.increment(); }
    public static void recordCoordinationPublished() { coordinationPublished.increment(); }
    public static void recordCoordinationDelivered() { coordinationDelivered.increment(); }
    public static void recordFailure() { failures.increment(); }

    public static AgentDialogueRuntimeSnapshot snapshot(int activeSessions) {
        return new AgentDialogueRuntimeSnapshot(
                semanticActs.sum(), projectionRequests.sum(), projected.sum(), topicSuppressed.sum(),
                noAudienceSuppressed.sum(), cooldownSuppressed.sum(), mapBudgetSuppressed.sum(),
                sessionsStarted.sum(), sessionsCompleted.sum(), sessionsTimedOut.sum(),
                coordinationPublished.sum(), coordinationDelivered.sum(), failures.sum(), activeSessions);
    }

    static void resetForTests() {
        semanticActs.reset();
        projectionRequests.reset();
        projected.reset();
        topicSuppressed.reset();
        noAudienceSuppressed.reset();
        cooldownSuppressed.reset();
        mapBudgetSuppressed.reset();
        sessionsStarted.reset();
        sessionsCompleted.reset();
        sessionsTimedOut.reset();
        coordinationPublished.reset();
        coordinationDelivered.reset();
        failures.reset();
    }
}
