package server.agents.capabilities.dialogue;

public final class AgentChatReportFlow {
    private AgentChatReportFlow() {
    }

    public static boolean handle(String message, ReportCallbacks callbacks) {
        if (AgentChatCommandClassifier.isRequestUpgradeCommand(message)) {
            callbacks.requestUpgrade();
            return true;
        }
        if (AgentChatCommandClassifier.isRecommendedGearQuery(message)) {
            callbacks.recommendedGear();
            return true;
        }
        if (AgentChatCommandClassifier.isSkillsQuery(message)) {
            callbacks.skills();
            return true;
        }
        if (AgentChatCommandClassifier.isStatsQuery(message)) {
            callbacks.stats();
        }
        if (AgentChatCommandClassifier.isMovementStatsQuery(message)) {
            callbacks.movementStats();
        }
        if (AgentChatCommandClassifier.isRangeQuery(message)) {
            callbacks.range();
        }
        if (AgentChatCommandClassifier.isBuildQuery(message)) {
            callbacks.build();
        }
        if (AgentChatCommandClassifier.isInventoryQuery(message)) {
            callbacks.inventory();
        }
        if (AgentChatCommandClassifier.isMesoQuery(message)) {
            callbacks.mesos();
        }
        if (AgentChatCommandClassifier.isExpQuery(message)) {
            callbacks.exp();
        }
        if (AgentChatCommandClassifier.isInventorySlotsQuery(message)) {
            callbacks.inventorySlots();
        }
        if (AgentChatCommandClassifier.isScrollsQuery(message)) {
            callbacks.scrolls();
        }
        if (AgentChatCommandClassifier.isPotionsQuery(message)) {
            callbacks.potions();
        }
        if (AgentChatCommandClassifier.isDebugStatsQuery(message)) {
            callbacks.debugStats();
        }
        if (AgentChatCommandClassifier.isCritDebugQuery(message)) {
            callbacks.critDebug();
        }
        if (AgentChatCommandClassifier.isPotDebugQuery(message)) {
            callbacks.potDebug();
        }
        return false;
    }

    public interface ReportCallbacks {
        void requestUpgrade();

        void recommendedGear();

        void skills();

        void stats();

        void movementStats();

        void range();

        void build();

        void inventory();

        void mesos();

        void exp();

        void inventorySlots();

        void scrolls();

        void potions();

        void debugStats();

        void critDebug();

        void potDebug();
    }
}
