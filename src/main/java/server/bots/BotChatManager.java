package server.bots;

import client.Character;
import client.Job;
import client.Stat;
import client.inventory.WeaponType;
import client.processor.stat.AssignAPProcessor;
import server.Trade;
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
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueReportFormatter;
import server.agents.capabilities.dialogue.AgentInventoryDialogueReporter;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.capabilities.dialogue.AgentChatJobAdvancementFlow;
import server.agents.capabilities.dialogue.AgentChatMovementFlow;
import server.agents.capabilities.dialogue.AgentChatSessionRequestFlow;
import server.agents.capabilities.dialogue.AgentChatToggleFlow;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.capabilities.dialogue.AgentPendingChatActionFlow;
import server.agents.capabilities.dialogue.AgentSkillDialogueReporter;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
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
        LAST_CHAT_HANDLED.set(true);
        markOwnerActive(entry);
        if (entry.pendingAction == null
                && AgentChatSessionRequestFlow.handle(message, sessionRequestCallbacks(entry))) {
            return;
        }
        if (entry.pendingAction != null) {
            AgentPendingChatActionFlow.handle(
                    pendingActionState(entry),
                    message,
                    pendingActionCallbacks(entry));
            return;
        }

        if (AgentChatSupplyRequestFlow.handle(message, supplyRequestCallbacks(entry))) {
            return;
        }
        if (AgentChatSocialFlow.handle(message, socialCallbacks(entry))) {
            return;
        }
        if (AgentChatToggleFlow.handle(message, toggleCallbacks(entry))) {
            return;
        }
        if (AgentChatBuffQueryFlow.handle(message, buffQueryCallbacks(entry))) {
            return;
        }
        if (AgentChatRespecFlow.handle(message, respecCallbacks(entry))) {
            return;
        }
        if (AgentChatEquipmentFlow.handle(message, equipmentCallbacks(entry))) {
            return;
        }

        AgentChatMovementFlow.handle(message, movementCallbacks(entry));

        AgentChatBuildFlow.handleSpVariantSelection(
                message,
                entry.spVariantPromptSent && entry.spVariant == null,
                spVariantCallbacks(entry));

        // AP build selection — "change build" always triggers a re-prompt;
        // "dexless" / "X dex" only apply when bot is actively waiting for the answer (apPromptSent=true)
        AgentChatBuildFlow.handleApBuildSelection(message, entry.apPromptSent, apBuildCallbacks(entry));

        if (AgentChatUtilityFlow.handle(message, utilityCallbacks(entry))) {
            return;
        }

        AgentChatTransferFlow.TransferCommand transferCommand = AgentChatTransferFlow.matchTransferCommand(message);
        if (transferCommand != null) {
            handleTransferCommand(entry, transferCommand, message);
            return;
        }

        if (AgentChatTransferFlow.handleItemQuery(message, itemQueryCallbacks(entry))) {
            return;
        }

        if (AgentChatReportFlow.handle(message, reportCallbacks(entry))) {
            return;
        }

        AgentChatJobAdvancementFlow.handle(
                message,
                entry.bot.getJob(),
                entry.bot.getLevel(),
                jobAdvancementCallbacks(entry));
        LAST_CHAT_HANDLED.set(false);
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
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.relogConfirmPrompts()));
                });
            }

            @Override
            public void requestLogout() {
                BotManager.after(BotManager.randMs(900, 1100), () -> {
                    entry.pendingAction = AgentChatPendingAction.LOGOUT;
                    BotManager.getInstance().issueStop(entry);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.logoutConfirmPrompts()));
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
                    BotManager.getInstance().botReply(entry,
                            enabled ? AgentDialogueCatalog.supportOnReply() : AgentDialogueCatalog.supportOffReply());
                });
            }

            @Override
            public void setHeals(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.supportHealsEnabled = enabled;
                    BotManager.getInstance().botReply(entry,
                            enabled ? AgentDialogueCatalog.healsOnReply() : AgentDialogueCatalog.healsOffReply());
                });
            }

            @Override
            public void setBuffConsumables(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffConsumablesEnabled = enabled;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, enabled
                            ? AgentDialogueCatalog.buffConsumablesOnReply(
                                    AgentDialogueCatalog.buffConsumablesModeLabel(entry.buffCheapMode))
                            : AgentDialogueCatalog.buffConsumablesOffReply());
                });
            }

            @Override
            public void setBuffConsumablesCheapMode(boolean cheapMode) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.buffCheapMode = cheapMode;
                    entry.lastBuffScanMs = 0;
                    BotManager.getInstance().botReply(entry, cheapMode
                            ? AgentDialogueCatalog.buffConsumablesCheapReply()
                            : AgentDialogueCatalog.buffConsumablesMaxReply());
                });
            }

            @Override
            public void setProactiveOffers(boolean enabled) {
                BotManager.after(BotManager.randMs(500, 700), () -> {
                    entry.proactiveUpgradeOffers = enabled;
                    BotManager.getInstance().botReply(entry, enabled
                            ? AgentDialogueCatalog.proactiveOffersOnReply()
                            : AgentDialogueCatalog.proactiveOffersOffReply());
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
                    BotManager.getInstance().botReply(entry, AgentDialogueCatalog.gearOptimizedReply());
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
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.moveHereReplies()));
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
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.moveHereReplies()));
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
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.moveHereReplies()));
                });
                return true;
            }

            @Override
            public void follow() {
                BotManager.after(BotManager.randMs(1500, 2000), () -> {
                    BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                    entry.nextGearSuggestionAt = 0;
                    maybeSuggestGearToSiblings(entry, entry.bot);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.followReplies()));
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
                            BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.stopReplies())));
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
                    queueBotReply(entry, BotManager.randomReply(AgentDialogueCatalog.greetingReplies()));
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
                        BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.tradeInviteReplies()));
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
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.oneHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            }

            @Override
            public void twoHanded() {
                entry.spVariant = AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.twoHandedSpVariantReply());
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
            String jobName = AgentDialogueReportFormatter.jobDisplayName(advJob);
            String reply = AgentDialogueReportFormatter.jobChangeReply(
                    BotManager.randomReply(AgentDialogueCatalog.jobChangeReplyTemplates()), jobName);
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
                        () -> BotManager.getInstance().botReply(entry, AgentDialogueCatalog.keepDropChoiceReply()));
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
                String cancelMsg = AgentDialogueCatalog.pendingActionCancelReply(dropAction);
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
        };
    }

    private static void scheduleRelogConfirm(BotEntry entry) {
        BotManager.after(BotManager.randMs(900, 1100), () -> {
            Character o = entry.owner;
            if (o == null) return; // owner logged out before relog fired
            BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.relogConfirmedReplies()));
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
            BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.logoutConfirmedReplies()));
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
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayTownOrLogoutPrompt());
            }

            @Override
            public void replyStayOrLogout() {
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayStayOrLogoutPrompt());
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
                    BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayLogoutConfirmReply());
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
                        BotManager.getInstance().botReply(entry, townOffered
                                ? AgentDialogueCatalog.awayTownConfirmReply()
                                : AgentDialogueCatalog.awayStayConfirmReply()));
            }

            @Override
            public void stay() {
                int ownerId = entry.owner != null ? entry.owner.getId() : 0;
                if (ownerId != 0) {
                    BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, false);
                }
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayStayConfirmReply()));
            }

            @Override
            public void cancel() {
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayCancelReply()));
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
        final String text = AgentDialogueReportFormatter.welcomeBackOfflineReply(
                BotManager.randomReply(AgentDialogueCatalog.welcomeBackOfflinePartyTemplates()),
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
                BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.welcomeBackReplies()));
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
        CombatFormulaProvider formulas = CombatFormulaProvider.getInstance();
        boolean magicAttack = BotEquipManager.isMageJob(bot.getJob());
        int attackStat;
        int accuracy;
        int minDmg;
        int maxDmg;

        if (magicAttack) {
            attackStat = bot.getTotalMagic();
            accuracy = formulas.getTotalMagicAccuracy(bot);
            maxDmg = (int) Math.max(1L, formulas.magicDamageBase(attackStat, bot.getTotalInt()));
            minDmg = (int) Math.max(1L, formulas.magicDamageBaseMin(attackStat, bot.getTotalInt(), 0.1d));
        } else {
            attackStat = bot.getTotalWatk();
            accuracy = formulas.getTotalAccuracy(bot);
            maxDmg = Math.max(1, bot.calculateMaxBaseDamage(attackStat));
            minDmg = Math.max(1, bot.calculateMinBaseDamage(attackStat, formulas.resolvePhysicalMastery(bot)));
        }

        String report = AgentDialogueReportFormatter.range(
                minDmg, maxDmg,
                AgentDialogueReportFormatter.rangeAttackLabel(magicAttack), attackStat,
                AgentDialogueReportFormatter.rangeAccuracyLabel(magicAttack), accuracy);
        if (hitProfile == null) {
            return report;
        }

        double hitChance = magicAttack
                ? formulas.calculateMagicMobHitChance(accuracy, bot.getLevel(), hitProfile.mobLevel(), hitProfile.mobAvoid())
                : formulas.calculatePhysicalMobHitChance(accuracy, bot.getLevel(), hitProfile.mobLevel(), hitProfile.mobAvoid());
        int hitPercent = (int) Math.round(hitChance * 100.0d);
        return AgentDialogueReportFormatter.rangeWithHit(report, hitPercent, hitProfile.mobAvoid());
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
        if (bot.isBeginnerJob()) {
            reportBeginnerSkills(entry, bot);
            return;
        }

        Map<Integer, List<AgentDialogueReportFormatter.AgentSkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noJobSkillsWithSpReply(bot.getRemainingSp()));
            return;
        }

        if (skillTrees.size() == 1) {
            Map.Entry<Integer, List<AgentDialogueReportFormatter.AgentSkillLine>> onlyTree =
                    skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        entry.pendingAction = AgentChatPendingAction.SKILL_TREE_CHOICE;
        queueBotReply(entry, AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
    }

    private static void reportBeginnerSkills(BotEntry entry, Character bot) {
        List<AgentDialogueReportFormatter.AgentSkillLine> beginnerSkills =
                AgentSkillDialogueReporter.collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = AgentSkillDialogueReporter.remainingBeginnerSp(bot);

        if (beginnerSkills.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noBeginnerSkillsReply(beginnerSpLeft));
            return;
        }

        queueBotReply(entry, AgentDialogueReportFormatter.beginnerSkillReport(beginnerSkills, beginnerSpLeft));
    }

    private static void reportInventory(BotEntry entry, Character bot) {
        queueBotReply(entry, BotInventoryManager.inventorySummary(bot));
    }

    private static void reportMesos(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.mesoReport(bot));
    }

    private static void reportExp(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentCharacterDialogueReporter.expReport(bot));
    }

    private static void reportInventorySlots(BotEntry entry, Character bot) {
        queueBotReply(entry, BotInventoryManager.slotsReport(bot));
    }

    private static void reportScrolls(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentInventoryDialogueReporter.scrollReport(bot));
    }

    private static void reportPotions(BotEntry entry, Character bot) {
        int[] counts = BotPotionManager.countPotions(bot);
        queueBotReply(entry, AgentDialogueReportFormatter.potionReport(counts[0], counts[1]));
    }

    private static void reportPotDebug(BotEntry entry, Character bot) {
        queueBotReply(entry, BotPotionManager.autopotDebugReport(bot));
    }

    static List<String> buildMovementStatsReport(Character bot) {
        if (bot == null) {
            return List.of(AgentDialogueCatalog.movementStatsUnavailableReply());
        }

        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        int rawSpeedStat = bot.getTotalMoveSpeedStat();
        int rawJumpStat = bot.getTotalJumpStat();
        String speedLine = movementStatLine(map, profile, rawSpeedStat, rawJumpStat);

        if (map == null) {
            return List.of(
                    speedLine,
                    AgentDialogueReportFormatter.movementWalkNoMap(
                            profile.walkVelocityPxs(), profile.hForcePxs(), BotPhysicsEngine.climbStepPerTick()),
                    AgentDialogueReportFormatter.movementJumpNoMap(
                            BotPhysicsEngine.jumpForcePerTick(profile),
                            BotPhysicsEngine.ropeJumpForcePerTick(profile),
                            BotPhysicsEngine.calculateMaxJumpHeight(profile))
            );
        }

        return List.of(
                speedLine,
                AgentDialogueReportFormatter.movementWalkWithMap(
                        profile.walkVelocityPxs(),
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        profile.hForcePxs()),
                AgentDialogueReportFormatter.movementJumpWithMap(
                        BotPhysicsEngine.jumpForcePerTick(profile),
                        BotPhysicsEngine.ropeJumpForcePerTick(profile),
                        BotPhysicsEngine.calculateMaxJumpHeight(profile),
                        BotPhysicsEngine.maxJumpHorizontalTravel(map, profile),
                        BotPhysicsEngine.maxRopeJumpHorizontalTravel(map, profile))
        );
    }

    private static String movementStatLine(MapleMap map,
                                           BotMovementProfile profile,
                                           int rawSpeedStat,
                                           int rawJumpStat) {
        if (map != null && FieldLimit.MOVEMENTSKILLS.check(map.getFieldLimit())
                && (rawSpeedStat != profile.totalSpeedStat() || rawJumpStat != profile.totalJumpStat())) {
            return AgentDialogueReportFormatter.movementStatLineForced(
                    profile.totalSpeedStat(), profile.totalJumpStat(), rawSpeedStat, rawJumpStat);
        }
        return AgentDialogueReportFormatter.movementStatLine(profile.totalSpeedStat(), profile.totalJumpStat());
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
        for (String line : AgentDialogueCatalog.helpLines()) {
            queueBotReply(entry, line);
        }
    }

    private static void handleApBuildSelection(BotEntry entry, String message) {
        Job job = entry.bot.getJob();

        if (job.isA(Job.WARRIOR) && AgentBuildDialogueClassifier.isPureStrBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), entry.bot.getDex());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.STR, BotBuildManager.StatType.DEX, 4),
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.WARRIOR_DEXLESS_AP_BUILD, effectiveDex),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.WARRIOR_DEXLESS_AP_BUILD));
            return;
        }
        if (job.isA(Job.THIEF) && AgentBuildDialogueClassifier.isDexlessBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), entry.bot.getDex());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.LUK, BotBuildManager.StatType.DEX, 4),
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.THIEF_DEXLESS_AP_BUILD, effectiveDex),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.THIEF_DEXLESS_AP_BUILD));
            return;
        }
        if (job.isA(Job.MAGICIAN) && AgentBuildDialogueClassifier.isLuklessBuildCommand(message)) {
            int effectiveLuk = Math.max(minStatFloor(job, Stat.LUK), entry.bot.getLuk());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.INT, BotBuildManager.StatType.LUK, 4),
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.MAGICIAN_LUKLESS_AP_BUILD, effectiveLuk),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.MAGICIAN_LUKLESS_AP_BUILD));
            return;
        }
        if (job.isA(Job.BOWMAN) && AgentBuildDialogueClassifier.isStrlessBuildCommand(message)) {
            int effectiveStr = Math.max(minStatFloor(job, Stat.STR), entry.bot.getStr());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.DEX, BotBuildManager.StatType.STR, 4),
                    AgentDialogueReportFormatter.apPureBuildConfirm(
                            AgentDialogueReportFormatter.BOWMAN_STRLESS_AP_BUILD, effectiveStr),
                    AgentDialogueReportFormatter.apPureBuildAlready(
                            AgentDialogueReportFormatter.BOWMAN_STRLESS_AP_BUILD));
            return;
        }

        if (job.isA(Job.WARRIOR) || job.isA(Job.THIEF)) {
            Integer dexTarget = AgentBuildDialogueClassifier.matchFixedDexTarget(message);
            if (dexTarget != null) {
                int legalDexTarget = Math.max(minStatFloor(job, Stat.DEX), dexTarget);
                int effectiveDex = Math.max(legalDexTarget, entry.bot.getDex());
                BotBuildManager.StatType primary = job.isA(Job.WARRIOR)
                        ? BotBuildManager.StatType.STR
                        : BotBuildManager.StatType.LUK;
                AgentDialogueReportFormatter.AgentApBuildDialogueProfile dialogueProfile = job.isA(Job.WARRIOR)
                        ? AgentDialogueReportFormatter.WARRIOR_FIXED_DEX_AP_BUILD
                        : AgentDialogueReportFormatter.THIEF_FIXED_DEX_AP_BUILD;
                applyApBuildChoice(entry,
                        new BotBuildManager.ApBuild(primary, BotBuildManager.StatType.DEX, dexTarget),
                        AgentDialogueReportFormatter.apFixedBuildConfirm(dialogueProfile, effectiveDex),
                        AgentDialogueReportFormatter.apFixedBuildAlready(dialogueProfile, legalDexTarget));
                return;
            }
        }
        if (job.isA(Job.MAGICIAN)) {
            Integer lukTarget = AgentBuildDialogueClassifier.matchFixedLukTarget(message);
            if (lukTarget != null) {
                int legalLukTarget = Math.max(minStatFloor(job, Stat.LUK), lukTarget);
                int effectiveLuk = Math.max(legalLukTarget, entry.bot.getLuk());
                applyApBuildChoice(entry,
                        new BotBuildManager.ApBuild(BotBuildManager.StatType.INT, BotBuildManager.StatType.LUK, lukTarget),
                        AgentDialogueReportFormatter.apFixedBuildConfirm(
                                AgentDialogueReportFormatter.MAGICIAN_FIXED_LUK_AP_BUILD, effectiveLuk),
                        AgentDialogueReportFormatter.apFixedBuildAlready(
                                AgentDialogueReportFormatter.MAGICIAN_FIXED_LUK_AP_BUILD, legalLukTarget));
                return;
            }
        }
        if (job.isA(Job.BOWMAN)) {
            Integer strTarget = AgentBuildDialogueClassifier.matchFixedStrTarget(message);
            if (strTarget != null) {
                int legalStrTarget = Math.max(minStatFloor(job, Stat.STR), strTarget);
                int effectiveStr = Math.max(legalStrTarget, entry.bot.getStr());
                applyApBuildChoice(entry,
                        new BotBuildManager.ApBuild(BotBuildManager.StatType.DEX, BotBuildManager.StatType.STR, strTarget),
                        AgentDialogueReportFormatter.apFixedBuildConfirm(
                                AgentDialogueReportFormatter.BOWMAN_FIXED_STR_AP_BUILD, effectiveStr),
                        AgentDialogueReportFormatter.apFixedBuildAlready(
                                AgentDialogueReportFormatter.BOWMAN_FIXED_STR_AP_BUILD, legalStrTarget));
            }
        }
    }

    private static int minStatFloor(Job job, Stat stat) {
        return AssignAPProcessor.getMinStatFloor(job, stat);
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
            queueBotReply(entry, AgentDialogueCatalog.gearCheckUnavailableReply());
            return;
        }
        if (!BotOfferManager.offerBestRecommendedGear(entry, bot, owner)) {
            queueBotReply(entry, AgentDialogueCatalog.noBetterGearReply());
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
        if (result == BotPotionManager.OwnerPotShareResult.NO_DONOR) {
            queueBotReply(entry, AgentDialogueReportFormatter.ownerPotShortageReply(
                    BotManager.randomReply(AgentDialogueCatalog.ownerPotShortageReplies()),
                    AgentDialogueReportFormatter.potionTypeLabel(forHp)));
        }
    }

    private static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            queueBotReply(entry, BotManager.randomReply(AgentDialogueCatalog.ammoNotNeededReplies()));
            return;
        }
        BotAmmoManager.OwnerAmmoShareResult result = BotAmmoManager.offerAmmoShareToOwner(entry, weaponType);
        if (result == BotAmmoManager.OwnerAmmoShareResult.NO_DONOR) {
            queueBotReply(entry, BotManager.randomReply(AgentDialogueCatalog.ownerAmmoShortageReplies()));
        }
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<AgentDialogueReportFormatter.AgentSkillLine>> skillTrees =
                AgentSkillDialogueReporter.collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            entry.pendingAction = null;
            queueBotReply(entry, AgentDialogueCatalog.noJobSkillsReply());
            return;
        }

        if (skillTrees.size() == 1) {
            entry.pendingAction = null;
            Map.Entry<Integer, List<AgentDialogueReportFormatter.AgentSkillLine>> onlyTree =
                    skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        Integer treeId = AgentBuildDialogueClassifier.resolveSkillTreeChoice(message, skillTrees.keySet());
        if (treeId == null) {
            queueBotReply(entry, AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
            return;
        }

        entry.pendingAction = null;
        queueSkillTreeReport(entry, treeId, skillTrees.get(treeId));
    }

    private static void queueSkillTreeReport(BotEntry entry, int treeId,
                                             List<AgentDialogueReportFormatter.AgentSkillLine> skills) {
        if (skills == null || skills.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noLearnedSkillsInReply(AgentDialogueReportFormatter.skillTreeLabel(treeId)));
            return;
        }

        for (String line : AgentDialogueReportFormatter.skillTreeReportLines(treeId, skills)) {
            queueBotReply(entry, line);
        }
    }

    private static void handleTransferCommand(BotEntry entry, AgentChatTransferFlow.TransferCommand transferCommand, String message) {
        String category = transferCommand.category();
        if (transferCommand.mode() == AgentChatTransferFlow.TransferMode.TRADE
                && AgentTradeDialogueClassifier.isTrashCategory(category)
                && message != null
                && AgentTradeDialogueClassifier.isShowJunkCommand(message)) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.weirdTransferReply());
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
        if (!result.hasItems()) {
            BotManager.getInstance().botReply(entry, BotInventoryManager.noItemsReply(category));
            return;
        }

        switch (transferCommand.mode()) {
            case TRADE -> BotInventoryManager.startTradeTransfer(category, entry, bot);
            case CHOICE -> {
                entry.pendingAction = AgentChatPendingAction.ITEM_CHOICE;
                entry.pendingDropCategory = category;
                BotManager.getInstance().botReply(entry, AgentDialogueReportFormatter.dropOrTradePrompt(
                        category, result.count(), AgentDialogueCatalog.dropOrTradePrompts()));
            }
        }
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
        if (result.count() <= 0) {
            BotManager.getInstance().botReply(entry, BotInventoryManager.noItemsReply(category));
            return;
        }

        entry.pendingAction = AgentChatPendingAction.ITEM_CHOICE;
        entry.pendingDropCategory = category;
        BotManager.getInstance().botReply(entry, AgentDialogueReportFormatter.dropOrTradePrompt(
                category, result.count(), AgentDialogueCatalog.dropOrTradePrompts()));
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
        if (target == null) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameTargetNotFoundReply(targetName));
            return;
        }
        if (target.getId() == bot.getId()) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameSelfReply());
            return;
        }
        if (bot.getLevel() < 15) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameTooLowLevelReply());
            return;
        }
        Character.FameStatus status = bot.canGiveFame(target);
        if (status == Character.FameStatus.NOT_TODAY) {
            BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.fameCooldownReplies()));
            return;
        }
        if (status == Character.FameStatus.NOT_THIS_MONTH) {
            String reply = AgentDialogueReportFormatter.fameSamePersonReply(
                    BotManager.randomReply(AgentDialogueCatalog.fameSamePersonReplies()), target.getName());
            BotManager.getInstance().botReply(entry, reply);
            return;
        }
        if (target.gainFame(1, bot, 1)) {
            bot.hasGivenFame(target);
            String template = BotManager.randomReply(AgentDialogueCatalog.fameOkReplies());
            String reply = AgentDialogueReportFormatter.fameOkReply(template, target.getName());
            BotManager.getInstance().botReply(entry, reply);
        } else {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameFailedReply());
        }
    }
}
