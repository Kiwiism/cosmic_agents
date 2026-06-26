package server.agents.capabilities.dialogue;

import java.util.ArrayList;
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

    private static Integer matchInt(Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    }
}
