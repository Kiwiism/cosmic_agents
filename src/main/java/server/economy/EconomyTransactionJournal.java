package server.economy;

import java.time.Duration;

public interface EconomyTransactionJournal {
    EconomyPrepareResult prepare(EconomyOperation operation);

    void commit(EconomyOperation operation, EconomyDurableState durableState);

    void transition(EconomyOperation operation, EconomyJournalStatus status, String failureReason);

    int reconcileStalePrepared(Duration age);
}
