package server.agents.runtime.activity.control.rollout;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorldDirectorRolloutConfigLoaderTest {
    @Test
    void checkedInConfigurationIsFailClosed() {
        assertFalse(AgentWorldDirectorRolloutConfigLoader.assisted().assistedEnabled());
        assertFalse(AgentWorldDirectorRolloutConfigLoader.autonomous().enabled());
    }

    @Test
    void parsesExplicitCohortsAndTargetsStrictly() {
        assertEquals(Set.of(27, 28),
                AgentWorldDirectorRolloutConfigLoader.agentIds("27,28"));
        assertEquals(Set.of(AgentActivityKind.QUESTING, AgentActivityKind.HUNTING),
                AgentWorldDirectorRolloutConfigLoader.kinds("questing,hunting"));
        assertThrows(IllegalStateException.class,
                () -> AgentWorldDirectorRolloutConfigLoader.agentIds("27,zero"));
    }
}
