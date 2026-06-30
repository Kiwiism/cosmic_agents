package server.agents.commands;

import java.util.Set;

/**
 * Cheap Levenshtein-based "did you mean?" for Agent command verbs.
 * Runs without any LLM dependency. Only suggests when the first
 * token of the message looks like a near-miss of a known verb.
 */
public final class AgentCommandTypoSuggester {

    public static final Set<String> KNOWN_VERBS = Set.of(
            // grind / combat
            "farm", "grind", "hunt", "train", "attack", "kill", "fight",
            // movement
            "follow", "come", "here", "move", "go", "warp",
            // halt
            "stop", "wait", "idle", "hold",
            // economy / inventory
            "trade", "drop", "give", "take", "transfer", "buy", "sell",
            // info
            "help", "commands", "stats", "exp", "level", "pots", "potions",
            "potion", "scrolls", "mesos", "meso", "ammo", "arrows", "bolts",
            "inv", "inventory", "slots", "fame",
            // builds & buffs
            "build", "buff", "skill", "skills", "cast",
            // formation
            "formation", "snap", "stagger", "stack", "spread", "split",
            "tight", "loose", "left", "right",
            // bot lifecycle
            "recruit", "dismiss", "relog", "logout"
    );

    private static final int MIN_LEN = 4;
    private static final int MAX_DISTANCE = 2;

    // Common chat words that look close to a verb but should never autocorrect.
    // Without this guard "hello" -> "help" and "hey" / "yes" produce noise replies.
    private static final Set<String> CHAT_DENYLIST = Set.of(
            "hello", "hey", "yes", "yeah", "yep", "nope", "sure", "thanks",
            "thank", "ok", "okay", "lol", "hmm", "haha", "what", "where",
            "when", "who", "why", "how"
    );

    private AgentCommandTypoSuggester() {
    }

    /**
     * Returns the likely intended verb if the first token is a near-miss to a
     * known verb, otherwise null. Returns null when the token IS already a known
     * verb (no correction needed) or is too short to safely fuzzy-match.
     */
    public static String suggest(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        int sp = 0;
        while (sp < trimmed.length() && !Character.isWhitespace(trimmed.charAt(sp))) {
            sp++;
        }
        String token = trimmed.substring(0, sp).toLowerCase();
        if (token.length() < MIN_LEN) {
            return null;
        }
        if (KNOWN_VERBS.contains(token)) {
            return null;
        }
        if (CHAT_DENYLIST.contains(token)) {
            return null;
        }

        // Tight gate: short tokens (4-5 chars) must be 1 edit away; only longer
        // tokens may use a 2-edit budget. This avoids "hello" -> "help" style noise.
        int allowed = token.length() >= 6 ? MAX_DISTANCE : 1;

        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String verb : KNOWN_VERBS) {
            if (Math.abs(verb.length() - token.length()) > allowed) {
                continue;
            }
            int d = levenshtein(token, verb, allowed);
            if (d < bestDist) {
                bestDist = d;
                best = verb;
            }
        }
        return bestDist <= allowed ? best : null;
    }

    /** Bounded Levenshtein. Returns ceiling+1 if known to exceed bound (early exit). */
    static int levenshtein(String a, String b, int bound) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > bound) {
            return bound + 1;
        }
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) {
                    rowMin = curr[j];
                }
            }
            if (rowMin > bound) {
                return bound + 1;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[m];
    }
}
