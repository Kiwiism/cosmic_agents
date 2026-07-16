package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapleIslandCohortPoolRegistryTest {
    @Test
    void stopReleasesHealthyLeaseButKeepsBrokenAgentQuarantined() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store);

        List<MapleIslandCohortPoolSnapshot.Agent> leased = registry.leaseAvailable(
                2, "session-a", 99, 2_000L, 0, Set.of(), ignored -> false);
        registry.markActive(leased.get(0).characterId(), "session-a", 3_000L);
        registry.markBroken(leased.get(1).characterId(), "session-a", "start failed");

        assertEquals(1, registry.releaseSession("session-a", ignored -> false));
        assertEquals(1, registry.stats(ignored -> false).available());
        assertEquals(1, registry.stats(ignored -> false).broken());
    }

    @Test
    void restartProtectsLiveCharacterAndKeepsBrokenAgentQuarantined() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store);
        List<MapleIslandCohortPoolSnapshot.Agent> leased = registry.leaseAvailable(
                2, "old-session", 99, 2_000L, 0, Set.of(), ignored -> false);
        int liveId = leased.get(0).characterId();
        registry.markActive(liveId, "old-session", 3_000L);
        registry.markBroken(leased.get(1).characterId(), "old-session", "crashed");

        MapleIslandCohortPoolRegistry restarted = new MapleIslandCohortPoolRegistry(store);
        assertEquals(0, restarted.recoverStaleLeases(Set.of(), id -> id == liveId));
        assertEquals(MapleIslandCohortPoolSnapshot.LeaseState.ACTIVE,
                restarted.snapshot().agents().stream()
                        .filter(agent -> agent.characterId() == liveId)
                        .findFirst().orElseThrow().leaseState());
        assertEquals(0, restarted.stats(id -> id == liveId).available());
        assertEquals(1, restarted.stats(id -> id == liveId).broken());
    }

    @Test
    void leaseSelectionNeverCrossesWorlds() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store);
        MapleIslandCohortPoolSnapshot.Account account = registry.snapshot().accounts().get(0);
        registry.addAgent(MapleIslandCohortPoolSnapshot.Agent.available(
                22, "GoldSnail", account, 99, 1));

        List<MapleIslandCohortPoolSnapshot.Agent> worldOne = registry.leaseAvailable(
                2, "world-one", 99, 2_000L, 1, Set.of(), ignored -> false);

        assertEquals(1, worldOne.size());
        assertEquals(1, worldOne.get(0).world());
    }

    @Test
    void rejectsPoolCharacterWhoseAccountNameDoesNotMatchAccountId() {
        MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 15, 1_000L);
        MapleIslandCohortPoolSnapshot.Agent mismatched = new MapleIslandCohortPoolSnapshot.Agent(
                20, "BlueSnail", 10, "MIQuest9999", 99, 0, 0,
                MapleIslandCohortPoolSnapshot.LeaseState.AVAILABLE,
                "", 0, 0L, "", 0L, "");
        MemoryStore store = new MemoryStore();
        store.snapshot = new MapleIslandCohortPoolSnapshot(
                MapleIslandCohortPoolSnapshot.CURRENT_SCHEMA_VERSION, 1L,
                List.of(account), List.of(mismatched));

        assertThrows(IOException.class, () -> new MapleIslandCohortPoolRegistry(store));
    }

    @Test
    void rejectsPartialLeaseMetadata() {
        MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 15, 1_000L);

        assertThrows(IllegalArgumentException.class, () ->
                new MapleIslandCohortPoolSnapshot.Agent(
                        20, "BlueSnail", 10, "MIQuest0001", 99, 0, 0,
                        MapleIslandCohortPoolSnapshot.LeaseState.LEASED,
                        "session", 0, 0L, "", 0L, ""));
    }

    private static MapleIslandCohortPoolRegistry populated(MemoryStore store) throws Exception {
        MapleIslandCohortPoolRegistry registry = new MapleIslandCohortPoolRegistry(store);
        MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 15, 1_000L);
        registry.addAccount(account);
        registry.addAgent(MapleIslandCohortPoolSnapshot.Agent.available(
                20, "BlueSnail", account, 99, 0));
        registry.addAgent(MapleIslandCohortPoolSnapshot.Agent.available(
                21, "RedSnail", account, 99, 0));
        return registry;
    }

    private static final class MemoryStore implements MapleIslandCohortPoolStore {
        private MapleIslandCohortPoolSnapshot snapshot = MapleIslandCohortPoolSnapshot.EMPTY;

        @Override
        public MapleIslandCohortPoolSnapshot load() {
            return snapshot;
        }

        @Override
        public void save(MapleIslandCohortPoolSnapshot snapshot) throws IOException {
            this.snapshot = snapshot;
        }
    }
}
