package server.bots.llm;

public final class BotLlmConfig {
    public static volatile boolean enabled = true;
    public static volatile boolean typoSuggesterEnabled = true;

    public static volatile String endpoint = "http://localhost:11434";
    public static volatile String model = "qwen3.5:0.8b";
    public static volatile int requestTimeoutMs = 15_000;
    public static volatile int maxConcurrentGlobal = 4;
    // Hard ceiling on the FULL reply (sum across split messages). Computed
    // dynamically as maxReplyMessages * maxReplyCharsPerMessage so changing
    // either knob below auto-resizes the cap.
    public static int maxReplyChars() {
        return Math.max(1, maxReplyMessages) * Math.max(1, maxReplyCharsPerMessage);
    }

    // CPU cap: how many threads Ollama may use per inference. 0 = let Ollama
    // decide (uses all cores → 100% spike). Default to half the available
    // logical cores so the game server keeps room to breathe.
    public static volatile int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    // Keep the model resident in Ollama for this long after each call so the
    // next reply doesn't pay a cold-load cost. Examples: "5m", "30m", "-1" (forever).
    public static volatile String keepAlive = "30m";

    // Speed knobs for CPU-only hosts. All can be tuned at runtime.

    // Qwen3/3.5 emit <think>...</think> tokens by default — pure waste for
    // one-line chat. Disabling roughly halves wall time on short replies.
    public static volatile boolean disableThinking = true;

    // Hard cap on tokens generated. Reply is split across up to
    // maxReplyMessages chat messages of maxReplyCharsPerMessage chars each,
    // so size this to roughly cover the multi-message budget plus a bit.
    public static volatile int maxPredictTokens = 120;

    // Context window size. Our prompts stay under ~300 tokens; the default
    // 4096 wastes memory bandwidth. Lower = faster on CPU.
    public static volatile int numCtx = 2048;

    // How many recent turns from memory to inline into the prompt. Lower =
    // shorter prompt = faster prompt eval on CPU. Long-term context still
    // survives via the compacted .summary.txt.
    public static volatile int recentTurnsInPrompt = 5;

    // Sampling — defaults are Qwen3.5's official recommendation for
    // non-thinking text tasks. Switch to the thinking preset (commented
    // below) if disableThinking is set to false.
    //
    //  Non-thinking text (active):
    //    temperature=1.0, top_p=1.0, top_k=20, min_p=0.0,
    //    presence_penalty=2.0, repeat_penalty=1.0
    //  Thinking text:
    //    temperature=1.0, top_p=0.95, top_k=20, min_p=0.0,
    //    presence_penalty=1.5, repeat_penalty=1.0
    public static volatile double temperature = 1.0;
    public static volatile double topP = 1.0;
    public static volatile int topK = 20;
    public static volatile double minP = 0.0;
    public static volatile double presencePenalty = 2.0;
    public static volatile double repeatPenalty = 1.0;

    // Multi-message reply: long replies are split on sentence boundaries and
    // sent as up to maxReplyMessages chat messages with multiMessageDelayMs
    // between each. Set to 1 to disable splitting.
    public static volatile int maxReplyMessages = 2;
    public static volatile int multiMessageDelayMs = 1800;
    public static volatile int maxReplyCharsPerMessage = 120;

    // When true, every LLM call prints the full prompt + raw response + timing
    // to the server log. Use to diagnose nonsense / sampling issues, then turn off.
    public static volatile boolean debugLog = true;

    public static volatile String memoryDir = "bots/llm-memory";
    public static volatile int compactThresholdTurns = 32;
    public static volatile int compactKeepRecentTurns = 8;

    private BotLlmConfig() {}
}
