package server.monitoring;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

public final class CharacterSaveDiagnostics {
    public enum SaveReason {
        AUTO_SAVE,
        FULL_SAVE,
        LOGOUT,
        SERVER_TRANSITION,
        CASHSHOP,
        MTS,
        MERCHANT,
        SAVE_ALL,
        WARP_WORLD
    }

    private static final AtomicLong totalSaves = new AtomicLong();
    private static final AtomicLong failedSaves = new AtomicLong();
    private static final AtomicLong autosaves = new AtomicLong();
    private static final AtomicLong manualSaves = new AtomicLong();
    private static final AtomicLong skippedCleanAutosaves = new AtomicLong();
    private static final AtomicLong totalDurationMs = new AtomicLong();
    private static final AtomicLong maxDurationMs = new AtomicLong();
    private static final AtomicLong lastDurationMs = new AtomicLong();
    private static final AtomicLong lastCharacterId = new AtomicLong();
    private static final ConcurrentMap<String, AtomicLong> reasonCounts = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, SectionStats> sections = new ConcurrentHashMap<>();
    private static volatile String lastCharacterName = "";
    private static volatile String slowestCharacterName = "";

    private CharacterSaveDiagnostics() {
    }

    public static void recordSuccess(int characterId, String characterName, boolean notAutosave, long startedNs) {
        recordSuccess(characterId, characterName, notAutosave, defaultReason(notAutosave), startedNs);
    }

    public static void recordSuccess(int characterId, String characterName, boolean notAutosave, SaveReason reason, long startedNs) {
        record(characterId, characterName, notAutosave, reason, startedNs, false);
    }

    public static void recordFailure(int characterId, String characterName, boolean notAutosave, long startedNs) {
        recordFailure(characterId, characterName, notAutosave, defaultReason(notAutosave), startedNs);
    }

    public static void recordFailure(int characterId, String characterName, boolean notAutosave, SaveReason reason, long startedNs) {
        record(characterId, characterName, notAutosave, reason, startedNs, true);
    }

    public static String diagnostics() {
        long saves = totalSaves.get();
        long avgMs = saves == 0 ? 0 : totalDurationMs.get() / saves;
        return "charSaves total=" + saves
                + " failed=" + failedSaves.get()
                + " manual=" + manualSaves.get()
                + " autosave=" + autosaves.get()
                + " skippedClean=" + skippedCleanAutosaves.get()
                + " avgMs=" + avgMs
                + " maxMs=" + maxDurationMs.get()
                + " maxChr=" + slowestCharacterName
                + " lastMs=" + lastDurationMs.get()
                + " lastChr=" + lastCharacterName
                + "(" + lastCharacterId.get() + ")";
    }

    public static String reasonDiagnostics() {
        if (reasonCounts.isEmpty()) {
            return "saveReasons=none";
        }

        StringBuilder sb = new StringBuilder("saveReasons");
        reasonCounts.entrySet().stream()
                .sorted((left, right) -> left.getKey().compareTo(right.getKey()))
                .forEach(entry -> sb.append(' ')
                        .append(entry.getKey())
                        .append('=')
                        .append(entry.getValue().get()));
        return sb.toString();
    }

    public static String sectionDiagnostics() {
        if (sections.isEmpty()) {
            return "saveSections=none";
        }

        StringBuilder sb = new StringBuilder("saveSections");
        sections.entrySet().stream()
                .sorted((left, right) -> Long.compare(right.getValue().maxMs.get(), left.getValue().maxMs.get()))
                .limit(8)
                .forEach(entry -> {
                    SectionStats stats = entry.getValue();
                    long count = stats.count.get();
                    long avgMs = count == 0 ? 0 : stats.totalMs.get() / count;
                    sb.append(' ')
                            .append(entry.getKey())
                            .append("[count=").append(count)
                            .append(",avgMs=").append(avgMs)
                            .append(",maxMs=").append(stats.maxMs.get())
                            .append(']');
                });
        return sb.toString();
    }

    public static void recordSection(String section, long startedNs) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        sections.computeIfAbsent(section, ignored -> new SectionStats()).record(elapsedMs);
    }

    public static void recordSkipped(int characterId, String characterName, SaveReason reason) {
        skippedCleanAutosaves.incrementAndGet();
        reasonCounts.computeIfAbsent(reason.name() + "_SKIPPED", ignored -> new AtomicLong()).incrementAndGet();
        lastCharacterId.set(characterId);
        lastCharacterName = characterName;
    }

    private static void record(int characterId, String characterName, boolean notAutosave, SaveReason reason, long startedNs, boolean failed) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        totalSaves.incrementAndGet();
        reasonCounts.computeIfAbsent(reason.name(), ignored -> new AtomicLong()).incrementAndGet();
        if (failed) {
            failedSaves.incrementAndGet();
        }
        if (notAutosave) {
            manualSaves.incrementAndGet();
        } else {
            autosaves.incrementAndGet();
        }
        totalDurationMs.addAndGet(elapsedMs);
        lastDurationMs.set(elapsedMs);
        lastCharacterId.set(characterId);
        lastCharacterName = characterName;
        recordMax(elapsedMs, characterName);
    }

    private static SaveReason defaultReason(boolean notAutosave) {
        return notAutosave ? SaveReason.FULL_SAVE : SaveReason.AUTO_SAVE;
    }

    private static void recordMax(long elapsedMs, String characterName) {
        long current;
        do {
            current = maxDurationMs.get();
            if (elapsedMs <= current) {
                return;
            }
        } while (!maxDurationMs.compareAndSet(current, elapsedMs));
        slowestCharacterName = characterName;
    }

    private static final class SectionStats {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong totalMs = new AtomicLong();
        private final AtomicLong maxMs = new AtomicLong();

        private void record(long elapsedMs) {
            count.incrementAndGet();
            totalMs.addAndGet(elapsedMs);
            long current;
            do {
                current = maxMs.get();
                if (elapsedMs <= current) {
                    return;
                }
            } while (!maxMs.compareAndSet(current, elapsedMs));
        }
    }
}
