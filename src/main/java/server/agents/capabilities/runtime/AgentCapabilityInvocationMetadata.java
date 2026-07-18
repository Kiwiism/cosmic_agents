package server.agents.capabilities.runtime;

public record AgentCapabilityInvocationMetadata(
        String objectiveId,
        String objectiveSource,
        String behaviorVersion,
        String correlationId) {

    public static final AgentCapabilityInvocationMetadata UNSPECIFIED =
            new AgentCapabilityInvocationMetadata("", "", "legacy-compatible", "");

    public AgentCapabilityInvocationMetadata {
        objectiveId = objectiveId == null ? "" : objectiveId;
        objectiveSource = objectiveSource == null ? "" : objectiveSource;
        behaviorVersion = behaviorVersion == null || behaviorVersion.isBlank()
                ? "legacy-compatible" : behaviorVersion;
        correlationId = correlationId == null ? "" : correlationId;
    }
}
