package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

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

    public static String expPercent(int currentExp, int neededExp) {
        if (neededExp <= 0) {
            return "0%";
        }
        double pct = (currentExp / (double) neededExp) * 100.0;
        String formatted = String.format(Locale.ROOT, "%.2f", pct);
        if (formatted.contains(".")) {
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return formatted + "%";
    }

    public static String scrollCount(int count) {
        return count > 0
                ? "I have " + count + " scroll" + (count != 1 ? "s" : "") + " on me"
                : "no scrolls on me";
    }

    public static String potionCount(int hp, int mp) {
        if (hp == 0 && mp == 0) {
            return "no pots on me rn";
        }
        if (mp == 0) {
            return "I have " + hp + " hp pot" + (hp != 1 ? "s" : "") + ", no mp pots";
        }
        if (hp == 0) {
            return "no hp pots, " + mp + " mp pot" + (mp != 1 ? "s" : "");
        }

        return "I have " + hp + " hp pot" + (hp != 1 ? "s" : "")
                + " and " + mp + " mp pot" + (mp != 1 ? "s" : "");
    }

    public static String compactMesos(int mesos) {
        if (mesos < 1_000) {
            return String.valueOf(mesos);
        }

        double value = mesos;
        String[] suffixes = {"k", "m", "b"};
        int suffixIndex = -1;

        while (value >= 1_000d && suffixIndex < suffixes.length - 1) {
            value /= 1_000d;
            suffixIndex++;
        }

        double rounded = Math.round(value * 10d) / 10d;
        if (rounded >= 1_000d && suffixIndex < suffixes.length - 1) {
            rounded = Math.round((rounded / 1_000d) * 10d) / 10d;
            suffixIndex++;
        }

        if (Math.floor(rounded) == rounded) {
            return String.format(Locale.ROOT, "%.0f%s", rounded, suffixes[suffixIndex]);
        }

        return String.format(Locale.ROOT, "%.1f%s", rounded, suffixes[suffixIndex]);
    }

    public static String mesoReport(int mesos, List<String> templates) {
        String amount = compactMesos(mesos);
        String pattern = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return String.format(pattern, amount);
    }
}
