package server.agents.capabilities.runtime;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.integration.cosmic.CosmicAgentCapabilityViewFactory;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicLong;

public final class AgentCapabilityRuntime {
    private static final Logger log = LoggerFactory.getLogger(AgentCapabilityRuntime.class);
    private static final long TERMINAL_FAILURE_LOG_INTERVAL_MS = config.AgentTuning.longValue("server.agents.capabilities.runtime.AgentCapabilityRuntime.TERMINAL_FAILURE_LOG_INTERVAL_MS");
    private static final AtomicLong lastTerminalFailureLogMs = new AtomicLong();

    private AgentCapabilityRuntime() {
    }

    public static boolean assign(AgentRuntimeEntry entry, AgentCapabilityInvocation<?> invocation) {
        AgentCapabilityRuntimeState state = entry.capabilityRuntimeState();
        synchronized (state) {
            if (!state.frames.isEmpty()) {
                return false;
            }
            state.frames.push(new AgentCapabilityFrame(invocation));
            state.cancellationRequested = false;
            state.lastResult = null;
            return true;
        }
    }

    public static void requestCancellation(AgentRuntimeEntry entry) {
        AgentCapabilityRuntimeState state = entry.capabilityRuntimeState();
        synchronized (state) {
            if (!state.frames.isEmpty()) {
                state.cancellationRequested = true;
            }
        }
    }

