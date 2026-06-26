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

    public static String grindStartMessage(String baseReply, int hpPotions, int mpPotions, int lowPotionWarning) {
        if (hpPotions >= lowPotionWarning && mpPotions >= lowPotionWarning) {
            return baseReply;
        }

        StringBuilder message = new StringBuilder(baseReply).append(", but");
        if (hpPotions < lowPotionWarning) {
            message.append(" only ").append(hpPotions).append(" HP pots");
        }
        if (hpPotions < lowPotionWarning && mpPotions < lowPotionWarning) {
            message.append(" and");
        }
        if (mpPotions < lowPotionWarning) {
            message.append(" only ").append(mpPotions).append(" MP pots");
        }
        return message.append(" left").toString();
    }

    public static String autopotDebugReport(int hpPotions, int mpPotions, String hpSlot, String mpSlot) {
        return "pots: " + hpPotions + " hp / " + mpPotions + " mp"
                + " | hp slot: " + hpSlot
                + " | mp slot: " + mpSlot;
    }

    public static String autopotChoice(String itemName, int itemId, String tierName, double value) {
        if (itemId <= 0 || tierName == null) {
            return "none";
        }
        String name = itemName == null ? String.valueOf(itemId) : itemName;
        String formattedValue = tierName.startsWith("FLAT_")
                ? String.valueOf((int) value)
                : String.format("%.0f%%", value * 100);
        return name + " (" + tierName + "/" + formattedValue + ")";
    }
}
