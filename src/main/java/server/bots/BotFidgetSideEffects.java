package server.bots;

/**
 * Temporary bot-side gateway for fidget side effects that still live in the
 * legacy bot package.
 */
public final class BotFidgetSideEffects {
    private BotFidgetSideEffects() {
    }

    public static void maybeStartSocialFidget(BotEntry entry) {
        BotFidgetManager.maybeStartSocialFidget(entry);
    }

    public static void maybeStartGreetingFidget(BotEntry entry, int roll) {
        BotFidgetManager.maybeStartGreetingFidget(entry, roll);
    }
}
