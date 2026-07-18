package server.agents.capabilities.runtime;

import java.util.Map;

/** Validated mutation request emitted by capability policy. */
public record AgentCapabilityActionRequest(
        String type,
        String idempotencyKey,
        Map<String, String> arguments) {

    public AgentCapabilityActionRequest {
        if (type == null || type.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Action type and idempotency key are required");
        }
        type = type.trim();
        idempotencyKey = idempotencyKey.trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
