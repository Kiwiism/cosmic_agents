package server.agents.capabilities.quest;

public record AmherstTestResetPlan(
        AmherstTestResetMode mode,
        int targetMapId,
        boolean resetCharacterBaseline,
        boolean resetAllAmherstQuests,
        int selectedQuestId,
        boolean seedAmherstPrerequisites) {
}
