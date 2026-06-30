package server.agents.capabilities.dialogue.llm;

public final class AgentLlmConfig {
    public static volatile boolean enabled = false;
    public static volatile boolean typoSuggesterEnabled = false; // recommended off if LLM on; it can block casual chat

    public static volatile String endpoint = "http://localhost:11434";
    public static volatile String model = "qwen3.5:0.8b";
    public static volatile int requestTimeoutMs = 15_000;
    public static volatile int maxConcurrentGlobal = 1;

    public static int maxReplyChars() {
        return Math.max(1, maxReplyMessages) * Math.max(1, maxReplyCharsPerMessage);
    }

    // CPU cap: how many threads Ollama may use per inference. 0 lets Ollama decide.
    public static volatile int numThreads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);

    // Keep the model resident in Ollama after each call to avoid cold-load cost.
    public static volatile String keepAlive = "30m";

    // Qwen-family models can emit thinking tokens. Disable them for one-line MMO chatter.
    public static volatile boolean disableThinking = true;

    // Short cosmetic replies should finish quickly and never compete with gameplay.
    public static volatile int maxPredictTokens = 24;
    public static volatile int numCtx = 4096;
    public static volatile int recentTurnsInPrompt = 3;

    // Qwen3.5 standard non-thinking sampler, kept tight for short chat.
    public static volatile double temperature = 1.0;
    public static volatile double topP = 0.95;
    public static volatile int topK = 20;
    public static volatile double minP = 0.0;
    public static volatile double presencePenalty = 1.5;
    public static volatile double repeatPenalty = 1.0;

    // Keep cosmetic chatter to one in-game line by default.
    public static volatile int maxReplyMessages = 1;
    public static volatile int multiMessageDelayMs = 1800;
    public static volatile int maxReplyCharsPerMessage = 120;

    public static volatile boolean debugLog = false;

    // Persistent memory is optional. Leave it off for the lightest setup; enable
    // only if bot chatter needs to remember past conversations across server restarts.
    public static volatile boolean recentMemoryEnabled = true;
    public static volatile int recentMemoryMaxTurns = 4;
    public static volatile long recentMemoryMaxAgeMs = 15L * 60L * 1000L;
    public static volatile boolean memoryEnabled = false;
    public static volatile String memoryDir = "bots/llm-memory";
    public static volatile int compactBatchSize = 8;
    public static volatile int summaryMaxPredictTokens = 300;

    private AgentLlmConfig() {}
}
