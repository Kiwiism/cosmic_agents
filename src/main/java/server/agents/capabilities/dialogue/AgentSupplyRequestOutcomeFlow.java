package server.agents.capabilities.dialogue;

public final class AgentSupplyRequestOutcomeFlow {
    private AgentSupplyRequestOutcomeFlow() {
    }

    public static String potionShareReply(boolean noDonor, boolean hpPotion, String shortageTemplate) {
        if (!noDonor) {
            return null;
        }
        return AgentDialogueReportFormatter.ownerPotShortageReply(
                shortageTemplate,
                AgentDialogueReportFormatter.potionTypeLabel(hpPotion));
    }

    public static String ammoNotNeededReply(String reply) {
        return reply;
    }

    public static String ammoShareReply(boolean noDonor, String shortageReply) {
        return noDonor ? shortageReply : null;
    }
}
