package server.agents.runtime.activity.control;

import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.Map;

/** One evidence-backed action exposed by the shared Director executive. */
public record AgentDirectorAction(
        String actionId,
        String label,
        AgentDirectorActionAvailability availability,
        String reason,
        AgentWorldDirectiveType directiveType,
        AgentActivityKind targetActivityKind,
        AgentWorldActivityRequestType requestType,
        String requestId,
        Map<String, String> parameters,
        AgentWorldInterruptionPolicy interruptionPolicy,
        AgentWorldCompletionPolicy completionPolicy,
        int priority,
        boolean destructive) {

    public AgentDirectorAction {
        actionId = text(actionId);
        label = text(label);
        reason = text(reason);
        requestId = text(requestId);
        parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
        if (actionId.isEmpty() || label.isEmpty() || availability == null
                || directiveType == null || interruptionPolicy == null
                || completionPolicy == null || priority < 0) {
            throw new IllegalArgumentException("complete Director action evidence is required");
        }
    }

    public AgentDirectorAction unavailable(String unavailableReason) {
        return new AgentDirectorAction(
                actionId, label, AgentDirectorActionAvailability.UNAVAILABLE,
                unavailableReason, directiveType, targetActivityKind, requestType,
                requestId, parameters, interruptionPolicy, completionPolicy,
                priority, destructive);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
