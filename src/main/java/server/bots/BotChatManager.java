package server.bots;

import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.Stat;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.processor.stat.AssignAPProcessor;
import constants.game.ExpTable;
import constants.game.GameConstants;
import constants.inventory.ItemConstants;
import server.Trade;
import server.agents.capabilities.dialogue.AgentBuildDialogueClassifier;
import server.agents.capabilities.dialogue.AgentChatCommandClassifier;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueReportFormatter;
import server.agents.capabilities.dialogue.AgentEquipmentDialogueClassifier;
import server.agents.capabilities.dialogue.AgentSocialDialogueClassifier;
import server.agents.capabilities.dialogue.AgentTradeDialogueClassifier;
import server.agents.capabilities.dialogue.AgentUtilityDialogueClassifier;
import server.agents.commands.AgentQueuedMessage;
import server.agents.commands.AgentReplyQueue;
import server.combat.CombatFormulaProvider;
import server.maps.FieldLimit;
import server.maps.MapleMap;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class BotChatManager {
    private static final String SKILL_TREE_CHOICE_ACTION = "skill_tree_choice";
    private static final ExecutorService TRADE_COMMAND_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "bot-trade-command");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, AtomicInteger> PENDING_TRANSFER_REQUESTS = new ConcurrentHashMap<>();

    private record LearnedSkill(int id, String name, int level) {}
    private record TransferCommandResult(boolean hasItems, int count) {}
    private record ItemQueryResult(int count) {}
    private static final List<String> FOLLOW_REPLIES = AgentDialogueCatalog.followReplies();
    private static final List<String> MOVE_HERE_REPLIES = AgentDialogueCatalog.moveHereReplies();
    private static final List<String> STOP_REPLIES = AgentDialogueCatalog.stopReplies();

    private static final List<String> AMMO_NOT_NEEDED_REPLIES = AgentDialogueCatalog.ammoNotNeededReplies();

    private static final List<String> FAME_OK_REPLIES = AgentDialogueCatalog.fameOkReplies();
    private static final List<String> FAME_COOLDOWN_REPLIES = AgentDialogueCatalog.fameCooldownReplies();
    private static final List<String> FAME_SAME_PERSON_REPLIES = AgentDialogueCatalog.fameSamePersonReplies();
    private static final List<String> OWNER_POT_SHORTAGE_REPLIES = AgentDialogueCatalog.ownerPotShortageReplies();
    private static final List<String> OWNER_AMMO_SHORTAGE_REPLIES = AgentDialogueCatalog.ownerAmmoShortageReplies();
    private static final List<String> TRADE_INVITE_REPLIES = AgentDialogueCatalog.tradeInviteReplies();
    private static final List<String> GREETING_REPLIES = AgentDialogueCatalog.greetingReplies();
    private static final List<String> WB_REPLIES = AgentDialogueCatalog.welcomeBackReplies();
    // %s = current map name (bot is in town since the offline-return warp put it there).
    // Sent via party chat so the owner sees it across maps when they reconnect.
    private static final List<String> WB_OFFLINE_PARTY_TEMPLATES = AgentDialogueCatalog.welcomeBackOfflinePartyTemplates();
    private static final List<String> MESO_REPLIES = AgentDialogueCatalog.mesoReplies();

    private enum TransferMode {
        TRADE,
        CHOICE
    }

    private static final class TransferCommand {
        private final TransferMode mode;
        private final String category;

        private TransferCommand(TransferMode mode, String category) {
            this.mode = mode;
            this.category = category;
        }
    }

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
        // Logout / relog — two-step confirmation
        if (entry.pendingAction == null && AgentChatCommandClassifier.isRelogRequest(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> {
                entry.pendingAction = "relog";
                BotManager.getInstance().issueStop(entry);
                BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.relogConfirmPrompts()));
            });
            return;
        }
        if (entry.pendingAction == null && AgentChatCommandClassifier.isLogoutRequest(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> {
                entry.pendingAction = "logout";
                BotManager.getInstance().issueStop(entry);
                BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.logoutConfirmPrompts()));
            });
            return;
        }
        if (entry.pendingAction == null && AgentChatCommandClassifier.isAwayRequest(message)) {
            if (!BotManager.getInstance().isFirstBotEntry(entry)) {
                return;
            }
            BotManager.after(BotManager.randMs(900, 1100), () -> promptOwnerAway(entry));
            return;
        }
        if (entry.pendingAction != null) {
            if ("owner_away".equals(entry.pendingAction)) {
                handleOwnerAwayChoice(entry, message);
                return;
            }
            // Item-choice: three-way "drop / trade / cancel" — handled independently of yes/no
            if ("item_choice".equals(entry.pendingAction)) {
                String category = entry.pendingDropCategory;
                String choice = normalizeCommandText(message);
                if (AgentTradeDialogueClassifier.isDropChoiceTradeCommand(choice)) {
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    BotManager.after(BotManager.randMs(400, 600),
                            () -> BotInventoryManager.executeChoice(category, true, entry, entry.bot));
                } else if (AgentTradeDialogueClassifier.isDropChoiceDropCommand(choice)) {
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    BotManager.after(BotManager.randMs(400, 600),
                            () -> BotInventoryManager.executeChoice(category, false, entry, entry.bot));
                } else {
                    // any other response = cancel
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    BotManager.after(BotManager.randMs(400, 600),
                            () -> BotManager.getInstance().botReply(entry, AgentDialogueCatalog.keepDropChoiceReply()));
                }
                return;
            }
            if (SKILL_TREE_CHOICE_ACTION.equals(entry.pendingAction)) {
                handleSkillTreeChoice(entry, entry.bot, message);
                return;
            }
            if (AgentChatCommandClassifier.isLogoutConfirm(message)) {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                if ("relog".equals(action)) {
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
                } else {
                    BotManager.after(BotManager.randMs(900, 1100), () -> {
                        BotManager.getInstance().botReply(entry, BotManager.randomReply(AgentDialogueCatalog.logoutConfirmedReplies()));
                        BotManager.after(BotManager.randMs(1800, 2200), () -> {
                            entry.bot.saveCharToDB(true);
                            entry.bot.getClient().disconnect(false, false);
                        });
                    });
                }
            } else {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                String cancelMsg = action != null && action.startsWith("drop") ? "ok! keeping them" : "ok nvm, staying!";
                BotManager.after(BotManager.randMs(700, 900), () ->
                        BotManager.getInstance().botReply(entry, cancelMsg));
            }
            return;
        }

        if (AgentChatCommandClassifier.isHelpCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> reportHelp(entry));
            return;
        }
        if (AgentChatCommandClassifier.isNeedHpPotCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> handleNeedPotionCommand(entry, true));
            return;
        }
        if (AgentChatCommandClassifier.isNeedMpPotCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> handleNeedPotionCommand(entry, false));
            return;
        }
        if (AgentChatCommandClassifier.isNeedPotCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAnyPotionCommand(entry));
            return;
        }
        if (AgentChatCommandClassifier.isNeedAmmoCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> handleNeedAmmoCommand(entry));
            return;
        }
        String fameTarget = AgentSocialDialogueClassifier.matchFameTarget(message);
        if (fameTarget != null) {
            BotManager.after(BotManager.randMs(500, 900), () -> handleFameCommand(entry, fameTarget));
            return;
        }
        if (AgentChatCommandClassifier.isSupportOffCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.skillBuffsEnabled = false;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.supportOffReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isSupportOnCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.skillBuffsEnabled = true;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.supportOnReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isHealsOffCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.supportHealsEnabled = false;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.healsOffReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isHealsOnCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.supportHealsEnabled = true;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.healsOnReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesOffCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.buffConsumablesEnabled = false;
                entry.lastBuffScanMs = 0;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.buffConsumablesOffReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesOnCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.buffConsumablesEnabled = true;
                entry.lastBuffScanMs = 0;
                String mode = entry.buffCheapMode ? "cheap" : "max";
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.buffConsumablesOnReply(mode));
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesCheapCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.buffCheapMode = true;
                entry.lastBuffScanMs = 0;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.buffConsumablesCheapReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffConsumablesMaxCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.buffCheapMode = false;
                entry.lastBuffScanMs = 0;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.buffConsumablesMaxReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isProactiveOffersOffCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.proactiveUpgradeOffers = false;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.proactiveOffersOffReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isProactiveOffersOnCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                entry.proactiveUpgradeOffers = true;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.proactiveOffersOnReply());
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffListQuery(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                String summary = BotBuffManager.getChatSummary(entry.buffConsumablesEnabled, entry.buffCheapMode, entry.bot);
                BotManager.getInstance().botReply(entry, summary);
            });
            return;
        }
        if (AgentChatCommandClassifier.isBuffDebugQuery(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> reportBuffDebug(entry, entry.bot));
            return;
        }
        if (AgentChatCommandClassifier.isSkillBuffDebugQuery(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> reportSkillBuffDebug(entry, entry.bot));
            return;
        }
        if (isApRespecCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotManager.getInstance().botReply(entry, BotBuildManager.respecAp(entry, entry.bot)));
            return;
        }
        if (isRespecCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotManager.getInstance().botReply(entry, BotBuildManager.respecSp(entry, entry.bot)));
            return;
        }
        String slotName = AgentEquipmentDialogueClassifier.matchUnequipSlotName(message);
        if (slotName != null) {
            short[] slots = BotEquipManager.slotsFromName(slotName);
            if (slots.length > 0) {
                BotManager.after(BotManager.randMs(500, 700), () ->
                        BotManager.getInstance().botReply(entry, BotEquipManager.unequipSlot(entry.bot, slots)));
                return;
            }
        }
        if (AgentEquipmentDialogueClassifier.isUnequipAllCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> {
                BotManager.getInstance().issueStop(entry);
                BotManager.getInstance().botReply(entry, BotEquipManager.unequipAll(entry.bot));
            });
            return;
        }
        // Debug match must run BEFORE the plain autoequip match (else "autoequip debug" is
        // swallowed by the plain pattern).
        if (AgentEquipmentDialogueClassifier.isAutoEquipDebugCommand(message)) {
            BotManager.after(BotManager.randMs(400, 600), () -> {
                List<String> lines = BotEquipManager.autoEquipDebug(entry.bot);
                for (String line : lines) {
                    BotManager.getInstance().botReply(entry, line);
                }
            });
            return;
        }
        if (AgentEquipmentDialogueClassifier.isAutoEquipCommand(message)) {
            BotManager.after(BotManager.randMs(400, 600), () -> {
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem, true);
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.gearOptimizedReply());
            });
            return;
        }

        if (isFarmHereCommand(message)) {
            Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (dest != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issueFarmHere(entry, dest);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (isPatrolCommand(message)) {
            Point ownerPos = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (ownerPos != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issuePatrol(entry, ownerPos);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (isMoveHereCommand(message)) {
            Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (dest != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotManager.getInstance().issueMoveTo(entry, dest, true);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (isFollowCommand(message)) {
            BotManager.after(BotManager.randMs(1500, 2000), () -> {
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                entry.nextGearSuggestionAt = 0;
                maybeSuggestGearToSiblings(entry, entry.bot);
                BotManager.getInstance().botReply(entry, BotManager.randomReply(FOLLOW_REPLIES));
                BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
                BotManager.after(BotManager.randMs(250, 750), () -> BotManager.getInstance().issueFollowOwner(entry));
            });
        } else if (isGrindCommand(message)) {
            BotManager.after(BotManager.randMs(1500, 2000), () -> {
                prepareActiveModeEntry(entry);
                BotManager.getInstance().botReply(entry, BotPotionManager.grindStartMessage(entry.bot));
                BotManager.after(BotManager.randMs(250, 750), () -> {
                    BotManager.getInstance().issueGrind(entry);
                    checkBotStatus(entry, entry.bot);
                });
            });
        } else if (isStopCommand(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> {
                BotManager.getInstance().issueStop(entry);
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                entry.nextGearSuggestionAt = 0;
                maybeSuggestGearToSiblings(entry, entry.bot);
                BotManager.after(BotManager.randMs(1400, 1600), () ->
                        BotManager.getInstance().botReply(entry, BotManager.randomReply(STOP_REPLIES)));
            });
        } else if (isFidgetCommand(message)) {
            BotManager.after(BotManager.randMs(250, 500), () -> {
                entry.bot.changeFaceExpression(randomFidgetExpression());
                BotFidgetManager.maybeStartSocialFidget(entry);
            });
        } else if (AgentSocialDialogueClassifier.isGreeting(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> {
                entry.bot.changeFaceExpression(Emote.HAPPY.getValue());
                BotFidgetManager.maybeStartGreetingFidget(entry, ThreadLocalRandom.current().nextInt(100));
                queueBotReply(entry, BotManager.randomReply(GREETING_REPLIES));
                checkBotStatus(entry, entry.bot);
            });
        }

        // SP build variant selection — only matched when waiting for an answer (Hero 1h vs 2h)
        if (entry.spVariantPromptSent && entry.spVariant == null) {
            if (AgentBuildDialogueClassifier.isOneHandedSpVariant(message)) {
                entry.spVariant = "1h";
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.oneHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            } else if (AgentBuildDialogueClassifier.isTwoHandedSpVariant(message)) {
                entry.spVariant = "2h";
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.twoHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            }
        }

        // AP build selection — "change build" always triggers a re-prompt;
        // "dexless" / "X dex" only apply when bot is actively waiting for the answer (apPromptSent=true)
        if (AgentBuildDialogueClassifier.isApChangeBuildCommand(message)) {
            entry.apBuild      = null;
            entry.apPromptSent = false;
            String prompt = BotBuildManager.requestApBuildPrompt(entry, entry.bot);
            if (prompt != null) BotManager.getInstance().botReply(entry, prompt);
        } else if (entry.apPromptSent) {
            handleApBuildSelection(entry, message);
        }

        if (AgentUtilityDialogueClassifier.isTradeInviteCommand(message)) {
            Character bot = entry.bot;
            Character owner = entry.owner;
            if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                    && entry.pendingTradeCategory == null) {
                BotManager.after(BotManager.randMs(600, 1000), () -> {
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(TRADE_INVITE_REPLIES));
                    BotManager.after(BotManager.randMs(800, 1200), () -> {
                        Trade.startTrade(bot);
                        Trade.inviteTrade(bot, owner);
                    });
                });
            }
            return;
        }

        if (AgentUtilityDialogueClassifier.isSellTrashCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotShopManager.requestSellTrashVisit(entry, entry.bot));
            return;
        }

        if (AgentUtilityDialogueClassifier.isMakeCrystalsCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotMakerManager.handleMakeCrystals(entry));
            return;
        }

        if (AgentUtilityDialogueClassifier.isDisassembleTrashCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotMakerManager.handleDisassembleTrash(entry));
            return;
        }

        TransferCommand transferCommand = matchTransferCommand(message);
        if (transferCommand != null) {
            handleTransferCommand(entry, transferCommand, message);
            return;
        }

        String queriedItem = matchItemQuery(message);
        if (queriedItem != null) {
            handleItemQuery(entry, queriedItem);
            return;
        }

        // Info commands
        if (isRequestUpgradeCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> handleRequestUpgradeCommand(entry, entry.bot));
            return;
        }
        if (AgentChatCommandClassifier.isRecommendedGearQuery(message)) {
            BotManager.after(BotManager.randMs(500, 700), () -> reportRecommendedGear(entry, entry.bot));
            return;
        }
        if (AgentChatCommandClassifier.isSkillsQuery(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> reportSkills(entry, entry.bot));
            return;
        }
        if (AgentChatCommandClassifier.isStatsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportStats(entry, entry.bot));
        if (isMovementStatsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportMovementStats(entry, entry.bot));
        if (AgentChatCommandClassifier.isRangeQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportRange(entry, entry.bot));
        if (AgentChatCommandClassifier.isBuildQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportBuild(entry, entry.bot));
        if (AgentChatCommandClassifier.isInventoryQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportInventory(entry, entry.bot));
        if (isMesoQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportMesos(entry, entry.bot));
        if (AgentChatCommandClassifier.isExpQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportExp(entry, entry.bot));
        if (AgentChatCommandClassifier.isInventorySlotsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportInventorySlots(entry, entry.bot));
        if (AgentChatCommandClassifier.isScrollsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportScrolls(entry, entry.bot));
        if (AgentChatCommandClassifier.isPotionsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportPotions(entry, entry.bot));
        if (AgentChatCommandClassifier.isDebugStatsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportDebugStats(entry, entry.bot));
        if (AgentChatCommandClassifier.isCritDebugQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportCritDebug(entry, entry.bot));
        if (AgentChatCommandClassifier.isPotDebugQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportPotDebug(entry, entry.bot));

        // Job advancement — check if message contains a valid job selection
        if (AgentBuildDialogueClassifier.isJobSelectionCandidate(message)) {
            Job advJob = resolveJobChange(entry.bot, message.toLowerCase());
            if (advJob != null) {
                String jobName = jobDisplayName(advJob);
                String reply = String.format(BotManager.randomReply(AgentDialogueCatalog.jobChangeReplyTemplates()), jobName);
                BotManager.getInstance().botReply(entry, reply);
                BotManager.after(BotManager.randMs(900, 1100), () -> BotStarterKitManager.advanceJob(entry, advJob));
            }
        }
        LAST_CHAT_HANDLED.set(false);
    }

    private static void promptOwnerAway(BotEntry entry) {
        entry.pendingAction = "owner_away";
        BotManager.getInstance().issueStop(entry);
        if (BotManager.getInstance().shouldOfferTownForAwayCommand(entry)) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayTownOrLogoutPrompt());
        } else {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayStayOrLogoutPrompt());
        }
    }

    private static void handleOwnerAwayChoice(BotEntry entry, String message) {
        String choice = normalizeCommandText(message);
        boolean townOffered = BotManager.getInstance().shouldOfferTownForAwayCommand(entry);
        entry.pendingAction = null;

        if (AgentChatCommandClassifier.isAwayLogoutConfirm(choice)) {
            BotManager.after(BotManager.randMs(700, 900), () -> {
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayLogoutConfirmReply());
                logoutOwnerBots(entry);
            });
            return;
        }

        if (AgentChatCommandClassifier.isAwayTownConfirm(choice)) {
            int ownerId = entry.owner != null ? entry.owner.getId() : 0;
            if (ownerId != 0) {
                BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, townOffered);
            }
            BotManager.after(BotManager.randMs(700, 900), () ->
                    BotManager.getInstance().botReply(entry, townOffered
                            ? AgentDialogueCatalog.awayTownConfirmReply()
                            : AgentDialogueCatalog.awayStayConfirmReply()));
            return;
        }

        if (AgentChatCommandClassifier.isAwayStayConfirm(choice) && !townOffered) {
            int ownerId = entry.owner != null ? entry.owner.getId() : 0;
            if (ownerId != 0) {
                BotManager.getInstance().issueOwnerAwaySafeModeForOwner(ownerId, false);
            }
            BotManager.after(BotManager.randMs(700, 900), () ->
                    BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayStayConfirmReply()));
            return;
        }

        BotManager.after(BotManager.randMs(700, 900), () ->
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.awayCancelReply()));
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
        String mapName = bot.getMap() != null ? bot.getMap().getMapName() : null;
        if (mapName == null || mapName.isBlank()) {
            mapName = "town";
        }
        final String text = String.format(BotManager.randomReply(WB_OFFLINE_PARTY_TEMPLATES), mapName);
        BotManager.after(BotManager.randMs(1500, 2500), () -> {
            bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? 2 : 3);
            BotManager.getInstance().botSayParty(bot, text);
        });
    }

    /** Detects owner AFK (same position ≥5 min) and says "wb" when they return. */
    static void tickAfkCheck(BotEntry entry, Character owner) {
        Point pos = owner.getPosition();
        long now  = System.currentTimeMillis();

        if (entry.ownerAfkPos == null) {
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
            return;
        }

        if (!pos.equals(entry.ownerAfkPos)) {
            if (entry.ownerWasAfk) {
                entry.ownerWasAfk = false;
                final Character bot = entry.bot;
                BotManager.after(BotManager.randMs(1800, 2200), () -> {
                    bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? 2 : 3);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(WB_REPLIES));
                });
            }
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
        } else if (!entry.ownerWasAfk && (now - entry.ownerAfkSinceMs) >= 5 * 60_000L) {
            entry.ownerWasAfk = true;
        }
    }

    private static void reportStats(BotEntry entry, Character bot) {
        queueBotReply(entry, AgentDialogueReportFormatter.stats(
                bot.getLevel(), jobDisplayName(bot.getJob()),
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getHp(), bot.getCurrentMaxHp(),
                bot.getMp(), bot.getCurrentMaxMp()));
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
        String attackLabel;
        String accuracyLabel;

        if (magicAttack) {
            attackStat = bot.getTotalMagic();
            accuracy = formulas.getTotalMagicAccuracy(bot);
            maxDmg = (int) Math.max(1L, formulas.magicDamageBase(attackStat, bot.getTotalInt()));
            minDmg = (int) Math.max(1L, formulas.magicDamageBaseMin(attackStat, bot.getTotalInt(), 0.1d));
            attackLabel = "matk";
            accuracyLabel = "magic acc";
        } else {
            attackStat = bot.getTotalWatk();
            accuracy = formulas.getTotalAccuracy(bot);
            maxDmg = Math.max(1, bot.calculateMaxBaseDamage(attackStat));
            minDmg = Math.max(1, bot.calculateMinBaseDamage(attackStat, formulas.resolvePhysicalMastery(bot)));
            attackLabel = "watk";
            accuracyLabel = "acc";
        }

        String report = AgentDialogueReportFormatter.range(
                minDmg, maxDmg, attackLabel, attackStat, accuracyLabel, accuracy);
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
        queueBotReply(entry, AgentDialogueReportFormatter.build(
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getRemainingAp()));
    }

    private static void reportSkills(BotEntry entry, Character bot) {
        if (bot.isBeginnerJob()) {
            reportBeginnerSkills(entry, bot);
            return;
        }

        Map<Integer, List<LearnedSkill>> skillTrees = collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noJobSkillsWithSpReply(bot.getRemainingSp()));
            return;
        }

        if (skillTrees.size() == 1) {
            Map.Entry<Integer, List<LearnedSkill>> onlyTree = skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        entry.pendingAction = SKILL_TREE_CHOICE_ACTION;
        queueBotReply(entry, skillTreeChoicePrompt(skillTrees));
    }

    private static void reportBeginnerSkills(BotEntry entry, Character bot) {
        List<LearnedSkill> beginnerSkills = collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = getRemainingBeginnerSp(bot);

        if (beginnerSkills.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noBeginnerSkillsReply(beginnerSpLeft));
            return;
        }

        StringBuilder line = new StringBuilder("beginner: ");
        for (int i = 0; i < beginnerSkills.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }

            LearnedSkill skill = beginnerSkills.get(i);
            line.append(skill.name()).append(" lv").append(skill.level());
        }
        line.append(" | ").append(beginnerSpLeft).append(" beginner SP left");
        queueBotReply(entry, line.toString());
    }

    private static void reportInventory(BotEntry entry, Character bot) {
        queueBotReply(entry, BotInventoryManager.inventorySummary(bot));
    }

    private static void reportMesos(BotEntry entry, Character bot) {
        queueBotReply(entry, buildMesoReport(bot.getMeso()));
    }

    private static void reportExp(BotEntry entry, Character bot) {
        queueBotReply(entry, buildExpReport(bot.getExp(), bot.getLevel()));
    }

    static String buildExpReport(int currentExp, int level) {
        return AgentDialogueReportFormatter.expPercent(currentExp, ExpTable.getExpNeededForLevel(level));
    }

    private static void reportInventorySlots(BotEntry entry, Character bot) {
        queueBotReply(entry, BotInventoryManager.slotsReport(bot));
    }

    private static void reportScrolls(BotEntry entry, Character bot) {
        int count = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (ItemConstants.isEquipScroll(id)) count += item.getQuantity();
        }
        queueBotReply(entry, AgentDialogueReportFormatter.scrollCount(count));
    }

    private static void reportPotions(BotEntry entry, Character bot) {
        int[] counts = BotPotionManager.countPotions(bot);
        queueBotReply(entry, buildPotionReport(counts[0], counts[1]));
    }

    private static void reportPotDebug(BotEntry entry, Character bot) {
        queueBotReply(entry, BotPotionManager.autopotDebugReport(bot));
    }

    static String buildPotionReport(int hp, int mp) {
        return AgentDialogueReportFormatter.potionCount(hp, mp);
    }

    static boolean isMesoQuery(String message) {
        return AgentChatCommandClassifier.isMesoQuery(message);
    }

    static String buildMesoReport(int mesos) {
        return AgentDialogueReportFormatter.mesoReport(mesos, MESO_REPLIES);
    }

    static boolean isMovementStatsQuery(String message) {
        return AgentChatCommandClassifier.isMovementStatsQuery(message);
    }

    static List<String> buildMovementStatsReport(Character bot) {
        if (bot == null) {
            return List.of("cant read my movement stats rn");
        }

        BotMovementProfile profile = BotMovementProfile.fromCharacter(bot);
        MapleMap map = bot.getMap();
        int rawSpeedStat = bot.getTotalMoveSpeedStat();
        int rawJumpStat = bot.getTotalJumpStat();
        String speedLine = movementStatLine(map, profile, rawSpeedStat, rawJumpStat);

        if (map == null) {
            return List.of(
                    speedLine,
                    String.format(Locale.ROOT, "walk %.1f px/s, hforce %.1f, climb %d px/tick",
                            profile.walkVelocityPxs(), profile.hForcePxs(), BotPhysicsEngine.climbStepPerTick()),
                    String.format(Locale.ROOT, "jump %.1f/tick, rope %.1f/tick, max jump %.1f px",
                            BotPhysicsEngine.jumpForcePerTick(profile),
                            BotPhysicsEngine.ropeJumpForcePerTick(profile),
                            BotPhysicsEngine.calculateMaxJumpHeight(profile))
            );
        }

        return List.of(
                speedLine,
                String.format(Locale.ROOT, "walk %.1f px/s, %d px/tick, climb %d, hforce %.1f",
                        profile.walkVelocityPxs(),
                        BotMovementManager.walkStep(map, profile),
                        BotPhysicsEngine.climbStepPerTick(),
                        profile.hForcePxs()),
                String.format(Locale.ROOT, "jump %.1f, rope %.1f, max %.1f px, reach %d/%d px",
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
            return String.format(Locale.ROOT,
                    "speed %d%% jump %d%% (map forced; raw %d%%/%d%%)",
                    profile.totalSpeedStat(), profile.totalJumpStat(), rawSpeedStat, rawJumpStat);
        }
        return String.format(Locale.ROOT, "speed %d%% jump %d%%",
                profile.totalSpeedStat(), profile.totalJumpStat());
    }

    static String formatCompactMesos(int mesos) {
        return AgentDialogueReportFormatter.compactMesos(mesos);
    }

    private static void reportDebugStats(BotEntry entry, Character bot) {
        queueBotReply(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    private static void reportCritDebug(BotEntry entry, Character bot) {
        CombatFormulaProvider formula = CombatFormulaProvider.getInstance();
        CombatFormulaProvider.CritProfile crit = formula.resolveCritProfile(bot);
        CombatFormulaProvider.DamageProfile dmg = formula.resolveDamageProfile(bot, 0, 0, false);

        int critPct = (int) Math.round(crit.critChance() * 100);
        if (critPct == 0) {
            queueBotReply(entry, AgentDialogueCatalog.noCritPassiveReply());
            return;
        }

        int critMin = (int) Math.min(99999, Math.floor(dmg.minDamage() * crit.critMultiplier()));
        int critMax = (int) Math.min(99999, Math.floor(dmg.maxDamage() * crit.critMultiplier()));
        queueBotReply(entry, AgentDialogueReportFormatter.crit(
                critPct, crit.critMultiplier(),
                dmg.minDamage(), dmg.maxDamage(),
                critMin, critMax));
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

    static boolean isRespecCommand(String message) {
        return AgentChatCommandClassifier.isRespecCommand(message);
    }

    static boolean isApRespecCommand(String message) {
        return AgentChatCommandClassifier.isApRespecCommand(message);
    }

    static boolean isFarmHereCommand(String message) {
        return AgentChatCommandClassifier.isFarmHereCommand(message);
    }

    static boolean isPatrolCommand(String message) {
        return AgentChatCommandClassifier.isPatrolCommand(message);
    }

    static boolean isMoveHereCommand(String message) {
        return AgentChatCommandClassifier.isMoveHereCommand(message);
    }

    static boolean isProactiveOffersOnCommand(String message) {
        return AgentChatCommandClassifier.isProactiveOffersOnCommand(message);
    }

    static boolean isProactiveOffersOffCommand(String message) {
        return AgentChatCommandClassifier.isProactiveOffersOffCommand(message);
    }

    static boolean isFollowCommand(String message) {
        return AgentChatCommandClassifier.isFollowCommand(message);
    }

    static boolean isGrindCommand(String message) {
        return AgentChatCommandClassifier.isGrindCommand(message);
    }

    static boolean isStopCommand(String message) {
        return AgentChatCommandClassifier.isStopCommand(message);
    }

    private static void handleApBuildSelection(BotEntry entry, String message) {
        Job job = entry.bot.getJob();

        if (job.isA(Job.WARRIOR) && AgentBuildDialogueClassifier.isPureStrBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), entry.bot.getDex());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.STR, BotBuildManager.StatType.DEX, 4),
                    "dexless it is! keeping dex at " + effectiveDex + ", rest into str",
                    "already doing dexless!");
            return;
        }
        if (job.isA(Job.THIEF) && AgentBuildDialogueClassifier.isDexlessBuildCommand(message)) {
            int effectiveDex = Math.max(minStatFloor(job, Stat.DEX), entry.bot.getDex());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.LUK, BotBuildManager.StatType.DEX, 4),
                    "dexless it is! keeping dex at " + effectiveDex + ", rest into luk",
                    "already doing dexless!");
            return;
        }
        if (job.isA(Job.MAGICIAN) && AgentBuildDialogueClassifier.isLuklessBuildCommand(message)) {
            int effectiveLuk = Math.max(minStatFloor(job, Stat.LUK), entry.bot.getLuk());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.INT, BotBuildManager.StatType.LUK, 4),
                    "lukless it is! keeping luk at " + effectiveLuk + ", rest into int",
                    "already doing lukless!");
            return;
        }
        if (job.isA(Job.BOWMAN) && AgentBuildDialogueClassifier.isStrlessBuildCommand(message)) {
            int effectiveStr = Math.max(minStatFloor(job, Stat.STR), entry.bot.getStr());
            applyApBuildChoice(entry,
                    new BotBuildManager.ApBuild(BotBuildManager.StatType.DEX, BotBuildManager.StatType.STR, 4),
                    "strless it is! keeping str at " + effectiveStr + ", rest into dex",
                    "already doing strless!");
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
                applyApBuildChoice(entry,
                        new BotBuildManager.ApBuild(primary, BotBuildManager.StatType.DEX, dexTarget),
                        "ok! keeping dex at " + effectiveDex + ", rest into " + primary.name().toLowerCase(Locale.ROOT),
                        "already doing " + legalDexTarget + " dex build!");
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
                        "ok! keeping luk at " + effectiveLuk + ", rest into int",
                        "already doing " + legalLukTarget + " luk build!");
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
                        "ok! keeping str at " + effectiveStr + ", rest into dex",
                        "already doing " + legalStrTarget + " str build!");
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

    private static String normalizeCommandText(String message) {
        if (message == null) {
            return "";
        }

        return message.strip()
                .replaceAll("^[\\p{Punct}\\s]+", "")
                .replaceAll("[\\p{Punct}\\s]+$", "")
                .replaceFirst("^(?:(?:please|pls|hey|yo)\\s+)+", "")
                .replaceFirst("^(?:(?:can|could|will|would)\\s+you\\s+)", "")
                .replaceFirst("^(?:(?:please|pls)\\s+)+", "")
                .replaceFirst("\\s+(?:please|pls)$", "")
                .replaceAll("\\s+", " ");
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

    static boolean isFidgetCommand(String message) {
        return AgentChatCommandClassifier.isFidgetCommand(message);
    }

    static int randomFidgetExpression() {
        int[] expressions = {2, 3, 5, 6, 7};
        return expressions[ThreadLocalRandom.current().nextInt(expressions.length)];
    }

    static boolean isNeedHpPotCommand(String message) {
        return AgentChatCommandClassifier.isNeedHpPotCommand(message);
    }

    static boolean isNeedMpPotCommand(String message) {
        return AgentChatCommandClassifier.isNeedMpPotCommand(message);
    }

    static boolean isNeedPotCommand(String message) {
        return AgentChatCommandClassifier.isNeedPotCommand(message);
    }

    static boolean isNeedAmmoCommand(String message) {
        return AgentChatCommandClassifier.isNeedAmmoCommand(message);
    }

    static boolean isRequestUpgradeCommand(String message) {
        return AgentChatCommandClassifier.isRequestUpgradeCommand(message);
    }

    /**
     * Group-wide supply requests ("need pots", "anyone have hp pots", "need arrows"
     * etc.) trigger a single response from the bot group. Broadcasting these to
     * every entry causes duplicate replies and duplicate trades because each bot
     * independently selects the same donor sibling.
     */
    static boolean isGroupSupplyRequest(String message) {
        return AgentChatCommandClassifier.isGroupSupplyRequest(message);
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
            String type = forHp ? "hp" : "mp";
            queueBotReply(entry, String.format(BotManager.randomReply(OWNER_POT_SHORTAGE_REPLIES), type));
        }
    }

    private static void handleNeedAmmoCommand(BotEntry entry) {
        Character owner = entry.owner;
        if (owner == null) {
            return;
        }
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(owner);
        if (weaponType != WeaponType.BOW && weaponType != WeaponType.CROSSBOW) {
            queueBotReply(entry, BotManager.randomReply(AMMO_NOT_NEEDED_REPLIES));
            return;
        }
        BotAmmoManager.OwnerAmmoShareResult result = BotAmmoManager.offerAmmoShareToOwner(entry, weaponType);
        if (result == BotAmmoManager.OwnerAmmoShareResult.NO_DONOR) {
            queueBotReply(entry, BotManager.randomReply(OWNER_AMMO_SHORTAGE_REPLIES));
        }
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<LearnedSkill>> skillTrees = collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            entry.pendingAction = null;
            queueBotReply(entry, AgentDialogueCatalog.noJobSkillsReply());
            return;
        }

        if (skillTrees.size() == 1) {
            entry.pendingAction = null;
            Map.Entry<Integer, List<LearnedSkill>> onlyTree = skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        Integer treeId = resolveSkillTreeChoice(message, skillTrees);
        if (treeId == null) {
            queueBotReply(entry, skillTreeChoicePrompt(skillTrees));
            return;
        }

        entry.pendingAction = null;
        queueSkillTreeReport(entry, treeId, skillTrees.get(treeId));
    }

    private static Map<Integer, List<LearnedSkill>> collectLearnedSkillTrees(Character bot) {
        Map<Integer, List<LearnedSkill>> skillTrees = new TreeMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : bot.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            int treeId = skillId / 10000;
            skillTrees.computeIfAbsent(treeId, ignored -> new ArrayList<>())
                    .add(new LearnedSkill(skillId, skillName(skillId), skillEntry.skillevel));
        }

        for (List<LearnedSkill> skills : skillTrees.values()) {
            skills.sort(Comparator.comparingInt(LearnedSkill::id));
        }
        return skillTrees;
    }

    private static List<LearnedSkill> collectLearnedBeginnerSkills(Character bot) {
        List<LearnedSkill> beginnerSkills = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : bot.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (!skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            beginnerSkills.add(new LearnedSkill(skillId, skillName(skillId), skillEntry.skillevel));
        }

        beginnerSkills.sort(Comparator.comparingInt(LearnedSkill::id));
        return beginnerSkills;
    }

    private static int getRemainingBeginnerSp(Character bot) {
        int usedBeginnerSp = 0;
        int beginnerSkillBase = bot.getJobType() * 10000000 + 1000;
        for (int i = 0; i < 3; i++) {
            Skill skill = SkillFactory.getSkill(beginnerSkillBase + i);
            if (skill != null) {
                usedBeginnerSp += bot.getSkillLevel(skill);
            }
        }

        return Math.max(0, Math.min(bot.getLevel() - 1, 6) - usedBeginnerSp);
    }

    private static void queueSkillTreeReport(BotEntry entry, int treeId, List<LearnedSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noLearnedSkillsInReply(skillTreeLabel(treeId)));
            return;
        }

        String label = skillTreeLabel(treeId);
        String prefix = label + ": ";
        String followupPrefix = "more " + label + ": ";
        StringBuilder line = new StringBuilder(prefix);
        int countOnLine = 0;

        for (LearnedSkill skill : skills) {
            String piece = skill.name() + " lv" + skill.level();
            boolean needsSeparator = countOnLine > 0;
            int extraChars = piece.length() + (needsSeparator ? 2 : 0);
            if ((line.length() + extraChars > 100 || countOnLine >= 3) && countOnLine > 0) {
                queueBotReply(entry, line.toString());
                line = new StringBuilder(followupPrefix);
                countOnLine = 0;
                needsSeparator = false;
            }

            if (needsSeparator) {
                line.append(", ");
            }
            line.append(piece);
            countOnLine++;
        }

        if (countOnLine > 0) {
            queueBotReply(entry, line.toString());
        }
    }

    private static Integer resolveSkillTreeChoice(String message, Map<Integer, List<LearnedSkill>> skillTrees) {
        for (int treeId : AgentBuildDialogueClassifier.skillTreeChoiceIds(message)) {
            if (skillTrees.containsKey(treeId)) {
                return treeId;
            }
        }

        String normalizedMessage = normalizeChoiceText(message);
        List<Integer> matches = new ArrayList<>();
        for (int treeId : skillTrees.keySet()) {
            if (matchesSkillTreeChoice(normalizedMessage, treeId)) {
                matches.add(treeId);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean matchesSkillTreeChoice(String normalizedMessage, int treeId) {
        String fullLabel = normalizeChoiceText(skillTreeLabel(treeId));
        if (!fullLabel.isEmpty() && normalizedMessage.contains(fullLabel)) {
            return true;
        }

        Job job = Job.getById(treeId);
        if (job == null) {
            return false;
        }

        String baseLabel = normalizeChoiceText(jobDisplayName(job));
        return !baseLabel.isEmpty() && normalizedMessage.contains(baseLabel);
    }

    private static String skillTreeChoicePrompt(Map<Integer, List<LearnedSkill>> skillTrees) {
        List<String> labels = new ArrayList<>();
        for (int treeId : skillTrees.keySet()) {
            labels.add(skillTreeLabel(treeId));
        }
        return "which skill tree? " + String.join(", ", labels);
    }

    private static String skillTreeLabel(int treeId) {
        Job job = Job.getById(treeId);
        if (job == null) {
            return "tree " + treeId;
        }

        return switch (job) {
            case NOBLESSE -> "noblesse (" + treeId + ")";
            case DAWNWARRIOR1 -> "dawn warrior 1st job (" + treeId + ")";
            case DAWNWARRIOR2 -> "dawn warrior 2nd job (" + treeId + ")";
            case DAWNWARRIOR3 -> "dawn warrior 3rd job (" + treeId + ")";
            case DAWNWARRIOR4 -> "dawn warrior 4th job (" + treeId + ")";
            case BLAZEWIZARD1 -> "blaze wizard 1st job (" + treeId + ")";
            case BLAZEWIZARD2 -> "blaze wizard 2nd job (" + treeId + ")";
            case BLAZEWIZARD3 -> "blaze wizard 3rd job (" + treeId + ")";
            case BLAZEWIZARD4 -> "blaze wizard 4th job (" + treeId + ")";
            case WINDARCHER1 -> "wind archer 1st job (" + treeId + ")";
            case WINDARCHER2 -> "wind archer 2nd job (" + treeId + ")";
            case WINDARCHER3 -> "wind archer 3rd job (" + treeId + ")";
            case WINDARCHER4 -> "wind archer 4th job (" + treeId + ")";
            case NIGHTWALKER1 -> "night walker 1st job (" + treeId + ")";
            case NIGHTWALKER2 -> "night walker 2nd job (" + treeId + ")";
            case NIGHTWALKER3 -> "night walker 3rd job (" + treeId + ")";
            case NIGHTWALKER4 -> "night walker 4th job (" + treeId + ")";
            case THUNDERBREAKER1 -> "thunder breaker 1st job (" + treeId + ")";
            case THUNDERBREAKER2 -> "thunder breaker 2nd job (" + treeId + ")";
            case THUNDERBREAKER3 -> "thunder breaker 3rd job (" + treeId + ")";
            case THUNDERBREAKER4 -> "thunder breaker 4th job (" + treeId + ")";
            case LEGEND -> "legend (" + treeId + ")";
            case ARAN1 -> "aran 1st job (" + treeId + ")";
            case ARAN2 -> "aran 2nd job (" + treeId + ")";
            case ARAN3 -> "aran 3rd job (" + treeId + ")";
            case ARAN4 -> "aran 4th job (" + treeId + ")";
            case EVAN -> "evan (" + treeId + ")";
            case EVAN1 -> "evan 1st job (" + treeId + ")";
            case EVAN2 -> "evan 2nd job (" + treeId + ")";
            case EVAN3 -> "evan 3rd job (" + treeId + ")";
            case EVAN4 -> "evan 4th job (" + treeId + ")";
            case EVAN5 -> "evan 5th job (" + treeId + ")";
            case EVAN6 -> "evan 6th job (" + treeId + ")";
            case EVAN7 -> "evan 7th job (" + treeId + ")";
            case EVAN8 -> "evan 8th job (" + treeId + ")";
            case EVAN9 -> "evan 9th job (" + treeId + ")";
            case EVAN10 -> "evan 10th job (" + treeId + ")";
            default -> jobDisplayName(job) + " (" + treeId + ")";
        };
    }

    private static String normalizeChoiceText(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String skillName(int skillId) {
        String name = SkillFactory.getSkillName(skillId);
        return name != null && !name.isBlank() ? name : String.valueOf(skillId);
    }

    private static void handleTransferCommand(BotEntry entry, TransferCommand transferCommand, String message) {
        String category = transferCommand.category;
        if (transferCommand.mode == TransferMode.TRADE
                && "trash".equals(category)
                && message != null
                && AgentTradeDialogueClassifier.isShowJunkCommand(message)) {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.weirdTransferReply());
        }
        if (transferCommand.mode == TransferMode.TRADE && BotInventoryManager.isMesoCategory(category)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotInventoryManager.startTradeTransfer(category, entry, entry.bot));
            return;
        }

        scheduleTransferCommandEvaluation(entry, transferCommand, category);
    }

    private static void scheduleTransferCommandEvaluation(BotEntry entry, TransferCommand transferCommand, String category) {
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
                                                                 TransferCommand transferCommand,
                                                                 String category,
                                                                 Character bot) {
        long hasItemsStartedAt = transferCommand.mode == TransferMode.TRADE
                && BotInventoryManager.profileTradeCategory(category)
                ? System.nanoTime() : 0L;
        boolean hasItems = BotInventoryManager.hasTransferableItems(category, entry, bot);
        BotInventoryManager.logSlowTradeCommand(category, "hasTransferableItems", entry, bot, hasItemsStartedAt);
        int count = hasItems && transferCommand.mode == TransferMode.CHOICE
                ? BotInventoryManager.countTransferableItems(category, entry, bot)
                : 0;
        return new TransferCommandResult(hasItems, count);
    }

    private static void applyTransferCommandResult(BotEntry entry,
                                                   TransferCommand transferCommand,
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

        switch (transferCommand.mode) {
            case TRADE -> BotInventoryManager.startTradeTransfer(category, entry, bot);
            case CHOICE -> {
                entry.pendingAction = "item_choice";
                entry.pendingDropCategory = category;
                BotManager.getInstance().botReply(entry, dropOrTradePrompt(category, result.count()));
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
        String category = "name:" + itemName;
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

        entry.pendingAction = "item_choice";
        entry.pendingDropCategory = category;
        BotManager.getInstance().botReply(entry, dropOrTradePrompt(category, result.count()));
    }

    private static TransferCommand matchTransferCommand(String message) {
        String tradeCategory = matchTradeCategory(message);
        if (tradeCategory != null) {
            return new TransferCommand(TransferMode.TRADE, tradeCategory);
        }

        String choiceCategory = matchChoiceCategory(message);
        if (choiceCategory != null) {
            return new TransferCommand(TransferMode.CHOICE, choiceCategory);
        }

        return null;
    }

    static String matchItemQuery(String message) {
        return AgentTradeDialogueClassifier.matchItemQuery(message);
    }

    static String matchTradeCategory(String message) {
        return AgentTradeDialogueClassifier.matchTradeCategory(message);
    }

    static String matchFollowTarget(String message) {
        return AgentChatCommandClassifier.matchFollowTarget(message);
    }

    static String matchChoiceCategory(String message) {
        return AgentTradeDialogueClassifier.matchChoiceCategory(message);
    }

    private static final String[] DROP_OR_TRADE_PROMPTS = {
        "got %s, want me to trade or drop?",
        "i have %s, trade or drop?",
        "sure, %s - trade or drop?",
        "just to confirm, trade or drop my %s?",
        "want me to trade or drop %s?",
    };

    private static String dropOrTradePrompt(String category, int count) {
        String base = switch (category) {
            case "scrolls" -> "scrolls";
            case "pots"    -> "pots";
            case "buff"    -> "buff pots";
            case "use"     -> "use items";
            case "equips"  -> "equips";
            case "etc"     -> "etc items";
            default        -> category.startsWith("name:") ? category.substring(5) : "those items";
        };
        String what = count > 0 ? count + " " + base : base;
        String fmt = DROP_OR_TRADE_PROMPTS[ThreadLocalRandom.current().nextInt(DROP_OR_TRADE_PROMPTS.length)];
        return String.format(fmt, what);
    }

    /** Maps a chat keyword to the correct next Job given bot's current job and level. Returns null if not valid. */
    private static Job resolveJobChange(Character bot, String msg) {
        Job cur = bot.getJob();
        int lvl = bot.getLevel();

        return switch (cur) {
            case BEGINNER -> {
                if (lvl >= 8  && msg.matches(".*\\b(mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage)\\b.*")) yield Job.MAGICIAN;
                if (lvl >= 10 && msg.matches(".*\\b(warrior|fighter|page|spearman|sader)\\b.*")) yield Job.WARRIOR;
                if (lvl >= 10 && msg.matches(".*\\b(bowman|bowmen|archer|hunter|crossbow|xbow)\\b.*")) yield Job.BOWMAN;
                if (lvl >= 10 && msg.matches(".*\\b(thief|assassin|sin|bandit|dit)\\b.*")) yield Job.THIEF;
                if (lvl >= 10 && msg.matches(".*\\b(pirate|brawler|gunslinger|gun|bucc)\\b.*")) yield Job.PIRATE;
                yield null;
            }
            // 2nd job
            case WARRIOR -> lvl < 30 ? null :
                    msg.matches(".*\\b(fighter|sader)\\b.*") ? Job.FIGHTER :
                    msg.matches(".*\\bpage\\b.*") ? Job.PAGE :
                    msg.matches(".*\\b(spearman|spear)\\b.*") ? Job.SPEARMAN : null;
            case MAGICIAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(fp|fp wizard|fp mage|fire|f\\.p)\\b.*") ? Job.FP_WIZARD :
                    msg.matches(".*\\b(il|il wizard|il mage|ice|i\\.l)\\b.*") ? Job.IL_WIZARD :
                    msg.matches(".*\\b(cleric|healer|priest|bishop)\\b.*") ? Job.CLERIC : null;
            case BOWMAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(hunter|bow)\\b.*") ? Job.HUNTER :
                    msg.matches(".*\\b(crossbow|xbow|crossbowman)\\b.*") ? Job.CROSSBOWMAN : null;
            case THIEF -> lvl < 30 ? null :
                    msg.matches(".*\\b(assassin|sin)\\b.*") ? Job.ASSASSIN :
                    msg.matches(".*\\b(bandit|dit)\\b.*") ? Job.BANDIT : null;
            case PIRATE -> lvl < 30 ? null :
                    msg.matches(".*\\b(brawler|knuckle)\\b.*") ? Job.BRAWLER :
                    msg.matches(".*\\b(gunslinger|gun)\\b.*") ? Job.GUNSLINGER : null;
            // 3rd job
            case FIGHTER     -> lvl >= 70 && msg.matches(".*\\bcrusader\\b.*")                 ? Job.CRUSADER     : null;
            case PAGE        -> lvl >= 70 && msg.matches(".*\\b(white knight|wk)\\b.*")         ? Job.WHITEKNIGHT  : null;
            case SPEARMAN    -> lvl >= 70 && msg.matches(".*\\b(dragon knight|dk)\\b.*")        ? Job.DRAGONKNIGHT : null;
            case FP_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(fp mage|fp)\\b.*")              ? Job.FP_MAGE      : null;
            case IL_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(il mage|il)\\b.*")              ? Job.IL_MAGE      : null;
            case CLERIC      -> lvl >= 70 && msg.matches(".*\\bpriest\\b.*")                    ? Job.PRIEST       : null;
            case HUNTER      -> lvl >= 70 && msg.matches(".*\\branger\\b.*")                    ? Job.RANGER       : null;
            case CROSSBOWMAN -> lvl >= 70 && msg.matches(".*\\bsniper\\b.*")                    ? Job.SNIPER       : null;
            case ASSASSIN    -> lvl >= 70 && msg.matches(".*\\bhermit\\b.*")                    ? Job.HERMIT       : null;
            case BANDIT      -> lvl >= 70 && msg.matches(".*\\b(chief bandit|cb|chief)\\b.*")   ? Job.CHIEFBANDIT  : null;
            case BRAWLER     -> lvl >= 70 && msg.matches(".*\\bmarauder\\b.*")                  ? Job.MARAUDER     : null;
            case GUNSLINGER  -> lvl >= 70 && msg.matches(".*\\boutlaw\\b.*")                    ? Job.OUTLAW       : null;
            // 4th job
            case CRUSADER    -> lvl >= 120 && msg.matches(".*\\bhero\\b.*")                         ? Job.HERO        : null;
            case WHITEKNIGHT -> lvl >= 120 && msg.matches(".*\\bpaladin\\b.*")                      ? Job.PALADIN     : null;
            case DRAGONKNIGHT -> lvl >= 120 && msg.matches(".*\\b(dark knight|drk)\\b.*")           ? Job.DARKKNIGHT  : null;
            case FP_MAGE     -> lvl >= 120 && msg.matches(".*\\b(fp archmage|fp arch)\\b.*")        ? Job.FP_ARCHMAGE : null;
            case IL_MAGE     -> lvl >= 120 && msg.matches(".*\\b(il archmage|il arch)\\b.*")        ? Job.IL_ARCHMAGE : null;
            case PRIEST      -> lvl >= 120 && msg.matches(".*\\bbishop\\b.*")                       ? Job.BISHOP      : null;
            case RANGER      -> lvl >= 120 && msg.matches(".*\\b(bowmaster|bm)\\b.*")               ? Job.BOWMASTER   : null;
            case SNIPER      -> lvl >= 120 && msg.matches(".*\\b(marksman|mm)\\b.*")                ? Job.MARKSMAN    : null;
            case HERMIT      -> lvl >= 120 && msg.matches(".*\\b(night lord|nl)\\b.*")              ? Job.NIGHTLORD   : null;
            case CHIEFBANDIT -> lvl >= 120 && msg.matches(".*\\b(shadower|shad)\\b.*")              ? Job.SHADOWER    : null;
            case MARAUDER    -> lvl >= 120 && msg.matches(".*\\b(buccaneer|bucc)\\b.*")             ? Job.BUCCANEER   : null;
            case OUTLAW      -> lvl >= 120 && msg.matches(".*\\bcorsair\\b.*")                      ? Job.CORSAIR     : null;
            default -> null;
        };
    }

    static String jobDisplayName(Job job) {
        return switch (job) {
            case WARRIOR     -> "warrior";      case MAGICIAN    -> "mage";
            case BOWMAN      -> "bowman";       case THIEF       -> "thief";
            case PIRATE      -> "pirate";       case FIGHTER     -> "fighter";
            case PAGE        -> "page";         case SPEARMAN    -> "spearman";
            case FP_WIZARD   -> "f/p wizard";   case IL_WIZARD   -> "i/l wizard";
            case CLERIC      -> "cleric";       case HUNTER      -> "hunter";
            case CROSSBOWMAN -> "crossbowman";  case ASSASSIN    -> "assassin";
            case BANDIT      -> "bandit";       case BRAWLER     -> "brawler";
            case GUNSLINGER  -> "gunslinger";   case CRUSADER    -> "crusader";
            case WHITEKNIGHT -> "white knight"; case DRAGONKNIGHT-> "dragon knight";
            case FP_MAGE     -> "f/p mage";     case IL_MAGE     -> "i/l mage";
            case PRIEST      -> "priest";       case RANGER      -> "ranger";
            case SNIPER      -> "sniper";       case HERMIT      -> "hermit";
            case CHIEFBANDIT -> "chief bandit"; case MARAUDER    -> "marauder";
            case OUTLAW      -> "outlaw";       case HERO        -> "hero";
            case PALADIN     -> "paladin";      case DARKKNIGHT  -> "dark knight";
            case FP_ARCHMAGE -> "f/p archmage"; case IL_ARCHMAGE -> "i/l archmage";
            case BISHOP      -> "bishop";       case BOWMASTER   -> "bowmaster";
            case MARKSMAN    -> "marksman";     case NIGHTLORD   -> "night lord";
            case SHADOWER    -> "shadower";     case BUCCANEER   -> "buccaneer";
            case NOBLESSE    -> "noblesse";
            case DAWNWARRIOR1 -> "dawn warrior";   case DAWNWARRIOR2 -> "dawn warrior";
            case DAWNWARRIOR3 -> "dawn warrior";   case DAWNWARRIOR4 -> "dawn warrior";
            case BLAZEWIZARD1 -> "blaze wizard";   case BLAZEWIZARD2 -> "blaze wizard";
            case BLAZEWIZARD3 -> "blaze wizard";   case BLAZEWIZARD4 -> "blaze wizard";
            case WINDARCHER1  -> "wind archer";    case WINDARCHER2  -> "wind archer";
            case WINDARCHER3  -> "wind archer";    case WINDARCHER4  -> "wind archer";
            case NIGHTWALKER1 -> "night walker";   case NIGHTWALKER2 -> "night walker";
            case NIGHTWALKER3 -> "night walker";   case NIGHTWALKER4 -> "night walker";
            case THUNDERBREAKER1 -> "thunder breaker"; case THUNDERBREAKER2 -> "thunder breaker";
            case THUNDERBREAKER3 -> "thunder breaker"; case THUNDERBREAKER4 -> "thunder breaker";
            case LEGEND       -> "legend";
            case ARAN1        -> "aran";         case ARAN2        -> "aran";
            case ARAN3        -> "aran";         case ARAN4        -> "aran";
            case CORSAIR     -> "corsair";
            default -> job.name().toLowerCase();
        };
    }

    private static void handleFameCommand(BotEntry entry, String targetName) {
        Character bot = entry.bot;
        Character target;
        if (targetName.equalsIgnoreCase("me")) {
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
            BotManager.getInstance().botReply(entry, BotManager.randomReply(FAME_COOLDOWN_REPLIES));
            return;
        }
        if (status == Character.FameStatus.NOT_THIS_MONTH) {
            String reply = String.format(BotManager.randomReply(FAME_SAME_PERSON_REPLIES), target.getName());
            BotManager.getInstance().botReply(entry, reply);
            return;
        }
        if (target.gainFame(1, bot, 1)) {
            bot.hasGivenFame(target);
            String template = BotManager.randomReply(FAME_OK_REPLIES);
            String reply = template.contains("%s") ? String.format(template, target.getName()) : template;
            BotManager.getInstance().botReply(entry, reply);
        } else {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameFailedReply());
        }
    }
}
