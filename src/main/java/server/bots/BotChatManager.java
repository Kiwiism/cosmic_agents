package server.bots;

import client.Character;
import client.Job;
import client.inventory.WeaponType;
import server.Trade;
import server.agents.capabilities.dialogue.AgentApBuildDialogueResolver;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatAwayFlow;
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
import server.agents.capabilities.dialogue.AgentCharacterDialogueReporter;
import server.agents.capabilities.dialogue.AgentCombatDialogueReporter;
import server.agents.capabilities.dialogue.AgentFameDialogueFlow;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentMovementDialogueReporter;
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
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.agents.capabilities.dialogue.AgentSupplyRequestOutcomeFlow;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;
import server.combat.CombatFormulaProvider;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BotChatManager {
    private static final ExecutorService TRADE_COMMAND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "bot-trade-command");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, AtomicInteger> PENDING_TRANSFER_REQUESTS = new ConcurrentHashMap<>();

    private record TransferCommandResult(boolean hasItems, int count) {}
    private record ItemQueryResult(int count) {}
    // %s = current map name (bot is in town since the offline-return warp put it there).
    // Sent via party chat so the owner sees it across maps when they reconnect.
    private static void markOwnerActive(BotEntry entry) {
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
        LAST_CHAT_HANDLED.set(AgentChatOrchestrator.handle(message, chatOrchestratorContext(entry)));
    }

    private static AgentChatOrchestrator.Context chatOrchestratorContext(BotEntry entry) {
        return new AgentChatOrchestrator.Context() {
            @Override
            public void markActive() {
                markOwnerActive(entry);
            }

            @Override
            public boolean hasPendingAction() {
                return entry.pendingAction != null;
            }

            @Override
            public AgentPendingChatActionFlow.PendingActionState pendingActionState() {
                return BotChatManager.pendingActionState(entry);
            }

            @Override
            public AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks() {
                return BotChatManager.pendingActionCallbacks(entry);
            }

            @Override
            public AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks() {
                return BotChatManager.sessionRequestCallbacks(entry);
            }

            @Override
            public AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks() {
                return BotChatManager.supplyRequestCallbacks(entry);
            }

            @Override
            public AgentChatSocialFlow.SocialCallbacks socialCallbacks() {
                return BotChatManager.socialCallbacks(entry);
            }

            @Override
            public AgentChatToggleFlow.ToggleCallbacks toggleCallbacks() {
                return BotChatManager.toggleCallbacks(entry);
            }

            @Override
            public AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks() {
                return BotChatManager.buffQueryCallbacks(entry);
            }

            @Override
            public AgentChatRespecFlow.RespecCallbacks respecCallbacks() {
                return BotChatManager.respecCallbacks(entry);
            }

            @Override
            public AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks() {
                return BotChatManager.equipmentCallbacks(entry);
            }

            @Override
            public AgentChatMovementFlow.MovementCallbacks movementCallbacks() {
                return BotChatManager.movementCallbacks(entry);
            }

            @Override
            public boolean isWaitingForSpVariant() {
                return entry.spVariantPromptSent && entry.spVariant == null;
            }

            @Override
            public AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks() {
                return BotChatManager.spVariantCallbacks(entry);
            }

            @Override
            public boolean isWaitingForApBuild() {
                return entry.apPromptSent;
            }

            @Override
            public AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks() {
                return BotChatManager.apBuildCallbacks(entry);
            }

            @Override
            public AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks() {
                return BotChatManager.utilityCallbacks(entry);
            }

            @Override
            public void handleTransferCommand(AgentChatTransferFlow.TransferCommand transferCommand, String message) {
                BotChatManager.handleTransferCommand(entry, transferCommand, message);
            }

            @Override
            public AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks() {
                return BotChatManager.itemQueryCallbacks(entry);
            }

            @Override
            public AgentChatReportFlow.ReportCallbacks reportCallbacks() {
                return BotChatManager.reportCallbacks(entry);
            }

            @Override
            public Job currentJob() {
                return entry.bot.getJob();
            }

            @Override
            public int level() {
                return entry.bot.getLevel();
            }

            @Override
            public AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks() {
                return BotChatManager.jobAdvancementCallbacks(entry);
            }
        };
    }

    private static AgentPendingChatActionFlow.PendingActionState pendingActionState(BotEntry entry) {
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

    private static AgentChatSessionRequestFlow.SessionRequestCallbacks sessionRequestCallbacks(BotEntry entry) {
        return new AgentChatSessionRequestFlow.SessionRequestCallbacks() {
            @Override
            public void requestRelog() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingAction = AgentChatPendingAction.RELOG;
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.relogConfirmPrompt());
                });
            }

            @Override
            public void requestLogout() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingAction = AgentChatPendingAction.LOGOUT;
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.logoutConfirmPrompt());
                });
            }

            @Override
            public void requestAway() {
                if (!BotManager.getInstance().isFirstBotEntry(entry)) {
                    return;
                }
                BotManager.after(BotManager.randMs(900, 1100), () -> promptOwnerAway(entry));
            }
        };
    }

    private static AgentChatToggleFlow.ToggleCallbacks toggleCallbacks(BotEntry entry) {
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

    private static AgentChatBuffQueryFlow.BuffQueryCallbacks buffQueryCallbacks(BotEntry entry) {
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
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatManager.reportBuffDebug(entry, entry.bot));
            }

            @Override
            public void reportSkillBuffDebug() {
                BotManager.after(BotManager.randMs(500, 700), () -> BotChatManager.reportSkillBuffDebug(entry, entry.bot));
            }
        };
    }

    private static AgentChatRespecFlow.RespecCallbacks respecCallbacks(BotEntry entry) {
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

    private static AgentChatEquipmentFlow.EquipmentCallbacks equipmentCallbacks(BotEntry entry) {
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

    private static AgentChatSupplyRequestFlow.SupplyRequestCallbacks supplyRequestCallbacks(BotEntry entry) {
        return new AgentChatSupplyRequestFlow.SupplyRequestCallbacks() {
            @Override
            public void requestPotion(boolean hpPotion) {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedPotionCommand(entry, hpPotion));
            }

            @Override
            public void requestAnyPotion() {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAnyPotionCommand(entry));
            }

            @Override
            public void requestAmmo() {
                BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAmmoCommand(entry));
            }
        };
    }

    private static AgentChatSocialFlow.SocialCallbacks socialCallbacks(BotEntry entry) {
        return targetName -> BotManager.after(BotManager.randMs(500, 900), () -> handleFameCommand(entry, targetName));
    }

    private static AgentChatMovementFlow.MovementCallbacks movementCallbacks(BotEntry entry) {
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

    private static AgentChatUtilityFlow.UtilityCallbacks utilityCallbacks(BotEntry entry) {
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

    private static AgentChatBuildFlow.SpVariantCallbacks spVariantCallbacks(BotEntry entry) {
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

    private static AgentChatBuildFlow.ApBuildCallbacks apBuildCallbacks(BotEntry entry) {
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

    private static AgentChatReportFlow.ReportCallbacks reportCallbacks(BotEntry entry) {
        return new AgentChatReportFlow.ReportCallbacks() {
            @Override
            public void help() {
                BotManager.after(BotManager.randMs(500, 700), () -> reportHelp(entry));
            }

            @Override
            public void requestUpgrade() {
                BotManager.after(BotManager.randMs(500, 700), () -> handleRequestUpgradeCommand(entry, entry.bot));
            }

            @Override
            public void recommendedGear() {
                BotManager.after(BotManager.randMs(500, 700), () -> reportRecommendedGear(entry, entry.bot));
            }

            @Override
            public void skills() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportSkills(entry, entry.bot));
            }

            @Override
            public void stats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportStats(entry, entry.bot));
            }

            @Override
            public void movementStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportMovementStats(entry, entry.bot));
            }

            @Override
            public void range() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportRange(entry, entry.bot));
            }

            @Override
            public void build() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportBuild(entry, entry.bot));
            }

            @Override
            public void inventory() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportInventory(entry, entry.bot));
            }

            @Override
            public void mesos() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportMesos(entry, entry.bot));
            }

            @Override
            public void exp() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportExp(entry, entry.bot));
            }

            @Override
            public void inventorySlots() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportInventorySlots(entry, entry.bot));
            }

            @Override
            public void scrolls() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportScrolls(entry, entry.bot));
            }

            @Override
            public void potions() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportPotions(entry, entry.bot));
            }

            @Override
            public void debugStats() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportDebugStats(entry, entry.bot));
            }

            @Override
            public void critDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportCritDebug(entry, entry.bot));
            }

            @Override
            public void potDebug() {
                BotManager.after(BotManager.randMs(900, 1100), () -> reportPotDebug(entry, entry.bot));
            }
        };
    }

    private static AgentChatJobAdvancementFlow.JobAdvancementCallbacks jobAdvancementCallbacks(BotEntry entry) {
        return advJob -> {
            String reply = AgentChatJobAdvancementFlow.jobChangeReply(advJob);
            BotManager.getInstance().botReply(entry, reply);
            BotManager.after(BotManager.randMs(900, 1100), () -> BotStarterKitManager.advanceJob(entry, advJob));
        };
    }

    private static AgentChatTransferFlow.ItemQueryCallbacks itemQueryCallbacks(BotEntry entry) {
        return itemName -> handleItemQuery(entry, itemName);
    }

    private static AgentPendingChatActionFlow.PendingActionCallbacks pendingActionCallbacks(BotEntry entry) {
        return new AgentPendingChatActionFlow.PendingActionCallbacks() {
            @Override
            public void handleOwnerAwayChoice(String message) {
                BotChatManager.handleOwnerAwayChoice(entry, message);
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
                scheduleRelogConfirm(entry);
            }

            @Override
            public void confirmLogout() {
                scheduleLogoutConfirm(entry);
            }

            @Override
            public void cancelPendingAction(boolean dropAction) {
                String cancelMsg = AgentPendingChatActionFlow.pendingActionCancelReply(dropAction);
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    private static void scheduleRelogConfirm(BotEntry entry) {
        BotManager.after(BotManager.randMs(900, 1100), () -> {
            Character o = entry.owner;
            if (o == null) return; // owner logged out before relog fired
            BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.relogConfirmedReply());
            int charId      = entry.bot.getId();
            int ownerCharId = o.getId();
            int world       = entry.bot.getClient().getWorld();
            int channel     = entry.bot.getClient().getChannel();
            BotManager.after(BotManager.randMs(1800, 2200), () -> {
                entry.bot.saveCharToDB(true);
                entry.bot.getClient().disconnect(false, false);
                BotManager.after(BotManager.randMs(10000, 10100),
                        () -> BotManager.getInstance().reloginBot(charId, ownerCharId, world, channel));
            });
        });
    }

    private static void scheduleLogoutConfirm(BotEntry entry) {
        BotManager.after(BotManager.randMs(900, 1100), () -> {
            BotManager.getInstance().botReply(entry, AgentChatSessionRequestFlow.logoutConfirmedReply());
            BotManager.after(BotManager.randMs(1800, 2200), () -> {
                entry.bot.saveCharToDB(true);
                entry.bot.getClient().disconnect(false, false);
            });
        });
    }

    private static void promptOwnerAway(BotEntry entry) {
        AgentChatAwayFlow.promptOwnerAway(
                BotManager.getInstance().shouldOfferTownForAwayCommand(entry),
                awayPromptCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayPromptCallbacks awayPromptCallbacks(BotEntry entry) {
        return new AgentChatAwayFlow.AwayPromptCallbacks() {
            @Override
            public void setPendingOwnerAway() {
                entry.pendingAction = AgentChatPendingAction.OWNER_AWAY;
            }

            @Override
            public void stopAgent() {
                BotManager.getInstance().issueStop(entry);
            }

            @Override
            public void replyTownOrLogout() {
                BotManager.getInstance().botReply(entry, AgentChatAwayFlow.townOrLogoutPrompt());
            }

            @Override
            public void replyStayOrLogout() {
                BotManager.getInstance().botReply(entry, AgentChatAwayFlow.stayOrLogoutPrompt());
            }
        };
    }

    private static void handleOwnerAwayChoice(BotEntry entry, String message) {
        AgentChatAwayFlow.handleOwnerAwayChoice(
                message,
                BotManager.getInstance().shouldOfferTownForAwayCommand(entry),
                awayChoiceCallbacks(entry));
    }

    private static AgentChatAwayFlow.AwayChoiceCallbacks awayChoiceCallbacks(BotEntry entry) {
        return new AgentChatAwayFlow.AwayChoiceCallbacks() {
            @Override
            public void clearPendingAction() {
                entry.pendingAction = null;
            }

            @Override
            public void logout() {
                BotManager.after(BotManager.randMs(700, 900), () -> {
                    BotManager.getInstance().botReply(entry, AgentChatAwayFlow.logoutConfirmReply());
                    logoutOwnerBots(entry);
                });
            }

            @Override
            public void townOrStay(boolean townOffered) {
                int ownerId = entry.owner != null ? entry.owner.getId() : 0;
                if (ownerId != 0) {
                    BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, townOffered);
                }
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.townOrStayConfirmReply(townOffered)));
            }

            @Override
            public void stay() {
                int ownerId = entry.owner != null ? entry.owner.getId() : 0;
                if (ownerId != 0) {
                    BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, false);
                }
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.stayConfirmReply()));
            }

            @Override
            public void cancel() {
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentChatAwayFlow.cancelReply()));
            }
        };
    }

    private static void logoutOwnerBots(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }

        for (BotEntry owned : BotManager.getInstance().getBotEntries(owner.getId())) {
            BotManager.getInstance().issueStop(owned);
            BotManager.after(BotManager.randMs(1200, 1800), () -> {
                owned.bot.saveCharToDB(true);
                owned.bot.getClient().disconnect(false, false);
            });
        }
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    public static void queueBotSay(BotEntry entry, String message) {
        queueMessageWithEstimatedDelay(entry, message, false);
    }

    static void queueBotReply(BotEntry entry, String message) {
        queueMessageWithEstimatedDelay(entry, message, true);
    }

    static long queueBotSayWithEstimatedDelay(BotEntry entry, String message) {
        return queueMessageWithEstimatedDelay(entry, message, false);
    }

    static long queueBotReplyWithEstimatedDelay(BotEntry entry, String message) {
        return queueMessageWithEstimatedDelay(entry, message, true);
    }

    private static long queueMessageWithEstimatedDelay(BotEntry entry, String message, boolean ownerDirected) {
        return AgentReplyQueue.queueMessageWithEstimatedDelay(
                replyQueueState(entry),
                message,
                ownerDirected,
                replyQueueDispatcher(entry));
    }

    private static AgentReplyQueue.State replyQueueState(BotEntry entry) {
        return new AgentReplyQueue.State() {
            @Override
            public java.util.Deque<AgentQueuedMessage> queue() {
                return entry.msgQueue;
            }

            @Override
            public boolean isSending() {
                return entry.msgSending;
            }

            @Override
            public void setSending(boolean sending) {
                entry.msgSending = sending;
            }
        };
    }

    private static AgentReplyQueue.Dispatcher replyQueueDispatcher(BotEntry entry) {
        return new AgentReplyQueue.Dispatcher() {
            @Override
            public void dispatch(AgentQueuedMessage message) {
                if (message.ownerDirected()) {
                    BotManager.getInstance().botReply(entry, message.text());
                } else {
                    BotManager.getInstance().botSay(entry, message.text());
                }
            }

            @Override
            public void scheduleNext(Runnable task, int delayMs) {
                BotManager.after(delayMs, task);
            }
        };
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

    private static void reportStats(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.statsReport(bot));
    }

    private static void reportRange(BotEntry entry, Character bot) {
        queueBotReply(entry, buildRangeReport(bot));
    }

    static String buildRangeReport(Character bot) {
        BotEquipManager.MapDamageProfile dmgProfile = BotEquipManager.MapDamageProfile.snapshot(bot);
        BotEquipManager.MapDamageProfile hitProfile = BotEquipManager.MapDamageProfile.snapshotByAvoid(bot);
        return buildRangeReport(bot, dmgProfile, hitProfile);
    }

    static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile) {
        return buildRangeReport(bot, mobProfile, mobProfile);
    }

    private static String buildRangeReport(Character bot, BotEquipManager.MapDamageProfile mobProfile,
                                           BotEquipManager.MapDamageProfile hitProfile) {
        AgentCombatDialogueReporter.MobHitProfile agentHitProfile = hitProfile == null
                ? null
                : new AgentCombatDialogueReporter.MobHitProfile(hitProfile.mobLevel(), hitProfile.mobAvoid());
        return AgentCombatDialogueReporter.rangeReport(
                bot, BotEquipManager.isMageJob(bot.getJob()), agentHitProfile);
    }

    private static void reportMovementStats(BotEntry entry, Character bot) {
        for (String line : buildMovementStatsReport(bot)) {
            queueBotReply(entry, line);
        }
    }

    private static void reportBuild(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.buildReport(bot));
    }

    private static void reportSkills(BotEntry entry, Character bot) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        List<AgentSkillReportFlow.SkillLine> beginnerSkills =
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = AgentSkillDialogueReporter.remainingBeginnerSp(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.reportSkills(
                bot.isBeginnerJob(),
                bot.getRemainingSp(),
                beginnerSkills,
                beginnerSpLeft,
                skillTrees));
    }

    private static void reportInventory(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentInventoryDialogueReporter.inventorySummary(bot));
    }

    private static void reportMesos(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    private static void reportExp(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    private static void reportInventorySlots(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentInventoryDialogueReporter.slotsReport(bot));
    }

    private static void reportScrolls(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentInventoryDialogueReporter.scrollReport(bot));
    }

    private static void reportPotions(BotEntry entry, Character bot) {
        int[] counts = BotPotionManager.countPotions(bot);
        queueBotReply(entry, AgentSupplyDialogueReporter.potionReport(counts));
    }

    private static void reportPotDebug(BotEntry entry, Character bot) {
        queueBotReply(entry, BotPotionManager.autopotDebugReport(bot));
    }

    static List<String> buildMovementStatsReport(Character bot) {
        if (bot == null) {
            return AgentMovementDialogueReporter.movementStatsReport(null, 0, 0, false, 0, null);
        }

        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        int rawSpeedStat = bot.getTotalMoveSpeedStat();
        int rawJumpStat = bot.getTotalJumpStat();
        boolean movementSkillsForced = map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit());
        AgentMovementDialogueReporter.MovementProfile agentProfile =
                new AgentMovementDialogueReporter.MovementProfile(
                        profile.totalSpeedStat(),
                        profile.totalJumpStat(),
                        profile.walkVelocityPxs(),
                        profile.hForcePxs(),
                        BotPhysicsEngine.jumpForcePerTick(profile),
                        BotPhysicsEngine.ropeJumpForcePerTick(profile),
                        BotPhysicsEngine.calculateMaxJumpHeight(profile));
        AgentMovementDialogueReporter.MapMovementProfile mapProfile = map == null
                ? null
                : new AgentMovementDialogueReporter.MapMovementProfile(
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        BotPhysicsEngine.maxJumpHorizontalTravel(map, profile),
                        BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile));
        return AgentMovementDialogueReporter.movementStatsReport(
                agentProfile,
                rawSpeedStat,
                rawJumpStat,
                movementSkillsForced,
                BotPhysicsEngine.climbStepPerTick(),
                mapProfile);
    }

    private static void reportDebugStats(BotEntry entry, Character bot) {
        queueBotReply(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    private static void reportCritDebug(BotEntry entry, Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);
        queueBotReply(entry, AgentCombatDialogueReporter.critReport(crit, dmg));
    }

    private static void reportBuffDebug(BotEntry entry, Character bot) {
        for (String line : BotBuffManager.getDebugLines(entry, bot)) {
            queueBotReply(entry, line);
        }
    }

    private static void reportSkillBuffDebug(BotEntry entry, Character bot) {
        for (String line : BotCombatManager.getSkillBuffDebugLines(entry, bot)) {
            queueBotReply(entry, line);
        }
    }

    private static void reportHelp(BotEntry entry) {
        for (String line : AgentChatReportFlow.helpLines()) {
            queueBotReply(entry, line);
        }
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

    private static void reportRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            queueBotReply(entry, AgentChatEquipmentFlow.gearCheckUnavailableReply());
            return;
        }
        if (!BotOfferManager.offerBestRecommendedGear(entry, bot, owner)) {
            queueBotReply(entry, AgentChatEquipmentFlow.noBetterGearReply());
        }
        entry.nextGearSuggestionAt = System.currentTimeMillis() + 60_000L;
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

    private static void handleRequestUpgradeCommand(BotEntry entry, Character bot) {
        BotOfferManager.clearPendingOfferForOwnerAsk(entry);
        if (BotPotionManager.requestLowSuppliesFromOwnerAsk(entry, bot)) {
            return;
        }
        BotOfferManager.requestBestUpgradeFromOwner(entry, bot);
    }

    private static void handleNeedAnyPotionCommand(BotEntry entry) {
        if (entry.owner == null) {
            return;
        }
        int[] pots = BotPotionManager.countPotions(entry.owner);
        handleNeedPotionCommand(entry, pots[0] <= pots[1]);
    }

    private static void handleNeedPotionCommand(BotEntry entry, boolean forHp) {
        BotPotionManager.OwnerPotShareResult result = BotPotionManager.offerPotShareToOwner(entry, forHp);
        String reply = AgentSupplyRequestOutcomeFlow.potionShareReply(
                result == BotPotionManager.OwnerPotShareResult.NO_DONOR,
                forHp);
        if (reply != null) {
            queueBotReply(entry, reply);
        }
    }

    private static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            queueBotReply(entry, AgentSupplyRequestOutcomeFlow.ammoNotNeededReply());
            return;
        }
        BotAmmoManager.OwnerAmmoShareResult result = BotAmmoManager.offerAmmoShareToOwner(entry, weaponType);
        String reply = AgentSupplyRequestOutcomeFlow.ammoShareReply(
                result == BotAmmoManager.OwnerAmmoShareResult.NO_DONOR);
        if (reply != null) {
            queueBotReply(entry, reply);
        }
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentSkillReportFlow.SkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        applySkillReportDecision(entry, AgentSkillReportFlow.resolveSkillTreeChoice(skillTrees, message));
    }

    private static void applySkillReportDecision(BotEntry entry, AgentSkillReportFlow.SkillReportDecision decision) {
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

    private static void handleTransferCommand(BotEntry entry, AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        String category = transferCommand.category();
        if (AgentChatTransferFlow.shouldReplyWithWeirdTransfer(transferCommand, message)) {
            BotManager.getInstance().botReply(entry, AgentChatTransferFlow.weirdTransferReply());
        }
        if (transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE && BotInventoryManager.isMesoCategory(category)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotInventoryManager.startTradeTransfer(category, entry, entry.bot));
            return;
        }

        scheduleTransferCommandEvaluation(entry, transferCommand, category);
    }

    private static void scheduleTransferCommandEvaluation(BotEntry entry,
                                                          AgentChatTransferFlow.TransferCommand transferCommand,
                                                          String category) {
        Character bot = entry.bot;
        if (bot == null) {
            return;
        }

        int requestId = nextTransferRequestId(bot);
        long replyDelay = BotManager.randMs(500, 700);
        long requestedAt = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> evaluateTransferCommand(entry, transferCommand, category, bot), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    BotManager.after(remainingDelay, () ->
                            applyTransferCommandResult(entry, transferCommand, category, bot, requestId, result));
                });
    }

    private static TransferCommandResult evaluateTransferCommand(BotEntry entry,
                                                                 AgentChatTransferFlow.TransferCommand transferCommand,
                                                                 String category,
                                                                 Character bot) {
        long hasItemsStartedAt = transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                && BotInventoryManager.profileTradeCategory(category)
                ? System.nanoTime() : 0L;
        boolean hasItems = BotInventoryManager.hasTransferableItems(category, entry, bot);
        BotInventoryManager.logSlowTradeCommand(category, "hasTransferableItems", entry, bot, hasItemsStartedAt);
        int count = hasItems && transferCommand.mode() == AgentChatTransferFlow.TransferMode.CHOICE
                ? BotInventoryManager.countTransferableItems(category, entry, bot)
                : 0;
        return new TransferCommandResult(hasItems, count);
    }

    private static void applyTransferCommandResult(BotEntry entry,
                                                   AgentChatTransferFlow.TransferCommand transferCommand,
                                                   String category,
                                                   Character bot,
                                                   int requestId,
                                                   TransferCommandResult result) {
        if (!isLatestTransferRequest(bot, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.transferResult(
                transferCommand, result.hasItems(), result.count()));
    }

    private static int nextTransferRequestId(Character bot) {
        return PENDING_TRANSFER_REQUESTS
                .computeIfAbsent(bot.getId(), ignored -> new AtomicInteger())
                .incrementAndGet();
    }

    private static boolean isLatestTransferRequest(Character bot, int requestId) {
        AtomicInteger current = PENDING_TRANSFER_REQUESTS.get(bot.getId());
        return current != null && current.get() == requestId;
    }

    private static void handleItemQuery(BotEntry entry, String itemName) {
        String category = AgentTradeDialogueClassifier.namedItemCategory(itemName);
        Character bot = entry.bot;
        if (bot == null) {
            return;
        }

        int requestId = nextTransferRequestId(bot);
        long replyDelay = BotManager.randMs(500, 700);
        long requestedAt = System.nanoTime();
        CompletableFuture
                .supplyAsync(() -> new ItemQueryResult(
                        BotInventoryManager.countTransferableItems(category, entry, bot)), TRADE_COMMAND_EXECUTOR)
                .thenAccept(result -> {
                    long elapsedMs = (System.nanoTime() - requestedAt) / 1_000_000L;
                    long remainingDelay = Math.max(0L, replyDelay - elapsedMs);
                    BotManager.after(remainingDelay, () ->
                            applyItemQueryResult(entry, category, bot, requestId, result));
                });
    }

    private static void applyItemQueryResult(BotEntry entry,
                                             String category,
                                             Character bot,
                                             int requestId,
                                             ItemQueryResult result) {
        if (!isLatestTransferRequest(bot, requestId)) {
            return;
        }
        applyTransferResultDecision(entry, bot, category, AgentChatTransferFlow.itemQueryResult(category, result.count()));
    }

    private static void applyTransferResultDecision(BotEntry entry,
                                                    Character bot,
                                                    String category,
                                                    AgentChatTransferFlow.TransferResultDecision decision) {
        switch (decision.action()) {
            case REPLY -> BotManager.getInstance().botReply(entry, decision.reply());
            case START_TRADE -> BotInventoryManager.startTradeTransfer(category, entry, bot);
            case PROMPT_ITEM_CHOICE -> {
                entry.pendingAction = AgentChatPendingAction.ITEM_CHOICE;
                entry.pendingDropCategory = decision.category();
                BotManager.getInstance().botReply(entry, decision.reply());
            }
        }
    }

    private static void handleFameCommand(BotEntry entry, String targetName) {
        Character bot = entry.bot;
        Character target;
        if (AgentSocialDialogueClassifier.isSelfFameTarget(targetName)) {
            target = entry.owner;
        } else {
            target = bot.getMap().getCharacters().stream()
                    .filter(c -> c.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
        }
        Character fameTarget = target;
        AgentFameDialogueFlow.handle(targetName, new AgentFameDialogueFlow.FameCallbacks() {
            @Override
            public boolean targetExists() {
                return fameTarget != null;
            }

            @Override
            public boolean targetIsSelf() {
                return fameTarget != null && fameTarget.getId() == bot.getId();
            }

            @Override
            public int agentLevel() {
                return bot.getLevel();
            }

            @Override
            public Character.FameStatus fameStatus() {
                return bot.canGiveFame(fameTarget);
            }

            @Override
            public boolean gainFame() {
                return fameTarget.gainFame(1, bot, 1);
            }

            @Override
            public void markFameGiven() {
                bot.hasGivenFame(fameTarget);
            }

            @Override
            public String targetDisplayName() {
                return fameTarget.getName();
            }

            @Override
            public void reply(String message) {
                BotManager.getInstance().botReply(entry, message);
            }
        });
    }
}
