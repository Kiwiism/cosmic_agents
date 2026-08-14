package server.economy;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.security.SecurityEventRuntime;
import server.security.SecurityEventType;
import server.security.SecuritySeverity;
import tools.DatabaseConnection;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class EconomyTransactionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(EconomyTransactionCoordinator.class);
    private static final ConcurrentHashMap<Integer, ReentrantLock> participantLocks = new ConcurrentHashMap<>();
    private static volatile EconomyTransactionJournal journal = new InMemoryEconomyTransactionJournal();

    private EconomyTransactionCoordinator() {
    }

    public static void initializePersistentJournal() {
        journal = new JdbcEconomyTransactionJournal();
        int reconciled = journal.reconcileStalePrepared(Duration.ofMinutes(2));
        if (reconciled > 0) {
            log.warn("Reconciled {} interrupted economy transactions as rolled back", reconciled);
        }
    }

    public static void execute(Character primary, Character secondary, EconomyOperationKind kind,
                               String summary, Runnable mutation) {
        execute(null, primary, secondary, kind, summary, ignored -> mutation.run());
    }

    public static void execute(String idempotencyKey, Character primary, Character secondary,
                               EconomyOperationKind kind, String summary, Runnable mutation) {
        execute(idempotencyKey, primary, secondary, kind, summary, ignored -> mutation.run());
    }

    public static void execute(Character primary, Character secondary, EconomyOperationKind kind,
                               String summary, EconomyMutation mutation) {
        execute(null, primary, secondary, kind, summary, mutation);
    }

    public static void execute(String idempotencyKey, Character primary, Character secondary,
                               EconomyOperationKind kind, String summary, EconomyMutation mutation) {
        if (primary == null || mutation == null) {
            throw new IllegalArgumentException("Economy transaction requires a primary participant and mutation");
        }
        EconomyOperation operation = idempotencyKey == null
                ? EconomyOperation.create(kind, primary.getId(), secondary == null ? null : secondary.getId(), summary)
                : EconomyOperation.create(idempotencyKey, kind, primary.getId(),
                        secondary == null ? null : secondary.getId(), summary);
        int firstId = secondary == null ? primary.getId() : Math.min(primary.getId(), secondary.getId());
        int secondId = secondary == null ? -1 : Math.max(primary.getId(), secondary.getId());
        ReentrantLock first = participantLocks.computeIfAbsent(firstId, ignored -> new ReentrantLock());
        ReentrantLock second = secondId < 0 ? null
                : participantLocks.computeIfAbsent(secondId, ignored -> new ReentrantLock());

        first.lock();
        if (second != null) {
            second.lock();
        }
        try {
            EconomyParticipantSnapshot primaryBefore = EconomyParticipantSnapshot.capture(primary);
            EconomyParticipantSnapshot secondaryBefore = secondary == null
                    ? null : EconomyParticipantSnapshot.capture(secondary);
            if (journal.prepare(operation) == EconomyPrepareResult.ALREADY_COMMITTED) {
                return;
            }
            EconomyTransactionContext context = new EconomyTransactionContext();
            try {
                mutation.run(context);
                EconomyParticipantSnapshot primaryAfter = EconomyParticipantSnapshot.capture(primary);
                EconomyParticipantSnapshot secondaryAfter = secondary == null
                        ? null : EconomyParticipantSnapshot.capture(secondary);
                journal.commit(operation, EconomyDurableState.capture(primaryBefore, primaryAfter,
                        secondaryBefore, secondaryAfter, context.persistence()));
            } catch (RuntimeException failure) {
                try {
                    context.rollback();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                rollbackOrMarkForReview(primaryBefore, secondaryBefore, operation, failure);
                throw failure;
            }
        } finally {
            if (second != null) {
                second.unlock();
                removeUnusedLock(secondId, second);
            }
            first.unlock();
            removeUnusedLock(firstId, first);
        }
    }

    private static void rollbackOrMarkForReview(EconomyParticipantSnapshot primaryBefore,
                                                EconomyParticipantSnapshot secondaryBefore,
                                                EconomyOperation operation, RuntimeException failure) {
        try {
            primaryBefore.restore();
            if (secondaryBefore != null) {
                secondaryBefore.restore();
            }
            journal.transition(operation, EconomyJournalStatus.ROLLED_BACK, failure.toString());
            primaryBefore.disconnectNetworkSession();
            if (secondaryBefore != null) {
                secondaryBefore.disconnectNetworkSession();
            }
        } catch (RuntimeException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
            markForReview(operation, failure);
        }
    }

    private static void markForReview(EconomyOperation operation, RuntimeException failure) {
        try {
            journal.transition(operation, EconomyJournalStatus.REVIEW_REQUIRED, failure.toString());
        } catch (RuntimeException journalFailure) {
            failure.addSuppressed(journalFailure);
        }
        SecurityEventRuntime.record(operation.primaryCharacterId(), SecurityEventType.ECONOMY_INVARIANT,
                SecuritySeverity.CRITICAL,
                Map.of("transactionId", operation.transactionId().toString(),
                        "operation", operation.kind().name(),
                        "failure", failure.getClass().getSimpleName()));
    }

    private static void removeUnusedLock(int id, ReentrantLock lock) {
        if (!lock.hasQueuedThreads()) {
            participantLocks.remove(id, lock);
        }
    }

    static void installJournalForTesting(EconomyTransactionJournal testJournal) {
        journal = testJournal;
    }

    static void resetJournalForTesting() {
        journal = DatabaseConnection.isInitialized()
                ? new JdbcEconomyTransactionJournal()
                : new InMemoryEconomyTransactionJournal();
    }
}
