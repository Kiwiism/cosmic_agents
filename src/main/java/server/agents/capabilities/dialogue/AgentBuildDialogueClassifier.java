package server.agents.capabilities.dialogue;

import client.Job;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentBuildDialogueClassifier {
    private static final Pattern JOB_SELECT_PATTERN = Pattern.compile(
            "\\b(warrior|fighter|page|spearman|sader|crusader|hero|dk|drk|dark knight|paladin|"
            + "mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage|fp arch|il arch|priest|bishop|"
            + "bowman|bowmen|archer|hunter|crossbow|xbow|sniper|ranger|bowmaster|bm|marksman|mm|"
            + "thief|assassin|sin|bandit|dit|hermit|chief bandit|cb|shadower|shad|night lord|nl|"
            + "pirate|brawler|gunslinger|gun|marauder|outlaw|bucc|buccaneer|corsair|"
            + "white knight|wk|dragon knight)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SP_1H_PATTERN = Pattern.compile("\\b1h\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SP_2H_PATTERN = Pattern.compile("\\b2h\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_CHANGE_BUILD_PATTERN = Pattern.compile(
            "\\b(change|switch|update|reset|new)\\s+(your\\s+|ur\\s+)?build\\b",
            Pattern.CASE_INSENSITIVE);
    private static final String PURE_NO_STAT = "^\\s*pure\\s*$";
    private static final Pattern AP_PURE_STR_PATTERN = Pattern.compile(
            "\\bpure\\s+str\\b|\\bdexless\\b|" + PURE_NO_STAT, Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_DEXLESS_PATTERN = Pattern.compile(
            "\\bdexless\\b|\\bpure\\s+luk\\b|" + PURE_NO_STAT, Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_LUKLESS_PATTERN = Pattern.compile(
            "\\blukless\\b|\\bpure\\s+int\\b|" + PURE_NO_STAT, Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_STRLESS_PATTERN = Pattern.compile(
            "\\bstrless\\b|\\bpure\\s+dex\\b|" + PURE_NO_STAT, Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_FIXED_DEX_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*dex\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_FIXED_LUK_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*luk\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_FIXED_STR_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*str\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_TREE_CHOICE_ID_PATTERN = Pattern.compile("\\b(\\d{3,4})\\b");

    private AgentBuildDialogueClassifier() {
    }

    public static boolean isJobSelectionCandidate(String message) {
        return JOB_SELECT_PATTERN.matcher(message).find();
    }

    public static Job resolveJobChange(Job currentJob, int level, String message) {
        return switch (currentJob) {
            case BEGINNER -> {
                if (level >= 8 && message.matches(".*\\b(mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage)\\b.*")) yield Job.MAGICIAN;
                if (level >= 10 && message.matches(".*\\b(warrior|fighter|page|spearman|sader)\\b.*")) yield Job.WARRIOR;
                if (level >= 10 && message.matches(".*\\b(bowman|bowmen|archer|hunter|crossbow|xbow)\\b.*")) yield Job.BOWMAN;
                if (level >= 10 && message.matches(".*\\b(thief|assassin|sin|bandit|dit)\\b.*")) yield Job.THIEF;
                if (level >= 10 && message.matches(".*\\b(pirate|brawler|gunslinger|gun|bucc)\\b.*")) yield Job.PIRATE;
                yield null;
            }
            case WARRIOR -> level < 30 ? null :
                    message.matches(".*\\b(fighter|sader)\\b.*") ? Job.FIGHTER :
                    message.matches(".*\\bpage\\b.*") ? Job.PAGE :
                    message.matches(".*\\b(spearman|spear)\\b.*") ? Job.SPEARMAN : null;
            case MAGICIAN -> level < 30 ? null :
                    message.matches(".*\\b(fp|fp wizard|fp mage|fire|f\\.p)\\b.*") ? Job.FP_WIZARD :
                    message.matches(".*\\b(il|il wizard|il mage|ice|i\\.l)\\b.*") ? Job.IL_WIZARD :
                    message.matches(".*\\b(cleric|healer|priest|bishop)\\b.*") ? Job.CLERIC : null;
            case BOWMAN -> level < 30 ? null :
                    message.matches(".*\\b(hunter|bow)\\b.*") ? Job.HUNTER :
                    message.matches(".*\\b(crossbow|xbow|crossbowman)\\b.*") ? Job.CROSSBOWMAN : null;
            case THIEF -> level < 30 ? null :
                    message.matches(".*\\b(assassin|sin)\\b.*") ? Job.ASSASSIN :
                    message.matches(".*\\b(bandit|dit)\\b.*") ? Job.BANDIT : null;
            case PIRATE -> level < 30 ? null :
                    message.matches(".*\\b(brawler|knuckle)\\b.*") ? Job.BRAWLER :
                    message.matches(".*\\b(gunslinger|gun)\\b.*") ? Job.GUNSLINGER : null;
            case FIGHTER -> level >= 70 && message.matches(".*\\bcrusader\\b.*") ? Job.CRUSADER : null;
            case PAGE -> level >= 70 && message.matches(".*\\b(white knight|wk)\\b.*") ? Job.WHITEKNIGHT : null;
            case SPEARMAN -> level >= 70 && message.matches(".*\\b(dragon knight|dk)\\b.*") ? Job.DRAGONKNIGHT : null;
            case FP_WIZARD -> level >= 70 && message.matches(".*\\b(fp mage|fp)\\b.*") ? Job.FP_MAGE : null;
            case IL_WIZARD -> level >= 70 && message.matches(".*\\b(il mage|il)\\b.*") ? Job.IL_MAGE : null;
            case CLERIC -> level >= 70 && message.matches(".*\\bpriest\\b.*") ? Job.PRIEST : null;
            case HUNTER -> level >= 70 && message.matches(".*\\branger\\b.*") ? Job.RANGER : null;
            case CROSSBOWMAN -> level >= 70 && message.matches(".*\\bsniper\\b.*") ? Job.SNIPER : null;
            case ASSASSIN -> level >= 70 && message.matches(".*\\bhermit\\b.*") ? Job.HERMIT : null;
            case BANDIT -> level >= 70 && message.matches(".*\\b(chief bandit|cb|chief)\\b.*") ? Job.CHIEFBANDIT : null;
            case BRAWLER -> level >= 70 && message.matches(".*\\bmarauder\\b.*") ? Job.MARAUDER : null;
            case GUNSLINGER -> level >= 70 && message.matches(".*\\boutlaw\\b.*") ? Job.OUTLAW : null;
            case CRUSADER -> level >= 120 && message.matches(".*\\bhero\\b.*") ? Job.HERO : null;
            case WHITEKNIGHT -> level >= 120 && message.matches(".*\\bpaladin\\b.*") ? Job.PALADIN : null;
            case DRAGONKNIGHT -> level >= 120 && message.matches(".*\\b(dark knight|drk)\\b.*") ? Job.DARKKNIGHT : null;
            case FP_MAGE -> level >= 120 && message.matches(".*\\b(fp archmage|fp arch)\\b.*") ? Job.FP_ARCHMAGE : null;
            case IL_MAGE -> level >= 120 && message.matches(".*\\b(il archmage|il arch)\\b.*") ? Job.IL_ARCHMAGE : null;
            case PRIEST -> level >= 120 && message.matches(".*\\bbishop\\b.*") ? Job.BISHOP : null;
            case RANGER -> level >= 120 && message.matches(".*\\b(bowmaster|bm)\\b.*") ? Job.BOWMASTER : null;
            case SNIPER -> level >= 120 && message.matches(".*\\b(marksman|mm)\\b.*") ? Job.MARKSMAN : null;
            case HERMIT -> level >= 120 && message.matches(".*\\b(night lord|nl)\\b.*") ? Job.NIGHTLORD : null;
            case CHIEFBANDIT -> level >= 120 && message.matches(".*\\b(shadower|shad)\\b.*") ? Job.SHADOWER : null;
            case MARAUDER -> level >= 120 && message.matches(".*\\b(buccaneer|bucc)\\b.*") ? Job.BUCCANEER : null;
            case OUTLAW -> level >= 120 && message.matches(".*\\bcorsair\\b.*") ? Job.CORSAIR : null;
            default -> null;
        };
    }

    public static boolean isOneHandedSpVariant(String message) {
        return SP_1H_PATTERN.matcher(message).find();
    }

    public static boolean isTwoHandedSpVariant(String message) {
        return SP_2H_PATTERN.matcher(message).find();
    }

    public static boolean isApChangeBuildCommand(String message) {
        return AP_CHANGE_BUILD_PATTERN.matcher(message).find();
    }

    public static boolean isPureStrBuildCommand(String message) {
        return AP_PURE_STR_PATTERN.matcher(message).find();
    }

    public static boolean isDexlessBuildCommand(String message) {
        return AP_DEXLESS_PATTERN.matcher(message).find();
    }

    public static boolean isLuklessBuildCommand(String message) {
        return AP_LUKLESS_PATTERN.matcher(message).find();
    }

    public static boolean isStrlessBuildCommand(String message) {
        return AP_STRLESS_PATTERN.matcher(message).find();
    }

    public static Integer matchFixedDexTarget(String message) {
        return matchInt(AP_FIXED_DEX_PATTERN, message);
    }

    public static Integer matchFixedLukTarget(String message) {
        return matchInt(AP_FIXED_LUK_PATTERN, message);
    }

    public static Integer matchFixedStrTarget(String message) {
        return matchInt(AP_FIXED_STR_PATTERN, message);
    }

    public static List<Integer> skillTreeChoiceIds(String message) {
        List<Integer> ids = new ArrayList<>();
        Matcher matcher = SKILL_TREE_CHOICE_ID_PATTERN.matcher(message);
        while (matcher.find()) {
            ids.add(Integer.parseInt(matcher.group(1)));
        }
        return ids;
    }

    public static Integer resolveSkillTreeChoice(String message, Collection<Integer> skillTreeIds) {
        for (int treeId : skillTreeChoiceIds(message)) {
            if (skillTreeIds.contains(treeId)) {
                return treeId;
            }
        }

        String normalizedMessage = normalizeChoiceText(message);
        List<Integer> matches = new ArrayList<>();
        for (int treeId : skillTreeIds) {
            if (matchesSkillTreeChoice(normalizedMessage, treeId)) {
                matches.add(treeId);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static Integer matchInt(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private static boolean matchesSkillTreeChoice(String normalizedMessage, int treeId) {
        String fullLabel = normalizeChoiceText(AgentDialogueReportFormatter.skillTreeLabel(treeId));
        if (!fullLabel.isEmpty() && normalizedMessage.contains(fullLabel)) {
            return true;
        }

        Job job = Job.getById(treeId);
        if (job == null) {
            return false;
        }

        String baseLabel = normalizeChoiceText(AgentDialogueReportFormatter.jobDisplayName(job));
        return !baseLabel.isEmpty() && normalizedMessage.contains(baseLabel);
    }

    private static String normalizeChoiceText(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }
}
