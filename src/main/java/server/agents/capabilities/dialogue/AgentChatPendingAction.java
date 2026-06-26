package server.agents.capabilities.dialogue;

public final class AgentChatPendingAction {
    public static final String SKILL_TREE_CHOICE = "skill_tree_choice";
    public static final String RELOG = "relog";
    public static final String LOGOUT = "logout";
    public static final String OWNER_AWAY = "owner_away";
    public static final String ITEM_CHOICE = "item_choice";
    private static final String DROP_PREFIX = "drop";

    private AgentChatPendingAction() {
    }

    public static boolean isSkillTreeChoice(String action) {
        return SKILL_TREE_CHOICE.equals(action);
    }

    public static boolean isRelog(String action) {
        return RELOG.equals(action);
    }

    public static boolean isOwnerAway(String action) {
        return OWNER_AWAY.equals(action);
    }

    public static boolean isItemChoice(String action) {
        return ITEM_CHOICE.equals(action);
    }

    public static boolean isDropAction(String action) {
        return action != null && action.startsWith(DROP_PREFIX);
    }
}
