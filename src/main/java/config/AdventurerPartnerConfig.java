package config;

import constants.skills.Beginner;
import constants.skills.Bishop;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Configuration for the disabled-by-default Adventurer Partner Program. */
public class AdventurerPartnerConfig {
    public boolean ENABLED = false;
    public int NPC_ID = 9000036;
    public boolean SOLO_TAG_ENABLED = true;
    public boolean DOUBLE_PARTNER_ENABLED = true;
    public long SWITCH_COOLDOWN_MS = 5_000L;
    public boolean SAME_MAP_REQUIRED = true;
    public long DOUBLE_PARTNER_READY_DELAY_MS = 0L;
    public long MEDAL_DAMAGE_DELAY_MS = 600L;
    public int SWITCH_EFFECT_ID = 8;
    public boolean SWITCH_EFFECT_BROADCAST = false;
    public boolean SWITCH_TRIGGER_EFFECT_ENABLED = false;
    public GenesisVisualProxyConfig GENESIS_VISUAL_PROXY = new GenesisVisualProxyConfig();
    public List<Integer> TRIGGER_SKILL_IDS = new ArrayList<>(List.of(
            Beginner.NIMBLE_FEET,
            Noblesse.NIMBLE_FEET,
            Legend.AGILE_BODY,
            Evan.NIMBLE_FEET));
    public boolean RESTORE_CANONICAL_ON_DISCONNECT = true;
    public boolean APPLY_ORDINARY_TRIGGER_BUFF = false;
    public List<PartnerMedalEffectConfig> MEDAL_EFFECTS = new ArrayList<>();

    private static final Set<String> SUPPORTED_MEDAL_EFFECTS = Set.of(
            "SWITCH_SKILL",
            "SELF_BUFF_BOND",
            "MESO_FAME_BONUS",
            "ETC_FAME_EXTRA_ROLL",
            "DROP_RATE_BONUS",
            "EXP_BONUS",
            "REGULAR_MOB_BONUS_DAMAGE");

    public void validate() {
        if (NPC_ID <= 0) {
            throw new IllegalStateException("adventurerPartner.NPC_ID must be positive");
        }
        if (SWITCH_COOLDOWN_MS < 0L) {
            throw new IllegalStateException("adventurerPartner.SWITCH_COOLDOWN_MS cannot be negative");
        }
        if (DOUBLE_PARTNER_READY_DELAY_MS < 0L || DOUBLE_PARTNER_READY_DELAY_MS > 10_000L) {
            throw new IllegalStateException(
                    "adventurerPartner.DOUBLE_PARTNER_READY_DELAY_MS must be between 0 and 10000");
        }
        if (MEDAL_DAMAGE_DELAY_MS < 0L || MEDAL_DAMAGE_DELAY_MS > 10_000L) {
            throw new IllegalStateException(
                    "adventurerPartner.MEDAL_DAMAGE_DELAY_MS must be between 0 and 10000");
        }
        if (SWITCH_EFFECT_ID < -1 || SWITCH_EFFECT_ID > 255) {
            throw new IllegalStateException(
                    "adventurerPartner.SWITCH_EFFECT_ID must be -1 or a byte-sized effect ID");
        }
        if (SWITCH_EFFECT_ID == 10) {
            throw new IllegalStateException(
                    "adventurerPartner.SWITCH_EFFECT_ID 10 is incompatible with the supported v83 client");
        }
        if (GENESIS_VISUAL_PROXY == null) {
            throw new IllegalStateException("adventurerPartner.GENESIS_VISUAL_PROXY cannot be null");
        }
        GENESIS_VISUAL_PROXY.validate();
        if (TRIGGER_SKILL_IDS == null || TRIGGER_SKILL_IDS.isEmpty()
                || TRIGGER_SKILL_IDS.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("adventurerPartner.TRIGGER_SKILL_IDS must contain positive skill IDs");
        }
        validateMedalEffects();
        if (ENABLED && !RESTORE_CANONICAL_ON_DISCONNECT) {
            throw new IllegalStateException(
                    "Adventurer Partner requires RESTORE_CANONICAL_ON_DISCONNECT=true to preserve canonical ownership");
        }
        if (ENABLED && !SOLO_TAG_ENABLED && !DOUBLE_PARTNER_ENABLED) {
            throw new IllegalStateException(
                    "Adventurer Partner requires at least one enabled mode");
        }
    }

