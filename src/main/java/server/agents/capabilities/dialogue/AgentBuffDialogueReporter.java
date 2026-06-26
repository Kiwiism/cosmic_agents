package server.agents.capabilities.dialogue;

import java.util.List;

public final class AgentBuffDialogueReporter {
    private AgentBuffDialogueReporter() {
    }

    public static String chatSummary(boolean enabled, boolean cheapMode, String activeSummary, String availableSummary) {
        return "buff pots " + (enabled ? "on" : "off")
                + " (" + (cheapMode ? "cheap" : "max") + ")"
                + ": active " + activeSummary
                + "; bag " + availableSummary;
    }

    public static List<String> debugLines(boolean enabled, boolean cheapMode,
                                          String activeSummary, String availableSummary) {
        return List.of(
                debugState(enabled, cheapMode) + "; active: " + activeSummary,
                "bag: " + availableSummary);
    }

    public static String debugState(boolean enabled, boolean cheapMode) {
        return "buff " + (enabled ? "on" : "off")
                + "(" + (cheapMode ? "cheap" : "best") + ")";
    }
}
