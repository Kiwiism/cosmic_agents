package server.agents.runtime;

import java.util.concurrent.CompletableFuture;

public final class AgentMailboxRuntime {
    private static final int DEFAULT_MAX_ACTIONS_PER_TICK = 32;

    private AgentMailboxRuntime() {
    }

    public static boolean enabled() {
        return Boolean.getBoolean("agents.mailbox.enabled");
    }

    public static int configuredCapacity() {
        return AgentBoundedExecutorFactory.positiveIntegerProperty("agents.mailbox.capacity", 128);
    }

    public static <R> CompletableFuture<R> submit(AgentRuntimeEntry entry, AgentMailboxAction<R> action) {
        if (entry == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Agent runtime entry is required"));
        }
        return entry.actionMailbox().submit(
                entry.sessionGeneration(),
                entry.transitionBarrierState().generation(),
                action);
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
