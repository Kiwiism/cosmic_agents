package server.agents.capabilities.contracts;

public record AgentInventoryReservation(
        String reservationId,
        int itemId,
        int quantity,
        AgentDisposition disposition,
        String capability,
        String reason,
        int priority,
        long expiresAtMs) {

    public AgentInventoryReservation {
        if (reservationId == null || reservationId.isBlank() || itemId <= 0 || quantity <= 0
                || disposition == null || capability == null || capability.isBlank()
                || priority < 0 || expiresAtMs < 0) {
            throw new IllegalArgumentException("Valid inventory reservation fields are required");
        }
        reason = reason == null ? "" : reason;
    }
}
