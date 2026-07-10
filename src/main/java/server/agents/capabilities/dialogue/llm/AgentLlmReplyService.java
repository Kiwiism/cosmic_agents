package server.agents.capabilities.dialogue.llm;

import server.agents.capabilities.dialogue.llm.AgentLlmConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeHandle;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.runtime.AgentBoundedExecutorFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestrates LLM-backed bot chat replies. Stays out of the game loop:
 * - All Ollama calls run on a dedicated executor with a small thread cap.
 * - A global semaphore caps concurrent inferences so a busy host doesn't thrash.
 * - Per-bot in-flight gate prevents queueing multiple replies for one bot.
 * - All errors are swallowed; LLM failures must never crash a bot tick.
 */
public final class AgentLlmReplyService {
    private static final Logger log = LoggerFactory.getLogger(AgentLlmReplyService.class);

    private static final ExecutorService EXEC = AgentBoundedExecutorFactory.fixed(
            "bot-llm",
            2,
            AgentBoundedExecutorFactory.positiveIntegerProperty("agents.async.llm.queueCapacity", 64));

    private static volatile Semaphore globalGate = new Semaphore(AgentLlmConfig.maxConcurrentGlobal);
    private static volatile int gateCapacity = AgentLlmConfig.maxConcurrentGlobal;

    /** Tracks per-bot in-flight requests so we don't queue more than one. */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, AtomicInteger> inflightByBotId =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<Integer, java.util.ArrayDeque<AgentMemoryStore.Turn>> recentMemoryByBotId =
            new java.util.concurrent.ConcurrentHashMap<>();

    private AgentLlmReplyService() {}

    public static void clearAgentRuntimeState(int agentId) {
        inflightByBotId.remove(agentId);
        recentMemoryByBotId.remove(agentId);
    }

    private static Semaphore gate() {
        int cap = AgentLlmConfig.maxConcurrentGlobal;
        if (cap != gateCapacity) {
            synchronized (AgentLlmReplyService.class) {
                if (cap != gateCapacity) {
                    globalGate = new Semaphore(cap);
                    gateCapacity = cap;
                }
            }
        }
        return globalGate;
    }

