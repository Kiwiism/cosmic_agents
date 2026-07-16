package server.agents.plans.mapleisland.cohort;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Account;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

/** Creates only dedicated, interactively locked accounts and fills their existing character slots. */
public final class MapleIslandCohortPoolProvisioner {
    public static final int MAX_CHARACTERS_PER_ACCOUNT = 15;

    public record AccountCapacity(int characterSlots, int characterCount) {
        public AccountCapacity {
            if (characterSlots < 0 || characterCount < 0) {
                throw new IllegalArgumentException("Account capacity values must be nonnegative");
            }
        }
    }

    public record CreatedAccount(int accountId, String accountName, int characterSlots) {
        public CreatedAccount {
            if (accountId <= 0 || accountName == null || accountName.isBlank() || characterSlots <= 0) {
                throw new IllegalArgumentException("Invalid created pool account");
            }
        }
    }

    public interface Hooks {
        boolean accountNameExists(String accountName) throws Exception;

        CreatedAccount createDedicatedAccount(String accountName) throws Exception;

        boolean isDedicatedAgentAccount(int accountId) throws Exception;

        AccountCapacity ensureDedicatedAccountCapacity(int accountId) throws Exception;

        AccountCapacity accountCapacity(int accountId) throws Exception;

        boolean characterNameExists(String characterName) throws Exception;

        int createCharacter(int accountId,
                            String accountName,
                            String characterName,
                            int world,
                            int channel) throws Exception;
    }

    private final MapleIslandCohortPoolRegistry registry;
    private final Hooks hooks;

    public MapleIslandCohortPoolProvisioner(MapleIslandCohortPoolRegistry registry, Hooks hooks) {
        this.registry = registry;
        this.hooks = hooks;
    }

    public synchronized int ensureLeaseCandidates(int desired,
                                                  int createdByCharacterId,
                                                  int world,
                                                  int channel,
                                                  Set<Integer> excludedCharacterIds,
                                                  IntPredicate isCharacterLive) throws Exception {
        if (desired < 0 || desired > MapleIslandCohortNameCatalog.CANDIDATE_COUNT) {
            throw new IllegalArgumentException("Desired pool capacity is out of range");
        }
        int created = 0;
        while (candidateCount(world, excludedCharacterIds, isCharacterLive) < desired) {
            Account account = reusableAccount();
            if (account == null) {
                account = createAccount(createdByCharacterId);
            }
            String characterName = nextCharacterName();
            int characterId = hooks.createCharacter(
                    account.accountId(), account.accountName(), characterName, world, channel);
            if (characterId <= 0) {
                throw new IOException("Failed to create pooled character '" + characterName + "'");
            }
            registry.addAgent(Agent.available(
                    characterId, characterName, account, createdByCharacterId, world));
            created++;
        }
        return created;
    }

    private int candidateCount(int world,
                               Set<Integer> excludedCharacterIds,
                               IntPredicate isCharacterLive) {
        Set<Integer> excluded = excludedCharacterIds == null ? Set.of() : excludedCharacterIds;
        return (int) registry.snapshot().agents().stream()
                .filter(agent -> agent.leaseState() == MapleIslandCohortPoolSnapshot.LeaseState.AVAILABLE)
                .filter(agent -> agent.world() == world)
                .filter(agent -> !excluded.contains(agent.characterId()))
                .filter(agent -> !isCharacterLive.test(agent.characterId()))
                .count();
    }

    private Account reusableAccount() throws Exception {
        for (Account account : registry.snapshot().accounts()) {
            if (!hooks.isDedicatedAgentAccount(account.accountId())) {
                continue;
            }
            AccountCapacity capacity = hooks.ensureDedicatedAccountCapacity(account.accountId());
            int usableSlots = Math.min(MAX_CHARACTERS_PER_ACCOUNT, capacity.characterSlots());
            registry.updateAccountSlots(account.accountId(), usableSlots);
            if (capacity.characterCount() < usableSlots) {
                return account;
            }
        }
        return null;
    }

    private Account createAccount(int createdByCharacterId) throws Exception {
        Set<String> recordedNames = new HashSet<>();
        registry.snapshot().accounts().forEach(account ->
                recordedNames.add(account.accountName().toLowerCase(java.util.Locale.ROOT)));
        for (int ordinal = 1; ordinal <= MapleIslandCohortNameCatalog.CANDIDATE_COUNT; ordinal++) {
            String accountName = MapleIslandCohortNameCatalog.accountCandidate(ordinal);
            if (recordedNames.contains(accountName.toLowerCase(java.util.Locale.ROOT))
                    || hooks.accountNameExists(accountName)) {
                continue;
            }
            CreatedAccount created = hooks.createDedicatedAccount(accountName);
            Account account = new Account(created.accountId(), created.accountName(), createdByCharacterId,
                    Math.min(MAX_CHARACTERS_PER_ACCOUNT, created.characterSlots()), System.currentTimeMillis());
            registry.addAccount(account);
            return account;
        }
        throw new IOException("No unused Maple Island cohort account names remain");
    }

    private String nextCharacterName() throws Exception {
        Set<String> recordedNames = new HashSet<>();
        registry.snapshot().agents().forEach(agent ->
                recordedNames.add(agent.name().toLowerCase(java.util.Locale.ROOT)));
        for (int index = 0; index < MapleIslandCohortNameCatalog.CANDIDATE_COUNT; index++) {
            String candidate = MapleIslandCohortNameCatalog.candidate(index);
            if (recordedNames.contains(candidate.toLowerCase(java.util.Locale.ROOT))
                    || hooks.characterNameExists(candidate)) {
                continue;
            }
            return candidate;
        }
        throw new IOException("No unused Maple Island cohort character names remain");
    }
}
