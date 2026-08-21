package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorPreparationConfigTest {
    @Test
    void defaultsPermitOnlyExplicitCommandDrivenObservation() {
        AgentWorldDirectorPreparationConfig config =
                AgentWorldDirectorPreparationConfig.defaults();

        assertTrue(config.commandDrivenShadowEnabled());
        assertFalse(config.automaticShadowSamplingEnabled());
        assertFalse(config.liveControlEnabled());
        assertThrows(IllegalArgumentException.class,
                () -> new AgentWorldDirectorPreparationConfig(true, false, true));
    }
}
