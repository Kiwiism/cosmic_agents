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
    void checkedInYamlPartnerConfigurationIsValid() {
        assertDoesNotThrow(YamlConfig.config.adventurerPartner::validate);
    }

    @Test
    void enabledProgramCannotDisableCanonicalDisconnectRecovery() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.ENABLED = true;
        config.RESTORE_CANONICAL_ON_DISCONNECT = false;

        assertThrows(IllegalStateException.class, config::validate);
    }

    @Test
    void medalEffectsRequireSupportedTypesItemsAndLevels() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        PartnerMedalEffectConfig effect = new PartnerMedalEffectConfig();
        effect.EFFECT = "SELF_BUFF_BOND";
        effect.LEVELS.add(new PartnerMedalEffectLevelConfig());
        config.MEDAL_EFFECTS.add(effect);
        assertThrows(IllegalStateException.class, config::validate);

        effect.ITEM_ID = 1142073;
        assertThrows(IllegalStateException.class, config::validate);

        effect.LEVELS.getFirst().MAX_SKILL_LEVEL = 10;
        assertDoesNotThrow(config::validate);
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
                    DOUBLE_PARTNER_READY_DELAY_MS: 777
                    SWITCH_EFFECT_ID: 11
                    SWITCH_EFFECT_BROADCAST: true
                    SWITCH_TRIGGER_EFFECT_ENABLED: true
                    TRIGGER_SKILL_IDS: [1002, 20011002]
                    RESTORE_CANONICAL_ON_DISCONNECT: true
                    APPLY_ORDINARY_TRIGGER_BUFF: true
                    MEDAL_EFFECTS:
                        - ITEM_ID: 1142073
                          EFFECT: SELF_BUFF_BOND
                          SOLO_TAG_ENABLED: true
                          DOUBLE_PARTNER_ENABLED: false
                          LEVELS:
                              - CONDITIONS:
                                    MIN_PARTNER_LEVEL: 70
                                MAX_SKILL_LEVEL: 10
                              - CONDITIONS:
                                    MIN_PARTNER_LEVEL: 95
                                MAX_SKILL_LEVEL: 20
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
        assertEquals(777L, config.DOUBLE_PARTNER_READY_DELAY_MS);
        assertEquals(11, config.SWITCH_EFFECT_ID);
        assertTrue(config.SWITCH_EFFECT_BROADCAST);
        assertTrue(config.SWITCH_TRIGGER_EFFECT_ENABLED);
        assertEquals(List.of(1002, 20011002), config.TRIGGER_SKILL_IDS);
        assertTrue(config.RESTORE_CANONICAL_ON_DISCONNECT);
        assertTrue(config.APPLY_ORDINARY_TRIGGER_BUFF);
        assertEquals(1, config.MEDAL_EFFECTS.size());
        PartnerMedalEffectConfig bond = config.MEDAL_EFFECTS.getFirst();
        assertEquals(1142073, bond.ITEM_ID);
        assertEquals("SELF_BUFF_BOND", bond.EFFECT);
        assertTrue(bond.SOLO_TAG_ENABLED);
        assertFalse(bond.DOUBLE_PARTNER_ENABLED);
        assertEquals(70, bond.LEVELS.getFirst().CONDITIONS.MIN_PARTNER_LEVEL);
        assertEquals(20, bond.LEVELS.getLast().MAX_SKILL_LEVEL);
    }

    @Test
    void readinessDelayAndSwitchEffectAreBounded() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        config.DOUBLE_PARTNER_READY_DELAY_MS = 10_001L;
        assertThrows(IllegalStateException.class, config::validate);

        config.DOUBLE_PARTNER_READY_DELAY_MS = 0L;
        config.SWITCH_EFFECT_ID = 256;
        assertThrows(IllegalStateException.class, config::validate);

        config.SWITCH_EFFECT_ID = 10;
        assertThrows(IllegalStateException.class, config::validate);

        config.SWITCH_EFFECT_ID = -1;
        assertDoesNotThrow(config::validate);
    }
}
