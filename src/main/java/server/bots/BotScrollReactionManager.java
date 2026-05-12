package server.bots;

import client.Character;
import client.inventory.Equip;
import server.ItemInformationProvider;

import java.awt.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

final class BotScrollReactionManager {
    private static final int REACTION_RADIUS_PX = 600;
    private static final int EMOTE_CHANCE_PCT = 16;
    private static final int CHAT_CHANCE_PCT = 8;
    private static final int FIDGET_CHANCE_PCT = 3;
    private static final int REACTION_COOLDOWN_MS = 10_000;
    private static final int LOAD_DECAY_MS = 60_000;
    private static final int STREAK_WINDOW_MS = 45_000;
    private static final int SCROLL_STREAK_REACTION_CHANCE = 45;
    private static final List<String> SCROLL_SUCCESS_REACTIONS = List.of(
            "nice", "nice!",
            "gz",
            "clean", "clean!"
            "juicy",
            "anything good?",
            "whachu scrollin?",
            "thats a hit",
            "good stuff",
            "we take those",
            "big pass",
            "solid",
            "not bad",
            "blessed", "damn",
            "yoo nice", ":)", ":D", "again!", "do it again!");
    private static final List<String> SCROLL_FAIL_REACTIONS = List.of(
            "rip", "f", "F",
            "aw", "aww",
            "bruh",
            "oof",
            "pain",
            "rough",
            "tragic",
            "unlucky",
            "damn",
            "scroll said no",
            "maybe next one",
            "happens");
    private static final List<String> SCROLL_SUCCESS_STREAK_REACTIONS = List.of(
            "ok youre cookin", "you are cooking", "!!!!", "!!!!!!!!",
            "thats a combo", "super combo", "woww", "holyy", "holy", "sheeshhh", "omg", "omg!!", "wtf", "WTF", "WTF!!",
            "cant miss rn", "no miss!",
            "buddy is on a run", "today is your day!",
            "save some luck for us", "crazy", "that's crazyyyyy", "crazy man", "crazy man!",
            "another one?", "another one", "another one!", "again!again!", "again!",
            "damn", "daaaaaamn", "dayum",
            "youre farming hits");
    private static final List<String> SCROLL_FAIL_STREAK_REACTIONS = List.of(
            "ok thats cursed", "cursed", "bad luck",
            "that streak is nasty", "damn", "fak",
            "nah id stop there", "not your day man",
            "map is eating your scrolls", "try a different map? xD", "change map bro",
            "someone break the curse",
            "scroll tax goin crazy",
            "the odds hate you rn",
            "ok thats just mean",
            "this map is cold");
    private static int SCROLL_STREAK_REACTION_CHANCE;

    private BotScrollReactionManager() {
    }

    static void handleScrollEvent(Character source,
                                  Equip.ScrollResult result,
                                  int scrollItemId,
                                  Collection<List<BotEntry>> allEntries) {
        if (source == null || source.getMap() == null || result == null || allEntries == null) {
            return;
        }
        if (result != Equip.ScrollResult.SUCCESS
                && result != Equip.ScrollResult.FAIL
                && result != Equip.ScrollResult.CURSE) {
            return;
        }

        Point sourcePos = source.getPosition();
        if (sourcePos == null) {
            return;
        }

        int radius = Math.max(0, REACTION_RADIUS_PX);
        long maxDistSq = (long) radius * radius;
        long now = System.currentTimeMillis();
        boolean success = result == Equip.ScrollResult.SUCCESS;
        int mapId = source.getMapId();
        int scrollSuccessRate = resolveScrollSuccessRate(scrollItemId);

        for (List<BotEntry> entries : allEntries) {
            for (BotEntry entry : entries) {
                Character bot = entry.bot;
                if (bot == null || bot.getId() == source.getId() || bot.getMapId() != mapId) {
                    continue;
                }
                Point botPos = bot.getPosition();
                if (botPos == null) {
                    continue;
                }
                long dx = (long) sourcePos.x - botPos.x;
                long dy = (long) sourcePos.y - botPos.y;
                if (dx * dx + dy * dy > maxDistSq) {
                    continue;
                }
                maybeReact(entry, success, scrollSuccessRate, now);
            }
        }
    }

    static void maybeReact(BotEntry entry, boolean success, int scrollSuccessRate, long now) {
        if (entry == null || entry.bot == null) {
            return;
        }

        int streak = updateReactionStreak(entry, success, now);
        double load = recordReactionLoad(entry, now);
        if (now < entry.nextScrollReactionAtMs) {
            return;
        }

        double chanceScale = reactionChanceScale(load)
                * successRateChanceScale(scrollSuccessRate)
                * streakChanceScale(streak, success, scrollSuccessRate);
        if (chanceScale <= 0.0) {
            return;
        }

        boolean reacted = false;
        if (rollPercent(EMOTE_CHANCE_PCT, chanceScale)) {
            entry.bot.changeFaceExpression(success ? successExpression() : failedExpression());
            reacted = true;
        }

        if (rollPercent(CHAT_CHANCE_PCT, chanceScale) && shouldQueueChat(entry)) {
            BotChatManager.queueBotSay(entry, selectChatLine(success, streak, scrollSuccessRate));
            reacted = true;
        }

        if (rollPercent(FIDGET_CHANCE_PCT, chanceScale)) {
            BotFidgetManager.maybeStartSocialFidget(entry);
            reacted = true;
        }

        if (reacted) {
            entry.nextScrollReactionAtMs = now + Math.max(0, REACTION_COOLDOWN_MS);
        }
    }

