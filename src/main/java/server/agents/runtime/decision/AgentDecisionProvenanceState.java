package server.agents.runtime.decision;

import server.agents.runtime.state.AgentCapabilityStateKey;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Per-Agent bounded journal used by diagnostics, replay, and future LLM context. */
public final class AgentDecisionProvenanceState {
    public static final AgentCapabilityStateKey<AgentDecisionProvenanceState> STATE_KEY =
            new AgentCapabilityStateKey<>("runtime.decision-provenance",
                    AgentDecisionProvenanceState.class,
                    AgentDecisionProvenanceState::new);
    private static final int MAX_RECORDS = config.AgentTuning.intValue(
            "server.agents.runtime.decision.AgentDecisionProvenanceState.MAX_RECORDS");

    private final ArrayDeque<AgentDecisionRecord> records = new ArrayDeque<>();
    private long sequence;

    public synchronized AgentDecisionRecord record(
            long nowMs,
            String domain,
            String choice,
            String source,
            String behaviorVersion,
            String reason,
            String correlationId,
            List<String> candidates) {
        while (records.size() >= MAX_RECORDS) {
            records.removeFirst();
        }
        AgentDecisionRecord record = new AgentDecisionRecord(
                ++sequence, nowMs, domain, choice, source, behaviorVersion,
                reason, correlationId, candidates);
        records.addLast(record);
        return record;
    }

    public synchronized List<AgentDecisionRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records));
    }

    public synchronized AgentDecisionRecord latest() {
        return records.peekLast();
    }

    public synchronized long sequence() {
        return sequence;
    }
}
