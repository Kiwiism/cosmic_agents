package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandCohortPoolProvisionerTest {
    @Test
    void expandsOnlyRecordedDedicatedAccountAndUsesAllFifteenSlotsBeforeAddingOne() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = new MapleIslandCohortPoolRegistry(store);
        MapleIslandCohortPoolSnapshot.Account first = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 3, 1_000L);
        registry.addAccount(first);
        FakeHooks hooks = new FakeHooks();
        hooks.accounts.put(10, new FakeAccount("MIQuest0001", 3));
        MapleIslandCohortPoolProvisioner provisioner =
                new MapleIslandCohortPoolProvisioner(registry, hooks);

        assertEquals(16, provisioner.ensureLeaseCandidates(
                16, 99, 0, 1, Set.of(), ignored -> false));

        assertEquals(2, registry.snapshot().accounts().size());
        assertEquals(16, registry.snapshot().agents().size());
        assertEquals(15, hooks.accounts.get(10).slots);
        assertEquals(15, hooks.accounts.get(10).count);
        assertTrue(registry.snapshot().accounts().stream()
                .allMatch(account -> account.characterSlots() == 15));
        assertEquals(0, provisioner.ensureLeaseCandidates(
                16, 99, 0, 1, Set.of(), ignored -> false));
    }

    @Test
    void neverUsesMoreThanFifteenSlotsEvenIfDatabaseReportsMore() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = new MapleIslandCohortPoolRegistry(store);
        registry.addAccount(new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 20, 1_000L));
        FakeHooks hooks = new FakeHooks();
        hooks.preserveExistingSlotCount = true;
        hooks.accounts.put(10, new FakeAccount("MIQuest0001", 20));
        MapleIslandCohortPoolProvisioner provisioner =
                new MapleIslandCohortPoolProvisioner(registry, hooks);

        provisioner.ensureLeaseCandidates(16, 99, 0, 1, Set.of(), ignored -> false);

        assertEquals(15, hooks.accounts.get(10).count);
        assertEquals(15, registry.snapshot().accounts().stream()
                .filter(account -> account.accountId() == 10)
                .findFirst().orElseThrow().characterSlots());
        assertEquals(2, registry.snapshot().accounts().size());
    }

    private static final class FakeHooks implements MapleIslandCohortPoolProvisioner.Hooks {
        private final Map<Integer, FakeAccount> accounts = new HashMap<>();
        private int nextAccountId = 11;
        private int nextCharacterId = 100;
        private boolean preserveExistingSlotCount;

        @Override
        public boolean accountNameExists(String accountName) {
            return accounts.values().stream().anyMatch(account -> account.name.equalsIgnoreCase(accountName));
        }

        @Override
        public MapleIslandCohortPoolProvisioner.CreatedAccount createDedicatedAccount(String accountName) {
            int id = nextAccountId++;
            accounts.put(id, new FakeAccount(accountName, 15));
            return new MapleIslandCohortPoolProvisioner.CreatedAccount(id, accountName, 15);
        }

        @Override
        public boolean isDedicatedAgentAccount(int accountId) {
            return accounts.containsKey(accountId);
        }

        @Override
        public MapleIslandCohortPoolProvisioner.AccountCapacity ensureDedicatedAccountCapacity(int accountId) {
            FakeAccount account = accounts.get(accountId);
            if (!preserveExistingSlotCount) {
                account.slots = 15;
            }
            return new MapleIslandCohortPoolProvisioner.AccountCapacity(account.slots, account.count);
        }

        @Override
        public MapleIslandCohortPoolProvisioner.AccountCapacity accountCapacity(int accountId) {
            FakeAccount account = accounts.get(accountId);
            return new MapleIslandCohortPoolProvisioner.AccountCapacity(account.slots, account.count);
        }

        @Override
        public boolean characterNameExists(String characterName) {
            return false;
        }

        @Override
        public int createCharacter(int accountId,
                                   String accountName,
                                   String characterName,
                                   int world,
                                   int channel) {
            FakeAccount account = accounts.get(accountId);
            if (account.count >= account.slots) {
                throw new AssertionError("Provisioner overfilled account " + accountName);
            }
            account.count++;
            return nextCharacterId++;
        }
    }

    private static final class FakeAccount {
        private final String name;
        private int slots;
        private int count;

        private FakeAccount(String name, int slots) {
            this.name = name;
            this.slots = slots;
        }
    }

    private static final class MemoryStore implements MapleIslandCohortPoolStore {
        private MapleIslandCohortPoolSnapshot snapshot = MapleIslandCohortPoolSnapshot.EMPTY;

        @Override
        public MapleIslandCohortPoolSnapshot load() {
            return snapshot;
        }

        @Override
        public void save(MapleIslandCohortPoolSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }
}
