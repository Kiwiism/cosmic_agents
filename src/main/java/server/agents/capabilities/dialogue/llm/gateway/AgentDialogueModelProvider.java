package server.agents.capabilities.dialogue.llm.gateway;

import java.util.Optional;

/** Text-only model provider. It receives no mutable game or Agent runtime object. */
@FunctionalInterface
public interface AgentDialogueModelProvider {
    Optional<String> generate(String prompt, String systemInstruction);
}
