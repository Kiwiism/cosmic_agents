package server.agents.plans.mapleisland.cohort;

import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Account;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.Agent;
import server.agents.plans.mapleisland.cohort.MapleIslandCohortPoolSnapshot.LeaseState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** Synchronized source of truth for pool identities and cross-session leases. */
public final class MapleIslandCohortPoolRegistry {
    public record Stats(int accounts, int total, int available, int leased, int active, int broken) {
    }

    private final MapleIslandCohortPoolStore store;
    private MapleIslandCohortPoolSnapshot snapshot;

    public MapleIslandCohortPoolRegistry(MapleIslandCohortPoolStore store) throws IOException {
        this.store = store;
        this.snapshot = store.load();
        validateUnique(snapshot);
    }

    public synchronized MapleIslandCohortPoolSnapshot snapshot() {
        return snapshot;
    }

    public synchronized Stats stats(IntPredicate isCharacterLive) {
        int available = 0;
        int leased = 0;
        int active = 0;
        int broken = 0;
        for (Agent agent : snapshot.agents()) {
            switch (agent.leaseState()) {
                case AVAILABLE -> {
                    if (!isCharacterLive.test(agent.characterId())) {
                        available++;
                    } else {
                        active++;
                    }
                }
                case LEASED -> leased++;
                case ACTIVE -> active++;
                case BROKEN -> broken++;
            }
        }
        return new Stats(snapshot.accounts().size(), snapshot.agents().size(),
                available, leased, active, broken);
    }

    public synchronized void addAccount(Account account) throws IOException {
        if (snapshot.accounts().stream().anyMatch(existing ->
                existing.accountId() == account.accountId()
                        || existing.accountName().equalsIgnoreCase(account.accountName()))) {
            return;
        }
        List<Account> accounts = new ArrayList<>(snapshot.accounts());
        accounts.add(account);
        persist(accounts, snapshot.agents());
    }

    public synchronized void addAgent(Agent agent) throws IOException {
        if (snapshot.agents().stream().anyMatch(existing ->
                existing.characterId() == agent.characterId()
                        || existing.name().equalsIgnoreCase(agent.name()))) {
            return;
        }
        boolean knownAccount = snapshot.accounts().stream().anyMatch(account ->
                account.accountId() == agent.accountId()
                        && account.accountName().equalsIgnoreCase(agent.accountName()));
        if (!knownAccount) {
            throw new IOException("Pool Agent references an unknown account: " + agent.accountName());
        }
        List<Agent> agents = new ArrayList<>(snapshot.agents());
        agents.add(agent);
        persist(snapshot.accounts(), agents);
    }

    public synchronized void updateAccountSlots(int accountId, int characterSlots) throws IOException {
        if (characterSlots <= 0) {
            throw new IllegalArgumentException("characterSlots must be positive");
        }
        boolean[] changed = {false};
        List<Account> accounts = snapshot.accounts().stream().map(account -> {
            if (account.accountId() != accountId || account.characterSlots() == characterSlots) {
                return account;
            }
            changed[0] = true;
            return new Account(account.accountId(), account.accountName(), account.createdByCharacterId(),
                    characterSlots, account.createdAtMs());
        }).toList();
        if (changed[0]) {
            persist(accounts, snapshot.agents());
        }
    }

    public synchronized List<Agent> leaseAvailable(int count,
                                                   String sessionId,
                                                   int ownerCharacterId,
                                                   long nowMs,
                                                   int world,
                                                   Set<Integer> excludedCharacterIds,
                                                   IntPredicate isCharacterLive) throws IOException {
        if (count <= 0) {
            return List.of();
        }
        Set<Integer> exclusions = excludedCharacterIds == null ? Set.of() : excludedCharacterIds;
        List<Agent> selected = snapshot.agents().stream()
                .filter(agent -> agent.leaseState() == LeaseState.AVAILABLE)
                .filter(agent -> agent.world() == world)
                .filter(agent -> !exclusions.contains(agent.characterId()))
                .filter(agent -> !isCharacterLive.test(agent.characterId()))
                .limit(count)
                .toList();
        if (selected.isEmpty()) {
            return List.of();
        }
        Set<Integer> selectedIds = selected.stream().map(Agent::characterId).collect(java.util.stream.Collectors.toSet());
        List<Agent> updated = snapshot.agents().stream()
                .map(agent -> selectedIds.contains(agent.characterId())
                        ? agent.leased(sessionId, ownerCharacterId, nowMs)
                        : agent)
                .toList();
        persist(snapshot.accounts(), updated);
        return snapshot.agents().stream()
                .filter(agent -> selectedIds.contains(agent.characterId()))
                .toList();
    }

