package server.agents.capabilities.dialogue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentChatCommandClassifier {
    private static final Pattern FOLLOW_TARGET_PATTERN = Pattern.compile(
            "^\\s*follow\\s+(\\S+?)(?:\\s+(?:pls|please|now))?\\s*[?!.,]*\\s*$",
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
}
