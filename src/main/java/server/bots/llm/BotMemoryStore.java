package server.bots.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-bot conversation memory. Plain-text, on disk, gitignored.
 * - <name>.jsonl     : append-only turn log, one JSON-ish line per exchange
 * - <name>.summary.txt : compacted long-term memory, plain text
 *
 * Compaction: when jsonl exceeds compactThresholdTurns, the oldest turns are
 * summarized (or just dropped if Ollama is unavailable) and the file is
 * truncated to keep only the newest compactKeepRecentTurns lines.
 */
public final class BotMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(BotMemoryStore.class);

    public record Turn(long ts, String relation, String sender, String msg, String reply) {}

    private static final ConcurrentHashMap<String, AtomicBoolean> COMPACTION_LOCKS = new ConcurrentHashMap<>();

    private BotMemoryStore() {}

    public static synchronized void ensureDir() {
        try {
            Path dir = Paths.get(BotLlmConfig.memoryDir);
            Files.createDirectories(dir);
            Path ignore = dir.resolve(".gitignore");
            if (!Files.exists(ignore)) {
                Files.writeString(ignore, "*\n!.gitignore\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("memory: ensureDir failed: {}", e.toString());
        }
    }

    public static String loadSummary(String botName) {
        Path p = summaryPath(botName);
        if (!Files.exists(p)) return "";
        try {
            return Files.readString(p, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /** Returns the most recent N turns, oldest first. */
    public static List<Turn> loadRecent(String botName, int n) {
        Path p = jsonlPath(botName);
        if (!Files.exists(p)) return List.of();
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            List<String> all = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.isBlank()) all.add(line);
            }
            int from = Math.max(0, all.size() - n);
            List<Turn> out = new ArrayList<>(all.size() - from);
            for (int i = from; i < all.size(); i++) {
                Turn t = parseTurn(all.get(i));
                if (t != null) out.add(t);
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static void appendTurn(String botName, Turn turn) {
        ensureDir();
        Path p = jsonlPath(botName);
        String line = serializeTurn(turn) + "\n";
        try {
            Files.writeString(p, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("memory: append failed for {}: {}", botName, e.toString());
        }
    }

    public static int countTurns(String botName) {
        Path p = jsonlPath(botName);
        if (!Files.exists(p)) return 0;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            int n = 0;
            while (br.readLine() != null) n++;
            return n;
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Compact: summarize oldest turns into the .summary file (best-effort via
     * Ollama; falls back to drop-only on failure), then keep only the newest
     * compactKeepRecentTurns lines in jsonl. Idempotent: held under a per-bot
     * lock so concurrent calls noop.
     */
    public static void compact(String botName) {
        AtomicBoolean lock = COMPACTION_LOCKS.computeIfAbsent(botName, k -> new AtomicBoolean(false));
        if (!lock.compareAndSet(false, true)) return;
        try {
            Path p = jsonlPath(botName);
            if (!Files.exists(p)) return;
            List<String> all;
            try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                all = new ArrayList<>();
                String line;
                while ((line = br.readLine()) != null) all.add(line);
            }
            int keep = BotLlmConfig.compactKeepRecentTurns;
            if (all.size() <= keep) return;

            int splitAt = all.size() - keep;
            List<String> oldLines = all.subList(0, splitAt);

            String previousSummary = loadSummary(botName);
            String newSummary = trySummarize(botName, previousSummary, oldLines);
            if (newSummary != null && !newSummary.isBlank()) {
                Files.writeString(summaryPath(botName), newSummary.trim() + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
            }

            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (int i = splitAt; i < all.size(); i++) {
                    bw.write(all.get(i));
                    bw.write('\n');
                }
            }
            log.info("memory: compacted {} ({} → {} lines)", botName, all.size(), keep);
        } catch (IOException e) {
            log.warn("memory: compact failed for {}: {}", botName, e.toString());
        } finally {
            lock.set(false);
        }
    }

    private static String trySummarize(String botName, String previousSummary, List<String> oldLines) {
        if (!BotLlmConfig.enabled) return null;
        StringBuilder convo = new StringBuilder();
        if (!previousSummary.isBlank()) {
            convo.append("Prior memory: ").append(previousSummary).append("\n\n");
        }
        convo.append("Recent chat to summarize:\n");
        for (String l : oldLines) {
            Turn t = parseTurn(l);
            if (t == null) continue;
            convo.append(t.sender).append(" (").append(t.relation).append("): ").append(t.msg).append("\n");
            convo.append(botName).append(": ").append(t.reply).append("\n");
        }
        String sys = "You compress chat memory. Reply with 1-2 short sentences capturing who the speakers are and what they care about. No preamble.";
        return OllamaClient.generate(convo.toString(), sys).orElse(null);
    }

    private static Path jsonlPath(String botName) {
        return Paths.get(BotLlmConfig.memoryDir, sanitize(botName) + ".jsonl");
    }

    private static Path summaryPath(String botName) {
        return Paths.get(BotLlmConfig.memoryDir, sanitize(botName) + ".summary.txt");
    }

    private static String sanitize(String name) {
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') out.append(c);
            else out.append('_');
        }
        return out.length() == 0 ? "_" : out.toString();
    }

    static String serializeTurn(Turn t) {
        return "{\"ts\":" + t.ts
                + ",\"rel\":\"" + OllamaClient.jsonEscape(t.relation) + "\""
                + ",\"who\":\"" + OllamaClient.jsonEscape(t.sender) + "\""
                + ",\"msg\":\"" + OllamaClient.jsonEscape(t.msg) + "\""
                + ",\"reply\":\"" + OllamaClient.jsonEscape(t.reply) + "\"}";
    }

    static Turn parseTurn(String line) {
        try {
            long ts = Long.parseLong(extractField(line, "\"ts\":"));
            String rel = extractStringField(line, "\"rel\":\"");
            String who = extractStringField(line, "\"who\":\"");
            String msg = extractStringField(line, "\"msg\":\"");
            String reply = extractStringField(line, "\"reply\":\"");
            return new Turn(ts, rel, who, msg, reply);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractField(String line, String key) {
        int i = line.indexOf(key);
        if (i < 0) return "0";
        i += key.length();
        int end = i;
        while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '-')) end++;
        return line.substring(i, end);
    }

    private static String extractStringField(String line, String key) {
        int i = line.indexOf(key);
        if (i < 0) return "";
        i += key.length();
        StringBuilder out = new StringBuilder();
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(i + 1);
                switch (n) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(n);
                }
                i += 2;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}
