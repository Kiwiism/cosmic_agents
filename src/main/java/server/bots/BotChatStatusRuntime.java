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
        String jobPrompt = BotBuildManager.buildJobPrompt(entry, bot);
        if (jobPrompt != null) {
            AgentBotReplyRuntime.queueReply(entry, jobPrompt);
        }
        String spPrompt = BotBuildManager.buildSpVariantPrompt(entry, bot);
        if (spPrompt != null) {
            AgentBotReplyRuntime.queueReply(entry, spPrompt);
        } else {
            BotBuildManager.autoAssignSp(entry, bot);
        }
        String apPrompt = BotBuildManager.buildApPrompt(entry, bot);
        if (apPrompt != null) {
            AgentBotReplyRuntime.queueReply(entry, apPrompt);
        } else {
            BotBuildManager.autoAssignAp(entry, bot);
        }
        maybeSuggestRecommendedGear(entry, bot);
        maybeSuggestGearToSiblings(entry, bot);
        if (!entry.spawnUpgradeCheckDone) {
            entry.spawnUpgradeCheckDone = true;
            Character owner = entry.owner;
            if (owner != null && !isOwnerIdle(entry) && entry.pendingAction == null && !BotOfferManager.hasPendingOffer(entry)) {
                List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    BotOfferManager.notifyOwnerGainedEquip(entry, bot, recs.get(0).candidate());
                }
            }
        }
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
        BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
        entry.nextGearSuggestionAt = 0;
        maybeSuggestGearToSiblings(entry, entry.bot);
        BotPotionManager.setupAutopotForBot(entry.bot);
        BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
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
