package server.economy;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryEconomyTransactionJournal implements EconomyTransactionJournal {
    private final Map<UUID, EconomyJournalStatus> states = new ConcurrentHashMap<>();
    private final Map<String, UUID> transactionByIdempotencyKey = new ConcurrentHashMap<>();

    @Override
    public synchronized EconomyPrepareResult prepare(EconomyOperation operation) {
        UUID existingId = transactionByIdempotencyKey.get(operation.idempotencyKey());
        if (existingId != null) {
            EconomyJournalStatus existingStatus = states.get(existingId);
            if (existingStatus == EconomyJournalStatus.COMMITTED) {
                return EconomyPrepareResult.ALREADY_COMMITTED;
            }
            if (existingStatus != EconomyJournalStatus.ROLLED_BACK) {
                throw new EconomyTransactionException("Economy operation is already in progress");
            }
        }
        states.put(operation.transactionId(), EconomyJournalStatus.PREPARED);
        transactionByIdempotencyKey.put(operation.idempotencyKey(), operation.transactionId());
        return EconomyPrepareResult.EXECUTE;
    }

    @Override
    public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
        if (status == EconomyJournalStatus.COMMITTED) {
            throw new EconomyTransactionException("Committed state requires an atomic durable commit");
        }
        if (!states.replace(operation.transactionId(), EconomyJournalStatus.PREPARED, status)) {
            throw new EconomyTransactionException("Invalid economy journal transition");
        }
    }

    @Override
    public void commit(EconomyOperation operation, EconomyDurableState durableState) {
        if (!states.replace(operation.transactionId(), EconomyJournalStatus.PREPARED,
                EconomyJournalStatus.COMMITTED)) {
            throw new EconomyTransactionException("Invalid economy journal commit");
        }
    }

    @Override
    public int reconcileStalePrepared(Duration age) {
        int changed = 0;
        for (Map.Entry<UUID, EconomyJournalStatus> entry : states.entrySet()) {
            if (states.replace(entry.getKey(), EconomyJournalStatus.PREPARED, EconomyJournalStatus.ROLLED_BACK)) {
                changed++;
            }
        }
        return changed;
    }

    EconomyJournalStatus status(UUID id) {
        return states.get(id);
    }
}
