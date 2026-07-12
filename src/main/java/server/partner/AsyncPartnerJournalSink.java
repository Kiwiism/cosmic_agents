package server.partner;

import server.ThreadManager;

public final class AsyncPartnerJournalSink implements PartnerJournalSink {
    private final AdventurerPartnerRepository repository;

    public AsyncPartnerJournalSink(AdventurerPartnerRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(long sessionId,
                       ProfileOrientation orientation,
                       long generation,
                       PartnerLifecycleStatus status,
                       String reason) {
        ThreadManager.getInstance().newDatabaseTask(() ->
                repository.updateSession(sessionId, orientation, generation, status, reason));
    }
}
