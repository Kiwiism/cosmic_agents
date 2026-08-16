package server.agents.economy.persistence;

public interface CosmicOutboxSink {
    /** Idempotent by outbox id. */
    void accept(CosmicOutboxRecord record);
}
