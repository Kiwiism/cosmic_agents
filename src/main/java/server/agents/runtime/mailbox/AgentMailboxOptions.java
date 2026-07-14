package server.agents.runtime.mailbox;

public record AgentMailboxOptions(AgentMailboxOverflowPolicy overflowPolicy,
                                  String coalescingKey,
                                  long expiresAtMs,
                                  AgentMailboxWorkClass workClass) {
    private static final AgentMailboxOptions FIFO =
            new AgentMailboxOptions(
                    AgentMailboxOverflowPolicy.REJECT,
                    null,
                    0L,
                    AgentMailboxWorkClass.ORDINARY);

    public AgentMailboxOptions(AgentMailboxOverflowPolicy overflowPolicy,
                               String coalescingKey,
                               long expiresAtMs) {
        this(overflowPolicy, coalescingKey, expiresAtMs, AgentMailboxWorkClass.ORDINARY);
    }

    public AgentMailboxOptions {
        if (overflowPolicy == null) {
            throw new IllegalArgumentException("Agent mailbox overflow policy is required");
        }
        if (workClass == null) {
            throw new IllegalArgumentException("Agent mailbox work class is required");
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
        return new AgentMailboxOptions(
                AgentMailboxOverflowPolicy.REJECT,
                null,
                expiresAtMs,
                AgentMailboxWorkClass.ORDINARY);
    }

    public static AgentMailboxOptions coalesceLatest(String key) {
        return new AgentMailboxOptions(
                AgentMailboxOverflowPolicy.COALESCE_LATEST,
                key,
                0L,
                AgentMailboxWorkClass.ORDINARY);
    }

    public static AgentMailboxOptions coalesceLatest(String key, long expiresAtMs) {
        return new AgentMailboxOptions(
                AgentMailboxOverflowPolicy.COALESCE_LATEST,
                key,
                expiresAtMs,
                AgentMailboxWorkClass.ORDINARY);
    }

    public static AgentMailboxOptions completionCoalesceLatest(String key) {
        return new AgentMailboxOptions(
                AgentMailboxOverflowPolicy.COALESCE_LATEST,
                key,
                0L,
                AgentMailboxWorkClass.COMPLETION_CRITICAL);
    }

    public static AgentMailboxOptions lifecycleCritical() {
        return new AgentMailboxOptions(
                AgentMailboxOverflowPolicy.REJECT,
                null,
                0L,
                AgentMailboxWorkClass.LIFECYCLE_CRITICAL);
    }

    public boolean expired(long nowMs) {
        return expiresAtMs > 0L && nowMs >= expiresAtMs;
    }
}
