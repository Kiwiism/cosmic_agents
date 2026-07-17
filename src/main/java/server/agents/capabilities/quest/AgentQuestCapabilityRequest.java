package server.agents.capabilities.quest;


import java.awt.Point;

public record AgentQuestCapabilityRequest(
        int questId,
        int mapId,
        int npcId,
        Point agentPosition,
        Point npcPosition,
        int maxRangePx,
        AgentQuestSnapshot snapshot,
        AgentQuestRequirement requirement,
        boolean requireAmherstScope) {

    public AgentQuestCapabilityRequest {
        if (agentPosition != null) {
            agentPosition = new Point(agentPosition);
        }
        if (npcPosition != null) {
            npcPosition = new Point(npcPosition);
        }
        if (snapshot == null) {
            snapshot = AgentQuestSnapshot.emptyLv1Beginner();
        }
    }
}
