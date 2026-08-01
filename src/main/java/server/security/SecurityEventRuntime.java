package server.security;

import client.Character;
import client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class SecurityEventRuntime {
    private static final Logger log = LoggerFactory.getLogger("SECURITY");
    private static final int RETAINED_EVENTS = 10_000;
    private static final AtomicLong sequence = new AtomicLong();
    private static final ArrayDeque<SecurityEvent> recent = new ArrayDeque<>(RETAINED_EVENTS);

    private SecurityEventRuntime() {
    }

    public static SecurityEvent record(Client client, SecurityEventType type, SecuritySeverity severity,
                                       Map<String, String> evidence) {
        Character chr = client == null ? null : client.getPlayer();
        int accountId = client == null ? -1 : client.getAccID();
        int characterId = chr == null ? -1 : chr.getId();
        String remote = client == null ? "unknown" : fingerprint(client.getRemoteAddress());
        return record(type, severity, accountId, characterId, remote, evidence);
    }

    public static SecurityEvent record(Character chr, SecurityEventType type, SecuritySeverity severity,
                                       Map<String, String> evidence) {
        return record(chr == null ? null : chr.getClient(), type, severity, evidence);
    }

    public static SecurityEvent recordExternal(SecurityEventType type, SecuritySeverity severity,
                                               String remoteAddress, Map<String, String> evidence) {
        return record(type, severity, -1, -1, fingerprint(remoteAddress), evidence);
    }

    private static SecurityEvent record(SecurityEventType type, SecuritySeverity severity, int accountId,
                                        int characterId, String remote, Map<String, String> evidence) {
        SecurityEvent event = new SecurityEvent(sequence.incrementAndGet(), Instant.now(), type, severity,
                accountId, characterId, remote, evidence);
        synchronized (recent) {
            if (recent.size() == RETAINED_EVENTS) {
                recent.removeFirst();
            }
            recent.addLast(event);
        }
        if (severity == SecuritySeverity.CRITICAL) {
            log.warn("securityEvent sequence={} type={} accountId={} characterId={} remote={} evidence={}",
                    event.sequence(), type, accountId, characterId, remote, event.evidence());
        } else {
            log.info("securityEvent sequence={} type={} severity={} accountId={} characterId={} remote={} evidence={}",
                    event.sequence(), type, severity, accountId, characterId, remote, event.evidence());
        }
        return event;
    }

    public static List<SecurityEvent> snapshot() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    static void clearForTesting() {
        synchronized (recent) {
            recent.clear();
        }
    }

    private static String fingerprint(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return "unknown";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(remoteAddress.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                result.append(String.format("%02x", digest[i]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
