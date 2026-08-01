package server.economy;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomyTransactionCoordinatorTest {
    @AfterEach
    void resetJournal() {
        EconomyTransactionCoordinator.resetJournalForTesting();
    }

    @Test
    void recordsCommitOnlyAfterMutationCompletes() {
        InMemoryEconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();
        EconomyTransactionCoordinator.installJournalForTesting(journal);
        Character participant = character(41);
        AtomicInteger mutations = new AtomicInteger();

        EconomyTransactionCoordinator.execute(participant, null, EconomyOperationKind.SHOP_BUY,
                "item=2000000 quantity=10 mesos=500", mutations::incrementAndGet);

        assertEquals(1, mutations.get());
        assertEquals(0, journal.markStalePreparedForReview(java.time.Duration.ZERO),
                "committed entries must not be reclassified");
    }

    @Test
    void marksMutationFailureForReview() {
        TrackingJournal journal = new TrackingJournal();
        EconomyTransactionCoordinator.installJournalForTesting(journal);
        Character participant = character(42);

        assertThrows(IllegalStateException.class, () -> EconomyTransactionCoordinator.execute(participant, null,
                EconomyOperationKind.SHOP_SELL, "item=4000000 quantity=1 mesos=5",
                () -> { throw new IllegalStateException("inventory mutation failed"); }));

        assertEquals(EconomyJournalStatus.REVIEW_REQUIRED, journal.lastStatus);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }

    private static final class TrackingJournal implements EconomyTransactionJournal {
        private EconomyJournalStatus lastStatus;

        @Override
        public void prepare(EconomyOperation operation) {
            lastStatus = EconomyJournalStatus.PREPARED;
        }

        @Override
        public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
            lastStatus = status;
        }

        @Override
        public int markStalePreparedForReview(java.time.Duration age) {
            return 0;
        }
    }
}
