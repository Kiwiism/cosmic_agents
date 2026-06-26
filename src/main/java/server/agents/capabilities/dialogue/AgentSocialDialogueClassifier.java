package server.agents.capabilities.dialogue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentSocialDialogueClassifier {
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "^\\s*(hi+|hey+|hello+|sup|yo+|howdy|hiya|heya|hai|ello|"
            + "whats?\\s*up|waz+up|wassup|hows?\\s+it\\s+going|"
            + "(good\\s+)?(morning|evening|afternoon)|"
            + "how\\s+(are|r)\\s+(you|u|ya)(\\s+doing)?|"
            + "what.?s\\s+(good|up|new|poppin.?))\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FAME_PATTERN = Pattern.compile(
            "^\\s*fame\\s+(me|\\S+?)\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private AgentSocialDialogueClassifier() {
    }

    public static boolean isGreeting(String message) {
        return GREETING_PATTERN.matcher(message).matches();
    }

    public static String matchFameTarget(String message) {
        Matcher matcher = FAME_PATTERN.matcher(message);
        return matcher.matches() ? matcher.group(1) : null;
    }

    public static boolean isSelfFameTarget(String targetName) {
        return "me".equalsIgnoreCase(targetName);
    }
}
