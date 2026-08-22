package server.agents.progression.questwork;

/** Early-game consumable budget for one resumable quest attempt. */
public final class AgentQuestAttemptBudgetPolicy {
    private static final String TUNING_PREFIX =
            "server.agents.progression.questwork.AgentQuestAttemptBudgetPolicy.";
    private static final int BASE_UNITS = tuningInt("BASE_UNITS");
    private static final int UNITS_PER_LEVEL = tuningInt("UNITS_PER_LEVEL");
    private static final int MAXIMUM_UNITS = tuningInt("MAXIMUM_UNITS");

    private AgentQuestAttemptBudgetPolicy() {
    }

    public static int budgetForLevel(int level) {
        long calculated = BASE_UNITS + (long) Math.max(1, level) * UNITS_PER_LEVEL;
        return (int) Math.clamp(calculated, 1L, MAXIMUM_UNITS);
    }

    private static int tuningInt(String name) {
        return config.AgentTuning.intValue(TUNING_PREFIX + name);
    }
}
