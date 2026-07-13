package config;

import constants.skills.Beginner;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;

import java.util.ArrayList;
import java.util.List;

/** Configuration for the disabled-by-default Adventurer Partner Program. */
public class AdventurerPartnerConfig {
    public boolean ENABLED = false;
    public int NPC_ID = 9000036;
    public boolean SOLO_TAG_ENABLED = true;
    public boolean DOUBLE_PARTNER_ENABLED = true;
    public long SWITCH_COOLDOWN_MS = 5_000L;
    public boolean SAME_MAP_REQUIRED = true;
    public boolean PUBLIC_PRESENTATION = true;
    public boolean PRESENT_JOB = true;
    public boolean PRESENT_STATS = true;
    public boolean PRESENT_SKILLS = true;
    public boolean PRESENT_KEY_BINDINGS = true;
    public boolean PRESENT_EQUIPMENT = true;
    public boolean PRESENT_INVENTORY = true;
    public boolean PRESENT_PETS = true;
    public boolean PRESENT_BUFFS = true;
    public boolean PRESENT_DISEASES = true;
    public boolean PRESENT_COOLDOWNS = true;
    public boolean PRESENT_PUBLIC_LOOK = true;
    public boolean PRESENT_SWITCH_EFFECT = true;
    public List<Integer> TRIGGER_SKILL_IDS = new ArrayList<>(List.of(
            Beginner.NIMBLE_FEET,
            Noblesse.NIMBLE_FEET,
            Legend.AGILE_BODY,
            Evan.NIMBLE_FEET));
    public boolean RESTORE_CANONICAL_ON_DISCONNECT = true;
    public boolean APPLY_ORDINARY_TRIGGER_BUFF = false;
    public boolean SOLO_TAG_BUFF_SHARING_ENABLED = false;
    public int SOLO_TAG_BUFF_SHARING_ITEM_ID = 1142073;
    public int SOLO_TAG_BUFF_SHARING_PRICE_MESOS = 10_000_000;

    public void validate() {
        if (NPC_ID <= 0) {
            throw new IllegalStateException("adventurerPartner.NPC_ID must be positive");
        }
        if (SWITCH_COOLDOWN_MS < 0L) {
            throw new IllegalStateException("adventurerPartner.SWITCH_COOLDOWN_MS cannot be negative");
        }
        if (TRIGGER_SKILL_IDS == null || TRIGGER_SKILL_IDS.isEmpty()
                || TRIGGER_SKILL_IDS.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("adventurerPartner.TRIGGER_SKILL_IDS must contain positive skill IDs");
        }
        if (SOLO_TAG_BUFF_SHARING_ITEM_ID <= 0) {
            throw new IllegalStateException(
                    "adventurerPartner.SOLO_TAG_BUFF_SHARING_ITEM_ID must be positive");
        }
        if (SOLO_TAG_BUFF_SHARING_PRICE_MESOS < 0) {
            throw new IllegalStateException(
                    "adventurerPartner.SOLO_TAG_BUFF_SHARING_PRICE_MESOS cannot be negative");
        }
        if (ENABLED && !RESTORE_CANONICAL_ON_DISCONNECT) {
            throw new IllegalStateException(
                    "Adventurer Partner requires restoreCanonicalOnDisconnect=true to preserve canonical ownership");
        }
        if (ENABLED && !SOLO_TAG_ENABLED && !DOUBLE_PARTNER_ENABLED) {
            throw new IllegalStateException(
                    "Adventurer Partner requires at least one enabled mode");
        }
    }
}
