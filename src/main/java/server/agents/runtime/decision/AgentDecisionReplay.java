package server.agents.runtime.decision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and fingerprints an immutable decision trace without executing
 * game mutations. Used by regression and deterministic replay gates.
 */
public final class AgentDecisionReplay {
    private AgentDecisionReplay() {
    }

    public static Result verify(List<AgentDecisionRecord> records) {
        List<AgentDecisionRecord> trace = List.copyOf(records == null ? List.of() : records);
        long previousSequence = 0L;
        long previousTime = 0L;
        Map<String, String> finalChoices = new LinkedHashMap<>();
        StringBuilder canonical = new StringBuilder();
        for (AgentDecisionRecord record : trace) {
            if (record.sequence() <= previousSequence) {
                return new Result(false, "decision sequence is not strictly increasing",
                        "", Map.copyOf(finalChoices));
            }
            if (record.decidedAtMs() < previousTime) {
                return new Result(false, "decision time moved backwards",
                        "", Map.copyOf(finalChoices));
            }
            previousSequence = record.sequence();
            previousTime = record.decidedAtMs();
            finalChoices.put(record.domain(), record.choice());
            canonical.append(record.sequence()).append('|')
                    .append(record.decidedAtMs()).append('|')
                    .append(record.domain()).append('|')
                    .append(record.choice()).append('|')
                    .append(record.source()).append('|')
                    .append(record.behaviorVersion()).append('|')
                    .append(record.reason()).append('|')
                    .append(record.correlationId()).append('|')
                    .append(String.join(",", record.candidates())).append('\n');
        }
        return new Result(true, "", sha256(canonical.toString()), Map.copyOf(finalChoices));
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record Result(
            boolean valid,
            String reason,
            String fingerprint,
            Map<String, String> finalChoices) {
    }
}
