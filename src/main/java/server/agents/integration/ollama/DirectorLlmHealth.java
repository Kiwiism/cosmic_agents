package server.agents.integration.ollama;

public record DirectorLlmHealth(
        boolean enabled,
        boolean reachable,
        boolean modelAvailable,
        String model,
        String status) {
    public DirectorLlmHealth {
        model = model == null ? "" : model.trim();
        status = status == null ? "" : status.trim();
        if (model.isEmpty() || status.isEmpty()) {
            throw new IllegalArgumentException("complete Director LLM health is required");
        }
    }

    public boolean ready() {
        return enabled && reachable && modelAvailable;
    }
}