    static double recordReactionLoad(BotEntry entry, long now) {
        if (entry == null) {
            return 0.0;
        }

        long decayMs = Math.max(1, LOAD_DECAY_MS);
        double load = entry.recentScrollReactionLoad;
        if (entry.lastScrollReactionObservedAtMs > 0L && now > entry.lastScrollReactionObservedAtMs) {
            load *= Math.exp(-(double) (now - entry.lastScrollReactionObservedAtMs) / decayMs);
        }
        load += 1.0;
        entry.recentScrollReactionLoad = load;
        entry.lastScrollReactionObservedAtMs = now;
        return load;
    }

    static double reactionChanceScale(double load) {
        if (load <= 2.5) {
            return 1.0;
        }
        if (load <= 3.5) {
            return 0.35;
        }
        if (load <= 5.0) {
            return 0.12;
        }
        return 0.03;
    }

    static double successRateChanceScale(int scrollSuccessRate) {
        if (scrollSuccessRate <= 0) {
            return 1.0;
        }
        if (scrollSuccessRate <= 10) {
            return 1.3;
        }
        if (scrollSuccessRate <= 30) {
            return 1.18;
        }
        if (scrollSuccessRate >= 100) {
            return 0.18;
        }
        return 1.0;
    }

    static int updateReactionStreak(BotEntry entry, boolean success, long now) {
        if (entry == null) {
            return 0;
        }

        long windowMs = Math.max(1, STREAK_WINDOW_MS);
        if (entry.lastScrollReactionOutcomeAtMs == 0L
                || now - entry.lastScrollReactionOutcomeAtMs > windowMs
                || entry.lastScrollReactionWasSuccess != success) {
            entry.scrollReactionStreak = 1;
        } else {
            entry.scrollReactionStreak++;
        }
        entry.lastScrollReactionWasSuccess = success;
        entry.lastScrollReactionOutcomeAtMs = now;
        return entry.scrollReactionStreak;
    }

    static double streakChanceScale(int streak, boolean success, int scrollSuccessRate) {
        if (!success || streak < 2 || scrollSuccessRate >= 100) {
            return 1.0;
        }
        return Math.min(1.35, 1.0 + 0.12 * Math.min(3, streak - 1));
    }

    static boolean isStreakChatEligible(int streak, int scrollSuccessRate) {
        return streak >= 3 && scrollSuccessRate < 100;
    }

    static int successReactionCount() {
        return SCROLL_SUCCESS_REACTIONS.size();
    }

    static int failReactionCount() {
        return SCROLL_FAIL_REACTIONS.size();
    }

    static int successStreakReactionCount() {
        return SCROLL_SUCCESS_STREAK_REACTIONS.size();
    }

    static int failStreakReactionCount() {
        return SCROLL_FAIL_STREAK_REACTIONS.size();
    }

    static int streakWindowMs() {
        return STREAK_WINDOW_MS;
    }

    private static String selectChatLine(boolean success, int streak, int scrollSuccessRate) {
        if (isStreakChatEligible(streak, scrollSuccessRate) && ThreadLocalRandom.current().nextInt(100) < SCROLL_STREAK_REACTION_CHANCE) {
            return BotManager.randomReply(success ? SCROLL_SUCCESS_STREAK_REACTIONS : SCROLL_FAIL_STREAK_REACTIONS);
        }
        return BotManager.randomReply(success ? SCROLL_SUCCESS_REACTIONS : SCROLL_FAIL_REACTIONS);
    }

    private static int resolveScrollSuccessRate(int scrollItemId) {
        if (scrollItemId <= 0) {
            return 0;
        }
        Map<String, Integer> stats = ItemInformationProvider.getInstance().getEquipStats(scrollItemId);
        if (stats == null) {
            return 0;
        }
        Integer success = stats.get("success");
        return success == null ? 0 : success;
    }

    private static boolean rollPercent(int baseChancePct, double chanceScale) {
        int chance = (int) Math.round(Math.max(0.0, Math.min(100.0, baseChancePct * chanceScale)));
        return chance > 0 && ThreadLocalRandom.current().nextInt(100) < chance;
    }

    private static boolean shouldQueueChat(BotEntry entry) {
        synchronized (entry.msgQueue) {
            return !entry.msgSending && entry.msgQueue.isEmpty();
        }
    }

    private static int successExpression() {
        return ThreadLocalRandom.current().nextInt(100) < 12
                ? Emote.DISTURBED.getValue()
                : Emote.HAPPY.getValue();
    }

    private static int failedExpression() {
        Emote[] options = {
                Emote.GLARE,
                Emote.SAD,
                Emote.ANGRY,
                Emote.DISTURBED,
                Emote.EMBARRASSED
        };
        return options[ThreadLocalRandom.current().nextInt(options.length)].getValue();
    }
}
