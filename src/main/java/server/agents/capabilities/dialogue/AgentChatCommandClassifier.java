package server.agents.capabilities.dialogue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentChatCommandClassifier {
    private static final String INFO_PFX =
            "(?:(?:(?:what.?s?|what\\s+is|tell\\s+me|show\\s+me|check|how.?s?)\\s+)?(?:your|ur)\\s+)?\\b";
    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow(\\s+(me|here|pls|please|now))?|come(\\s+(here|to\\s+me|with\\s+me|closer|on|back))?|"
            + "get\\s+over\\s+here|f\\s+me|(pls|please)\\s+follow)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOW_TARGET_PATTERN = Pattern.compile(
            "^\\s*follow\\s+(\\S+?)(?:\\s+(?:pls|please|now))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE_HERE_PATTERN = Pattern.compile(
            "(?:move\\s+(?:here|there)|go\\s+(?:here|there)|here|move)(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FARM_HERE_PATTERN = Pattern.compile(
            "(?:(?:farm|grind|hunt|train)\\s+here"
            + "|(?:go\\s+)?(?:sentry|camp|guard|defend|post\\s+up|anchor)(?:\\s+(?:here|mode))?)"
            + "(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATROL_PATTERN = Pattern.compile(
            "(?:patrol|roam|wander)(?:\\s+(?:here|the\\s+area|around))?(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop(\\s+(moving|it|now|pls|please))?|stay(\\s+(here|there|put))?|"
            + "wait(\\s+(here|up|for\\s+me|a\\s+(sec|moment|bit)))?|"
            + "hold(\\s+(on|up|still|it))?|halt|freeze|don.?t\\s+move|stand\\s+(still|by)|"
            + "chill(\\s+here)?|idle|park(\\s+here)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRIND_PATTERN = Pattern.compile(
            "\\b(go\\s+|start\\s+|begin\\s+|let.?s\\s+)?(farm(ing)?|grind(ing)?|hunt(ing)?|train(ing)?)\\b"
            + "|\\b(kill|fight)\\s+(mobs?|monsters?|stuff)\\b"
            + "|\\btime\\s+to\\s+(farm|grind|hunt)\\b"
            + "|\\bgo\\s+get\\s+(exp|xp)\\b"
            + "|\\b(auto|attack)\\s*(on|mode)?\\b"
            + "|\\bstart\\s+(killing|attacking)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIDGET_PATTERN = Pattern.compile(
            "^\\s*fidget\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVEMENT_STATS_PATTERN = Pattern.compile(
            INFO_PFX + "(?:move\\s*speed|movespeed|speed|jump|movement|mobility)(?:\\s+stats?)?\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+(?:move\\s*speed|movespeed|speed|jump)\\b"
            + "|\\bhow\\s+fast\\s+(are|r)\\s+(you|u)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MESOS_PATTERN = Pattern.compile(
            "^\\s*(?:meso|mesos|cash)\\s*[?!.,]*\\s*$"
            + "|\\bhow\\s+much\\s+(?:meso|mesos|cash)(?:\\s+do\\s+(?:you|u)\\s+have)?\\b"
            + "|\\bwhat.?s\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\bshow\\s+me\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:meso|mesos|cash)\\s+(?:left|on\\s+(?:you|u|ya))\\b"
            + "|\\b(?:do\\s+(you|u)\\s+have|got(?:\\s+any)?|(you|u)\\s+got)\\s+(?:any\\s+)?(?:meso|mesos|cash)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROACTIVE_OFFERS_ON_PATTERN = Pattern.compile(
            "\\b(?:(?:proactive|future)\\s+(?:offers?|upgrades?)\\s+on|offers?\\s+(?:proactive|future)\\s+on)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROACTIVE_OFFERS_OFF_PATTERN = Pattern.compile(
            "\\b(?:(?:proactive|future)\\s+(?:offers?|upgrades?)\\s+off|offers?\\s+(?:proactive|future)\\s+off)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_UPGRADE_PATTERN = Pattern.compile(
            "\\brequest\\s*\\?|\\bneed\\s+anything\\b|\\bdo\\s+you\\s+need\\s+(anything|something)\\b"
            + "|\\bdo\\s+you\\s+need\\s+(?:any\\s+)?(?:gear|equips?|equipment)\\s*(?:from\\s+me)?\\b"
            + "|\\bneed\\s+(?:any\\s+)?(?:gear|equips?|equipment)\\s+from\\s+me\\b"
            + "|\\bwhat\\s+do\\s+you\\s+need\\b|\\bwhat.?s\\s+(on\\s+your\\s+)?wish\\s*list\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESPEC_PATTERN = Pattern.compile(
            "\\b(respec|reset\\s+(skills?|sp)|rebuild\\s+(skills?|sp)|fix\\s+(skills?|sp|build))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_RESPEC_PATTERN = Pattern.compile(
            "\\b(respec\\s+ap|reset\\s+ap|rebuild\\s+ap|fix\\s+ap(?:\\s+build)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_HP_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:hp|health)\\s+(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:hp|health)\\s+(?:pots?|potions?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_MP_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:mp|mana)\\s+(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:mp|mana)\\s+(?:pots?|potions?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:pots?|potions?|supplies)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_AMMO_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:ammo|arrows?|bolts?)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:ammo|arrows?|bolts?)\\b",
            Pattern.CASE_INSENSITIVE);

    private AgentChatCommandClassifier() {
    }

    public static String matchFollowTarget(String message) {
        Matcher matcher = FOLLOW_TARGET_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String target = matcher.group(1);
        if (target == null) {
            return null;
        }

        String normalized = target.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "me", "here", "pls", "please", "now" -> null;
            default -> target.trim();
        };
    }

    public static boolean isFarmHereCommand(String message) {
        return matchesWholeCommand(FARM_HERE_PATTERN, message);
    }

    public static boolean isPatrolCommand(String message) {
        return matchesWholeCommand(PATROL_PATTERN, message);
    }

    public static boolean isMoveHereCommand(String message) {
        return matchesWholeCommand(MOVE_HERE_PATTERN, message);
    }

    public static boolean isFollowCommand(String message) {
        return matchesWholeCommand(FOLLOW_PATTERN, message);
    }

    public static boolean isGrindCommand(String message) {
        return matchesWholeCommand(GRIND_PATTERN, message);
    }

    public static boolean isStopCommand(String message) {
        return matchesWholeCommand(STOP_PATTERN, message);
    }

    public static boolean isFidgetCommand(String message) {
        return message != null && FIDGET_PATTERN.matcher(message).find();
    }

    public static boolean isMesoQuery(String message) {
        return matchesWholeCommand(MESOS_PATTERN, message);
    }

    public static boolean isMovementStatsQuery(String message) {
        return matchesWholeCommand(MOVEMENT_STATS_PATTERN, message);
    }

    public static boolean isProactiveOffersOnCommand(String message) {
        return PROACTIVE_OFFERS_ON_PATTERN.matcher(message).find();
    }

    public static boolean isProactiveOffersOffCommand(String message) {
        return PROACTIVE_OFFERS_OFF_PATTERN.matcher(message).find();
    }

    public static boolean isRequestUpgradeCommand(String message) {
        return matchesWholeCommand(REQUEST_UPGRADE_PATTERN, message);
    }

    public static boolean isRespecCommand(String message) {
        return RESPEC_PATTERN.matcher(message).find();
    }

    public static boolean isApRespecCommand(String message) {
        return AP_RESPEC_PATTERN.matcher(message).find();
    }

    public static boolean isNeedHpPotCommand(String message) {
        return NEED_HP_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedMpPotCommand(String message) {
        return NEED_MP_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedPotCommand(String message) {
        return NEED_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedAmmoCommand(String message) {
        return NEED_AMMO_PATTERN.matcher(message).find();
    }

    public static boolean isGroupSupplyRequest(String message) {
        return isNeedHpPotCommand(message)
                || isNeedMpPotCommand(message)
                || isNeedPotCommand(message)
                || isNeedAmmoCommand(message);
    }

    private static boolean matchesWholeCommand(Pattern pattern, String message) {
        String normalized = normalizeCommandText(message);
        return !normalized.isEmpty() && pattern.matcher(normalized).matches();
    }

    private static String normalizeCommandText(String message) {
        if (message == null) {
            return "";
        }

        return message.strip()
                .replaceAll("^[\\p{Punct}\\s]+", "")
                .replaceAll("[\\p{Punct}\\s]+$", "")
                .replaceFirst("^(?:(?:please|pls|hey|yo)\\s+)+", "")
                .replaceFirst("^(?:(?:can|could|will|would)\\s+you\\s+)", "")
                .replaceFirst("^(?:(?:please|pls)\\s+)+", "")
                .replaceFirst("\\s+(?:please|pls)$", "")
                .replaceAll("\\s+", " ");
    }
}
