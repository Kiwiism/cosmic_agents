package config;

import client.SkinColor;
import constants.game.CharacterStance;

import java.util.LinkedHashMap;
import java.util.Map;

/** Presentation-only appearance and timing for Genesis switch-skill proxies. */
public class GenesisVisualProxyConfig {
    public String NAME = "Seraph";
    public int GENDER = 1;
    public int SKIN_COLOR_ID = 0;
    public int FACE_ID = 21104;
    public int HAIR_ID = 31153;
    public Map<String, Integer> VISIBLE_EQUIPS = defaultVisibleEquips();
    public int CASH_WEAPON_ID = 1702185;
    public int STANCE = CharacterStance.STAND_RIGHT_STANCE;
    public boolean DARKSIGHT_ENABLED = false;
    public String TRANSFORM_EFFECT_PATH = "Effect/BasicEff.img/Transform";
    public long ATTACK_DELAY_MS = 700L;
    public long LIFETIME_MS = 5_000L;
    public long EXIT_EFFECT_LEAD_MS = 700L;

    private static Map<String, Integer> defaultVisibleEquips() {
        Map<String, Integer> equips = new LinkedHashMap<>();
        equips.put("1", 1002333);
        equips.put("5", 1051190);
        equips.put("7", 1072280);
        equips.put("9", 1102222);
        equips.put("11", 1372001);
        return equips;
    }

    /** YAMLBeans materializes mapping keys as strings, so normalize them once here. */
    public Map<Integer, Integer> resolvedVisibleEquips() {
        Map<Integer, Integer> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> equip : VISIBLE_EQUIPS.entrySet()) {
            int slot;
            try {
                slot = Integer.parseInt(equip.getKey());
            } catch (NumberFormatException | NullPointerException e) {
                throw new IllegalStateException(
                        "adventurerPartner.GENESIS_VISUAL_PROXY.VISIBLE_EQUIPS contains a non-numeric slot", e);
            }
            resolved.put(slot, equip.getValue());
        }
        return resolved;
    }

    public void validate() {
        String path = "adventurerPartner.GENESIS_VISUAL_PROXY";
        if (NAME == null || NAME.length() > 12) {
            throw new IllegalStateException(path + ".NAME must be at most 12 characters");
        }
        if (GENDER != 0 && GENDER != 1) {
            throw new IllegalStateException(path + ".GENDER must be 0 or 1");
        }
        if (SkinColor.getById(SKIN_COLOR_ID) == null) {
            throw new IllegalStateException(path + ".SKIN_COLOR_ID is unsupported");
        }
        if (FACE_ID <= 0 || HAIR_ID <= 0) {
            throw new IllegalStateException(path + ".FACE_ID and HAIR_ID must be positive");
        }
        if (VISIBLE_EQUIPS == null) {
            throw new IllegalStateException(path + ".VISIBLE_EQUIPS cannot be null");
        }
        for (Map.Entry<Integer, Integer> equip : resolvedVisibleEquips().entrySet()) {
            if (equip.getKey() < 1 || equip.getKey() > 99
                    || equip.getValue() == null || equip.getValue() <= 0) {
                throw new IllegalStateException(
                        path + ".VISIBLE_EQUIPS must map slots 1-99 to positive item IDs");
            }
        }
        if (CASH_WEAPON_ID < 0) {
            throw new IllegalStateException(path + ".CASH_WEAPON_ID cannot be negative");
        }
        if (STANCE != CharacterStance.STAND_RIGHT_STANCE
                && STANCE != CharacterStance.STAND_LEFT_STANCE) {
            throw new IllegalStateException(path + ".STANCE must be a standing stance (4 or 5)");
        }
        if (TRANSFORM_EFFECT_PATH == null || TRANSFORM_EFFECT_PATH.length() > 128
                || TRANSFORM_EFFECT_PATH.contains("..")) {
            throw new IllegalStateException(
                    path + ".TRANSFORM_EFFECT_PATH must be at most 128 characters and cannot contain '..'");
        }
        if (ATTACK_DELAY_MS < 0L || ATTACK_DELAY_MS > 2_000L) {
            throw new IllegalStateException(path + ".ATTACK_DELAY_MS must be between 0 and 2000");
        }
        if (LIFETIME_MS < 2_500L || LIFETIME_MS > 15_000L
                || LIFETIME_MS < ATTACK_DELAY_MS + 1_800L) {
            throw new IllegalStateException(
                    path + ".LIFETIME_MS must be between 2500 and 15000 and allow 1800 ms after the attack delay");
        }
        if (EXIT_EFFECT_LEAD_MS < 0L || EXIT_EFFECT_LEAD_MS > 2_000L
                || EXIT_EFFECT_LEAD_MS >= LIFETIME_MS) {
            throw new IllegalStateException(
                    path + ".EXIT_EFFECT_LEAD_MS must be 0 (disabled) or between 1 and 2000, before LIFETIME_MS");
        }
    }
}
