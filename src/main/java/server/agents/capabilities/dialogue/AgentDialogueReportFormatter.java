package server.agents.capabilities.dialogue;

import client.Job;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentDialogueReportFormatter {
    private AgentDialogueReportFormatter() {
    }

    public record AgentSkillLine(int id, String name, int level) {
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

    public static String rangeAttackLabel(boolean magicAttack) {
        return magicAttack ? "matk" : "watk";
    }

    public static String rangeAccuracyLabel(boolean magicAttack) {
        return magicAttack ? "magic acc" : "acc";
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

    public static String movementStatLine(int totalSpeedStat, int totalJumpStat) {
        return String.format(Locale.ROOT, "speed %d%% jump %d%%", totalSpeedStat, totalJumpStat);
    }

    public static String movementStatLineForced(int totalSpeedStat, int totalJumpStat, int rawSpeedStat, int rawJumpStat) {
        return String.format(Locale.ROOT,
                "speed %d%% jump %d%% (map forced; raw %d%%/%d%%)",
                totalSpeedStat, totalJumpStat, rawSpeedStat, rawJumpStat);
    }

    public static String movementWalkNoMap(double walkVelocityPxs, double hForcePxs, int climbStepPerTick) {
        return String.format(Locale.ROOT, "walk %.1f px/s, hforce %.1f, climb %d px/tick",
                walkVelocityPxs, hForcePxs, climbStepPerTick);
    }

    public static String movementJumpNoMap(double jumpForcePerTick, double ropeJumpForcePerTick, double maxJumpHeight) {
        return String.format(Locale.ROOT, "jump %.1f/tick, rope %.1f/tick, max jump %.1f px",
                jumpForcePerTick, ropeJumpForcePerTick, maxJumpHeight);
    }

    public static String movementWalkWithMap(double walkVelocityPxs, int walkStep, int climbStep, double hForcePxs) {
        return String.format(Locale.ROOT, "walk %.1f px/s, %d px/tick, climb %d, hforce %.1f",
                walkVelocityPxs, walkStep, climbStep, hForcePxs);
    }

    public static String movementJumpWithMap(double jumpForcePerTick, double ropeJumpForcePerTick, double maxJumpHeight,
                                             int maxJumpHorizontalTravel, int maxRopeJumpHorizontalTravel) {
        return String.format(Locale.ROOT, "jump %.1f, rope %.1f, max %.1f px, reach %d/%d px",
                jumpForcePerTick, ropeJumpForcePerTick, maxJumpHeight,
                maxJumpHorizontalTravel, maxRopeJumpHorizontalTravel);
    }

    public static String dropOrTradePrompt(String category, int count, List<String> templates) {
        String base = switch (category) {
            case "scrolls" -> "scrolls";
            case "pots" -> "pots";
            case "buff" -> "buff pots";
            case "use" -> "use items";
            case "equips" -> "equips";
            case "etc" -> "etc items";
            default -> category.startsWith("name:") ? category.substring(5) : "those items";
        };
        String what = count > 0 ? count + " " + base : base;
        String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        return String.format(template, what);
    }

    public static String jobChangeReply(String template, String jobName) {
        return String.format(template, jobName);
    }

    public static String welcomeBackOfflineReply(String template, String mapName) {
        return String.format(template, mapName);
    }

    public static String ownerPotShortageReply(String template, String type) {
        return String.format(template, type);
    }

    public static String apPureBuildConfirm(String buildName, String secondaryStat, int effectiveSecondaryTarget,
                                            String primaryStat) {
        return buildName + " it is! keeping " + secondaryStat + " at " + effectiveSecondaryTarget
                + ", rest into " + primaryStat;
    }

    public static String apPureBuildAlready(String buildName) {
        return "already doing " + buildName + "!";
    }

    public static String apFixedBuildConfirm(String secondaryStat, int effectiveSecondaryTarget, String primaryStat) {
        return "ok! keeping " + secondaryStat + " at " + effectiveSecondaryTarget + ", rest into " + primaryStat;
    }

    public static String apFixedBuildAlready(int legalSecondaryTarget, String secondaryStat) {
        return "already doing " + legalSecondaryTarget + " " + secondaryStat + " build!";
    }

    public static String statTypeName(String statTypeName) {
        return statTypeName.toLowerCase(Locale.ROOT);
    }

    public static String fameSamePersonReply(String template, String targetName) {
        return String.format(template, targetName);
    }

    public static String fameOkReply(String template, String targetName) {
        return template.contains("%s") ? String.format(template, targetName) : template;
    }

    public static String skillTreeChoicePrompt(Collection<Integer> skillTreeIds) {
        List<String> labels = new ArrayList<>();
        for (int treeId : skillTreeIds) {
            labels.add(skillTreeLabel(treeId));
        }
        return "which skill tree? " + String.join(", ", labels);
    }

    public static String beginnerSkillReport(List<AgentSkillLine> skills, int beginnerSpLeft) {
        StringBuilder line = new StringBuilder("beginner: ");
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }

            AgentSkillLine skill = skills.get(i);
            line.append(skill.name()).append(" lv").append(skill.level());
        }
        line.append(" | ").append(beginnerSpLeft).append(" beginner SP left");
        return line.toString();
    }

    public static List<String> skillTreeReportLines(int treeId, List<AgentSkillLine> skills) {
        List<String> lines = new ArrayList<>();
        String label = skillTreeLabel(treeId);
        String prefix = label + ": ";
        String followupPrefix = "more " + label + ": ";
        StringBuilder line = new StringBuilder(prefix);
        int countOnLine = 0;

        for (AgentSkillLine skill : skills) {
            String piece = skill.name() + " lv" + skill.level();
            boolean needsSeparator = countOnLine > 0;
            int extraChars = piece.length() + (needsSeparator ? 2 : 0);
            if ((line.length() + extraChars > 100 || countOnLine >= 3) && countOnLine > 0) {
                lines.add(line.toString());
                line = new StringBuilder(followupPrefix);
                countOnLine = 0;
                needsSeparator = false;
            }

            if (needsSeparator) {
                line.append(", ");
            }
            line.append(piece);
            countOnLine++;
        }

        if (countOnLine > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    public static String jobDisplayName(Job job) {
        return switch (job) {
            case WARRIOR -> "warrior"; case MAGICIAN -> "mage";
            case BOWMAN -> "bowman"; case THIEF -> "thief";
            case PIRATE -> "pirate"; case FIGHTER -> "fighter";
            case PAGE -> "page"; case SPEARMAN -> "spearman";
            case FP_WIZARD -> "f/p wizard"; case IL_WIZARD -> "i/l wizard";
            case CLERIC -> "cleric"; case HUNTER -> "hunter";
            case CROSSBOWMAN -> "crossbowman"; case ASSASSIN -> "assassin";
            case BANDIT -> "bandit"; case BRAWLER -> "brawler";
            case GUNSLINGER -> "gunslinger"; case CRUSADER -> "crusader";
            case WHITEKNIGHT -> "white knight"; case DRAGONKNIGHT -> "dragon knight";
            case FP_MAGE -> "f/p mage"; case IL_MAGE -> "i/l mage";
            case PRIEST -> "priest"; case RANGER -> "ranger";
            case SNIPER -> "sniper"; case HERMIT -> "hermit";
            case CHIEFBANDIT -> "chief bandit"; case MARAUDER -> "marauder";
            case OUTLAW -> "outlaw"; case HERO -> "hero";
            case PALADIN -> "paladin"; case DARKKNIGHT -> "dark knight";
            case FP_ARCHMAGE -> "f/p archmage"; case IL_ARCHMAGE -> "i/l archmage";
            case BISHOP -> "bishop"; case BOWMASTER -> "bowmaster";
            case MARKSMAN -> "marksman"; case NIGHTLORD -> "night lord";
            case SHADOWER -> "shadower"; case BUCCANEER -> "buccaneer";
            case NOBLESSE -> "noblesse";
            case DAWNWARRIOR1 -> "dawn warrior"; case DAWNWARRIOR2 -> "dawn warrior";
            case DAWNWARRIOR3 -> "dawn warrior"; case DAWNWARRIOR4 -> "dawn warrior";
            case BLAZEWIZARD1 -> "blaze wizard"; case BLAZEWIZARD2 -> "blaze wizard";
            case BLAZEWIZARD3 -> "blaze wizard"; case BLAZEWIZARD4 -> "blaze wizard";
            case WINDARCHER1 -> "wind archer"; case WINDARCHER2 -> "wind archer";
            case WINDARCHER3 -> "wind archer"; case WINDARCHER4 -> "wind archer";
            case NIGHTWALKER1 -> "night walker"; case NIGHTWALKER2 -> "night walker";
            case NIGHTWALKER3 -> "night walker"; case NIGHTWALKER4 -> "night walker";
            case THUNDERBREAKER1 -> "thunder breaker"; case THUNDERBREAKER2 -> "thunder breaker";
            case THUNDERBREAKER3 -> "thunder breaker"; case THUNDERBREAKER4 -> "thunder breaker";
            case LEGEND -> "legend";
            case ARAN1 -> "aran"; case ARAN2 -> "aran";
            case ARAN3 -> "aran"; case ARAN4 -> "aran";
            case CORSAIR -> "corsair";
            default -> job.name().toLowerCase(Locale.ROOT);
        };
    }

    public static String skillTreeLabel(int treeId) {
        Job job = Job.getById(treeId);
        if (job == null) {
            return "tree " + treeId;
        }

        return switch (job) {
            case NOBLESSE -> "noblesse (" + treeId + ")";
            case DAWNWARRIOR1 -> "dawn warrior 1st job (" + treeId + ")";
            case DAWNWARRIOR2 -> "dawn warrior 2nd job (" + treeId + ")";
            case DAWNWARRIOR3 -> "dawn warrior 3rd job (" + treeId + ")";
            case DAWNWARRIOR4 -> "dawn warrior 4th job (" + treeId + ")";
            case BLAZEWIZARD1 -> "blaze wizard 1st job (" + treeId + ")";
            case BLAZEWIZARD2 -> "blaze wizard 2nd job (" + treeId + ")";
            case BLAZEWIZARD3 -> "blaze wizard 3rd job (" + treeId + ")";
            case BLAZEWIZARD4 -> "blaze wizard 4th job (" + treeId + ")";
            case WINDARCHER1 -> "wind archer 1st job (" + treeId + ")";
            case WINDARCHER2 -> "wind archer 2nd job (" + treeId + ")";
            case WINDARCHER3 -> "wind archer 3rd job (" + treeId + ")";
            case WINDARCHER4 -> "wind archer 4th job (" + treeId + ")";
            case NIGHTWALKER1 -> "night walker 1st job (" + treeId + ")";
            case NIGHTWALKER2 -> "night walker 2nd job (" + treeId + ")";
            case NIGHTWALKER3 -> "night walker 3rd job (" + treeId + ")";
            case NIGHTWALKER4 -> "night walker 4th job (" + treeId + ")";
            case THUNDERBREAKER1 -> "thunder breaker 1st job (" + treeId + ")";
            case THUNDERBREAKER2 -> "thunder breaker 2nd job (" + treeId + ")";
            case THUNDERBREAKER3 -> "thunder breaker 3rd job (" + treeId + ")";
            case THUNDERBREAKER4 -> "thunder breaker 4th job (" + treeId + ")";
            case LEGEND -> "legend (" + treeId + ")";
            case ARAN1 -> "aran 1st job (" + treeId + ")";
            case ARAN2 -> "aran 2nd job (" + treeId + ")";
            case ARAN3 -> "aran 3rd job (" + treeId + ")";
            case ARAN4 -> "aran 4th job (" + treeId + ")";
            case EVAN -> "evan (" + treeId + ")";
            case EVAN1 -> "evan 1st job (" + treeId + ")";
            case EVAN2 -> "evan 2nd job (" + treeId + ")";
            case EVAN3 -> "evan 3rd job (" + treeId + ")";
            case EVAN4 -> "evan 4th job (" + treeId + ")";
            case EVAN5 -> "evan 5th job (" + treeId + ")";
            case EVAN6 -> "evan 6th job (" + treeId + ")";
            case EVAN7 -> "evan 7th job (" + treeId + ")";
            case EVAN8 -> "evan 8th job (" + treeId + ")";
            case EVAN9 -> "evan 9th job (" + treeId + ")";
            case EVAN10 -> "evan 10th job (" + treeId + ")";
            default -> jobDisplayName(job) + " (" + treeId + ")";
        };
    }
}
