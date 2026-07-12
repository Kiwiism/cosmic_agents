package config;

import constants.skills.Beginner;
import constants.skills.Evan;
import constants.skills.Legend;
import constants.skills.Noblesse;

import java.util.ArrayList;
import java.util.List;

/** Configuration for the disabled-by-default Adventurer Partner Program. */
public class AdventurerPartnerConfig {
    public boolean enabled = false;
    public int npcId = 9000036;
    public boolean soloTagEnabled = true;
    public boolean doublePartnerEnabled = true;
    public long switchCooldownMs = 5_000L;
    public boolean sameMapRequired = true;
    public boolean publicPresentation = true;
    public List<Integer> triggerSkillIds = new ArrayList<>(List.of(
            Beginner.NIMBLE_FEET,
            Noblesse.NIMBLE_FEET,
            Legend.AGILE_BODY,
            Evan.NIMBLE_FEET));
    public boolean restoreCanonicalOnDisconnect = true;
    public boolean applyOrdinaryTriggerBuff = false;
    public boolean soloTagBuffSharingEnabled = false;
    public int soloTagBuffSharingItemId = 1142073;
    public int soloTagBuffSharingPriceMesos = 10_000_000;

    public void validate() {
        if (npcId <= 0) {
            throw new IllegalStateException("adventurerPartner.npcId must be positive");
        }
        if (switchCooldownMs < 0L) {
            throw new IllegalStateException("adventurerPartner.switchCooldownMs cannot be negative");
        }
        if (triggerSkillIds == null || triggerSkillIds.isEmpty()
                || triggerSkillIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalStateException("adventurerPartner.triggerSkillIds must contain positive skill IDs");
        }
        if (soloTagBuffSharingItemId <= 0) {
            throw new IllegalStateException(
                    "adventurerPartner.soloTagBuffSharingItemId must be positive");
        }
        if (soloTagBuffSharingPriceMesos < 0) {
            throw new IllegalStateException(
                    "adventurerPartner.soloTagBuffSharingPriceMesos cannot be negative");
        }
        if (enabled && !restoreCanonicalOnDisconnect) {
            throw new IllegalStateException(
                    "Adventurer Partner requires restoreCanonicalOnDisconnect=true to preserve canonical ownership");
        }
        if (enabled && !soloTagEnabled && !doublePartnerEnabled) {
            throw new IllegalStateException(
                    "Adventurer Partner requires at least one enabled mode");
        }
    }
}
