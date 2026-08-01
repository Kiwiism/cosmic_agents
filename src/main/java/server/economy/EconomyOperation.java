package server.economy;

import java.util.UUID;

public record EconomyOperation(
        UUID transactionId,
        EconomyOperationKind kind,
        int primaryCharacterId,
        Integer secondaryCharacterId,
        String summary) {

    public EconomyOperation {
        if (transactionId == null || kind == null || primaryCharacterId < 0) {
            throw new IllegalArgumentException("An economy operation requires an id, kind, and primary character");
        }
        summary = summary == null ? "" : summary.substring(0, Math.min(summary.length(), 1024));
    }

    public static EconomyOperation create(EconomyOperationKind kind, int primaryCharacterId,
                                          Integer secondaryCharacterId, String summary) {
        return new EconomyOperation(UUID.randomUUID(), kind, primaryCharacterId, secondaryCharacterId, summary);
    }
}
