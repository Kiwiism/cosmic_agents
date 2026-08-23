package server.agents.capabilities.partyquest.kpq;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Deterministic KPQ chat matching and short mixed-party guidance text. */
final class AgentKpqHumanDialoguePolicy {
    private static final Set<String> COUNT_WORDS = Set.of(
            "need", "needs", "needed", "count", "number", "amount", "many", "much");
    private static final Set<String> QUESTION_WORDS = Set.of(
            "str", "dex", "int", "luk", "warrior", "fighter", "bowman", "archer",
            "magician", "mage", "thief", "rogue", "pirate", "level", "advance",
            "adv", "advancement", "coupon", "coupons", "ticket", "tickets");

    private AgentKpqHumanDialoguePolicy() {
    }

    static boolean asksForCouponCount(String message) {
        String normalized = normalize(message);
        if (normalized.isEmpty()) return false;
        Set<String> words = new HashSet<>(java.util.List.of(normalized.split(" ")));
        boolean mentionsCoupon = words.contains("coupon") || words.contains("coupons")
                || words.contains("ticket") || words.contains("tickets");
        boolean asksCount = normalized.contains("how many") || normalized.contains("how much")
                || words.stream().anyMatch(COUNT_WORDS::contains);
        boolean mentionsQuestion = words.stream().anyMatch(QUESTION_WORDS::contains);
        return asksCount && (mentionsCoupon || mentionsQuestion);
    }

    static String couponAnswer(String name, int target) {
        return name + ", Cloto assigned you " + target + " coupons for that question.";
    }

    static String delayedPrompt(
            String name, String task, long seed, long turn,
            boolean partyLeader, boolean teasingAllowed) {
        if (partyLeader && (!teasingAllowed || turn <= 0L)) {
            return name + ", we're ready when you are - " + task + '.';
        }
        if (turn <= 0L) {
            return name + ", no rush - " + task + ". Just follow what the leader tells you.";
        }
        return switch (Math.floorMod(seed + turn, 4)) {
            case 0 -> name + ", lol, tiny noob moment :P " + task + '.';
            case 1 -> name + ", need a hand? " + task + '.';
            default -> name + ", take your time - " + task + '.';
        };
    }

    private static String normalize(String message) {
        if (message == null) return "";
        return message.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll(" +", " ");
    }
}
