package server.agents.runtime.mailbox;

public record AgentMailboxOptions(AgentMailboxOverflowPolicy overflowPolicy,
                                  String coalescingKey,
                                  long expiresAtMs) {
    private static final AgentMailboxOptions FIFO =
            new AgentMailboxOptions(AgentMailboxOverflowPolicy.REJECT, null, 0L);

    public AgentMailboxOptions {
        if (overflowPolicy == null) {
            throw new IllegalArgumentException("Agent mailbox overflow policy is required");
        }
        if (expiresAtMs < 0L) {
            throw new IllegalArgumentException("Agent mailbox expiry must not be negative");
        }
        if (overflowPolicy == AgentMailboxOverflowPolicy.COALESCE_LATEST
                && (coalescingKey == null || coalescingKey.isBlank())) {
            throw new IllegalArgumentException("Coalescing requires a non-blank key");
        }
    }

    public static AgentMailboxOptions fifo() {
        return FIFO;
    }

    public static AgentMailboxOptions expiringAt(long expiresAtMs) {
        return new AgentMailboxOptions(AgentMailboxOverflowPolicy.REJECT, null, expiresAtMs);
    }

    public static AgentMailboxOptions coalesceLatest(String key) {
        return new AgentMailboxOptions(AgentMailboxOverflowPolicy.COALESCE_LATEST, key, 0L);
    }

    public static AgentMailboxOptions coalesceLatest(String key, long expiresAtMs) {
        return new AgentMailboxOptions(AgentMailboxOverflowPolicy.COALESCE_LATEST, key, expiresAtMs);
    }

    public boolean expired(long nowMs) {
        return expiresAtMs > 0L && nowMs >= expiresAtMs;
    }
}
