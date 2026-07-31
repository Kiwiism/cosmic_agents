package server.observer;

import config.ObserverConfig;
import config.YamlConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObserverAdaptersTest {
    private boolean previousEnabled;
    private boolean previousNavGraphEnabled;
    private boolean previousAgentSignalsEnabled;

    @BeforeEach
    void enableObserverAdapters() {
        ObserverConfig observer = YamlConfig.config.server.observer;
        previousEnabled = observer.enabled;
        previousNavGraphEnabled = observer.navgraph_enabled;
        previousAgentSignalsEnabled = observer.agent_signals_enabled;
        observer.enabled = true;
        observer.navgraph_enabled = true;
        observer.agent_signals_enabled = true;
    }

    @AfterEach
    void restoreConfiguration() {
        ObserverConfig observer = YamlConfig.config.server.observer;
        observer.enabled = previousEnabled;
        observer.navgraph_enabled = previousNavGraphEnabled;
        observer.agent_signals_enabled = previousAgentSignalsEnabled;
    }

    @Test
    void discoversOptionalAgentAdapters() {
        assertTrue(ObserverAdapters.navGraph().isPresent());
        assertTrue(ObserverAdapters.interest().isPresent());
    }

    @Test
    void hidesAdaptersWhenTheirSwitchesAreOff() {
        ObserverConfig observer = YamlConfig.config.server.observer;
        observer.navgraph_enabled = false;
        observer.agent_signals_enabled = false;

        assertFalse(ObserverAdapters.navGraph().isPresent());
        assertFalse(ObserverAdapters.interest().isPresent());
    }
}
