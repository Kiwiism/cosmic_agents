package server.agents.coordination;

/** Latest receiver disposition for a routed coordination message. */
public enum AgentCoordinationDisposition {
    DELIVERED,
    ACKNOWLEDGED,
    ACCEPTED,
    REJECTED,
    COMPLETED,
    EXPIRED
}
