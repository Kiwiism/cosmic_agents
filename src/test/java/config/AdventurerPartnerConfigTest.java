package config;

import com.esotericsoftware.yamlbeans.YamlReader;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdventurerPartnerConfigTest {
    @Test
    void disabledDefaultsAreSafeAndValid() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();

        assertEquals(600L, config.MEDAL_DAMAGE_DELAY_MS);
        assertEquals("Seraph", config.GENESIS_VISUAL_PROXY.NAME);
        assertEquals(4, config.GENESIS_VISUAL_PROXY.STANCE);
        assertFalse(config.GENESIS_VISUAL_PROXY.DARKSIGHT_ENABLED);
        assertEquals(1002333, config.GENESIS_VISUAL_PROXY.VISIBLE_EQUIPS.get("1"));
        assertDoesNotThrow(config::validate);
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
                    MEDAL_DAMAGE_DELAY_MS: 654
                    SWITCH_EFFECT_ID: 11
                    SWITCH_EFFECT_BROADCAST: true
                    SWITCH_TRIGGER_EFFECT_ENABLED: true
                    GENESIS_VISUAL_PROXY:
                        NAME: Cherub
                        GENDER: 0
                        SKIN_COLOR_ID: 3
                        FACE_ID: 20000
                        HAIR_ID: 30000
                        VISIBLE_EQUIPS:
                            1: 1002000
                            11: 1372000
                        CASH_WEAPON_ID: 0
                        STANCE: 5
                        DARKSIGHT_ENABLED: true
                        TRANSFORM_EFFECT_PATH: Effect/BasicEff.img/Transform/custom
                        ATTACK_DELAY_MS: 250
                        LIFETIME_MS: 4200
                        EXIT_EFFECT_LEAD_MS: 0
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
        assertEquals(654L, config.MEDAL_DAMAGE_DELAY_MS);
        assertEquals(11, config.SWITCH_EFFECT_ID);
        assertTrue(config.SWITCH_EFFECT_BROADCAST);
        assertTrue(config.SWITCH_TRIGGER_EFFECT_ENABLED);
        GenesisVisualProxyConfig proxy = config.GENESIS_VISUAL_PROXY;
        assertEquals("Cherub", proxy.NAME);
        assertEquals(0, proxy.GENDER);
        assertEquals(3, proxy.SKIN_COLOR_ID);
        assertEquals(20000, proxy.FACE_ID);
        assertEquals(30000, proxy.HAIR_ID);
        assertEquals(1002000, proxy.VISIBLE_EQUIPS.get("1"));
        assertEquals(1372000, proxy.VISIBLE_EQUIPS.get("11"));
        assertEquals(0, proxy.CASH_WEAPON_ID);
        assertEquals(5, proxy.STANCE);
        assertTrue(proxy.DARKSIGHT_ENABLED);
        assertEquals("Effect/BasicEff.img/Transform/custom", proxy.TRANSFORM_EFFECT_PATH);
        assertEquals(250L, proxy.ATTACK_DELAY_MS);
        assertEquals(4200L, proxy.LIFETIME_MS);
        assertEquals(0L, proxy.EXIT_EFFECT_LEAD_MS);
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

    @Test
    void genesisVisualProxyRejectsUnsafeAppearanceAndTiming() {
        AdventurerPartnerConfig config = new AdventurerPartnerConfig();
        GenesisVisualProxyConfig proxy = config.GENESIS_VISUAL_PROXY;

        proxy.NAME = "1234567890123";
        assertThrows(IllegalStateException.class, config::validate);
        proxy.NAME = "Seraph";

        proxy.SKIN_COLOR_ID = 8;
        assertThrows(IllegalStateException.class, config::validate);
        proxy.SKIN_COLOR_ID = 0;

        proxy.VISIBLE_EQUIPS = new LinkedHashMap<>();
        proxy.VISIBLE_EQUIPS.put("0", 1002333);
        assertThrows(IllegalStateException.class, config::validate);
        proxy.VISIBLE_EQUIPS.clear();
        proxy.VISIBLE_EQUIPS.put("1", -1);
        assertThrows(IllegalStateException.class, config::validate);
        proxy.VISIBLE_EQUIPS.clear();
        proxy.VISIBLE_EQUIPS.put("1", 1002333);

        proxy.STANCE = 2;
        assertThrows(IllegalStateException.class, config::validate);
        proxy.STANCE = 4;

        proxy.TRANSFORM_EFFECT_PATH = "Effect/../unsafe";
        assertThrows(IllegalStateException.class, config::validate);
        proxy.TRANSFORM_EFFECT_PATH = "Effect/BasicEff.img/Transform";

        proxy.ATTACK_DELAY_MS = 2_001L;
        assertThrows(IllegalStateException.class, config::validate);
        proxy.ATTACK_DELAY_MS = 700L;

        proxy.LIFETIME_MS = 2_499L;
        assertThrows(IllegalStateException.class, config::validate);
        proxy.LIFETIME_MS = 5_000L;

        proxy.EXIT_EFFECT_LEAD_MS = 2_001L;
        assertThrows(IllegalStateException.class, config::validate);
        proxy.EXIT_EFFECT_LEAD_MS = 0L;
        assertDoesNotThrow(config::validate);
    }
}
