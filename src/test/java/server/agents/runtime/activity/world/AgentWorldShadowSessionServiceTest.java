package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorldShadowSessionServiceTest {
    @TempDir
    Path directory;

    @Test
    void samplesOnlyAfterExplicitStartAndNeverOwnsActivity() {
        AgentWorldShadowSessionService service = service();
        AgentWorldContext context = AgentWorldMilestoneEvaluatorTest.context(
                18, 100, 100_000_000, "COMPLETE", false);

        assertThrows(IllegalStateException.class, () -> service.sample(context));
        service.start(context);

        AgentWorldDirectorSession session = service.session(27).orElseThrow();
        assertEquals(1L, session.observationCount());
        assertFalse(session.mayOwnActivity());
        assertEquals(1, service.recent(27, 10).size());
    }

    private AgentWorldShadowSessionService service() {
        return new AgentWorldShadowSessionService(
                new AgentFileWorldDirectorSessionStore(directory.resolve("sessions")),
                new AgentFileWorldDirectorJournalStore(directory.resolve("journal")),
                AgentWorldShadowEvaluator.baseline());
    }
}
