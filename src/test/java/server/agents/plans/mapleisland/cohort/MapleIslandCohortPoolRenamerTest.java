package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandCohortPoolRenamerTest {
    @Test
    void redistributesOldDepthFirstNamesAcrossTheCurrentCatalog() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(
                store, "Aeri", "LilAeri", "AeriJr", "xAerix");
        FakeHooks hooks = new FakeHooks(registry.snapshot().agents());

        MapleIslandCohortPoolRenamer.Result result = new MapleIslandCohortPoolRenamer(
                registry, hooks, ignored -> false).renameAll();

        assertEquals(4, result.total());
        assertEquals(3, result.renamed());
        assertEquals(List.of("Aeri", "Bambi", "Dori", "Jae"), registry.snapshot().agents().stream()
                .map(MapleIslandCohortPoolSnapshot.Agent::name).toList());
        assertEquals(1, hooks.renameCalls);
    }

    @Test
    void skipsCatalogNameOwnedOutsideThePool() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store, "OldOne", "OldTwo", "OldThree");
        FakeHooks hooks = new FakeHooks(registry.snapshot().agents());
        hooks.names.put("bambi", 999);

        new MapleIslandCohortPoolRenamer(registry, hooks, ignored -> false).renameAll();

        assertEquals(List.of("Aeri", "Dori", "Jae"), registry.snapshot().agents().stream()
                .map(MapleIslandCohortPoolSnapshot.Agent::name).toList());
    }

    @Test
    void refusesToRenameLeasedOrLiveCharacters() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store, "OldOne", "OldTwo");
        registry.leaseAvailable(1, "active", 99, 2_000L, 0, Set.of(), ignored -> false);
        FakeHooks hooks = new FakeHooks(registry.snapshot().agents());

        assertThrows(IllegalStateException.class,
                () -> new MapleIslandCohortPoolRenamer(registry, hooks, ignored -> false).renameAll());
        assertThrows(IllegalStateException.class,
                () -> new MapleIslandCohortPoolRenamer(
                        populated(new MemoryStore(), "OldOne"), hooks, ignored -> true).renameAll());
        assertEquals(0, hooks.renameCalls);
    }

    @Test
    void restoresDatabaseNamesWhenPoolSnapshotCannotBeSaved() throws Exception {
        MemoryStore store = new MemoryStore();
        MapleIslandCohortPoolRegistry registry = populated(store, "OldOne", "OldTwo");
        FakeHooks hooks = new FakeHooks(registry.snapshot().agents());
        store.failNextSave = true;

        assertThrows(IOException.class,
                () -> new MapleIslandCohortPoolRenamer(registry, hooks, ignored -> false).renameAll());

        assertEquals(List.of("OldOne", "OldTwo"), registry.snapshot().agents().stream()
                .map(MapleIslandCohortPoolSnapshot.Agent::name).toList());
        assertEquals(2, hooks.renameCalls);
        assertTrue(hooks.names.containsKey("oldone"));
        assertTrue(hooks.names.containsKey("oldtwo"));
    }

    private static MapleIslandCohortPoolRegistry populated(MemoryStore store, String... names)
            throws Exception {
        MapleIslandCohortPoolRegistry registry = new MapleIslandCohortPoolRegistry(store);
        MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 15, 1_000L);
        registry.addAccount(account);
        for (int index = 0; index < names.length; index++) {
            registry.addAgent(MapleIslandCohortPoolSnapshot.Agent.available(
                    20 + index, names[index], account, 99, 0));
        }
        return registry;
    }

    private static final class FakeHooks implements MapleIslandCohortPoolRenamer.Hooks {
        private final Map<String, Integer> names = new HashMap<>();
        private int renameCalls;

        private FakeHooks(List<MapleIslandCohortPoolSnapshot.Agent> agents) {
            agents.forEach(agent -> names.put(normalize(agent.name()), agent.characterId()));
        }

        @Override
        public Integer characterIdByName(String characterName) {
            return names.get(normalize(characterName));
        }

        @Override
        public void renameCharacters(List<MapleIslandCohortPoolRenamer.Rename> renames) {
            renameCalls++;
            renames.forEach(rename -> names.remove(normalize(rename.oldName())));
            renames.forEach(rename -> names.put(normalize(rename.newName()), rename.characterId()));
        }

        private static String normalize(String name) {
            return name.toLowerCase(Locale.ROOT);
        }
    }

    private static final class MemoryStore implements MapleIslandCohortPoolStore {
        private MapleIslandCohortPoolSnapshot snapshot = MapleIslandCohortPoolSnapshot.EMPTY;
        private boolean failNextSave;

        @Override
        public MapleIslandCohortPoolSnapshot load() {
            return snapshot;
        }

        @Override
        public void save(MapleIslandCohortPoolSnapshot snapshot) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("simulated save failure");
            }
            this.snapshot = snapshot;
        }
    }
}
