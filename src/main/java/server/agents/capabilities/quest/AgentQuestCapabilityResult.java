package server.agents.capabilities.quest;

import server.agents.capabilities.AgentCapabilityStatus;

public record AgentQuestCapabilityResult(
        boolean success,
        AgentCapabilityStatus status,
        String message,
        int questId,
        AgentQuestRequirement requirement) {

    public static AgentQuestCapabilityResult success(String message, AgentQuestCapabilityRequest request) {
        return new AgentQuestCapabilityResult(true, AgentCapabilityStatus.SUCCESS, message,
                request.questId(), request.requirement());
    }

    public static AgentQuestCapabilityResult pending(String message, AgentQuestCapabilityRequest request) {
        return new AgentQuestCapabilityResult(false, AgentCapabilityStatus.NOT_READY, message,
                request.questId(), request.requirement());
    }

    public static AgentQuestCapabilityResult blocked(AgentCapabilityStatus status, String message,
            AgentQuestCapabilityRequest request) {
        int questId = request == null ? 0 : request.questId();
        AgentQuestRequirement requirement = request == null ? null : request.requirement();
        return new AgentQuestCapabilityResult(false, status, message, questId, requirement);
    }
}