    private void validateMedalEffects() {
        if (MEDAL_EFFECTS == null) {
            throw new IllegalStateException("adventurerPartner.MEDAL_EFFECTS cannot be null");
        }
        for (int effectIndex = 0; effectIndex < MEDAL_EFFECTS.size(); effectIndex++) {
            PartnerMedalEffectConfig effect = MEDAL_EFFECTS.get(effectIndex);
            String path = "adventurerPartner.MEDAL_EFFECTS[" + effectIndex + "]";
            if (effect == null || effect.ITEM_ID <= 0) {
                throw new IllegalStateException(path + ".ITEM_ID must be positive");
            }
            if (!SUPPORTED_MEDAL_EFFECTS.contains(effect.EFFECT)) {
                throw new IllegalStateException(path + ".EFFECT is unsupported: " + effect.EFFECT);
            }
            if (!effect.SOLO_TAG_ENABLED && !effect.DOUBLE_PARTNER_ENABLED) {
                throw new IllegalStateException(path + " must enable at least one Partner mode");
            }
            if (effect.LEVELS == null || effect.LEVELS.isEmpty()) {
                throw new IllegalStateException(path + ".LEVELS must contain at least one level");
            }
            for (int levelIndex = 0; levelIndex < effect.LEVELS.size(); levelIndex++) {
                validateMedalEffectLevel(effect, effect.LEVELS.get(levelIndex),
                        path + ".LEVELS[" + levelIndex + "]");
            }
        }
    }

    private static void validateMedalEffectLevel(PartnerMedalEffectConfig effect,
                                                  PartnerMedalEffectLevelConfig level,
                                                  String path) {
        if (level == null || level.CONDITIONS == null) {
            throw new IllegalStateException(path + ".CONDITIONS cannot be null");
        }
        PartnerMedalEffectConditionConfig conditions = level.CONDITIONS;
        if (conditions.MIN_PARTNER_LEVEL < 0
                || conditions.MIN_PARTNER_LEVEL > conditions.MAX_PARTNER_LEVEL
                || conditions.MIN_CHARACTER_LEVEL < 0
                || conditions.MIN_CHARACTER_LEVEL > conditions.MAX_CHARACTER_LEVEL
                || conditions.MIN_FAME > conditions.MAX_FAME) {
            throw new IllegalStateException(path + " contains invalid condition bounds");
        }
        if (level.COOLDOWN_MS < 0L || level.PERCENT < 0.0 || level.MAX_PERCENT < 0.0
                || level.FAME_PER_PERCENT < 0 || level.SKILL_LEVEL < 0
                || level.MAX_SKILL_LEVEL < 0) {
            throw new IllegalStateException(path + " contains a negative effect value");
        }
        switch (effect.EFFECT) {
            case "SWITCH_SKILL" -> {
                if (level.SKILL_ID <= 0 || level.SKILL_LEVEL <= 0) {
                    throw new IllegalStateException(path + " requires positive SKILL_ID and SKILL_LEVEL");
                }
                if (level.SKILL_ID != Bishop.GENESIS) {
                    throw new IllegalStateException(path + " supports Genesis as the switch skill in v83");
                }
            }
            case "SELF_BUFF_BOND" -> {
                if (level.MAX_SKILL_LEVEL <= 0) {
                    throw new IllegalStateException(path + " requires a positive MAX_SKILL_LEVEL");
                }
            }
            case "MESO_FAME_BONUS", "ETC_FAME_EXTRA_ROLL" -> {
                if (level.FAME_PER_PERCENT <= 0 || level.MAX_PERCENT <= 0.0) {
                    throw new IllegalStateException(
                            path + " requires positive FAME_PER_PERCENT and MAX_PERCENT");
                }
            }
            case "DROP_RATE_BONUS", "EXP_BONUS", "REGULAR_MOB_BONUS_DAMAGE" -> {
                if (level.PERCENT <= 0.0) {
                    throw new IllegalStateException(path + " requires a positive PERCENT");
                }
            }
            default -> throw new IllegalStateException(path + " has an unsupported effect");
        }
    }
}
