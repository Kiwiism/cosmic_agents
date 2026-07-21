package server.agents.events;

/** Versioned routing and execution metadata kept separate from an event payload. */
public record AgentEventContext(
        int schemaVersion,
        String sourceCapability,
        String correlationId,
        String causationId,
        String objectiveId,
        int mapId) {

    public AgentEventContext {
        if (schemaVersion < 1 || sourceCapability == null || sourceCapability.isBlank() || mapId < -1) {
            throw new IllegalArgumentException("Valid event schema, source capability, and map are required");
        }
        sourceCapability = sourceCapability.trim();
        correlationId = normalize(correlationId);
        causationId = normalize(causationId);
        objectiveId = normalize(objectiveId);
    }

    public static AgentEventContext from(AgentEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event is required");
        }
        String type = event.type();
        int separator = type.indexOf('.');
        String sourceCapability = separator > 0 ? type.substring(0, separator) : type;
        if (event instanceof AgentContextualEvent contextual) {
            return new AgentEventContext(event.schemaVersion(), sourceCapability,
                    contextual.correlationId(), contextual.causationId(),
                    contextual.objectiveId(), contextual.mapId());
        }
        return new AgentEventContext(event.schemaVersion(), sourceCapability,
                "agent:" + event.agentId(), "", "", -1);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
