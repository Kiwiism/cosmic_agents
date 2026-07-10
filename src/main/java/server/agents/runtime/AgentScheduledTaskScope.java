package server.agents.runtime;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class AgentScheduledTaskScope {
    private final Set<TrackedAction> actions = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public ScheduledFuture<?> schedule(Function<Runnable, ScheduledFuture<?>> scheduler, Runnable action) {
        TrackedAction tracked = new TrackedAction(action);
        actions.add(tracked);
        if (cancelled.get()) {
            tracked.cancel();
        }

        ScheduledFuture<?> future;
        try {
            future = scheduler.apply(tracked);
        } catch (RuntimeException | Error failure) {
            actions.remove(tracked);
            throw failure;
        }
        tracked.attach(future);
        return future;
    }

    public void cancelAll() {
        cancelled.set(true);
        for (TrackedAction action : actions) {
            action.cancel();
        }
        actions.clear();
    }

    boolean isCancelled() {
        return cancelled.get();
    }

    int pendingTaskCount() {
        return actions.size();
    }

    private final class TrackedAction implements Runnable {
        private final Runnable action;
        private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicBoolean taskCancelled = new AtomicBoolean();

        private TrackedAction(Runnable action) {
            this.action = action;
        }

        @Override
        public void run() {
            if (taskCancelled.get() || cancelled.get()) {
                finish();
                return;
            }
            try {
                action.run();
            } finally {
                finish();
            }
        }

        private void attach(ScheduledFuture<?> scheduledFuture) {
            if (scheduledFuture == null) {
                actions.remove(this);
                throw new IllegalArgumentException("scheduledFuture must not be null");
            }
            if (!future.compareAndSet(null, scheduledFuture)) {
                throw new IllegalStateException("scheduled future is already attached");
            }
            if (taskCancelled.get() || cancelled.get() || finished.get()) {
                scheduledFuture.cancel(false);
                actions.remove(this);
            }
        }

        private void cancel() {
            taskCancelled.set(true);
            ScheduledFuture<?> scheduledFuture = future.get();
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            actions.remove(this);
        }

        private void finish() {
            finished.set(true);
            actions.remove(this);
        }
    }
}