    public static void cancelNow(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentCapabilityRuntimeState state = entry.capabilityRuntimeState();
        synchronized (state) {
            if (state.frames.isEmpty()) {
                state.cancellationRequested = false;
                return;
            }
            abortStack(state, entry, agent, nowMs, new AgentCapabilityResult(
                    AgentCapabilityStatus.CANCELLED,
                    AgentCapabilityReasonCode.CANCELLED_BY_REQUEST,
                    "capability cancelled"));
        }
    }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        AgentCapabilityRuntimeState state = entry.capabilityRuntimeState();
        synchronized (state) {
            if (state.frames.isEmpty()) {
                return false;
            }
            if (state.cancellationRequested) {
                AgentCapabilityResult result = new AgentCapabilityResult(
                        AgentCapabilityStatus.CANCELLED,
                        AgentCapabilityReasonCode.CANCELLED_BY_REQUEST,
                        "capability cancelled");
                abortStack(state, entry, agent, nowMs, result);
                return true;
            }

            AgentCapabilityFrame frame = state.frames.peek();
            if (!startIfNeeded(entry, state, frame, nowMs)) {
                if (hasExpiredFrame(state, nowMs)) {
                    abortStack(state, entry, agent, nowMs, new AgentCapabilityResult(
                            AgentCapabilityStatus.TIMED_OUT,
                            AgentCapabilityReasonCode.DEADLINE_EXCEEDED,
                            "capability timed out waiting for resource locks"));
                }
                return true;
            }
            if (hasExpiredFrame(state, nowMs)) {
                abortStack(state, entry, agent, nowMs, new AgentCapabilityResult(
                        AgentCapabilityStatus.TIMED_OUT,
                        AgentCapabilityReasonCode.DEADLINE_EXCEEDED,
                        "capability deadline exceeded"));
                return true;
            }

            AgentCapabilityContext context = new AgentCapabilityContext(
                    entry,
                    agent,
                    nowMs,
                    Math.max(0L, nowMs - frame.startedAtMs),
                    frame.retryCount,
                    frame.childResult,
                    frame.memory,
                    CosmicAgentCapabilityViewFactory.create(entry, agent, nowMs));
            AgentCapabilityStep step;
            try {
                step = frame.invocation.tick(context);
            } catch (RuntimeException failure) {
                completeTop(state, entry, agent, nowMs, AgentCapabilityResult.failed(
                        AgentCapabilityReasonCode.EXECUTION_FAILED,
                        failure.getClass().getSimpleName()));
                return true;
            }
            if (step == null) {
                completeTop(state, entry, agent, nowMs, AgentCapabilityResult.failed(
                        AgentCapabilityReasonCode.EXECUTION_FAILED,
                        "capability returned no step"));
                return true;
            }

            switch (step.status()) {
                case RUNNING -> frame.state = AgentCapabilityFrameState.RUNNING;
                case WAITING_CHILD -> handoff(state, frame, step.child(), nowMs, step.result());
                case RETRY -> retry(state, entry, agent, frame, nowMs, step.result());
                default -> {
                    if (!step.result().terminal()) {
                        completeTop(state, entry, agent, nowMs, AgentCapabilityResult.failed(
                                AgentCapabilityReasonCode.EXECUTION_FAILED,
                                "non-terminal result returned for terminal step"));
                    } else {
                        completeTop(state, entry, agent, nowMs, step.result());
                    }
                }
            }
            return step.consumedTick();
        }
    }

    private static boolean startIfNeeded(AgentRuntimeEntry entry,
                                         AgentCapabilityRuntimeState state,
                                         AgentCapabilityFrame frame,
                                         long nowMs) {
        if (frame.state != AgentCapabilityFrameState.STARTING) {
            return true;
        }
        if (frame.deadlineMs == 0L) {
            frame.startedAtMs = nowMs;
            frame.deadlineMs = saturatedAdd(nowMs, frame.invocation.timeoutMs());
        }
        AgentCapabilityResourceLockState locks = entry.capabilityStates()
                .require(AgentCapabilityResourceLockState.STATE_KEY);
        if (!locks.acquire(frame.lockOwnerId, frame.requiredResources,
                nowMs, frame.deadlineMs)) {
            return false;
        }
        frame.locksAcquired = true;
        frame.state = AgentCapabilityFrameState.RUNNING;
        journal(state, nowMs, state.frames.size() > 1
                        ? AgentCapabilityJournalEventType.CHILD_STARTED
                        : AgentCapabilityJournalEventType.STARTED,
                frame,
                new AgentCapabilityResult(AgentCapabilityStatus.RUNNING,
                        AgentCapabilityReasonCode.IN_PROGRESS,
                        "capability started"));
        return true;
    }

    private static void handoff(AgentCapabilityRuntimeState state,
                                AgentCapabilityFrame parent,
                                AgentCapabilityInvocation<?> child,
                                long nowMs,
                                AgentCapabilityResult result) {
        parent.state = AgentCapabilityFrameState.WAITING_CHILD;
        parent.childResult = null;
        journal(state, nowMs, AgentCapabilityJournalEventType.HANDOFF_REQUESTED, parent, result);
        state.frames.push(new AgentCapabilityFrame(child, parent.lockOwnerId));
    }

    private static void retry(AgentCapabilityRuntimeState state,
                              AgentRuntimeEntry entry,
                              Character agent,
                              AgentCapabilityFrame frame,
                              long nowMs,
                              AgentCapabilityResult result) {
        if (frame.retryCount >= frame.invocation.maxRetries()) {
            completeTop(state, entry, agent, nowMs, AgentCapabilityResult.failed(
                    AgentCapabilityReasonCode.RETRIES_EXHAUSTED,
                    "capability retries exhausted"));
            return;
        }
        frame.retryCount++;
        journal(state, nowMs, AgentCapabilityJournalEventType.RETRY, frame, result);
    }

    private static void completeTop(AgentCapabilityRuntimeState state,
                                    AgentRuntimeEntry entry,
                                    Character agent,
                                    long nowMs,
                                    AgentCapabilityResult result) {
        AgentCapabilityFrame completed = state.frames.pop();
        releaseLocks(entry, completed);
        completed.state = terminalFrameState(result);
        notifyTerminal(completed, entry, agent, nowMs, result);
        journal(state, nowMs, terminalEvent(result), completed, result);
        if (state.frames.isEmpty()) {
            state.lastResult = result;
            return;
        }

        AgentCapabilityFrame parent = state.frames.peek();
        journal(state, nowMs, AgentCapabilityJournalEventType.CHILD_RESULT, completed, result);
        parent.state = AgentCapabilityFrameState.RUNNING;
        parent.childResult = result;
        journal(state, nowMs, AgentCapabilityJournalEventType.PARENT_RESUMED, parent, result);
    }

    private static boolean hasExpiredFrame(AgentCapabilityRuntimeState state, long nowMs) {
        return state.frames.stream()
                .filter(frame -> frame.deadlineMs > 0L)
                .anyMatch(frame -> nowMs >= frame.deadlineMs);
    }

    private static void abortStack(AgentCapabilityRuntimeState state,
                                   AgentRuntimeEntry entry,
                                   Character agent,
                                   long nowMs,
                                   AgentCapabilityResult result) {
        while (!state.frames.isEmpty()) {
            AgentCapabilityFrame frame = state.frames.pop();
            releaseLocks(entry, frame);
            frame.state = terminalFrameState(result);
            notifyTerminal(frame, entry, agent, nowMs, result);
            journal(state, nowMs, terminalEvent(result), frame, result);
        }
        state.lastResult = result;
        state.cancellationRequested = false;
    }

    private static void notifyTerminal(AgentCapabilityFrame frame,
                                       AgentRuntimeEntry entry,
                                       Character agent,
                                       long nowMs,
                                       AgentCapabilityResult result) {
        AgentCapabilityContext context = new AgentCapabilityContext(
                entry,
                agent,
                nowMs,
                frame.deadlineMs == 0L
                        ? 0L
                        : Math.max(0L, nowMs - frame.startedAtMs),
                frame.retryCount,
                frame.childResult,
                frame.memory,
                CosmicAgentCapabilityViewFactory.create(entry, agent, nowMs));
        try {
            frame.invocation.onTerminal(context, result);
        } catch (RuntimeException failure) {
            // Terminal cleanup must not prevent the runtime from closing the frame.
            long now = System.currentTimeMillis();
            long previous = lastTerminalFailureLogMs.get();
            if (now - previous >= TERMINAL_FAILURE_LOG_INTERVAL_MS
                    && lastTerminalFailureLogMs.compareAndSet(previous, now)) {
                log.warn("Agent capability terminal cleanup failed capability={} command={} status={}",
                        frame.invocation.capabilityId(), frame.invocation.commandType(), result.status(), failure);
            }
        }
    }

    private static void releaseLocks(AgentRuntimeEntry entry, AgentCapabilityFrame frame) {
        if (!frame.locksAcquired) {
            return;
        }
        entry.capabilityStates().require(AgentCapabilityResourceLockState.STATE_KEY)
                .release(frame.lockOwnerId, frame.requiredResources);
        frame.locksAcquired = false;
    }

    private static AgentCapabilityJournalEventType terminalEvent(AgentCapabilityResult result) {
        return switch (result.status()) {
            case SUCCESS -> AgentCapabilityJournalEventType.SUCCEEDED;
            case CANCELLED -> AgentCapabilityJournalEventType.CANCELLED;
            case TIMED_OUT -> AgentCapabilityJournalEventType.TIMED_OUT;
            case MISSING_REQUIREMENT, BLOCKED_BY_SCOPE, BLOCKED_FORBIDDEN_QUEST,
                    BLOCKED_FORBIDDEN_MAP, BLOCKED_FORBIDDEN_NPC -> AgentCapabilityJournalEventType.BLOCKED;
            default -> AgentCapabilityJournalEventType.FAILED;
        };
    }

    private static AgentCapabilityFrameState terminalFrameState(AgentCapabilityResult result) {
        return switch (result.status()) {
            case SUCCESS -> AgentCapabilityFrameState.SUCCEEDED;
            case CANCELLED -> AgentCapabilityFrameState.CANCELLED;
            case TIMED_OUT -> AgentCapabilityFrameState.TIMED_OUT;
            case MISSING_REQUIREMENT, BLOCKED_BY_SCOPE, BLOCKED_FORBIDDEN_QUEST,
                    BLOCKED_FORBIDDEN_MAP, BLOCKED_FORBIDDEN_NPC -> AgentCapabilityFrameState.BLOCKED;
            default -> AgentCapabilityFrameState.FAILED;
        };
    }

    private static void journal(AgentCapabilityRuntimeState state,
                                long nowMs,
                                AgentCapabilityJournalEventType type,
                                AgentCapabilityFrame frame,
                                AgentCapabilityResult result) {
        while (state.journal.size() >= AgentCapabilityRuntimeState.MAX_JOURNAL_EVENTS) {
            state.journal.removeFirst();
        }
        state.journal.addLast(new AgentCapabilityJournalEvent(
                nowMs,
                type,
                frame.invocation.capabilityId(),
                frame.invocation.commandType(),
                result.status(),
                result.reasonCode(),
                result.message(),
                frame.invocation.metadata().objectiveId(),
                frame.invocation.metadata().objectiveSource(),
                frame.invocation.metadata().behaviorVersion(),
                frame.invocation.metadata().correlationId()));
        state.journalSequence++;
    }

    private static long saturatedAdd(long value, long increment) {
        if (Long.MAX_VALUE - value < increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }
}
