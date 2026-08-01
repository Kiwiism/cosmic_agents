package server.economy;

import java.util.UUID;

public record EconomyOperation(
        UUID transactionId,
        String idempotencyKey,
        EconomyOperationKind kind,
        int primaryCharacterId,
        Integer secondaryCharacterId,
        String summary) {

    public EconomyOperation {
        if (transactionId == null || idempotencyKey == null || idempotencyKey.isBlank()
                || kind == null || primaryCharacterId < 0) {
            throw new IllegalArgumentException("An economy operation requires an id, kind, and primary character");
        }
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Economy idempotency key exceeds 128 characters");
        }
        summary = summary == null ? "" : summary.substring(0, Math.min(summary.length(), 1024));
    }

    public static EconomyOperation create(EconomyOperationKind kind, int primaryCharacterId,
                                          Integer secondaryCharacterId, String summary) {
        UUID transactionId = UUID.randomUUID();
        return new EconomyOperation(transactionId, transactionId.toString(), kind,
                primaryCharacterId, secondaryCharacterId, summary);
    }

    public static EconomyOperation create(String idempotencyKey, EconomyOperationKind kind,
                                          int primaryCharacterId, Integer secondaryCharacterId,
                                          String summary) {
        return new EconomyOperation(UUID.randomUUID(), idempotencyKey, kind,
                primaryCharacterId, secondaryCharacterId, summary);
    }
}
