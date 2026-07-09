package server.agents.capabilities.npc;

import java.awt.Point;

public record AgentNpcInteractionRequest(
        int mapId,
        int npcId,
        Integer questId,
        AgentNpcInteractionType type,
        Point agentPosition,
        Point npcPosition,
        int maxRangePx,
        boolean requireApproachPoint,
        String dialoguePhase,
        long randomSeed) {

    public AgentNpcInteractionRequest {
        if (type == null) {
            type = AgentNpcInteractionType.TALK;
        }
        if (agentPosition != null) {
            agentPosition = new Point(agentPosition);
        }
        if (npcPosition != null) {
            npcPosition = new Point(npcPosition);
        }
        if (dialoguePhase == null || dialoguePhase.isBlank()) {
            dialoguePhase = defaultDialoguePhase(type);
        }
    }

    public boolean hasQuestId() {
        return questId != null && questId > 0;
    }

    public boolean hasRangeCheck() {
        return agentPosition != null && maxRangePx >= 0;
    }

    private static String defaultDialoguePhase(AgentNpcInteractionType type) {
        return switch (type) {
            case QUEST_START -> "start";
            case QUEST_COMPLETE -> "complete";
            case SHOP -> "shop";
            case SERVICE -> "service";
            case TALK -> "talk";
        };
    }
}
