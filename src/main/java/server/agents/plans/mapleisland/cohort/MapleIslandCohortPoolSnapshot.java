package server.agents.plans.mapleisland.cohort;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Durable metadata for the reusable Maple Island test-character pool. */
public record MapleIslandCohortPoolSnapshot(
        int schemaVersion,
        long revision,
        List<Account> accounts,
        List<Agent> agents) {
    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final MapleIslandCohortPoolSnapshot EMPTY = new MapleIslandCohortPoolSnapshot(
            CURRENT_SCHEMA_VERSION, 0L, List.of(), List.of());

    public MapleIslandCohortPoolSnapshot {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Maple Island cohort pool schema: " + schemaVersion);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Pool revision must be nonnegative");
        }
        accounts = accounts == null ? List.of() : accounts.stream()
                .sorted(Comparator.comparingInt(Account::accountId))
                .toList();
        agents = agents == null ? List.of() : agents.stream()
                .sorted(Comparator.comparingInt(Agent::characterId))
                .toList();
    }

    public enum LeaseState {
        AVAILABLE,
        LEASED,
        ACTIVE,
        BROKEN
    }

    public record Account(
            int accountId,
            String accountName,
            int createdByCharacterId,
            int characterSlots,
            long createdAtMs) {
        public Account {
            if (accountId <= 0 || createdByCharacterId <= 0 || characterSlots <= 0 || createdAtMs <= 0L) {
                throw new IllegalArgumentException("Invalid cohort pool account metadata");
            }
            accountName = requireName(accountName, "accountName");
        }
    }

    public record Agent(
            int characterId,
            String name,
            int accountId,
            String accountName,
            int createdByCharacterId,
            int world,
            Integer characterTemplateOrdinal,
            LeaseState leaseState,
            String leaseSessionId,
            int leaseOwnerCharacterId,
            long leaseAcquiredAtMs,
            String lastSessionId,
            long lastResetAtMs,
            String lastError) {
        public Agent {
            if (characterId <= 0 || accountId <= 0 || createdByCharacterId <= 0 || world < 0) {
                throw new IllegalArgumentException("Invalid cohort pool Agent identity");
            }
            name = requireName(name, "name");
            accountName = requireName(accountName, "accountName");
            leaseState = Objects.requireNonNullElse(leaseState, LeaseState.AVAILABLE);
            leaseSessionId = normalize(leaseSessionId);
            lastSessionId = normalize(lastSessionId);
            lastError = normalize(lastError);
            if (characterTemplateOrdinal != null
                    && (characterTemplateOrdinal < 0
                    || characterTemplateOrdinal >= MapleIslandCohortCharacterCatalog.COMBINATION_COUNT)) {
                throw new IllegalArgumentException("Invalid cohort character-template ordinal");
            }
            if (leaseOwnerCharacterId < 0 || leaseAcquiredAtMs < 0L || lastResetAtMs < 0L) {
                throw new IllegalArgumentException("Pool Agent lease timestamps/owner must be nonnegative");
            }
            boolean hasLeaseMetadata = !leaseSessionId.isEmpty()
                    && leaseOwnerCharacterId > 0 && leaseAcquiredAtMs > 0L;
            if (leaseState == LeaseState.AVAILABLE && hasLeaseMetadata) {
                throw new IllegalArgumentException("Available pool Agent cannot retain an active lease");
            }
            if (leaseState == LeaseState.AVAILABLE
                    && (!leaseSessionId.isEmpty() || leaseOwnerCharacterId != 0 || leaseAcquiredAtMs != 0L)) {
                throw new IllegalArgumentException("Available pool Agent has partial lease metadata");
            }
            if (leaseState != LeaseState.AVAILABLE && !hasLeaseMetadata) {
                throw new IllegalArgumentException("Leased, active, or broken pool Agent needs complete lease metadata");
            }
        }

        public static Agent available(int characterId,
                                      String name,
                                      Account account,
                                      int createdByCharacterId,
                                      int world,
                                      int characterTemplateOrdinal) {
            return new Agent(characterId, name, account.accountId(), account.accountName(),
                    createdByCharacterId, world, characterTemplateOrdinal,
                    LeaseState.AVAILABLE, "", 0, 0L, "", 0L, "");
        }

        public static Agent available(int characterId,
                                      String name,
                                      Account account,
                                      int createdByCharacterId,
                                      int world) {
            return new Agent(characterId, name, account.accountId(), account.accountName(),
                    createdByCharacterId, world, null,
                    LeaseState.AVAILABLE, "", 0, 0L, "", 0L, "");
        }

        public Agent leased(String sessionId, int ownerCharacterId, long nowMs) {
            return new Agent(characterId, name, accountId, accountName, createdByCharacterId, world,
                    characterTemplateOrdinal, LeaseState.LEASED,
                    requireName(sessionId, "sessionId"), ownerCharacterId, nowMs,
                    lastSessionId, lastResetAtMs, "");
        }

        public Agent active(String sessionId, long resetAtMs) {
            return new Agent(characterId, name, accountId, accountName, createdByCharacterId, world,
                    characterTemplateOrdinal, LeaseState.ACTIVE,
                    requireName(sessionId, "sessionId"), leaseOwnerCharacterId,
                    leaseAcquiredAtMs, sessionId, resetAtMs, "");
        }

        public Agent released(String completedSessionId) {
            return new Agent(characterId, name, accountId, accountName, createdByCharacterId, world,
                    characterTemplateOrdinal, LeaseState.AVAILABLE, "", 0, 0L,
                    normalize(completedSessionId).isEmpty() ? lastSessionId : completedSessionId,
                    lastResetAtMs, "");
        }

        public Agent broken(String sessionId, String error) {
            return new Agent(characterId, name, accountId, accountName, createdByCharacterId, world,
                    characterTemplateOrdinal, LeaseState.BROKEN,
                    normalize(sessionId), leaseOwnerCharacterId, leaseAcquiredAtMs,
                    normalize(sessionId).isEmpty() ? lastSessionId : sessionId,
                    lastResetAtMs, normalize(error));
        }

        public Agent withCharacterTemplateOrdinal(int ordinal) {
            return new Agent(characterId, name, accountId, accountName, createdByCharacterId, world,
                    ordinal, leaseState, leaseSessionId, leaseOwnerCharacterId, leaseAcquiredAtMs,
                    lastSessionId, lastResetAtMs, lastError);
        }
    }

    private static String requireName(String value, String field) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
