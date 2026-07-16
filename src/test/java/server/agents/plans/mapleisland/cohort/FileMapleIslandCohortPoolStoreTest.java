package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FileMapleIslandCohortPoolStoreTest {
    @Test
    void atomicallyRoundTripsPoolIdentityAndLeaseMetadata(@TempDir Path tempDir) throws Exception {
        Path path = tempDir.resolve("nested/pool.json");
        MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                10, "MIQuest0001", 99, 15, 1_000L);
        MapleIslandCohortPoolSnapshot.Agent agent = MapleIslandCohortPoolSnapshot.Agent
                .available(20, "BlueSnail", account, 99, 0)
                .leased("mi-session", 99, 2_000L);
        MapleIslandCohortPoolSnapshot expected = new MapleIslandCohortPoolSnapshot(
                MapleIslandCohortPoolSnapshot.CURRENT_SCHEMA_VERSION, 7L,
                List.of(account), List.of(agent));

        FileMapleIslandCohortPoolStore store = new FileMapleIslandCohortPoolStore(path);
        store.save(expected);

        assertEquals(expected, store.load());
        try (var files = Files.list(path.getParent())) {
            assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(".tmp")));
        }
    }
}
