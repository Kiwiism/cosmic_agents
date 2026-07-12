package server.agents.capabilities.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class AgentCapabilityRuntimeState {
    static final int MAX_JOURNAL_EVENTS = 256;

    final Deque<AgentCapabilityFrame> frames = new ArrayDeque<>();
    final Deque<AgentCapabilityJournalEvent> journal = new ArrayDeque<>();
    boolean cancellationRequested;
    AgentCapabilityResult lastResult;

    public synchronized boolean hasActiveCapability() {
        return !frames.isEmpty();
    }

    public synchronized int frameCount() {
        return frames.size();
    }

    public synchronized AgentCapabilityResult lastResult() {
        return lastResult;
    }

    public synchronized String activeCapabilityId() {
        AgentCapabilityFrame frame = frames.peek();
        return frame == null ? null : frame.invocation.capabilityId();
    }

    public synchronized List<AgentCapabilityJournalEvent> journalSnapshot() {
        return List.copyOf(new ArrayList<>(journal));
    }
}
