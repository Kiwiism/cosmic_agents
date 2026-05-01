package client;

import client.BotClient;
import net.server.world.PartyCharacter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks EXP gains per character over time for debugging bot vs player EXP differences.
 * Activated via @expdebug command.
 */
public class ExpDebugTracker {
    private static final String LOG_BASE_DIR = "D:/GameServers/Maplestory/Cosmic/logs/expdebug/";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    /** Per-character tracking state */
    public static class ExpSession {
        public final int characterId;
        public final String characterName;
        public final int level;
        public final boolean isBot;
        public long startTime;
        public final AtomicLong totalPersonalExp = new AtomicLong(0);
        public final AtomicLong totalPartyExp = new AtomicLong(0);
        public final AtomicLong totalEquipExp = new AtomicLong(0);
        public final AtomicLong expGainCount = new AtomicLong(0);
        public volatile boolean active = true;

        public ExpSession(int characterId, String characterName, int level, boolean isBot) {
            this.characterId = characterId;
            this.characterName = characterName;
            this.level = level;
            this.isBot = isBot;
            this.startTime = System.currentTimeMillis();
        }

        public long elapsedSeconds() {
            return (System.currentTimeMillis() - startTime) / 1000;
        }

        public double expPerMinute() {
            long seconds = elapsedSeconds();
            if (seconds < 1) {
                return 0;
            }
            return ((double) (totalPersonalExp.get() + totalPartyExp.get())) / seconds * 60.0;
        }
    }

    // Active tracking sessions keyed by character ID
    private static final Map<Integer, ExpSession> activeSessions = new ConcurrentHashMap<>();
    // Global flag: is tracking enabled server-wide
    private static volatile boolean trackingEnabled = false;

    public static boolean isTrackingEnabled() {
        return trackingEnabled;
    }

    public static void setTrackingEnabled(boolean enabled) {
        trackingEnabled = enabled;
    }

    /**
     * Record an EXP gain event for a character.
     * Called from Character.gainExpInternal().
     */
    public static void recordExpGain(Character chr, int personalExp, int partyExp, int equipExp) {
        if (!trackingEnabled) {
            return;
        }

        int chrId = chr.getId();
        ExpSession session = activeSessions.get(chrId);

        if (session == null) {
            // Auto-create session on first EXP gain if tracking is enabled
            boolean isBot = chr.getClient() instanceof BotClient;
            session = new ExpSession(chrId, chr.getName(), chr.getLevel(), isBot);
            ExpSession existing = activeSessions.putIfAbsent(chrId, session);
            if (existing != null) {
                session = existing;
            }
        }

        if (session.active) {
            session.totalPersonalExp.addAndGet(personalExp);
            session.totalPartyExp.addAndGet(partyExp);
            session.totalEquipExp.addAndGet(equipExp);
            session.expGainCount.incrementAndGet();
        }
    }

    /**
     * Start tracking for all party members of the given character.
     * Returns the list of sessions created/updated.
     */
    public static List<ExpSession> startPartyTracking(Character leader) {
        List<ExpSession> sessions = new ArrayList<>();
        Map<Integer, Character> members = getTrackedPartyMembers(leader);

        // Party members already include bots (bots join via party system)
        // No additional bot discovery needed

        for (Character member : members.values()) {
            boolean isBot = member.getClient() instanceof BotClient;
            ExpSession session = new ExpSession(member.getId(), member.getName(), member.getLevel(), isBot);
            ExpSession existing = activeSessions.putIfAbsent(member.getId(), session);
            if (existing != null) {
                // Reset existing session
                existing.totalPersonalExp.set(0);
                existing.totalPartyExp.set(0);
                existing.totalEquipExp.set(0);
                existing.expGainCount.set(0);
                existing.startTime = System.currentTimeMillis();
                existing.active = true;
                sessions.add(existing);
            } else {
                sessions.add(session);
            }
        }

        return sessions;
    }

    /**
     * Stop tracking for all party members and return sessions.
     */
    public static List<ExpSession> stopPartyTracking(Character leader) {
        List<ExpSession> sessions = new ArrayList<>();
        Set<Integer> memberIds = getTrackedPartyMemberIds(leader);

        // Party members already include bots (no separate discovery needed)

        for (int id : memberIds) {
            ExpSession session = activeSessions.get(id);
            if (session != null) {
                session.active = false;
                sessions.add(session);
            }
        }

        return sessions;
    }

    /**
     * Return current tracked sessions for all party members without mutating counters.
     */
    public static List<ExpSession> getPartyTrackingSessions(Character leader) {
        List<ExpSession> sessions = new ArrayList<>();
        Set<Integer> memberIds = getTrackedPartyMemberIds(leader);

        for (int id : memberIds) {
            ExpSession session = activeSessions.get(id);
            if (session != null && session.active) {
                sessions.add(session);
            }
        }

        return sessions;
    }

