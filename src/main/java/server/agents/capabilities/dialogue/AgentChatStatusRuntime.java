package server.agents.capabilities.dialogue;

import java.awt.Point;
import java.util.concurrent.ThreadLocalRandom;

public final class AgentChatStatusRuntime {
    private static final int[] FIDGET_EXPRESSIONS = {2, 3, 5, 6, 7};

    private AgentChatStatusRuntime() {
    }

    public static void markActive(StatusState state, Point ownerPosition, long nowMs) {
        state.setOwnerWasAfk(false);
        state.setOwnerAfkSinceMs(nowMs);
        state.setOwnerAfkPosition(ownerPosition != null ? new Point(ownerPosition) : null);
    }

    public static boolean isOwnerIdle(StatusState state) {
        return state.ownerWasAfk();
    }

    public static int randomFidgetExpression() {
        return FIDGET_EXPRESSIONS[ThreadLocalRandom.current().nextInt(FIDGET_EXPRESSIONS.length)];
    }

    public static void checkStatus(StatusCheckState state, StatusCheckActions actions) {
        String jobPrompt = actions.buildJobPrompt();
        if (jobPrompt != null) {
            actions.queueReply(jobPrompt);
        }

        String spPrompt = actions.buildSpVariantPrompt();
        if (spPrompt != null) {
            actions.queueReply(spPrompt);
        } else {
            actions.autoAssignSp();
        }

        String apPrompt = actions.buildApPrompt();
        if (apPrompt != null) {
            actions.queueReply(apPrompt);
        } else {
            actions.autoAssignAp();
        }

        actions.maybeSuggestRecommendedGear();
        actions.maybeSuggestGearToSiblings();

        if (!state.spawnUpgradeCheckDone()) {
            state.setSpawnUpgradeCheckDone(true);
            if (actions.canOfferSpawnUpgrade()) {
                actions.offerSpawnUpgradeIfAvailable();
            }
        }
    }

    public static void prepareActiveMode(ActiveModeActions actions) {
        actions.autoEquip();
        actions.resetGearSuggestionCooldown();
        actions.maybeSuggestGearToSiblings();
        actions.setupAutopot();
        actions.checkPotShareOnModeStart();
    }

    public interface StatusState {
        void setOwnerAfkPosition(Point position);

        void setOwnerAfkSinceMs(long sinceMs);

        boolean ownerWasAfk();

        void setOwnerWasAfk(boolean wasAfk);
    }

    public interface StatusCheckState {
        boolean spawnUpgradeCheckDone();

        void setSpawnUpgradeCheckDone(boolean done);
    }

    public interface StatusCheckActions {
        String buildJobPrompt();

        String buildSpVariantPrompt();

        String buildApPrompt();

        void queueReply(String message);

        void autoAssignSp();

        void autoAssignAp();

        void maybeSuggestRecommendedGear();

        void maybeSuggestGearToSiblings();

        boolean canOfferSpawnUpgrade();

        void offerSpawnUpgradeIfAvailable();
    }

    public interface ActiveModeActions {
        void autoEquip();

        void resetGearSuggestionCooldown();

        void maybeSuggestGearToSiblings();

        void setupAutopot();

        void checkPotShareOnModeStart();
    }
}
