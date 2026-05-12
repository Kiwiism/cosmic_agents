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
    private static final int EMOTE_CHANCE_PCT = 20;
    private static final int CHAT_CHANCE_PCT = 15;
    private static final int FIDGET_CHANCE_PCT = 10;
    private static final int REACTION_COOLDOWN_MS = 10_000;
    private static final int LOAD_DECAY_MS = 60_000;
    private static final int STREAK_WINDOW_MS = 45_000;
    private static final int PER_BOT_REACTION_JITTER_MAX_MS = 2_000;
    private static final int STREAK_PRUNE_INTERVAL_MS = 60_000;
    private static final List<String> SCROLL_SUCCESS_REACTIONS = List.of(
            "nice", "nice!", "nice one", "yay", "yay!",
            "clean", "clean!", "let's go gambling!",
            "juicy", "good stuff",
            "anything good?",
            "whachu scrollin?",
            "thats a hit",
            "good stuff",
            "we take those",
            "big pass",
            "solid",
            "not bad",
            "blessed", "dayum",
            "yoo nice", ":)", ":D", "again!", "do it again!");
    private static final List<String> SCROLL_FAIL_REACTIONS = List.of(
            "rip", "f", "F", "noo", "nooooo", "bad luck",
            "aw", "aww", ":(", "sad",
            "bruh", "oof", "pain", "rough",
            "tragic", "unlucky", "dayum",
            "scroll said no", "maybe next one", "happens");
    private static final List<String> SCROLL_SUCCESS_STREAK_REACTIONS = List.of(
            "ok youre cookin", "you are cooking!!", "!!!!", "!!!!!!!!", "OMFG", "OMGGG", "WOWZA",
            "thats a combo", "super combo", "woww", "WOWWWW", "HOLYYY", "HOLY", "holyyy", "sheeshhh", "omg!!", "wtf", "WTF", "WTF!!",
            "cant miss rn", "no miss!", "CRAZY", "CRAZY!!", "AMAZING", "HOW MANY WAS THAT!",
            "buddy is on a run", "today is your day!",
            "save some luck for us", "crazy", "that's crazyyyyy", "crazy man", "crazy! man",
            "another one?", "another one", "MORE", "MOARRR", "another one!", "again!again!", "again!", "AGAIN!",
            "daaaaaamn", "DAYUM!");
    private static final List<String> SCROLL_FAIL_STREAK_REACTIONS = List.of(
            "ok thats cursed", "cursed af", "90% of gambler something something",
            "that streak is nasty", "nasty streak man", "fak",
            "nah id stop there", "not your day man",
            "map is eating your scrolls", "try a different map? xD", "change map bro",
            "someone break the curse",
            "scroll tax goin crazy",
            "the odds hate you rn",
            "ok thats just mean",
            "this map is cold");

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
                long botDelayMs = BotManager.randMs(0, PER_BOT_REACTION_JITTER_MAX_MS + 1);
                long reactionAtMs = now + botDelayMs;
                BotManager.after(botDelayMs, () -> maybeReact(entry, source.getId(), success, scrollSuccessRate, reactionAtMs));
            }
        }
    }

    static void maybeReact(BotEntry entry, int scrollerId, boolean success, int scrollSuccessRate, long now) {
        if (entry == null || entry.bot == null) {
            return;
        }

        int streak = updateReactionStreak(entry, scrollerId, success, now);
        double load = recordReactionLoad(entry, now);
        if (now < entry.nextScrollReactionAtMs) {
            return;
        }

        double chanceScale = (streak >= 2 ? 1.0 : reactionChanceScale(load))
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
            return 0.6;
        }
        if (load <= 5.0) {
            return 0.4;
        }
        return 0.3;
    }

    static double successRateChanceScale(int scrollSuccessRate) {
        if (scrollSuccessRate <= 20) {
            return 3;
        }
        if (scrollSuccessRate <= 40) {
            return 2;
        }
        if (scrollSuccessRate <= 80) {
            return 1.0;
        }
        if (scrollSuccessRate <= 90) {
            return 0.5;
        }
        return 0.25;
    }

    static int updateReactionStreak(BotEntry entry, int scrollerId, boolean success, long now) {
        if (entry == null || scrollerId <= 0) {
            return 0;
        }

        pruneStreaks(entry, now);
        long windowMs = Math.max(1, STREAK_WINDOW_MS);
        BotEntry.ScrollReactionStreakState state = entry.scrollReactionStreaksByScroller
                .computeIfAbsent(scrollerId, ignored -> new BotEntry.ScrollReactionStreakState());
        if (state.lastOutcomeAtMs == 0L
                || now - state.lastOutcomeAtMs > windowMs
                || state.lastWasSuccess != success) {
            state.streak = 1;
        } else {
            state.streak++;
        }
        state.lastWasSuccess = success;
        state.lastOutcomeAtMs = now;
        return state.streak;
    }

    static double streakChanceScale(int streak, boolean success, int scrollSuccessRate) {
        if (streak < 2 || scrollSuccessRate >= 100) {
            return 1.0;
        }
        if (success) {
            double successRateInverse = 100.0 / scrollSuccessRate;
            return Math.min(1.5, 1.0 + (0.2 * streak * streak) * successRateInverse);
        } else {
            return Math.min(1.5, 1.0 + (0.1 * streak * streak));
        }
    }

    static boolean isStreakChatEligible(int streak, int scrollSuccessRate) {
        return streak >= 3 && scrollSuccessRate < 100;
    }

    static int streakWindowMs() {
        return STREAK_WINDOW_MS;
    }

    private static String selectChatLine(boolean success, int streak, int scrollSuccessRate) {
        if (isStreakChatEligible(streak, scrollSuccessRate) && ThreadLocalRandom.current().nextInt(100) < 75) {
            return BotManager.randomReply(success ? SCROLL_SUCCESS_STREAK_REACTIONS : SCROLL_FAIL_STREAK_REACTIONS);
        } // 75% chance to use streak chat
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

    private static void pruneStreaks(BotEntry entry, long now) {
        if (now < entry.nextScrollReactionStreakPruneAtMs) {
            return;
        }
        long cutoff = now - STREAK_WINDOW_MS;
        entry.scrollReactionStreaksByScroller.entrySet()
                .removeIf(it -> it.getValue() == null || it.getValue().lastOutcomeAtMs < cutoff);
        entry.nextScrollReactionStreakPruneAtMs = now + STREAK_PRUNE_INTERVAL_MS;
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
