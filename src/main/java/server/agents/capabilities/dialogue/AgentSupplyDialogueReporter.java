package server.agents.capabilities.dialogue;

public final class AgentSupplyDialogueReporter {
    private AgentSupplyDialogueReporter() {
    }

    public static String potionReport(int[] counts) {
        int hp = counts != null && counts.length > 0 ? counts[0] : 0;
        int mp = counts != null && counts.length > 1 ? counts[1] : 0;
        return potionReport(hp, mp);
    }

    public static String potionReport(int hpPotions, int mpPotions) {
        return AgentDialogueReportFormatter.potionReport(hpPotions, mpPotions);
    }
}
