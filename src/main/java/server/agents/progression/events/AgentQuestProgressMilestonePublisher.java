package server.agents.progression.events;

import client.Character;
import server.agents.events.AgentEventPriority;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.life.MonsterInformationProvider;

/** Detects configured quest progress threshold crossings at the authoritative mutation boundary. */
public final class AgentQuestProgressMilestonePublisher {
    private static final int HALF_PERCENT = config.AgentTuning.intValue(
            "server.agents.progression.events.AgentQuestProgressMilestonePublisher.HALF_PERCENT");
    private static final int NEARLY_COMPLETE_PERCENT = config.AgentTuning.intValue(
            "server.agents.progression.events.AgentQuestProgressMilestonePublisher.NEARLY_COMPLETE_PERCENT");

    static {
        if (HALF_PERCENT <= 0 || HALF_PERCENT >= 100
                || NEARLY_COMPLETE_PERCENT <= HALF_PERCENT
                || NEARLY_COMPLETE_PERCENT >= 100) {
            throw new IllegalStateException(
                    "Quest progress milestones must be ordered percentages between 1 and 99");
        }
    }

    private AgentQuestProgressMilestonePublisher() {
    }

    public static void publishMobProgress(
            Character agent,
            int questId,
            int targetId,
            int previousCount,
            int currentCount,
            int requiredCount) {
        if (agent == null || questId <= 0 || targetId <= 0 || previousCount < 0
                || currentCount <= previousCount || requiredCount <= 0) {
            return;
        }
        String targetName = mobName(targetId);
        publishIfCrossed(agent, questId, targetId, targetName, previousCount, currentCount,
                requiredCount, HALF_PERCENT);
        publishIfCrossed(agent, questId, targetId, targetName, previousCount, currentCount,
                requiredCount, NEARLY_COMPLETE_PERCENT);
    }

    /** Publishes collection milestones for an active quest that requires the acquired item. */
    public static void publishItemProgress(
            Character agent,
            int questId,
            int itemId,
            int previousCount,
            int currentCount,
            int requiredCount) {
        if (agent == null || questId <= 0 || itemId <= 0 || previousCount < 0
                || currentCount <= previousCount || requiredCount <= 0) {
            return;
        }
        String itemName = itemName(itemId);
        publishIfCrossed(agent, questId, itemId, itemName, previousCount, currentCount,
                requiredCount, HALF_PERCENT);
        publishIfCrossed(agent, questId, itemId, itemName, previousCount, currentCount,
                requiredCount, NEARLY_COMPLETE_PERCENT);
    }

    private static void publishIfCrossed(
            Character agent,
            int questId,
            int targetId,
            String targetName,
            int previousCount,
            int currentCount,
            int requiredCount,
            int milestonePercent) {
        int milestoneCount = milestoneCount(requiredCount, milestonePercent);
        if (previousCount >= milestoneCount || currentCount < milestoneCount) {
            return;
        }
        int boundedCurrent = Math.min(currentCount, requiredCount);
        AgentProgressionEventPublisher.publishFor(agent,
                (entry, objectiveId) -> new AgentQuestProgressMilestoneEvent(
                        agent.getId(), System.currentTimeMillis(), questId, targetId, targetName,
                        boundedCurrent, requiredCount, milestonePercent, agent.getMapId(),
                        objectiveId),
                AgentEventPriority.NORMAL);
    }

    private static int milestoneCount(int requiredCount, int milestonePercent) {
        if (milestonePercent == NEARLY_COMPLETE_PERCENT && requiredCount < 10) {
            return requiredCount - 1;
        }
        return divideRoundingUp((long) requiredCount * milestonePercent, 100);
    }

    private static int divideRoundingUp(long value, int divisor) {
        return Math.toIntExact((value + divisor - 1L) / divisor);
    }

    private static String mobName(int mobId) {
        try {
            String name = MonsterInformationProvider.getInstance().getMobNameFromId(mobId);
            return name == null || name.isBlank() ? "monster" : name.trim();
        } catch (RuntimeException | LinkageError ignored) {
            return "monster";
        }
    }

    private static String itemName(int itemId) {
        try {
            String name = AgentInventoryGatewayRuntime.inventory().getItemName(itemId);
            return name == null || name.isBlank() ? "item" : name.trim();
        } catch (RuntimeException | LinkageError ignored) {
            return "item";
        }
    }
}
