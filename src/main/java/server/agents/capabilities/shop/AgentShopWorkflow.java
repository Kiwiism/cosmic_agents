package server.agents.capabilities.shop;

import java.util.EnumSet;
import java.util.Map;

/** Explicit, session-inspectable state machine for one NPC shop transaction. */
public final class AgentShopWorkflow {
    private static final Map<AgentShopWorkflowPhase, EnumSet<AgentShopWorkflowPhase>> ALLOWED = Map.of(
            AgentShopWorkflowPhase.IDLE, EnumSet.of(AgentShopWorkflowPhase.PLANNED),
            AgentShopWorkflowPhase.PLANNED, EnumSet.of(AgentShopWorkflowPhase.NAVIGATING,
                    AgentShopWorkflowPhase.APPROACHING, AgentShopWorkflowPhase.CANCELLED,
                    AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.NAVIGATING, EnumSet.of(AgentShopWorkflowPhase.APPROACHING,
                    AgentShopWorkflowPhase.CANCELLED, AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.APPROACHING, EnumSet.of(AgentShopWorkflowPhase.OPENING,
                    AgentShopWorkflowPhase.TRANSACTING, AgentShopWorkflowPhase.CANCELLED,
                    AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.OPENING, EnumSet.of(AgentShopWorkflowPhase.TRANSACTING,
                    AgentShopWorkflowPhase.CANCELLED, AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.TRANSACTING, EnumSet.of(AgentShopWorkflowPhase.VERIFYING,
                    AgentShopWorkflowPhase.COMPLETED, AgentShopWorkflowPhase.CANCELLED,
                    AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.VERIFYING, EnumSet.of(AgentShopWorkflowPhase.COMPLETED,
                    AgentShopWorkflowPhase.CANCELLED, AgentShopWorkflowPhase.BLOCKED),
            AgentShopWorkflowPhase.COMPLETED, EnumSet.of(AgentShopWorkflowPhase.PLANNED),
            AgentShopWorkflowPhase.BLOCKED, EnumSet.of(AgentShopWorkflowPhase.PLANNED),
            AgentShopWorkflowPhase.CANCELLED, EnumSet.of(AgentShopWorkflowPhase.PLANNED));

    private String workflowId = "";
    private AgentShopWorkflowPhase phase = AgentShopWorkflowPhase.IDLE;
    private int npcId;
    private long startedAtMs;
    private long updatedAtMs;
    private String reason = "";

    public synchronized void start(String workflowId, int npcId, long nowMs) {
        if (workflowId == null || workflowId.isBlank() || nowMs < 0) {
            throw new IllegalArgumentException("Shop workflow identity and timestamp are required");
        }
        if (phase != AgentShopWorkflowPhase.IDLE && !phase.terminal()) {
            throw new IllegalStateException("Cannot replace active shop workflow " + this.workflowId);
        }
        this.workflowId = workflowId;
        this.npcId = npcId;
        this.startedAtMs = nowMs;
        this.updatedAtMs = nowMs;
        this.reason = "";
        this.phase = AgentShopWorkflowPhase.PLANNED;
    }

    public synchronized void transition(AgentShopWorkflowPhase next, String reason, long nowMs) {
        if (next == null || nowMs < updatedAtMs) {
            throw new IllegalArgumentException("Valid monotonic shop transition is required");
        }
        if (next == phase) {
            return;
        }
        EnumSet<AgentShopWorkflowPhase> allowed = ALLOWED.getOrDefault(phase,
                EnumSet.noneOf(AgentShopWorkflowPhase.class));
        if (!allowed.contains(next)) {
            throw new IllegalStateException("Invalid shop transition " + phase + " -> " + next);
        }
        phase = next;
        this.reason = reason == null ? "" : reason;
        updatedAtMs = nowMs;
    }

    public synchronized String workflowId() { return workflowId; }
    public synchronized AgentShopWorkflowPhase phase() { return phase; }
    public synchronized int npcId() { return npcId; }
    public synchronized long startedAtMs() { return startedAtMs; }
    public synchronized long updatedAtMs() { return updatedAtMs; }
    public synchronized String reason() { return reason; }
}
