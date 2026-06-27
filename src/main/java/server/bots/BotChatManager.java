package server.bots;

public class BotChatManager {
    public static boolean wasLastChatHandled() {
        return BotChatRuntime.wasLastChatHandled();
    }

    static void handleChat(BotEntry entry, String message) {
        BotChatRuntime.handleChat(entry, message);
    }

}
