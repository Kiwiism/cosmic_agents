package server.agents.capabilities.dialogue;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentSupplyRequestOutcomeFlow {
    private AgentSupplyRequestOutcomeFlow() {
    }

    public static String potionShareReply(boolean noDonor, boolean hpPotion) {
        return potionShareReply(noDonor, hpPotion, randomReply(AgentDialogueCatalog.ownerPotShortageReplies()));
    }

    public static String potionShareReply(boolean noDonor, boolean hpPotion, String shortageTemplate) {
        if (!noDonor) {
            return null;
        }
        return AgentDialogueReportFormatter.ownerPotShortageReply(
                shortageTemplate,
                AgentDialogueReportFormatter.potionTypeLabel(hpPotion));
    }

    public static String ammoNotNeededReply() {
        return ammoNotNeededReply(randomReply(AgentDialogueCatalog.ammoNotNeededReplies()));
    }

    public static String ammoNotNeededReply(String reply) {
        return reply;
    }

    public static String ammoShareReply(boolean noDonor) {
        return ammoShareReply(noDonor, randomReply(AgentDialogueCatalog.ownerAmmoShortageReplies()));
    }

    public static String ammoShareReply(boolean noDonor, String shortageReply) {
        return noDonor ? shortageReply : null;
    }

    private static String randomReply(List<String> replies) {
        return replies.get(ThreadLocalRandom.current().nextInt(replies.size()));
    }
}
