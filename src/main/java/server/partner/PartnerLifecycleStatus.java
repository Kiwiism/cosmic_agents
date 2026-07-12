package server.partner;

public enum PartnerLifecycleStatus {
    ACTIVATING,
    ACTIVE,
    SWAPPING,
    RELEASING,
    CLOSED,
    FAILED,
    RECOVERED;

    public boolean isTerminal() {
        return this == CLOSED || this == FAILED || this == RECOVERED;
    }
}
