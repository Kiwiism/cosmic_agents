package server.agents.monitoring;

import server.agents.runtime.mailbox.AgentMailboxSubmission;
import server.agents.runtime.mailbox.AgentMailboxSubmissionStatus;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Named acceptance counters for event listeners that defer mutations through Agent mailboxes. */
public final class AgentEventReactionMetrics {
    public record Snapshot(long accepted, long coalesced, long rejected) {
    }

    private static final class MutableMetrics {
        private final LongAdder accepted = new LongAdder();
        private final LongAdder coalesced = new LongAdder();
        private final LongAdder rejected = new LongAdder();
    }

    private static final Map<String, MutableMetrics> METRICS = new ConcurrentHashMap<>();

    private AgentEventReactionMetrics() {
    }

    public static void record(String reaction, AgentMailboxSubmission<?> submission) {
        if (reaction == null || reaction.isBlank() || submission == null) {
            return;
        }
        MutableMetrics metrics = METRICS.computeIfAbsent(reaction, ignored -> new MutableMetrics());
        AgentMailboxSubmissionStatus status = submission.status();
        if (status == AgentMailboxSubmissionStatus.COALESCED) {
            metrics.coalesced.increment();
        } else if (status.accepted()) {
            metrics.accepted.increment();
        } else {
            metrics.rejected.increment();
        }
    }

    public static Map<String, Snapshot> snapshots() {
        Map<String, Snapshot> snapshots = new TreeMap<>();
        METRICS.forEach((name, metrics) -> snapshots.put(name,
                new Snapshot(metrics.accepted.sum(), metrics.coalesced.sum(), metrics.rejected.sum())));
        return Collections.unmodifiableMap(snapshots);
    }

    static void reset() {
        METRICS.clear();
    }
}
