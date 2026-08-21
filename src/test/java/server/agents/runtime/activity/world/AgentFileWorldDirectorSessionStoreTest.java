package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentFileWorldDirectorSessionStoreTest {
    @TempDir
    Path directory;

    @Test
    void roundTripsDisabledOwnershipShadowState() {
        AgentFileWorldDirectorSessionStore store =
                new AgentFileWorldDirectorSessionStore(directory);
        AgentWorldDirectorSession session = AgentWorldDirectorSession.shadow(27, 1_000L);

        store.save(session);
        AgentWorldDirectorSession restored = store.load(27).orElseThrow();

        assertEquals(session, restored);
        assertFalse(restored.mayOwnActivity());
        store.delete(27);
        assertTrueEmpty(store);
    }

    private static void assertTrueEmpty(AgentFileWorldDirectorSessionStore store) {
        assertFalse(store.load(27).isPresent());
    }
}