    public synchronized void markActive(int characterId, String sessionId, long resetAtMs) throws IOException {
        updateLeased(characterId, sessionId, agent -> agent.active(sessionId, resetAtMs));
    }

    public synchronized void markBroken(int characterId, String sessionId, String error) throws IOException {
        updateLeased(characterId, sessionId, agent -> agent.broken(sessionId, error));
    }

    public synchronized int releaseSession(String sessionId, IntPredicate isCharacterLive) throws IOException {
        int[] released = {0};
        List<Agent> updated = snapshot.agents().stream().map(agent -> {
            if (!agent.leaseSessionId().equals(sessionId)
                    || agent.leaseState() == LeaseState.BROKEN
                    || isCharacterLive.test(agent.characterId())) {
                return agent;
            }
            released[0]++;
            return agent.released(sessionId);
        }).toList();
        if (released[0] > 0) {
            persist(snapshot.accounts(), updated);
        }
        return released[0];
    }

    public synchronized int recoverStaleLeases(Set<String> activeSessionIds,
                                               IntPredicate isCharacterLive) throws IOException {
        Set<String> activeSessions = activeSessionIds == null ? Set.of() : Set.copyOf(activeSessionIds);
        int[] recovered = {0};
        List<Agent> updated = snapshot.agents().stream().map(agent -> {
            if (agent.leaseState() == LeaseState.AVAILABLE
                    || agent.leaseState() == LeaseState.BROKEN
                    || activeSessions.contains(agent.leaseSessionId())
                    || isCharacterLive.test(agent.characterId())) {
                return agent;
            }
            recovered[0]++;
            return agent.released(agent.leaseSessionId());
        }).toList();
        if (recovered[0] > 0) {
            persist(snapshot.accounts(), updated);
        }
        return recovered[0];
    }

    private void updateLeased(int characterId,
                              String sessionId,
                              java.util.function.UnaryOperator<Agent> update) throws IOException {
        boolean[] found = {false};
        List<Agent> agents = snapshot.agents().stream().map(agent -> {
            if (agent.characterId() != characterId || !agent.leaseSessionId().equals(sessionId)) {
                return agent;
            }
            if (agent.leaseState() != LeaseState.LEASED && agent.leaseState() != LeaseState.ACTIVE) {
                throw new IllegalStateException("Pool Agent is not leased to session " + sessionId);
            }
            found[0] = true;
            return update.apply(agent);
        }).toList();
        if (!found[0]) {
            throw new IllegalStateException("No pool lease for character " + characterId + " in " + sessionId);
        }
        persist(snapshot.accounts(), agents);
    }

    private void persist(List<Account> accounts, List<Agent> agents) throws IOException {
        MapleIslandCohortPoolSnapshot next = new MapleIslandCohortPoolSnapshot(
                MapleIslandCohortPoolSnapshot.CURRENT_SCHEMA_VERSION,
                snapshot.revision() + 1L,
                accounts,
                agents);
        validateUnique(next);
        store.save(next);
        snapshot = next;
    }

    private static void validateUnique(MapleIslandCohortPoolSnapshot snapshot) throws IOException {
        Map<Integer, String> accountIds = new LinkedHashMap<>();
        Set<String> accountNames = new HashSet<>();
        for (Account account : snapshot.accounts()) {
            if (accountIds.putIfAbsent(account.accountId(), account.accountName()) != null
                    || !accountNames.add(account.accountName().toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate cohort pool account: " + account.accountName());
            }
        }
        Map<Integer, String> characterIds = new LinkedHashMap<>();
        Set<String> characterNames = new HashSet<>();
        for (Agent agent : snapshot.agents()) {
            if (characterIds.putIfAbsent(agent.characterId(), agent.name()) != null
                    || !characterNames.add(agent.name().toLowerCase(Locale.ROOT))) {
                throw new IOException("Duplicate cohort pool character: " + agent.name());
            }
            if (!accountIds.containsKey(agent.accountId())) {
                throw new IOException("Pool character references missing account: " + agent.name());
            }
            if (!accountIds.get(agent.accountId()).equalsIgnoreCase(agent.accountName())) {
                throw new IOException("Pool character account name does not match account id: " + agent.name());
            }
        }
    }
}
