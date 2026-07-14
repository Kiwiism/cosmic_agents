package server.agents.integration.live;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.population.AgentPopulationRecord;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSoakRosterProvisioningTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesGuardedOptionsOutsideWorktree() {
        Path repo = tempDir.resolve("repo");
        Path output = tempDir.resolve("runtime/population.json");

        AgentSoakRosterProvisioning.Options options = AgentSoakRosterProvisioning.parse(new String[]{
                "--target=250",
                "--prefix=Sched",
                "--expected-database=cosmic_scheduler_soak_test",
                "--output=" + output,
                "--confirm=" + AgentSoakRosterProvisioning.CONFIRMATION
        }, repo);

        assertEquals(250, options.target());
        assertEquals("Sched", options.prefix());
        assertEquals(output.toAbsolutePath().normalize(), options.output());
    }

    @Test
    void rejectsNormalDatabaseAndWorktreeOutput() {
        Path repo = tempDir.resolve("repo");
        String[] normalDatabase = arguments(repo.resolveSibling("population.json"), "cosmic");
        String[] worktreeOutput = arguments(repo.resolve("population.json"), "cosmic_scheduler_soak_test");

        assertThrows(IllegalArgumentException.class,
                () -> AgentSoakRosterProvisioning.parse(normalDatabase, repo));
        assertThrows(IllegalArgumentException.class,
                () -> AgentSoakRosterProvisioning.parse(worktreeOutput, repo));
    }

    @Test
    void extractsAndPinsConfiguredDatabase() {
        String config = "server:\n  DB_URL_FORMAT: \"jdbc:mysql://%s:3306/cosmic_scheduler_soak_test?x=1\"\n";

        String database = AgentSoakRosterProvisioning.configuredDatabase(config);

        assertEquals("cosmic_scheduler_soak_test", database);
        AgentSoakRosterProvisioning.requireDisposableDatabase(database, "cosmic_scheduler_soak_test");
        assertThrows(IllegalArgumentException.class,
                () -> AgentSoakRosterProvisioning.requireDisposableDatabase(database, "cosmic_scheduler_soak_other"));
    }

    @Test
    void createsDeterministicBoundedNamesAndEnabledSnapshot() {
        assertEquals("Sched0001", AgentSoakRosterProvisioning.name("Sched", 1));
        assertEquals("Sched2000", AgentSoakRosterProvisioning.name("Sched", 2_000));

        var snapshot = AgentSoakRosterProvisioning.snapshot(List.of(
                new AgentPopulationRecord(2, "Sched0002", null),
                new AgentPopulationRecord(1, "Sched0001", null)));

        assertTrue(snapshot.enabled());
        assertEquals(1.0, snapshot.multiplier());
        assertEquals(List.of("Sched0001", "Sched0002"),
                snapshot.agents().stream().map(AgentPopulationRecord::name).toList());
    }

    private String[] arguments(Path output, String database) {
        return new String[]{
                "--target=1",
                "--expected-database=" + database,
                "--output=" + output,
                "--confirm=" + AgentSoakRosterProvisioning.CONFIRMATION
        };
    }
}
