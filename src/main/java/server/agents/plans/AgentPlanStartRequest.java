package server.agents.plans;

import java.util.Map;

public record AgentPlanStartRequest(Map<String, Object> inputs, Object transientAttachment) {
    public static final AgentPlanStartRequest EMPTY = new AgentPlanStartRequest(Map.of(), null);

    public AgentPlanStartRequest {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }

    public int intInput(String key, int fallback) {
        Object value = inputs.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    public boolean booleanInput(String key, boolean fallback) {
        Object value = inputs.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }
}
