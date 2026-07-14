package config;

/** Values for one Partner medal level. Fields unused by an effect remain zero. */
public class PartnerMedalEffectLevelConfig {
    public PartnerMedalEffectConditionConfig CONDITIONS = new PartnerMedalEffectConditionConfig();
    public int SKILL_ID;
    public int SKILL_LEVEL;
    public int MAX_SKILL_LEVEL;
    public long COOLDOWN_MS;
    public double PERCENT;
    public int FAME_PER_PERCENT;
    public double MAX_PERCENT;
}