    private static Map<Integer, Character> getTrackedPartyMembers(Character leader) {
        Map<Integer, Character> members = new LinkedHashMap<>();
        members.put(leader.getId(), leader);

        if (leader.getParty() != null) {
            for (PartyCharacter pc : leader.getParty().getMembers()) {
                Character member = pc.getPlayer();
                if (member != null && member.isAlive() && member.getMapId() == leader.getMapId()) {
                    members.put(member.getId(), member);
                }
            }
        }

        return members;
    }

    private static Set<Integer> getTrackedPartyMemberIds(Character leader) {
        Set<Integer> memberIds = new LinkedHashSet<>();
        memberIds.add(leader.getId());

        if (leader.getParty() != null) {
            for (PartyCharacter pc : leader.getParty().getMembers()) {
                Character member = pc.getPlayer();
                if (member != null) {
                    memberIds.add(member.getId());
                }
            }
        }

        return memberIds;
    }

    /**
     * Format tracking results as chat messages.
     */
    public static List<String> formatResults(List<ExpSession> sessions) {
        List<String> lines = new ArrayList<>();
        lines.add("--- EXP Debug Report (" + sessions.size() + " members) ---");

        for (ExpSession s : sessions) {
            String type = s.isBot ? "[BOT]" : "[CHR]";
            long totalExp = s.totalPersonalExp.get() + s.totalPartyExp.get();
            String line = String.format(Locale.US, "%s %s (Lvl %d) | Total: %,d | Personal: %,d | Party: %,d | Equip: %,d | Gains: %,d | EXP/min: %,.0f",
                    type, s.characterName, s.level, totalExp,
                    s.totalPersonalExp.get(), s.totalPartyExp.get(),
                    s.totalEquipExp.get(), s.expGainCount.get(),
                    s.expPerMinute());
            lines.add(line);
        }

        return lines;
    }

    /**
     * Write tracking results to a log file.
     */
    public static String saveResultsToFile(List<ExpSession> sessions) {
        try {
            File dir = new File(LOG_BASE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File file = new File(dir, "expdebug_" + timestamp + ".txt");

            try (FileWriter fw = new FileWriter(file)) {
                fw.write("EXP Debug Report\n");
                fw.write("Generated: " + LocalDateTime.now().format(FORMATTER) + "\n");
                fw.write("Members: " + sessions.size() + "\n");
                fw.write("Separator: |\n");
                fw.write("Header:\n");
                fw.write("Type,Name,Level,IsBot,TotalExp,PersonalExp,PartyExp,EquipExp,GainCount,ElapsedSeconds,EXPPerMinute\n");
                fw.write("Data:\n");

                for (ExpSession s : sessions) {
                    fw.write(String.format("%s,%s,%d,%b,%d,%d,%d,%d,%d,%d,%.2f\n",
                            s.isBot ? "BOT" : "CHR",
                            s.characterName,
                            s.level,
                            s.isBot,
                            s.totalPersonalExp.get() + s.totalPartyExp.get(),
                            s.totalPersonalExp.get(),
                            s.totalPartyExp.get(),
                            s.totalEquipExp.get(),
                            s.expGainCount.get(),
                            s.elapsedSeconds(),
                            s.expPerMinute()));
                }

                fw.write("\nDetailed breakdown:\n");
                for (ExpSession s : sessions) {
                    fw.write(String.format("\n%s %s (Lvl %d, ID: %d)\n",
                            s.isBot ? "[BOT]" : "[CHR]",
                            s.characterName, s.level, s.characterId));
                    fw.write(String.format(Locale.US, "  Total EXP: %,d\n", s.totalPersonalExp.get() + s.totalPartyExp.get()));
                    fw.write(String.format(Locale.US, "  Personal EXP: %,d\n", s.totalPersonalExp.get()));
                    fw.write(String.format(Locale.US, "  Party EXP: %,d\n", s.totalPartyExp.get()));
                    fw.write(String.format(Locale.US, "  Equip EXP: %,d\n", s.totalEquipExp.get()));
                    fw.write(String.format(Locale.US, "  EXP Gain Events: %,d\n", s.expGainCount.get()));
                    fw.write(String.format("  Elapsed: %d seconds\n", s.elapsedSeconds()));
                    fw.write(String.format("  EXP/minute: %.2f\n", s.expPerMinute()));
                }
            }

            return file.getAbsolutePath();
        } catch (IOException e) {
            return "ERROR: Failed to write log - " + e.getMessage();
        }
    }
}
