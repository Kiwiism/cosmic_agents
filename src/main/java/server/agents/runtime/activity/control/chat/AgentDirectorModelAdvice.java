package server.agents.runtime.activity.control.chat;

import java.util.List;

public record AgentDirectorModelAdvice(
        List<AgentDirectorRankedSelection> selections,
        String provider,
        long latencyMs) {
    public AgentDirectorModelAdvice {
        selections = List.copyOf(selections == null ? List.of() : selections);
        provider = provider == null ? "" : provider.trim();
        if (selections.isEmpty() || provider.isEmpty() || latencyMs < 0L) {
            throw new IllegalArgumentException("complete Director model advice is required");
        }
    }
}
