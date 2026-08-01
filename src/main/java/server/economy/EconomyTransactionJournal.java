package server.economy;

import java.time.Duration;

public interface EconomyTransactionJournal {
    void prepare(EconomyOperation operation);

    void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason);

    int markStalePreparedForReview(Duration age);
}
