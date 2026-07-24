package server.agents.capabilities.dialogue.llm.gateway;

import server.agents.capabilities.dialogue.llm.OllamaClient;

import java.util.Optional;

/**
 * Read-only LLM plugin seam. The gateway can produce dialogue text but cannot
 * assign objectives, issue capability commands, or mutate Cosmic state.
 */
public final class AgentReadOnlyLlmGateway {
    private static final AgentDialogueModelProvider DEFAULT_PROVIDER = OllamaClient::generate;
    private static volatile AgentDialogueModelProvider dialogueProvider = DEFAULT_PROVIDER;

    private AgentReadOnlyLlmGateway() {
    }

    public static Optional<String> generateDialogue(String prompt, String systemInstruction) {
        if (prompt == null || prompt.isBlank()
                || systemInstruction == null || systemInstruction.isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<String> result = dialogueProvider.generate(prompt, systemInstruction);
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    public static void installDialogueProvider(AgentDialogueModelProvider provider) {
        dialogueProvider = provider == null ? DEFAULT_PROVIDER : provider;
    }

    public static void resetDialogueProvider() {
        dialogueProvider = DEFAULT_PROVIDER;
    }
}
