package server.partner;

@FunctionalInterface
public interface PartnerJournalSink {
    void record(long sessionId,
                ProfileOrientation orientation,
                long generation,
                PartnerLifecycleStatus status,
                String reason);
}
