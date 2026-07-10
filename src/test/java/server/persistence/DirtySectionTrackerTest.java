package server.persistence;

import org.junit.jupiter.api.Test;
import server.persistence.DirtySectionTracker.SavePlan;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtySectionTrackerTest {
    private enum Section { STATS, ITEMS }

    @Test
    void clearsOnlySuccessfullySavedVersions() {
        DirtySectionTracker<Section> tracker = new DirtySectionTracker<>(Section.class, 10);
        SavePlan<Section> initial = tracker.plan(false);
        tracker.mark(Section.ITEMS);

        tracker.complete(initial);

        assertEquals(EnumSet.of(Section.ITEMS), tracker.dirtySnapshot());
    }

    @Test
    void periodicallyForcesAConservativeFullCheckpoint() {
        DirtySectionTracker<Section> tracker = new DirtySectionTracker<>(Section.class, 2);
        SavePlan<Section> initial = tracker.plan(false);
        tracker.complete(initial);
        SavePlan<Section> forced = tracker.plan(false);

        assertTrue(forced.isFullCheckpoint());
        assertTrue(forced.includes(Section.STATS));
        assertTrue(forced.includes(Section.ITEMS));

        tracker.complete(forced);
        assertFalse(tracker.plan(false).isFullCheckpoint());
    }

    @Test
    void cleanAutosaveHasNoSections() {
        DirtySectionTracker<Section> tracker = cleanTracker();

        assertTrue(tracker.plan(false).isEmpty());
    }

    @Test
    void explicitCheckpointIncludesEverySection() {
        DirtySectionTracker<Section> tracker = cleanTracker();

        SavePlan<Section> plan = tracker.plan(true);

        assertTrue(plan.isFullCheckpoint());
        assertEquals(EnumSet.allOf(Section.class), plan.sections());
    }

    @Test
    void failedSaveLeavesPlannedSectionsDirty() {
        DirtySectionTracker<Section> tracker = cleanTracker();
        tracker.mark(Section.STATS);
        SavePlan<Section> failedPlan = tracker.plan(false);

        // Failure deliberately does not call complete.

        assertEquals(EnumSet.of(Section.STATS), tracker.dirtySnapshot());
        assertEquals(EnumSet.of(Section.STATS), failedPlan.sections());
    }

    @Test
    void successfulSaveClearsOnlySectionsWhoseVersionDidNotChange() {
        DirtySectionTracker<Section> tracker = cleanTracker();
        tracker.mark(Section.STATS);
        tracker.mark(Section.ITEMS);
        SavePlan<Section> plan = tracker.plan(false);

        tracker.mark(Section.ITEMS);
        tracker.complete(plan);

        assertEquals(EnumSet.of(Section.ITEMS), tracker.dirtySnapshot());
    }

    private static DirtySectionTracker<Section> cleanTracker() {
        DirtySectionTracker<Section> tracker = new DirtySectionTracker<>(Section.class, 10);
        tracker.complete(tracker.plan(false));
        return tracker;
    }
}
