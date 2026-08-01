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
        assertEquals(0, journal.reconcileStalePrepared(java.time.Duration.ZERO),
                "committed entries must not be reclassified");
    }

    @Test
    void committedIdempotencyKeyDoesNotRepeatMutation() {
        EconomyTransactionCoordinator.installJournalForTesting(new InMemoryEconomyTransactionJournal());
        Character participant = character(44);
        AtomicInteger mutations = new AtomicInteger();

        EconomyTransactionCoordinator.execute("shop-request-44", participant, null,
                EconomyOperationKind.SHOP_BUY, "idempotent-shop-request", mutations::incrementAndGet);
        EconomyTransactionCoordinator.execute("shop-request-44", participant, null,
                EconomyOperationKind.SHOP_BUY, "idempotent-shop-request", mutations::incrementAndGet);

        assertEquals(1, mutations.get());
    }

    @Test
    void rollsBackMutationFailure() {
        TrackingJournal journal = new TrackingJournal();
        EconomyTransactionCoordinator.installJournalForTesting(journal);
        Character participant = character(42);

        assertThrows(IllegalStateException.class, () -> EconomyTransactionCoordinator.execute(participant, null,
                EconomyOperationKind.SHOP_SELL, "item=4000000 quantity=1 mesos=5",
                () -> { throw new IllegalStateException("inventory mutation failed"); }));

        assertEquals(EconomyJournalStatus.ROLLED_BACK, journal.lastStatus());
    }

    @Test
    void rollsBackWhenAtomicDurableCommitFails() {
        FailingCommitJournal journal = new FailingCommitJournal();
        EconomyTransactionCoordinator.installJournalForTesting(journal);
        Character participant = character(43);

        assertThrows(EconomyTransactionException.class, () -> EconomyTransactionCoordinator.execute(
                participant, null, EconomyOperationKind.SHOP_BUY, "endpoint-crash-injection", () -> { }));

        assertEquals(EconomyJournalStatus.ROLLED_BACK, journal.lastStatus());
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }

    private static class TrackingJournal implements EconomyTransactionJournal {
        private EconomyJournalStatus lastStatus;

        @Override
        public EconomyPrepareResult prepare(EconomyOperation operation) {
            lastStatus = EconomyJournalStatus.PREPARED;
            return EconomyPrepareResult.EXECUTE;
        }

        @Override
        public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
            lastStatus = status;
        }

        @Override
        public void commit(EconomyOperation operation, EconomyDurableState durableState) {
            lastStatus = EconomyJournalStatus.COMMITTED;
        }

        @Override
        public int reconcileStalePrepared(java.time.Duration age) {
            return 0;
        }

        protected final EconomyJournalStatus lastStatus() {
            return lastStatus;
        }
    }

    private static final class FailingCommitJournal extends TrackingJournal {
        @Override
        public void commit(EconomyOperation operation, EconomyDurableState durableState) {
            throw new EconomyTransactionException("injected failure after endpoint mutation");
        }
    }
}
