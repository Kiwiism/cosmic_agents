package server.economy;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InMemoryEconomyTransactionJournal implements EconomyTransactionJournal {
    private final Map<UUID, EconomyJournalStatus> states = new ConcurrentHashMap<>();

    @Override
    public void prepare(EconomyOperation operation) {
        if (states.putIfAbsent(operation.transactionId(), EconomyJournalStatus.PREPARED) != null) {
            throw new EconomyTransactionException("Duplicate economy transaction id");
        }
    }

    @Override
    public void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason) {
        if (!states.replace(operation.transactionId(), EconomyJournalStatus.PREPARED, status)) {
            throw new EconomyTransactionException("Invalid economy journal transition");
        }
    }

    @Override
    public int markStalePreparedForReview(Duration age) {
        int changed = 0;
        for (Map.Entry<UUID, EconomyJournalStatus> entry : states.entrySet()) {
            if (states.replace(entry.getKey(), EconomyJournalStatus.PREPARED, EconomyJournalStatus.REVIEW_REQUIRED)) {
                changed++;
            }
        }
        return changed;
    }

    EconomyJournalStatus status(UUID id) {
        return states.get(id);
    }
}
