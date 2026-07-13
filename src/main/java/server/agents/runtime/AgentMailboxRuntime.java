package server.agents.runtime;

import server.agents.runtime.scheduler.AgentScheduler;
import server.agents.runtime.scheduler.AgentSchedulerConfig;
import server.agents.runtime.scheduler.AgentSchedulerMode;
import server.agents.runtime.mailbox.AgentMailboxOptions;
import server.agents.runtime.mailbox.AgentMailboxSubmission;

import java.util.concurrent.CompletableFuture;

public final class AgentMailboxRuntime {
    private static final int DEFAULT_MAX_ACTIONS_PER_TICK = 32;

    private AgentMailboxRuntime() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("agents.mailbox.enabled")
                || AgentSchedulerConfig.fromSystemProperties().mode() != AgentSchedulerMode.LEGACY_PER_AGENT;
    }

    public static int configuredCapacity() {
        return AgentBoundedExecutorFactory.positiveIntegerProperty("agents.mailbox.capacity", 128);
    }

    public static <R> CompletableFuture<R> submit(AgentRuntimeEntry entry, AgentMailboxAction<R> action) {
        return submit(entry, action, AgentMailboxOptions.fifo()).result();
    }

    public static <R> AgentMailboxSubmission<R> submit(
            AgentRuntimeEntry entry,
            AgentMailboxAction<R> action,
            AgentMailboxOptions options) {
        if (entry == null) {
            throw new IllegalArgumentException("Agent runtime entry is required");
        }
        AgentMailboxSubmission<R> submission =
                entry.actionMailbox().submit(entry.sessionGeneration(), action, options);
        if (submission.accepted()) {
            AgentScheduler.wake(entry);
        }
        return submission;
    }

    public static <R> CompletableFuture<R> dispatch(
            AgentRuntimeEntry entry,
            AgentMailboxAction<R> action) {
        return dispatch(entry, action, AgentMailboxOptions.fifo());
    }

    public static <R> CompletableFuture<R> dispatch(
            AgentRuntimeEntry entry,
            AgentMailboxAction<R> action,
            AgentMailboxOptions options) {
        if (entry == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Agent runtime entry is required"));
        }
        if (enabled()) {
            return submit(entry, action, options).result();
        }
        try {
            return CompletableFuture.completedFuture(action.execute(entry));
        } catch (Throwable failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    public static int drain(AgentRuntimeEntry entry) {
        if (entry == null) {
            return 0;
        }
        int maxActions = AgentBoundedExecutorFactory.positiveIntegerProperty(
                "agents.mailbox.maxActionsPerTick", DEFAULT_MAX_ACTIONS_PER_TICK);
        return entry.actionMailbox().drain(entry, maxActions);
    }

    public static void close(AgentRuntimeEntry entry) {
        if (entry != null) {
            entry.actionMailbox().close();
        }
    }
}
