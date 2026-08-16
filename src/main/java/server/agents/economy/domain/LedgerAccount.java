package server.agents.economy.domain;

public record LedgerAccount(String type, String ownerId) {
    public LedgerAccount {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type is required");
        if (ownerId == null || ownerId.isBlank()) throw new IllegalArgumentException("ownerId is required");
    }

    public static LedgerAccount agent(String id) { return new LedgerAccount("AGENT", id); }
    public static LedgerAccount source(String id) { return new LedgerAccount("SOURCE", id); }
    public static LedgerAccount sink(String id) { return new LedgerAccount("SINK", id); }
    public static LedgerAccount escrow(String id) { return new LedgerAccount("ESCROW", id); }
}