    public static <E extends AgentRuntimeHandle> void maybeRespond(
            AgentLlmReplyRequest<E> request,
            client.Character sender,
            String message,
            ReplyEmitter<E> replyEmitter) {
        if (!AgentLlmConfig.enabled) return;
        if (request == null || request.entry() == null || sender == null) return;
        if (message == null || message.isBlank()) return;

        // Strangers' whispers are dropped pre-LLM (whisper hook isn't wired in
        // Phase 1 anyway, but defend in depth).
        if (request.relation() == AgentSenderRelation.STRANGER
                && request.replyChannel() == AgentReplyChannel.WHISPER) {
            return;
        }

        int botId = request.agentId();
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
        try {
            EXEC.execute(() -> {
                try {
                    runReply(request, senderName, message, replyEmitter);
                } catch (Throwable t) {
                    log.warn("llm reply failed: {}", t.toString());
                } finally {
                    g.release();
                    inflight.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            g.release();
            inflight.decrementAndGet();
            log.warn("Dropped LLM reply because the Agent LLM queue is full");
        }
    }

    private static <E extends AgentRuntimeHandle> void runReply(
            AgentLlmReplyRequest<E> request,
            String senderName,
            String message,
            ReplyEmitter<E> replyEmitter) {
        String botName = request.agentName();
        int botId = request.agentId();
        // Use disk-backed memory only when enabled; otherwise keep a tiny recent in-memory window.
        String summary = AgentLlmConfig.memoryEnabled ? AgentMemoryStore.loadSummary(botName) : "";
        // Prompt shows ALL uncompacted turns (cursor..end). The summary covers everything before
        // cursor — together they're gap-free. Compaction (below) keeps the uncompacted window
        // bounded to recentTurnsInPrompt..recentTurnsInPrompt+compactBatchSize turns.
        List<AgentMemoryStore.Turn> recent = AgentLlmConfig.memoryEnabled
                ? AgentMemoryStore.loadUncompacted(botName)
                : loadRecentMemory(botId, System.currentTimeMillis());
        AgentLlmPromptContext promptContext = request.promptContext();
        long now = System.currentTimeMillis();
        String system = AgentPromptBuilder.buildSystem(promptContext.agent(), request.relation(), senderName);
        String prompt = AgentPromptBuilder.buildPrompt(
                promptContext.botName(),
                AgentSituationBuilder.build(
                        promptContext.agent(),
                        promptContext.map(),
                        promptContext.grinding(),
                        promptContext.following(),
                        promptContext.farmAnchorInCurrentMap(),
                        promptContext.lastOwnerCommand(),
                        promptContext.lastOwnerCommandAtMs(),
                        now),
                senderName,
                message,
                summary,
                recent);

        long t0 = System.currentTimeMillis();
        if (AgentLlmConfig.debugLog) {
            log.info("llm[{}] <- {}: {}", botName, senderName, message);
            log.info("llm[{}] system: {}", botName, system);
            log.info("llm[{}] prompt ({} chars, {} recent turns, num_ctx={}, num_predict={}):\n{}",
                    botName, prompt.length(), recent.size(),
                    AgentLlmConfig.numCtx, AgentLlmConfig.maxPredictTokens, prompt);
        }

        Optional<String> raw = OllamaClient.generate(prompt, system);
        long elapsed = System.currentTimeMillis() - t0;

        if (raw.isEmpty()) {
            if (AgentLlmConfig.debugLog) log.info("llm[{}] no reply ({} ms)", botName, elapsed);
            return;
        }
        String reply = sanitize(raw.get());
        if (AgentLlmConfig.debugLog) {
            log.info("llm[{}] raw ({} ms, {} chars): {}", botName, elapsed, raw.get().length(), raw.get());
            log.info("llm[{}] sanitized ({} chars): {}", botName, reply.length(), reply);
        }
//        if (looksLowQuality(message, reply)) {
//            String fallback = fallbackReply(message);
//            if (AgentLlmConfig.debugLog) {
//                log.info("llm[{}] rejected low-quality reply: {}, fallback: {}", botName, reply, fallback);
//            }
//            reply = fallback;
//        }
        if (reply.isEmpty()) return;
        if (request.entry() instanceof AgentRuntimeEntry runtimeEntry
                && !AgentSchedulerRuntime.isCurrentSession(runtimeEntry)) {
            return;
        }

        List<String> parts = splitForChat(reply, AgentLlmConfig.maxReplyMessages,
                AgentLlmConfig.maxReplyCharsPerMessage);
        if (parts.isEmpty()) return;
        if (AgentLlmConfig.debugLog && parts.size() > 1) {
            log.info("llm[{}] split into {} messages", botName, parts.size());
        }

        deliverReplyParts(request.entry(), parts,
                replyEmitter,
                (action, delayMs) -> scheduleFollowUp(request.entry(), action, delayMs));

        if (!looksLowQuality(message, reply)) {
            AgentMemoryStore.Turn turn = new AgentMemoryStore.Turn(System.currentTimeMillis(),
                    request.relation().name().toLowerCase(), senderName, message, reply);
            if (AgentLlmConfig.memoryEnabled) {
                AgentMemoryStore.appendTurn(botName, turn);
            } else {
                rememberRecent(botId, turn);
            }
        }

        // Compact when uncompacted overflows the window. One LLM call per compactBatchSize turns.
        if (AgentLlmConfig.memoryEnabled && AgentMemoryStore.countUncompacted(botName)
                > AgentLlmConfig.recentTurnsInPrompt + AgentLlmConfig.compactBatchSize) {
            try {
                EXEC.execute(() -> AgentMemoryStore.compact(botName));
            } catch (RejectedExecutionException e) {
                log.debug("Deferred Agent memory compaction because the LLM queue is full");
            }
        }
    }

    private static List<AgentMemoryStore.Turn> loadRecentMemory(int botId, long now) {
        if (!AgentLlmConfig.recentMemoryEnabled) return List.of();
        java.util.ArrayDeque<AgentMemoryStore.Turn> turns = recentMemoryByBotId.get(botId);
        if (turns == null) return List.of();
        synchronized (turns) {
            pruneRecent(turns, now);
            return turns.isEmpty() ? List.of() : new java.util.ArrayList<>(turns);
        }
    }

    private static void rememberRecent(int botId, AgentMemoryStore.Turn turn) {
        if (!AgentLlmConfig.recentMemoryEnabled || turn == null) return;
        java.util.ArrayDeque<AgentMemoryStore.Turn> turns =
                recentMemoryByBotId.computeIfAbsent(botId, k -> new java.util.ArrayDeque<>());
        synchronized (turns) {
            turns.addLast(turn);
            pruneRecent(turns, turn.ts());
        }
    }

    private static void pruneRecent(java.util.ArrayDeque<AgentMemoryStore.Turn> turns, long now) {
        long maxAge = Math.max(0L, AgentLlmConfig.recentMemoryMaxAgeMs);
        int maxTurns = Math.max(0, AgentLlmConfig.recentMemoryMaxTurns);
        while (!turns.isEmpty() && (maxTurns == 0 || turns.size() > maxTurns
                || now - turns.peekFirst().ts() > maxAge)) {
            turns.removeFirst();
        }
    }

    private static void scheduleFollowUp(AgentRuntimeHandle entry, Runnable action, long delayMs) {
        if (entry instanceof AgentRuntimeEntry runtimeEntry) {
            AgentSchedulerRuntime.afterDelay(runtimeEntry, delayMs, () -> executeFollowUp(action));
            return;
        }
        AgentSchedulerRuntime.afterDelay(delayMs, () -> executeFollowUp(action));
    }

    private static void executeFollowUp(Runnable action) {
        try {
            EXEC.execute(action);
        } catch (RejectedExecutionException e) {
            log.debug("Dropped LLM follow-up because the Agent LLM queue is full");
        }
    }

    @FunctionalInterface
    interface FollowUpScheduler {
        void schedule(Runnable action, long delayMs);
    }

    @FunctionalInterface
    public interface ReplyEmitter<E extends AgentRuntimeHandle> {
        void replyNow(E entry, String message);
    }

    static <E extends AgentRuntimeHandle> void deliverReplyParts(
            E entry,
            List<String> parts,
            ReplyEmitter<E> replyEmitter,
            FollowUpScheduler scheduler) {
        if (parts.isEmpty()) return;
        replyEmitter.replyNow(entry, parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            final String part = parts.get(i);
            scheduler.schedule(() -> {
                try {
                    replyEmitter.replyNow(entry, part);
                } catch (Throwable t) {
                    log.warn("llm follow-up reply failed: {}", t.toString());
                }
            }, (long) AgentLlmConfig.multiMessageDelayMs * i);
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
        // remove emoji and most pictographic symbols; in-game chat should stay plain text
        s = s.replaceAll("[\\p{So}\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]", "").trim();
        // keep the chat style consistent even when the model ignores the style instruction
        s = s.toLowerCase(Locale.ROOT);
        int cap = AgentLlmConfig.maxReplyChars();
        if (s.length() > cap) {
            s = s.substring(0, cap).trim();
        }
        return s;
    }

    private static boolean looksLowQuality(String message, String reply) {
        if (reply == null) return true;
        String trimmed = reply.trim();
        if (trimmed.isEmpty()) return true;

        String normalizedReply = trimmed.toLowerCase(Locale.ROOT);
        String normalizedMessage = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);

        if (normalizedReply.contains("as an ai")
                || normalizedReply.contains("language model")
                || normalizedReply.contains("chatbot")
                || normalizedReply.contains("i'm just a bot")
                || normalizedReply.contains("i am just a bot")
                || normalizedReply.contains("assistant")) {
            return true;
        }

        if (normalizedMessage.endsWith("?") && normalizedReply.endsWith("?")) {
            return true;
        }

        if (isEcho(normalizedMessage, normalizedReply)) {
            return true;
        }

        return false;
    }

    private static boolean isEcho(String message, String reply) {
        if (reply.isEmpty()) return true;
        String cleanMessage = message.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        String cleanReply = reply.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (cleanReply.isEmpty()) return true;
        if (cleanReply.length() <= 16 && cleanMessage.contains(cleanReply)) {
            return true;
        }
        String[] replyTokens = cleanReply.split(" ");
        if (replyTokens.length <= 2) {
            for (String token : replyTokens) {
                if (!token.isEmpty() && !cleanMessage.contains(token)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static String fallbackReply(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("how are you")) return "im good just chillin";
        if (normalized.startsWith("hi") || normalized.startsWith("hey") || normalized.startsWith("hello")) {
            return "yo";
        }
        if (normalized.contains("weather")) return "idk rn";
        if (normalized.contains("how many")) return "idk tbh";
        if (normalized.endsWith("?")) return "not sure tbh";
        return "idk tbh";
    }
}
