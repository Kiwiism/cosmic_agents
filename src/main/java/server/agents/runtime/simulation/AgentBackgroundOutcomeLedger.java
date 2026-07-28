package server.agents.runtime.simulation;

/**
 * Records an abstract execution window until the Agent returns to exact world simulation.
 *
 * TownLife is initially mutation-free while abstract, so reconciliation only has to prove
 * that no unsupported mutable outcome was recorded. Any future abstract capability must
 * add a typed outcome applier before it can be allowlisted.
 */
public final class AgentBackgroundOutcomeLedger {
    private boolean active;
    private AgentAbstractExecutionScope scope = AgentAbstractExecutionScope.NONE;
    private long startedAtMs;
    private long lastHeartbeatAtMs;
    private long heartbeatCount;
    private String unsupportedOutcome;
    private long reconciliationCount;

    public synchronized void begin(AgentAbstractExecutionScope nextScope, long nowMs) {
        if (nextScope == null || nextScope == AgentAbstractExecutionScope.NONE) {
            throw new IllegalArgumentException("An abstract execution scope is required");
        }
        if (active && scope == nextScope) {
            heartbeat(nowMs);
            return;
        }
        active = true;
        scope = nextScope;
        startedAtMs = Math.max(0L, nowMs);
        lastHeartbeatAtMs = startedAtMs;
        heartbeatCount = 0L;
        unsupportedOutcome = null;
    }

    public synchronized void heartbeat(long nowMs) {
        if (!active) {
            return;
        }
        lastHeartbeatAtMs = Math.max(lastHeartbeatAtMs, Math.max(0L, nowMs));
        heartbeatCount++;
    }

    public synchronized void recordUnsupportedOutcome(String description) {
        if (!active) {
            return;
        }
        unsupportedOutcome = description == null || description.isBlank()
                ? "unspecified abstract mutation"
                : description.trim();
    }

    public synchronized boolean reconcile() {
        if (!active) {
            return true;
        }
        if (unsupportedOutcome != null) {
            return false;
        }
        active = false;
        scope = AgentAbstractExecutionScope.NONE;
        reconciliationCount++;
        return true;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                active,
                scope,
                startedAtMs,
                lastHeartbeatAtMs,
                heartbeatCount,
                unsupportedOutcome,
                reconciliationCount);
    }

    public record Snapshot(
            boolean active,
            AgentAbstractExecutionScope scope,
            long startedAtMs,
            long lastHeartbeatAtMs,
            long heartbeatCount,
            String unsupportedOutcome,
            long reconciliationCount) {
    }
}
