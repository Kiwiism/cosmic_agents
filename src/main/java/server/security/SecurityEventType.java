package server.security;

public enum SecurityEventType {
    MALFORMED_PACKET,
    PACKET_RATE_LIMIT,
    AUTOBAN_SIGNAL,
    ADMIN_BRIDGE_REJECTION,
    ECONOMY_INVARIANT
}
