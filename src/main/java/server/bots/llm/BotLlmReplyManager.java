package server.bots.llm;

import client.Character;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates LLM-backed bot chat replies. Stays out of the game loop:
 * - All Ollama calls run on a dedicated executor with a small thread cap.
 * - A global semaphore caps concurrent inferences so a busy host doesn't thrash.
 * - Per-bot in-flight gate prevents queueing multiple replies for one bot.
 * - All errors are swallowed; LLM failures must never crash a bot tick.
 */
public final class BotLlmReplyManager {
    private static final Logger log = LoggerFactory.getLogger(BotLlmReplyManager.class);

    private static final ScheduledExecutorService EXEC = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "bot-llm");
        t.setDaemon(true);
        return t;
    });

    private static volatile Semaphore globalGate = new Semaphore(BotLlmConfig.maxConcurrentGlobal);
    private static volatile int gateCapacity = BotLlmConfig.maxConcurrentGlobal;

    /** Tracks per-bot in-flight requests so we don't queue more than one. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, AtomicInteger> inflightByBotId =
            new java.util.concurrent.ConcurrentHashMap<>();

    private BotLlmReplyManager() {}

    private static Semaphore gate() {
        int cap = BotLlmConfig.maxConcurrentGlobal;
        if (cap != gateCapacity) {
            synchronized (BotLlmReplyManager.class) {
                if (cap != gateCapacity) {
                    globalGate = new Semaphore(cap);
                    gateCapacity = cap;
                }
            }
        }
        return globalGate;
    }

    public static void maybeRespond(BotEntry entry, Character sender, String message) {
        if (!BotLlmConfig.enabled) return;
        if (entry == null || entry.getBot() == null || sender == null) return;
        if (message == null || message.isBlank()) return;

        SenderRelation relation = SenderRelation.resolve(entry, sender);
        // Strangers' whispers are dropped pre-LLM (whisper hook isn't wired in
        // Phase 1 anyway, but defend in depth).
        if (relation == SenderRelation.STRANGER && entry.getReplyChannel() == server.bots.ReplyChannel.WHISPER) {
            return;
        }

        int botId = entry.getBot().getId();
        AtomicInteger inflight = inflightByBotId.computeIfAbsent(botId, k -> new AtomicInteger(0));
        if (!compareAndIncrement(inflight, 0, 1)) {
            // already 1 in-flight for this bot; drop this one
            return;
        }

        Semaphore g = gate();
        if (!g.tryAcquire()) {
            inflight.decrementAndGet();
            return;
        }

        final String senderName = sender.getName();
        EXEC.submit(() -> {
            try {
                runReply(entry, senderName, relation, message);
            } catch (Throwable t) {
                log.warn("llm reply failed: {}", t.toString());
            } finally {
                g.release();
                inflight.decrementAndGet();
            }
        });
    }

    private static void runReply(BotEntry entry, String senderName, SenderRelation relation, String message) {
        String botName = entry.getBot().getName();
        String summary = BotMemoryStore.loadSummary(botName);
        List<BotMemoryStore.Turn> recent = BotMemoryStore.loadRecent(botName, BotLlmConfig.recentTurnsInPrompt);
        String system = PromptBuilder.buildSystem(entry, relation, senderName);
        String prompt = PromptBuilder.buildPrompt(entry, senderName, message, summary, recent);

        long t0 = System.currentTimeMillis();
        if (BotLlmConfig.debugLog) {
            log.info("llm[{}] <- {}: {}", botName, senderName, message);
            log.info("llm[{}] system: {}", botName, system);
            log.info("llm[{}] prompt ({} chars, {} recent turns, num_ctx={}, num_predict={}):\n{}",
                    botName, prompt.length(), recent.size(),
                    BotLlmConfig.numCtx, BotLlmConfig.maxPredictTokens, prompt);
        }

        Optional<String> raw = OllamaClient.generate(prompt, system);
        long elapsed = System.currentTimeMillis() - t0;

        if (raw.isEmpty()) {
            if (BotLlmConfig.debugLog) log.info("llm[{}] no reply ({} ms)", botName, elapsed);
            return;
        }
        String reply = sanitize(raw.get());
        if (BotLlmConfig.debugLog) {
            log.info("llm[{}] raw ({} ms, {} chars): {}", botName, elapsed, raw.get().length(), raw.get());
            log.info("llm[{}] sanitized ({} chars): {}", botName, reply.length(), reply);
        }
        if (reply.isEmpty()) return;

        List<String> parts = splitForChat(reply, BotLlmConfig.maxReplyMessages,
                BotLlmConfig.maxReplyCharsPerMessage);
        if (BotLlmConfig.debugLog && parts.size() > 1) {
            log.info("llm[{}] split into {} messages", botName, parts.size());
        }

        BotManager bm = BotManager.getInstance();
        bm.botReply(entry, parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            final String part = parts.get(i);
            EXEC.schedule(() -> {
                try { bm.botReply(entry, part); }
                catch (Throwable t) { log.warn("llm follow-up reply failed: {}", t.toString()); }
            }, (long) BotLlmConfig.multiMessageDelayMs * i, TimeUnit.MILLISECONDS);
        }

        BotMemoryStore.appendTurn(botName,
                new BotMemoryStore.Turn(System.currentTimeMillis(),
                        relation.name().toLowerCase(), senderName, message, reply));

        if (BotMemoryStore.countTurns(botName) >= BotLlmConfig.compactThresholdTurns) {
            EXEC.submit(() -> BotMemoryStore.compact(botName));
        }
    }

    /**
     * Split a multi-sentence reply into up to maxParts chat messages, each
     * <= maxCharsPerPart chars. Splits on sentence boundaries first, then on
     * word boundaries when a single sentence overflows. Anything that won't
     * fit in maxParts is discarded — the model already capped to num_predict.
     */
    static List<String> splitForChat(String text, int maxParts, int maxCharsPerPart) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>(maxParts);
        if (text == null || text.isBlank() || maxParts <= 0) return out;
        if (maxParts == 1 || text.length() <= maxCharsPerPart) {
            out.add(text.length() > maxCharsPerPart ? text.substring(0, maxCharsPerPart).trim() : text);
            return out;
        }

        // Greedy: pack sentences into a part until it would overflow.
        java.util.List<String> sentences = splitSentences(text);
        StringBuilder cur = new StringBuilder();
        for (String s : sentences) {
            if (s.isEmpty()) continue;
            if (cur.length() == 0) {
                if (s.length() <= maxCharsPerPart) cur.append(s);
                else cur.append(s, 0, maxCharsPerPart);
            } else if (cur.length() + 1 + s.length() <= maxCharsPerPart) {
                cur.append(' ').append(s);
            } else {
                out.add(cur.toString());
                if (out.size() >= maxParts) return out;
                cur.setLength(0);
                cur.append(s.length() <= maxCharsPerPart ? s : s.substring(0, maxCharsPerPart));
            }
        }
        if (cur.length() > 0 && out.size() < maxParts) out.add(cur.toString());
        return out;
    }

    private static java.util.List<String> splitSentences(String text) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                int end = i + 1;
                String sent = text.substring(start, end).trim();
                if (!sent.isEmpty()) out.add(sent);
                start = end;
            }
        }
        if (start < text.length()) {
            String tail = text.substring(start).trim();
            if (!tail.isEmpty()) out.add(tail);
        }
        if (out.isEmpty() && !text.isBlank()) out.add(text.trim());
        return out;
    }

    private static boolean compareAndIncrement(AtomicInteger v, int expected, int newVal) {
        return v.compareAndSet(expected, newVal);
    }

    /**
     * Strip thinking-mode tags, collapse whitespace, cap length, drop common
     * model preambles. Best-effort cleanup so output looks like in-game chat.
     */
    static String sanitize(String raw) {
        if (raw == null) return "";
        String s = raw;
        // qwen3-style thinking tags
        int think = s.indexOf("</think>");
        if (think >= 0) s = s.substring(think + "</think>".length());
        // strip surrounding quotes
        s = s.trim();
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            s = s.substring(1, s.length() - 1);
        }
        // collapse all whitespace (incl newlines) — multi-message split happens later on sentence boundaries
        s = s.replaceAll("\\s+", " ").trim();
        // drop leading "Bot:" / "Reply:" preambles
        s = s.replaceFirst("^(?i)(reply|bot|response|assistant)\\s*:\\s*", "");
        int cap = BotLlmConfig.maxReplyChars();
        if (s.length() > cap) {
            s = s.substring(0, cap).trim();
        }
        return s;
    }
}
