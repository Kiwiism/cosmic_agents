package server.agents.capabilities.dialogue;

public final class AgentDialogueReportFormatter {
    private AgentDialogueReportFormatter() {
    }

    public static String stats(int level, String jobName, int str, int dex, int intStat, int luk,
                               int hp, int maxHp, int mp, int maxMp) {
        return String.format("lv%d %s | str %d dex %d int %d luk %d | hp %d/%d mp %d/%d",
                level, jobName, str, dex, intStat, luk, hp, maxHp, mp, maxMp);
    }

    public static String range(int minDamage, int maxDamage, String attackLabel, int attackStat,
                               String accuracyLabel, int accuracy) {
        return String.format("my dmg is %d-%d, %s %d, %s %d",
                minDamage, maxDamage, attackLabel, attackStat, accuracyLabel, accuracy);
    }

    public static String rangeWithHit(String rangeReport, int hitPercent, int mobAvoid) {
        return String.format("%s | hit %d%% vs hardest mob (avd %d)", rangeReport, hitPercent, mobAvoid);
    }

    public static String build(int str, int dex, int intStat, int luk, int remainingAp) {
        return String.format("build: str %d / dex %d / int %d / luk %d, %d ap left",
                str, dex, intStat, luk, remainingAp);
    }

    public static String crit(int critPct, double critMultiplier, int minDamage, int maxDamage,
                              int critMin, int critMax) {
        return String.format("crit: %d%% chance, %.2fx multiplier | base %d-%d | crit %d-%d",
                critPct, critMultiplier, minDamage, maxDamage, critMin, critMax);
    }
}
