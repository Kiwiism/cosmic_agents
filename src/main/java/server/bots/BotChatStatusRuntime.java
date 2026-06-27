package server.bots;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.integration.AgentBotBuildStatusRuntime;
import server.agents.integration.AgentBotStatusRuntime;

final class BotChatStatusRuntime {
    private BotChatStatusRuntime() {
    }

    static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.markActive(
                AgentBotStatusRuntime.statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    static void checkBotStatus(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(
                AgentBotStatusRuntime.statusCheckState(entry),
                AgentBotBuildStatusRuntime.statusCheckActions(entry, bot));
    }

    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        final Character bot = entry.bot;
        AgentChatStatusRuntime.announceOfflineReturn(AgentBotStatusRuntime.offlineReturnActions(bot));
    }

    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentBotStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                AgentBotStatusRuntime.afkReturnActions(entry));
    }

    static void prepareActiveModeEntry(BotEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(activeModeActions(entry));
    }

    static boolean isOwnerIdle(BotEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(AgentBotStatusRuntime.statusState(entry));
    }

    static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }

    private static AgentChatStatusRuntime.ActiveModeActions activeModeActions(BotEntry entry) {
        return new AgentChatStatusRuntime.ActiveModeActions() {
            @Override
            public void autoEquip() {
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
            }

            @Override
            public void resetGearSuggestionCooldown() {
                entry.setNextGearSuggestionAt(0);
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                BotChatStatusRuntime.maybeSuggestGearToSiblings(entry, entry.bot);
            }

            @Override
            public void setupAutopot() {
                BotPotionManager.setupAutopotForBot(entry.bot);
            }

            @Override
            public void checkPotShareOnModeStart() {
                BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
            }
        };
    }

    private static void maybeSuggestRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentBotStatusRuntime.gearSuggestionState(entry),
                AgentChatStatusRuntime.gearSuggestionActions(
                        owner != null,
                        () -> BotOfferManager.offerBestRecommendedGear(entry, bot, owner)),
                System.currentTimeMillis());
    }

    static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentBotStatusRuntime.gearSuggestionState(entry),
                AgentChatStatusRuntime.gearSuggestionActions(
                        owner != null,
                        () -> BotOfferManager.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

}
