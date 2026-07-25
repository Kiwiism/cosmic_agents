package server.agents.runtime.autonomy;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Per-Agent bounded journal of deterministic autonomy cycles. */
public final class AgentAutonomyCycleState {
    public static final AgentCapabilityStateKey<AgentAutonomyCycleState> STATE_KEY =
            new AgentCapabilityStateKey<>(
                    "runtime.autonomy-cycle",
                    AgentAutonomyCycleState.class,
                    AgentAutonomyCycleState::new);
    private static final int MAX_RECORDS = config.AgentTuning.intValue(
            "server.agents.runtime.autonomy.AgentAutonomyCycleState.MAX_RECORDS");

    private final ArrayDeque<AgentAutonomyCycleRecord> records = new ArrayDeque<>();
    private long snapshotSequence;
    private long cycleSequence;

    public synchronized long nextSnapshotSequence() {
        return ++snapshotSequence;
    }

    public synchronized AgentAutonomyCycleRecord begin(
            AgentAutonomySnapshot snapshot,
            String goalType,
            String planId,
            String planVersion,
            String stepId,
            String commandType,
            List<String> capabilityIds,
            String correlationId) {
        while (records.size() >= MAX_RECORDS) {
            records.removeFirst();
        }
        AgentAutonomyCycleRecord record = new AgentAutonomyCycleRecord(
                ++cycleSequence, snapshot, goalType, planId, planVersion,
                stepId, commandType, capabilityIds, correlationId,
                null, "", 0L);
        records.addLast(record);
        return record;
    }

    public synchronized AgentAutonomyCycleRecord complete(
            String correlationId,
            server.agents.plans.AgentPlanExecutionStatus status,
            String reason,
            long nowMs) {
        AgentAutonomyCycleRecord current = records.peekLast();
        if (current == null || current.complete()
                || !current.correlationId().equals(correlationId)) {
            return null;
        }
        records.removeLast();
        AgentAutonomyCycleRecord completed = current.complete(status, reason, nowMs);
        records.addLast(completed);
        return completed;
    }

    public synchronized AgentAutonomyCycleRecord latest() {
        return records.peekLast();
    }

    public synchronized List<AgentAutonomyCycleRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records));
    }
}
