package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotStatusRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;

import java.util.List;

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
        AgentChatStatusRuntime.checkStatus(AgentBotStatusRuntime.statusCheckState(entry), statusCheckActions(entry, bot));
    }

    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        final Character bot = entry.bot;
        AgentChatStatusRuntime.announceOfflineReturn(offlineReturnActions(bot));
    }

    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatStatusRuntime.tickAfkCheck(
                AgentBotStatusRuntime.afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                afkReturnActions(entry));
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

    private static AgentChatStatusRuntime.StatusCheckActions statusCheckActions(BotEntry entry, Character bot) {
        return new AgentChatStatusRuntime.StatusCheckActions() {
            @Override
            public String buildJobPrompt() {
                return BotBuildManager.buildJobPrompt(entry, bot);
            }

            @Override
            public String buildSpVariantPrompt() {
                return BotBuildManager.buildSpVariantPrompt(entry, bot);
            }

            @Override
            public String buildApPrompt() {
                return BotBuildManager.buildApPrompt(entry, bot);
            }

            @Override
            public void queueReply(String message) {
                AgentBotReplyRuntime.queueReply(entry, message);
            }

            @Override
            public void autoAssignSp() {
                BotBuildManager.autoAssignSp(entry, bot);
            }

            @Override
            public void autoAssignAp() {
                BotBuildManager.autoAssignAp(entry, bot);
            }

            @Override
            public void maybeSuggestRecommendedGear() {
                BotChatStatusRuntime.maybeSuggestRecommendedGear(entry, bot);
            }

            @Override
            public void maybeSuggestGearToSiblings() {
                BotChatStatusRuntime.maybeSuggestGearToSiblings(entry, bot);
            }

            @Override
            public boolean canOfferSpawnUpgrade() {
                Character owner = entry.owner;
                return owner != null
                        && !isOwnerIdle(entry)
                        && entry.pendingAction == null
                        && !BotOfferManager.hasPendingOffer(entry);
            }

            @Override
            public void offerSpawnUpgradeIfAvailable() {
                Character owner = entry.owner;
                List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    BotOfferManager.notifyOwnerGainedEquip(entry, bot, recs.get(0).candidate());
                }
            }
        };
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
                gearSuggestionActions(owner != null, () -> BotOfferManager.offerBestRecommendedGear(entry, bot, owner)),
                System.currentTimeMillis());
    }

    static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.maybeSuggestGear(
                AgentBotStatusRuntime.gearSuggestionState(entry),
                gearSuggestionActions(owner != null, () -> BotOfferManager.offerBestGearToSibling(entry, bot)),
                System.currentTimeMillis());
    }

    private static AgentChatStatusRuntime.GearSuggestionActions gearSuggestionActions(
            boolean hasRecipient,
            java.util.function.BooleanSupplier offerGear) {
        return new AgentChatStatusRuntime.GearSuggestionActions() {
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

    private static AgentChatStatusRuntime.OfflineReturnActions offlineReturnActions(Character bot) {
        return new AgentChatStatusRuntime.OfflineReturnActions() {
            @Override
            public boolean hasAgent() {
                return bot != null;
            }

            @Override
            public String mapName() {
                return bot != null && bot.getMap() != null ? bot.getMap().getMapName() : null;
            }

            @Override
            public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
                AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                bot.changeFaceExpression(expression);
            }

            @Override
            public void sayParty(String text) {
                BotManager.getInstance().botSayParty(bot, text);
            }
        };
    }

    private static AgentChatStatusRuntime.AfkReturnActions afkReturnActions(BotEntry entry) {
        return new AgentChatStatusRuntime.AfkReturnActions() {
            @Override
            public boolean hasAgent() {
                return entry.bot != null;
            }

            @Override
            public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
                AgentBotSchedulerRuntime.afterRandomDelay(minMs, maxMs, action);
            }

            @Override
            public void changeFaceExpression(int expression) {
                entry.bot.changeFaceExpression(expression);
            }

            @Override
            public void reply(String text) {
                BotManager.getInstance().botReply(entry, text);
            }
        };
    }
}
