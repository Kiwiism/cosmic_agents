package server.agents.runtime.activity.control;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.world.AgentFileWorldDirectorSessionStore;
import server.agents.runtime.activity.world.AgentWorldDirectorMode;
import server.agents.runtime.activity.world.AgentWorldDirectorSession;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentWorldDirectorModeRestoreRuntimeTest {
    @TempDir Path directory;

    @Test
    void restoresObservationAndLiveAuthorityIntoSessionLocalState() {
        AgentFileWorldDirectorSessionStore sessions =
                new AgentFileWorldDirectorSessionStore(directory);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        AgentWorldDirectorModeRestoreRuntime runtime =
                new AgentWorldDirectorModeRestoreRuntime(sessions, 5_000L);
        sessions.save(AgentWorldDirectorSession.create(27, AgentWorldDirectorMode.OBSERVE, 1_000L));

        runtime.restore(entry, 27, 1_001L);
        assertEquals(AgentWorldDirectorMode.OBSERVE, entry.capabilityStates()
                .require(AgentWorldDirectorObserveState.STATE_KEY).snapshot().mode());

        AgentWorldDirectorSession manual = sessions.load(27).orElseThrow()
                .withMode(AgentWorldDirectorMode.MANUAL, "operator", 1_002L);
        sessions.save(manual);
        runtime.restore(entry, 27, 1_003L);
        assertEquals(AgentWorldDirectorMode.DISABLED, entry.capabilityStates()
                .require(AgentWorldDirectorObserveState.STATE_KEY).snapshot().mode());
        assertEquals(AgentWorldDirectorMode.MANUAL, entry.capabilityStates()
                .require(AgentWorldDirectorRuntimeState.STATE_KEY).snapshot().mode());
    }
}
