package server.bots;


import server.agents.integration.AgentBotReplyRuntime;
import client.Character;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;

import java.awt.Point;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

final class BotChatStatusRuntime {
    private BotChatStatusRuntime() {
    }

    static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner;
        AgentChatStatusRuntime.markActive(
                statusState(entry),
                owner != null ? owner.getPosition() : null,
                System.currentTimeMillis());
    }

    static void checkBotStatus(BotEntry entry, Character bot) {
        AgentChatStatusRuntime.checkStatus(statusCheckState(entry), statusCheckActions(entry, bot));
    }

    static void announceOwnerReturnedFromOffline(BotEntry entry) {
        final Character bot = entry.bot;
        if (bot == null) {
            return;
        }
        final String text = AgentChatWelcomeBackFlow.welcomeBackOfflinePartyReply(
                bot.getMap() != null ? bot.getMap().getMapName() : null);
        BotManager.after(BotManager.randMs(1500, 2500), () -> {
            bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? 2 : 3);
            BotManager.getInstance().botSayParty(bot, text);
        });
    }

    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatWelcomeBackFlow.tickAfkCheck(
                afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                welcomeBackCallbacks(entry));
    }

    static void prepareActiveModeEntry(BotEntry entry) {
        AgentChatStatusRuntime.prepareActiveMode(activeModeActions(entry));
    }

    static boolean isOwnerIdle(BotEntry entry) {
        return AgentChatStatusRuntime.isOwnerIdle(statusState(entry));
    }

    static int randomFidgetExpression() {
        return AgentChatStatusRuntime.randomFidgetExpression();
    }

    private static AgentChatStatusRuntime.StatusState statusState(BotEntry entry) {
        return new AgentChatStatusRuntime.StatusState() {
            @Override
            public void setOwnerAfkPosition(Point position) {
                entry.ownerAfkPos = position;
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                entry.ownerAfkSinceMs = sinceMs;
            }

            @Override
            public boolean ownerWasAfk() {
                return entry.ownerWasAfk;
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                entry.ownerWasAfk = wasAfk;
            }
        };
    }

    private static AgentChatStatusRuntime.StatusCheckState statusCheckState(BotEntry entry) {
        return new AgentChatStatusRuntime.StatusCheckState() {
            @Override
            public boolean spawnUpgradeCheckDone() {
                return entry.spawnUpgradeCheckDone;
            }

            @Override
            public void setSpawnUpgradeCheckDone(boolean done) {
                entry.spawnUpgradeCheckDone = done;
            }
        };
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
                entry.nextGearSuggestionAt = 0;
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

    private static AgentChatWelcomeBackFlow.AfkState afkState(BotEntry entry) {
        return new AgentChatWelcomeBackFlow.AfkState() {
            @Override
            public Point ownerAfkPosition() {
                return entry.ownerAfkPos;
            }

            @Override
            public void setOwnerAfkPosition(Point position) {
                entry.ownerAfkPos = position;
            }

            @Override
            public long ownerAfkSinceMs() {
                return entry.ownerAfkSinceMs;
            }

            @Override
            public void setOwnerAfkSinceMs(long sinceMs) {
                entry.ownerAfkSinceMs = sinceMs;
            }

            @Override
            public boolean ownerWasAfk() {
                return entry.ownerWasAfk;
            }

            @Override
            public void setOwnerWasAfk(boolean wasAfk) {
                entry.ownerWasAfk = wasAfk;
            }
        };
    }

    private static AgentChatWelcomeBackFlow.WelcomeBackCallbacks welcomeBackCallbacks(BotEntry entry) {
        return () -> {
            final Character bot = entry.bot;
            BotManager.after(BotManager.randMs(1800, 2200), () -> {
                bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? 2 : 3);
                BotManager.getInstance().botReply(entry, AgentChatWelcomeBackFlow.welcomeBackReply());
            });
        };
    }

    private static void maybeSuggestRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null || now < entry.nextGearSuggestionAt) {
            return;
        }

        if (BotOfferManager.offerBestRecommendedGear(entry, bot, owner)) {
            entry.nextGearSuggestionAt = now + 60_000L;
        }
    }

    static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null || now < entry.nextGearSuggestionAt) {
            return;
        }

        if (BotOfferManager.offerBestGearToSibling(entry, bot)) {
            entry.nextGearSuggestionAt = now + 60_000L;
        }
    }
}
