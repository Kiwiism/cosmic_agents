package server.persistence;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Versioned dirty-section tracker. A section changed while its save is in
 * flight remains dirty, and periodic full checkpoints cover legacy mutators
 * that have not yet acquired an explicit marker.
 */
public final class DirtySectionTracker<S extends Enum<S>> {
    private final Class<S> sectionType;
    private final EnumSet<S> dirty;
    private final AtomicLongArray versions;
    private final int fullCheckpointInterval;
    private int successfulAutosavesSinceCheckpoint;

    public DirtySectionTracker(Class<S> sectionType, int fullCheckpointInterval) {
        this.sectionType = sectionType;
        this.dirty = EnumSet.allOf(sectionType);
        this.versions = new AtomicLongArray(sectionType.getEnumConstants().length);
        this.fullCheckpointInterval = Math.max(1, fullCheckpointInterval);
    }

    public synchronized void mark(S section) {
        versions.incrementAndGet(section.ordinal());
        dirty.add(section);
    }

    public synchronized SavePlan<S> plan(boolean fullCheckpoint) {
        boolean periodicCheckpoint = !fullCheckpoint
                && successfulAutosavesSinceCheckpoint + 1 >= fullCheckpointInterval;
        EnumSet<S> selected = fullCheckpoint || periodicCheckpoint
                ? EnumSet.allOf(sectionType)
                : EnumSet.copyOf(dirty);
        long[] selectedVersions = new long[versions.length()];
        for (S section : selected) {
            selectedVersions[section.ordinal()] = versions.get(section.ordinal());
        }
        return new SavePlan<>(selected, selectedVersions, fullCheckpoint || periodicCheckpoint);
    }

    public synchronized void complete(SavePlan<S> plan) {
        for (S section : plan.sections) {
            if (versions.get(section.ordinal()) == plan.versions[section.ordinal()]) {
                dirty.remove(section);
            }
        }
        if (plan.resetsCheckpointCounter) {
            successfulAutosavesSinceCheckpoint = 0;
        } else {
            successfulAutosavesSinceCheckpoint++;
        }
    }

    public synchronized EnumSet<S> dirtySnapshot() {
        return EnumSet.copyOf(dirty);
    }

    public static final class SavePlan<S extends Enum<S>> {
        private final EnumSet<S> sections;
        private final long[] versions;
        private final boolean resetsCheckpointCounter;

        private SavePlan(EnumSet<S> sections, long[] versions, boolean resetsCheckpointCounter) {
            this.sections = sections;
            this.versions = versions;
            this.resetsCheckpointCounter = resetsCheckpointCounter;
        }

        public boolean includes(S section) {
            return sections.contains(section);
        }

        public boolean isEmpty() {
            return sections.isEmpty();
        }

        public EnumSet<S> sections() {
            return EnumSet.copyOf(sections);
        }

        public boolean isFullCheckpoint() {
            return resetsCheckpointCounter;
        }
    }
}
