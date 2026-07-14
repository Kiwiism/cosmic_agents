package config;

import java.util.ArrayList;
import java.util.List;

/** One equipment-gated Partner effect and its ordered configuration levels. */
public class PartnerMedalEffectConfig {
    public int ITEM_ID;
    public String EFFECT = "";
    public boolean SOLO_TAG_ENABLED = true;
    public boolean DOUBLE_PARTNER_ENABLED = true;
    public List<PartnerMedalEffectLevelConfig> LEVELS = new ArrayList<>();
}
