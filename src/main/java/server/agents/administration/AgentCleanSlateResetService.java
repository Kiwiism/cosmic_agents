package server.agents.administration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentCleanSlateResetService {
    public static final long PREVIEW_TTL_MS = 120_000L;

    public interface MaintenanceLease extends AutoCloseable {
        @Override void close();
    }

    public interface Hooks {
        boolean online(int characterId);
        boolean runtimeActive(int characterId);
        MaintenanceLease acquire(int characterId);
        void clearAgentOsProgress(int characterId) throws Exception;
    }

    private static final List<String> RESET_SCOPE = List.of(
            "level, job, EXP, stats, HP/MP, fame, mesos, and map",
            "ordinary inventory replaced with standard beginner equipment",
            "skills, cooldowns, quests, medals, monster book, and travel locations",
            "Agent objective, plan, career-progress, energy, and pending Director state");
    private static final List<String> RETAINED_SCOPE = List.of(
            "Agent identity, character name, appearance, and account",
            "personality, social memories, and relationship records",
            "cash/cosmetic items and relationship rings",
            "Director journey and reset audit history");

    private final AgentCleanSlateResetPort port;
    private final Hooks hooks;
    private final SecureRandom random;
    private final Map<String, PendingConfirmation> pending = new ConcurrentHashMap<>();

    public AgentCleanSlateResetService(AgentCleanSlateResetPort port, Hooks hooks) {
        this(port, hooks, new SecureRandom());
    }

    AgentCleanSlateResetService(AgentCleanSlateResetPort port, Hooks hooks, SecureRandom random) {
        if (port == null || hooks == null || random == null) {
            throw new IllegalArgumentException("reset port, hooks, and randomness are required");
        }
        this.port = port;
        this.hooks = hooks;
        this.random = random;
    }

    public AgentCleanSlatePreview preview(
            int characterId, String requestedBy, String reason, long nowMs) throws Exception {
        String actor = requireText(requestedBy, "reset requester is required");
        String rationale = requireText(reason, "reset reason is required");
        if (characterId <= 0 || nowMs < 0L) {
            throw new IllegalArgumentException("positive character id and current time are required");
        }
        AgentCleanSlateTarget target = port.inspect(characterId);
        List<String> blockers = blockers(target);
        String resetId = UUID.randomUUID().toString();
        String token = token();
        String phrase = "RESET " + target.name();
        long expiresAtMs = nowMs + PREVIEW_TTL_MS;
        AgentCleanSlatePreview preview = new AgentCleanSlatePreview(
                resetId, target, blockers.isEmpty(), blockers, RESET_SCOPE, RETAINED_SCOPE,
                blockers.isEmpty() ? token : "", blockers.isEmpty() ? phrase : "", expiresAtMs);
        String tokenHash = digest(token);
        port.recordPreview(preview, actor, rationale, tokenHash, nowMs);
        if (preview.eligible()) {
            pending.put(resetId, new PendingConfirmation(
                    characterId, tokenHash, phrase, target.fingerprint(), expiresAtMs));
        }
        return preview;
    }

    public AgentCleanSlateResult execute(
            int characterId,
            String resetId,
            String confirmationToken,
            String confirmationPhrase,
            long nowMs) throws Exception {
        String id = requireText(resetId, "reset id is required");
        PendingConfirmation confirmation = pending.remove(id);
        if (confirmation == null || confirmation.characterId() != characterId) {
            throw new IllegalStateException("reset confirmation is missing or already used");
        }
        try (MaintenanceLease ignored = hooks.acquire(characterId)) {
            validateConfirmation(confirmation, confirmationToken, confirmationPhrase, nowMs);
            AgentCleanSlateTarget current = port.inspect(characterId);
            List<String> blockers = blockers(current);
            if (!current.fingerprint().equals(confirmation.targetFingerprint())) {
                blockers.add("character state changed after the reset preview");
            }
            if (!blockers.isEmpty()) {
                throw new IllegalStateException(String.join("; ", blockers));
            }

            AgentCleanSlateTarget reset = port.resetGameplay(
                    id, characterId, confirmation.targetFingerprint(), nowMs);
            List<String> warnings = new ArrayList<>();
            try {
                hooks.clearAgentOsProgress(characterId);
            } catch (Exception cleanupFailure) {
                String warning = "gameplay reset succeeded, but Agent OS checkpoint cleanup failed: "
                        + cleanupFailure.getMessage();
                warnings.add(warning);
                try { port.markCleanupWarning(id, warning); } catch (Exception ignoredAuditFailure) { }
            }
            return new AgentCleanSlateResult(id, true,
                    "Agent gameplay state was reset to a level-1 beginner clean slate",
                    reset, warnings, nowMs);
        } catch (IllegalStateException failure) {
            rejectQuietly(id, failure.getMessage(), nowMs);
            throw failure;
        } catch (Exception failure) {
            rejectQuietly(id, failure.getMessage(), nowMs);
            throw failure;
        }
    }

    private List<String> blockers(AgentCleanSlateTarget target) {
        List<String> blockers = new ArrayList<>();
        if (!target.activeAgent()) blockers.add("character is not an active durable Agent");
        if (target.interactiveAllowed()) blockers.add("interactive Agent login is allowed");
        if (!target.dedicatedAccount()) blockers.add("character is not on a dedicated Agent account");
        if (!target.merchantStateClear()) blockers.add("merchant proceeds, listings, or escrow remain unsettled");
        if (hooks.online(target.characterId())) blockers.add("Agent must be fully offline");
        if (hooks.runtimeActive(target.characterId())) blockers.add("Agent runtime session is still active");
        return blockers;
    }

    private void validateConfirmation(PendingConfirmation pending,
                                      String token,
                                      String phrase,
                                      long nowMs) {
        if (nowMs < 0L || nowMs > pending.expiresAtMs()) {
            throw new IllegalStateException("reset confirmation expired; request a new preview");
        }
        String suppliedHash = digest(requireText(token, "reset confirmation token is required"));
        if (!MessageDigest.isEqual(
                pending.tokenHash().getBytes(StandardCharsets.US_ASCII),
                suppliedHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new IllegalStateException("reset confirmation token does not match");
        }
        if (!pending.phrase().equals(requireText(phrase, "reset confirmation phrase is required"))) {
            throw new IllegalStateException("reset confirmation phrase does not match");
        }
    }

    private void rejectQuietly(String resetId, String reason, long nowMs) {
        try { port.markRejected(resetId, reason, nowMs); } catch (Exception ignored) { }
    }

    private String token() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String requireText(String value, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(message);
        return normalized;
    }

    private record PendingConfirmation(
            int characterId,
            String tokenHash,
            String phrase,
            String targetFingerprint,
            long expiresAtMs) { }
}
