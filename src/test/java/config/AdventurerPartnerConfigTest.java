package config;

import com.esotericsoftware.yamlbeans.YamlReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventurerPartnerConfigTest {
    @Test
    void disabledDefaultsAreSafeAndValid() {
        assertDoesNotThrow(() -> new AdventurerPartnerConfig().validate());
    }

    @Test
    void enabledProgramCannotDisableCanonicalDisconnectRecovery() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        config.RESTORE_CANONICAL_ON_DISCONNECT = false;

        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void buffSharingItemAndPriceMustBeValidEvenWhileFeatureIsOff() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 0;
        assertThrows(IllegalStateException.class, config::validate);

        config.SOLO_TAG_BUFF_SHARING_ITEM_ID = 1142073;
        config.SOLO_TAG_BUFF_SHARING_PRICE_MESOS = -1;
        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void uppercaseSnakeCaseYamlBindsEveryPartnerSetting() throws Exception {
        String yaml = """
                adventurerPartner:
                    ENABLED: true
                    NPC_ID: 9000042
                    SOLO_TAG_ENABLED: false
                    DOUBLE_PARTNER_ENABLED: true
                    SWITCH_COOLDOWN_MS: 321
                    SAME_MAP_REQUIRED: false
                    PUBLIC_PRESENTATION: false
                    TRIGGER_SKILL_IDS: [1002, 20011002]
                    RESTORE_CANONICAL_ON_DISCONNECT: true
                    APPLY_ORDINARY_TRIGGER_BUFF: true
                    SOLO_TAG_BUFF_SHARING_ENABLED: true
                    SOLO_TAG_BUFF_SHARING_ITEM_ID: 4000144
                    SOLO_TAG_BUFF_SHARING_PRICE_MESOS: 12345678
                """;
        YamlConfig loaded;
        try (YamlReader reader = new YamlReader(new StringReader(yaml))) {
            loaded = reader.read(YamlConfig.class);
        }
        AdventurerPartnerConfig config = loaded.adventurerPartner;

        assertTrue(config.ENABLED);
        assertEquals(9000042, config.NPC_ID);
        assertFalse(config.SOLO_TAG_ENABLED);
        assertTrue(config.DOUBLE_PARTNER_ENABLED);
        assertEquals(321L, config.SWITCH_COOLDOWN_MS);
        assertFalse(config.SAME_MAP_REQUIRED);
        assertFalse(config.PUBLIC_PRESENTATION);
        assertEquals(List.of(1002, 20011002), config.TRIGGER_SKILL_IDS);
        assertTrue(config.RESTORE_CANONICAL_ON_DISCONNECT);
        assertTrue(config.APPLY_ORDINARY_TRIGGER_BUFF);
        assertTrue(config.SOLO_TAG_BUFF_SHARING_ENABLED);
        assertEquals(4000144, config.SOLO_TAG_BUFF_SHARING_ITEM_ID);
        assertEquals(12_345_678, config.SOLO_TAG_BUFF_SHARING_PRICE_MESOS);
    }
}
