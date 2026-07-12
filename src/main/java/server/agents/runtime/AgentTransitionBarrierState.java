package server.agents.runtime;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Scheduler-independent pause/drain barrier for profile transitions. */
public final class AgentTransitionBarrierState {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private final AtomicBoolean paused = new AtomicBoolean();
    private final AtomicLong generation = new AtomicLong();

    public TickPermit tryEnterTick() {
        if (paused.get()) {
            return null;
        }
        lock.readLock().lock();
        if (paused.get()) {
            lock.readLock().unlock();
            return null;
        }
        return new TickPermit(this);
    }

    public PauseLease pauseAndDrain() {
        if (!paused.compareAndSet(false, true)) {
            throw new IllegalStateException("Agent transition barrier is already paused");
        }
        long startedNs = System.nanoTime();
        lock.writeLock().lock();
        long nextGeneration = generation.incrementAndGet();
        return new PauseLease(this, nextGeneration, System.nanoTime() - startedNs);
    }

    public long generation() {
        return generation.get();
    }

    public boolean isCurrentGeneration(long expectedGeneration) {
        return generation.get() == expectedGeneration;
    }

    public boolean isPaused() {
        return paused.get();
    }

    private void exitTick() {
        lock.readLock().unlock();
    }

    private void resume(PauseLease lease) {
        if (lease.owner != this || lease.generation != generation.get()) {
            throw new IllegalStateException("Stale or foreign Agent transition lease");
        }
        if (!paused.compareAndSet(true, false)) {
            throw new IllegalStateException("Agent transition barrier is not paused");
        }
        lock.writeLock().unlock();
    }

    public static final class TickPermit implements AutoCloseable {
        private AgentTransitionBarrierState owner;

        private TickPermit(AgentTransitionBarrierState owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            AgentTransitionBarrierState current = owner;
            if (current != null) {
                owner = null;
                current.exitTick();
            }
        }
    }

    public static final class PauseLease implements AutoCloseable {
        private final AgentTransitionBarrierState owner;
        private final long generation;
        private final long drainDurationNs;
        private boolean closed;

        private PauseLease(AgentTransitionBarrierState owner, long generation, long drainDurationNs) {
            this.owner = owner;
            this.generation = generation;
            this.drainDurationNs = drainDurationNs;
        }

        public long generation() {
            return generation;
        }

        public long drainDurationNs() {
            return drainDurationNs;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                owner.resume(this);
            }
        }
    }
}
