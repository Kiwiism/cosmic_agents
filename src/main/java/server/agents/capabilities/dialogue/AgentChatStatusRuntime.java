package server.agents.capabilities.dialogue;

import java.awt.Point;
import java.util.function.BooleanSupplier;
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

    public static void maybeSuggestGear(GearSuggestionState state, GearSuggestionActions actions, long nowMs) {
        if (!actions.hasRecipient() || nowMs < state.nextGearSuggestionAt()) {
            return;
        }

        if (actions.offerGear()) {
            state.setNextGearSuggestionAt(nowMs + 60_000L);
        }
    }

    public static GearSuggestionActions gearSuggestionActions(boolean hasRecipient, BooleanSupplier offerGear) {
        return new GearSuggestionActions() {
            @Override
            public boolean hasRecipient() {
                return hasRecipient;
            }

            @Override
            public boolean offerGear() {
                return offerGear.getAsBoolean();
            }
        };
    }

    public static void announceOfflineReturn(OfflineReturnActions actions) {
        if (!actions.hasAgent()) {
            return;
        }

        String text = AgentChatWelcomeBackFlow.welcomeBackOfflinePartyReply(actions.mapName());
        actions.afterRandomDelay(1500, 2500, () -> {
            actions.changeFaceExpression(randomWelcomeExpression());
            actions.sayParty(text);
        });
    }

    public static void announceAfkReturn(AfkReturnActions actions) {
        if (!actions.hasAgent()) {
            return;
        }

        String text = AgentChatWelcomeBackFlow.welcomeBackReply();
        actions.afterRandomDelay(1800, 2200, () -> {
            actions.changeFaceExpression(randomWelcomeExpression());
            actions.reply(text);
        });
    }

    public static void tickAfkCheck(
            AgentChatWelcomeBackFlow.AfkState state,
            Point ownerPosition,
            long nowMs,
            AfkReturnActions actions) {
        AgentChatWelcomeBackFlow.tickAfkCheck(
                state,
                ownerPosition,
                nowMs,
                () -> announceAfkReturn(actions));
    }

    private static int randomWelcomeExpression() {
        return ThreadLocalRandom.current().nextBoolean() ? 2 : 3;
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

    public interface GearSuggestionState {
        long nextGearSuggestionAt();

        void setNextGearSuggestionAt(long nextGearSuggestionAt);
    }

    public interface GearSuggestionActions {
        boolean hasRecipient();

        boolean offerGear();
    }

    public interface OfflineReturnActions {
        boolean hasAgent();

        String mapName();

        void afterRandomDelay(int minMs, int maxMs, Runnable action);

        void changeFaceExpression(int expression);

        void sayParty(String text);
    }

    public interface AfkReturnActions {
        boolean hasAgent();

        void afterRandomDelay(int minMs, int maxMs, Runnable action);

        void changeFaceExpression(int expression);

        void reply(String text);
    }
}
