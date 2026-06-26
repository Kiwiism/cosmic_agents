package server.bots;

import client.Character;
import client.Job;
import server.Trade;
import server.agents.capabilities.dialogue.AgentApBuildDialogueResolver;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatBuildFlow;
import server.agents.capabilities.dialogue.AgentChatBuffQueryFlow;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentChatPendingAction;
import server.agents.capabilities.dialogue.AgentChatRespecFlow;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatSocialFlow;
import server.agents.capabilities.dialogue.AgentChatSupplyRequestFlow;
import server.agents.capabilities.dialogue.AgentChatUtilityFlow;
import server.agents.capabilities.dialogue.AgentChatTransferFlow;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.capabilities.dialogue.AgentChatOrchestrator;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSkillReportFlow;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BotChatManager {
    // %s = current map name (bot is in town since the offline-return warp put it there).
    // Sent via party chat so the owner sees it across maps when they reconnect.
    static void markOwnerActive(BotEntry entry) {
        Character owner = entry.owner;
        entry.ownerWasAfk = false;
        entry.ownerAfkSinceMs = System.currentTimeMillis();
        entry.ownerAfkPos = owner != null ? new Point(owner.getPosition()) : null;
    }

    // Set true on entry; cleared to false only if we fall off the natural end of handleChat
    // (no command pattern matched). Every match path returns early, leaving this true. Caller
    // (BotManager) reads via wasLastChatHandled() to gate the LLM fallback.
    private static final ThreadLocal<Boolean> LAST_CHAT_HANDLED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean wasLastChatHandled() {
        return LAST_CHAT_HANDLED.get();
    }

    static void handleChat(BotEntry entry, String message) {
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, new BotChatOrchestratorContext(entry)));
    }

    static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionState() {
            @Override
            public String pendingAction() {
                return entry.pendingAction;
            }

            @Override
            public String pendingDropCategory() {
                return entry.pendingDropCategory;
            }

            @Override
            public void clearPendingAction() {
                entry.pendingAction = null;
            }

            @Override
            public void clearPendingDropCategory() {
                entry.pendingDropCategory = null;
            }
        };
    }

    static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return BotChatSessionRuntime.sessionRequestCallbacks(entry);
    }

    static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
        return new AgentChatToggleFlow.ToggleCallbacks() {
            @Override
            public void setSupport(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.skillBuffsEnabled = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.supportReply(enabled));
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.supportHealsEnabled = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.healsReply(enabled));
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffConsumablesEnabled = enabled;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.buffConsumablesReply(
                            enabled, entry.buffCheapMode));
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffCheapMode = cheapMode;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.buffConsumablesModeReply(cheapMode));
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.proactiveUpgradeOffers = enabled;
                    BotManager.getInstance().botReply(entry, AgentChatToggleFlow.proactiveOffersReply(enabled));
                });
            }
        };
    }

    static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
        return new AgentChatBuffQueryFlow.BuffQueryCallbacks() {
            @Override
            public void reportBuffList() {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    String summary = BotBuffManager.getChatSummary(
                            entry.buffConsumablesEnabled, entry.buffCheapMode, entry.bot);
                    BotManager.getInstance().botReply(entry, summary);
                });
            }

            @Override
            public void reportBuffDebug() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportBuffDebug(entry, entry.bot));
            }

            @Override
            public void reportSkillBuffDebug() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportSkillBuffDebug(entry, entry.bot));
            }
        };
    }

    static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
        return new AgentChatRespecFlow.RespecCallbacks() {
            @Override
            public void respecAp() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotBuildManager.respecAp(entry, entry.bot)));
            }

            @Override
            public void respecSp() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotBuildManager.respecSp(entry, entry.bot)));
            }
        };
    }

    static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
        return new AgentChatEquipmentFlow.EquipmentCallbacks() {
            @Override
            public boolean unequipSlot(String slotName) {
                short[] slots = BotEquipManager.slotsFromName(slotName);
                if (slots.length == 0) {
                    return false;
                }
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotEquipManager.unequipSlot(entry.bot, slots)));
                return true;
            }

            @Override
            public void unequipAll() {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, BotEquipManager.unequipAll(entry.bot));
                });
            }

            @Override
            public void autoEquipDebug() {
                BotManager.after(BotManager.randMs(400, 600), () -> {
                    List<String> lines = BotEquipManager.autoEquipDebug(entry.bot);
                    for (String line : lines) {
                        BotManager.getInstance().botReply(entry, line);
                    }
                });
            }

            @Override
            public void autoEquip() {
                BotManager.after(BotManager.randMs(400, 600), () -> {
                    BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem, true);
                    BotManager.getInstance().botReply(entry, AgentChatEquipmentFlow.gearOptimizedReply());
                });
            }
        };
    }

    static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return BotChatSupplyRuntime.supplyRequestCallbacks(entry);
    }

    static AgentChatSocialFlow.SocialCallbacks socialCallbacks(BotEntry entry) {
        return BotChatSocialRuntime.socialCallbacks(entry);
    }

    static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
        return new AgentChatMovementFlow.MovementCallbacks() {
            @Override
            public boolean farmHere() {
                Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issueFarmHere(entry, dest);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean patrol() {
                Point ownerPos = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (ownerPos == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issuePatrol(entry, ownerPos);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public boolean moveHere() {
                Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
                if (dest == null) {
                    return false;
                }
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotManager.getInstance().issueMoveTo(entry, dest, true);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.moveHereReply());
                });
                return true;
            }

            @Override
            public void follow() {
                BotManager.after(BotManager.randMs(1500, 2000), () -> {
                    BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                    entry.nextGearSuggestionAt = 0;
                    maybeSuggestGearToSiblings(entry, entry.bot);
                    BotManager.getInstance().botReply(entry, AgentChatMovementFlow.followReply());
                    BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
                    BotManager.after(BotManager.randMs(250, 750), () -> BotManager.getInstance().issueFollowOwner(entry));
                });
            }

            @Override
            public void grind() {
                BotManager.after(BotManager.randMs(1500, 2000), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().botReply(entry, BotPotionManager.grindStartMessage(entry.bot));
                    BotManager.after(BotManager.randMs(250, 750), () -> {
                        BotManager.getInstance().issueGrind(entry);
                        checkBotStatus(entry, entry.bot);
                    });
                });
            }

            @Override
            public void stop() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    BotManager.getInstance().issueStop(entry);
                    BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                    entry.nextGearSuggestionAt = 0;
                    maybeSuggestGearToSiblings(entry, entry.bot);
                    BotManager.after(BotManager.randMs(1400, 1600), () ->
                            BotManager.getInstance().botReply(entry, AgentChatMovementFlow.stopReply()));
                });
            }

            @Override
            public void fidget() {
                BotManager.after(BotManager.randMs(250, 500), () -> {
                    entry.bot.changeFaceExpression(randomFidgetExpression());
                    BotFidgetManager.maybeStartSocialFidget(entry);
                });
            }

            @Override
            public void greeting() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.bot.changeFaceExpression(Emote.HAPPY.getValue());
                    BotFidgetManager.maybeStartGreetingFidget(entry, ThreadLocalRandom.current().nextInt(100));
                    queueBotReply(entry, AgentChatMovementFlow.greetingReply());
                    checkBotStatus(entry, entry.bot);
                });
            }
        };
    }

    static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
        return new AgentChatUtilityFlow.UtilityCallbacks() {
            @Override
            public void tradeInvite() {
                Character bot = entry.bot;
                Character owner = entry.owner;
                if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                        && entry.pendingTradeCategory == null) {
                    BotManager.after(BotManager.randMs(600, 1000), () -> {
                        BotManager.getInstance().botReply(entry, AgentChatUtilityFlow.tradeInviteReply());
                        BotManager.after(BotManager.randMs(800, 1200), () -> {
                            Trade.startTrade(bot);
                            Trade.inviteTrade(bot, owner);
                        });
                    });
                }
            }

            @Override
            public void sellTrash() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotShopManager.requestSellTrashVisit(entry, entry.bot));
            }

            @Override
            public void makeCrystals() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotMakerManager.handleMakeCrystals(entry));
            }

            @Override
            public void disassembleTrash() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotMakerManager.handleDisassembleTrash(entry));
            }
        };
    }

    static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(BotEntry entry) {
        return new AgentChatBuildFlow.SpVariantCallbacks() {
            @Override
            public void oneHanded() {
                entry.spVariant = AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT;
                BotManager.getInstance().botReply(entry, AgentChatBuildFlow.oneHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            }

            @Override
            public void twoHanded() {
                entry.spVariant = AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT;
                BotManager.getInstance().botReply(entry, AgentChatBuildFlow.twoHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            }
        };
    }

    static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
        return new AgentChatBuildFlow.ApBuildCallbacks() {
            @Override
            public void requestBuildPrompt() {
                entry.apBuild = null;
                entry.apPromptSent = false;
                String prompt = BotBuildManager.requestApBuildPrompt(entry, entry.bot);
                if (prompt != null) {
                    BotManager.getInstance().botReply(entry, prompt);
                }
            }

            @Override
            public void selectBuild(String message) {
                handleApBuildSelection(entry, message);
            }
        };
    }

    static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return new AgentChatReportFlow.ReportCallbacks() {
            @Override
            public void help() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportHelp(entry));
            }

            @Override
            public void requestUpgrade() {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotChatSupplyRuntime.handleRequestUpgradeCommand(entry, entry.bot));
            }

            @Override
            public void recommendedGear() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatReportRuntime.reportRecommendedGear(entry, entry.bot));
            }

            @Override
            public void skills() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportSkills(entry, entry.bot));
            }

            @Override
            public void stats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportStats(entry, entry.bot));
            }

            @Override
            public void movementStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportMovementStats(entry, entry.bot));
            }

            @Override
            public void range() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportRange(entry, entry.bot));
            }

            @Override
            public void build() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportBuild(entry, entry.bot));
            }

            @Override
            public void inventory() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportInventory(entry, entry.bot));
            }

            @Override
            public void mesos() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportMesos(entry, entry.bot));
            }

            @Override
            public void exp() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportExp(entry, entry.bot));
            }

            @Override
            public void inventorySlots() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportInventorySlots(entry, entry.bot));
            }

            @Override
            public void scrolls() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportScrolls(entry, entry.bot));
            }

            @Override
            public void potions() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportPotions(entry, entry.bot));
            }

            @Override
            public void debugStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportDebugStats(entry, entry.bot));
            }

            @Override
            public void critDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportCritDebug(entry, entry.bot));
            }

            @Override
            public void potDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> BotChatReportRuntime.reportPotDebug(entry, entry.bot));
            }
        };
    }

    static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(BotEntry entry) {
        return advJob -> {
            String reply = AgentChatJobAdvancementFlow.jobChangeReply(advJob);
            BotManager.getInstance().botReply(entry, reply);
            BotManager.after(BotManager.randMs(900, 1100), () -> BotStarterKitManager.advanceJob(entry, advJob));
        };
    }

    static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(BotEntry entry) {
        return BotChatTransferRuntime.itemQueryCallbacks(entry);
    }

    static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                BotChatSessionRuntime.handleOwnerAwayChoice(entry, message);
            }

            @Override
            public void executeItemChoice(String category, boolean trade) {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotInventoryManager.executeChoice(category, trade, entry, entry.bot));
            }

            @Override
            public void cancelItemChoice() {
                BotManager.after(BotManager.randMs(400, 600),
                        () -> BotManager.getInstance().botReply(entry, AgentPendingChatActionFlow.keepDropChoiceReply()));
            }

            @Override
            public void handleSkillTreeChoice(String message) {
                BotChatManager.handleSkillTreeChoice(entry, entry.bot, message);
            }

            @Override
            public void confirmRelog() {
                BotChatSessionRuntime.scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                BotChatSessionRuntime.scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    public static void queueBotSay(BotEntry entry, String message) {
        BotChatReplyRuntime.queueSay(entry, message);
    }

    static void queueBotReply(BotEntry entry, String message) {
        BotChatReplyRuntime.queueReply(entry, message);
    }

    static long queueBotSayWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueSayWithEstimatedDelay(entry, message);
    }

    static long queueBotReplyWithEstimatedDelay(BotEntry entry, String message) {
        return BotChatReplyRuntime.queueReplyWithEstimatedDelay(entry, message);
    }

    // Status check — called on spawn, grind start, greeting, and level-up
    static void checkBotStatus(BotEntry entry, Character bot) {
        String jobPrompt = BotBuildManager.buildJobPrompt(entry, bot);
        if (jobPrompt != null) queueBotReply(entry, jobPrompt);
        String spPrompt = BotBuildManager.buildSpVariantPrompt(entry, bot);
        if (spPrompt != null) {
            queueBotReply(entry, spPrompt);
        } else {
            BotBuildManager.autoAssignSp(entry, bot);
        }
        String apPrompt = BotBuildManager.buildApPrompt(entry, bot);
        if (apPrompt != null) {
            queueBotReply(entry, apPrompt);
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

    /**
     * Announces the bot's town location via party chat after the owner reconnects
     * (or revives) following a 5+ min offline-or-dead window during which the bot
     * scrolled to town. Party chat reaches the owner even if they spawn back into
     * a different map.
     */
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

    /** Detects owner AFK (same position ≥5 min) and says "wb" when they return. */
    static void tickAfkCheck(BotEntry entry, Character owner) {
        AgentChatWelcomeBackFlow.tickAfkCheck(
                afkState(entry),
                owner.getPosition(),
                System.currentTimeMillis(),
                welcomeBackCallbacks(entry));
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

    static String buildRangeReport(Character bot) {
        return BotChatReportRuntime.buildRangeReport(bot);
    }

    static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return BotChatReportRuntime.buildRangeReport(bot, mobProfile);
    }

    static List<String> buildMovementStatsReport(Character bot) {
        return BotChatReportRuntime.buildMovementStatsReport(bot);
    }

    private static void reportHelp(BotEntry entry) {
        BotChatReportRuntime.reportHelp(entry);
    }

    private static void handleApBuildSelection(BotEntry entry, String message) {
        Job job = entry.bot.getJob();
        AgentApBuildDialogueResolver.ApBuildChoice choice = AgentApBuildDialogueResolver.resolve(
                job, entry.bot.getDex(), entry.bot.getLuk(), entry.bot.getStr(), message);
        if (choice != null) {
            applyApBuildChoice(entry, toBotApBuild(choice), choice.confirmMessage(), choice.alreadyMessage());
        }
    }

    private static BotBuildManager.ApBuild toBotApBuild(AgentApBuildDialogueResolver.ApBuildChoice choice) {
        return new BotBuildManager.ApBuild(
                toBotStatType(choice.primaryStat()),
                toBotStatType(choice.secondaryStat()),
                choice.secondaryTarget());
    }

    private static BotBuildManager.StatType toBotStatType(AgentApBuildDialogueResolver.StatType statType) {
        return switch (statType) {
            case STR -> BotBuildManager.StatType.STR;
            case DEX -> BotBuildManager.StatType.DEX;
            case INT -> BotBuildManager.StatType.INT;
            case LUK -> BotBuildManager.StatType.LUK;
        };
    }

    private static void applyApBuildChoice(BotEntry entry, BotBuildManager.ApBuild build, String confirmMsg, String alreadyMsg) {
        if (sameApBuild(entry.apBuild, build)) {
            BotManager.getInstance().botReply(entry, alreadyMsg);
            return;
        }
        BotBuildManager.setApBuild(entry, build, confirmMsg);
    }

    private static boolean sameApBuild(BotBuildManager.ApBuild left, BotBuildManager.ApBuild right) {
        return left != null
                && right != null
                && left.primaryStat == right.primaryStat
                && left.secondaryStat == right.secondaryStat
                && left.secondaryTarget == right.secondaryTarget;
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

    /** Check if this bot has gear that would be an upgrade for a sibling bot. */
    private static void maybeSuggestGearToSiblings(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null || now < entry.nextGearSuggestionAt) {
            return;
        }

        if (BotOfferManager.offerBestGearToSibling(entry, bot)) {
            entry.nextGearSuggestionAt = now + 60_000L;
        }
    }

    /**
     * Shared prelude for owner-issued active-combat-mode commands (grind / sentry
     * / patrol). Keeps the modes in lock-step on autoEquip, gear suggestion,
     * autopot keybind setup, and the initial pot-share request — otherwise new
     * modes silently miss one of these (the original sentry-mode bug).
     */
    private static void prepareActiveModeEntry(BotEntry entry) {
        BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
        entry.nextGearSuggestionAt = 0;
        maybeSuggestGearToSiblings(entry, entry.bot);
        BotPotionManager.setupAutopotForBot(entry.bot);
        BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
    }

    /** Returns true when the owner hasn't moved in ≥5 min (AFK). Skip chat interactions. */
    static boolean isOwnerIdle(BotEntry entry) {
        return entry.ownerWasAfk;
    }

    static int randomFidgetExpression() {
        int[] expressions = {2, 3, 5, 6, 7};
        return expressions[ThreadLocalRandom.current().nextInt(expressions.length)];
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
        if (decision.clearPendingAction()) {
            entry.pendingAction = null;
        }
        if (decision.requestSkillTreeChoice()) {
            entry.pendingAction = AgentChatPendingAction.SKILL_TREE_CHOICE;
        }
        for (String line : decision.replies()) {
            queueBotReply(entry, line);
        }
    }

    static void handleTransferCommand(BotEntry entry, AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        BotChatTransferRuntime.handleTransferCommand(entry, transferCommand, message);
    }

}
