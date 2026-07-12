package config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdventurerPartnerConfigTest {
    @Test
    void disabledDefaultsAreSafeAndValid() {
        assertDoesNotThrow(() -> new AdventurerPartnerConfig().validate());
    }

    @Test
    void enabledProgramCannotDisableCanonicalDisconnectRecovery() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.enabled = true;
        config.restoreCanonicalOnDisconnect = false;

        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void buffSharingItemAndPriceMustBeValidEvenWhileFeatureIsOff() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.soloTagBuffSharingItemId = 0;
        assertThrows(IllegalStateException.class, config::validate);

        config.soloTagBuffSharingItemId = 1142073;
        config.soloTagBuffSharingPriceMesos = -1;
        assertThrows(IllegalStateException.class, config::validate);
    }
}
