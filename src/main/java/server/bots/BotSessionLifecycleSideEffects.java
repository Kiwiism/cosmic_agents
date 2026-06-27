package server.bots;

import java.util.List;

/**
 * Temporary bot-side gateway for session lifecycle side effects that still need
 * BotManager package access while orchestration moves into Agent modules.
 */
public final class BotSessionLifecycleSideEffects {
    private BotSessionLifecycleSideEffects() {
    }

    public static void reloginBot(int charId, int ownerCharId, int world, int channel) {
        BotManager.getInstance().reloginBot(charId, ownerCharId, world, channel);
    }

    public static List<BotEntry> getBotEntries(int ownerCharId) {
        return BotManager.getInstance().getBotEntries(ownerCharId);
    }
}
