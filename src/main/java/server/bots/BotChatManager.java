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
    private static final List<String> DROP_OR_TRADE_PROMPTS = AgentDialogueCatalog.dropOrTradePrompts();

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
                String choice = AgentChatCommandClassifier.normalizeCommandText(message);
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
                String cancelMsg = AgentDialogueCatalog.pendingActionCancelReply(
                        action != null && action.startsWith("drop"));
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
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.buffConsumablesOnReply(
                        AgentDialogueCatalog.buffConsumablesModeLabel(entry.buffCheapMode)));
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
        if (AgentChatCommandClassifier.isApRespecCommand(message)) {
            BotManager.after(BotManager.randMs(500, 700), () ->
                    BotManager.getInstance().botReply(entry, BotBuildManager.respecAp(entry, entry.bot)));
            return;
        }
        if (AgentChatCommandClassifier.isRespecCommand(message)) {
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

        if (AgentChatCommandClassifier.isFarmHereCommand(message)) {
            Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (dest != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issueFarmHere(entry, dest);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (AgentChatCommandClassifier.isPatrolCommand(message)) {
            Point ownerPos = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (ownerPos != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    prepareActiveModeEntry(entry);
                    BotManager.getInstance().issuePatrol(entry, ownerPos);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (AgentChatCommandClassifier.isMoveHereCommand(message)) {
            Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (dest != null) {
                BotManager.after(BotManager.randMs(1000, 1500), () -> {
                    BotManager.getInstance().issueMoveTo(entry, dest, true);
                    BotManager.getInstance().botReply(entry, BotManager.randomReply(MOVE_HERE_REPLIES));
                });
            }
        } else if (AgentChatCommandClassifier.isFollowCommand(message)) {
            BotManager.after(BotManager.randMs(1500, 2000), () -> {
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                entry.nextGearSuggestionAt = 0;
                maybeSuggestGearToSiblings(entry, entry.bot);
                BotManager.getInstance().botReply(entry, BotManager.randomReply(FOLLOW_REPLIES));
                BotPotionManager.checkPotShareOnModeStart(entry, entry.bot);
                BotManager.after(BotManager.randMs(250, 750), () -> BotManager.getInstance().issueFollowOwner(entry));
            });
        } else if (AgentChatCommandClassifier.isGrindCommand(message)) {
            BotManager.after(BotManager.randMs(1500, 2000), () -> {
                prepareActiveModeEntry(entry);
                BotManager.getInstance().botReply(entry, BotPotionManager.grindStartMessage(entry.bot));
                BotManager.after(BotManager.randMs(250, 750), () -> {
                    BotManager.getInstance().issueGrind(entry);
                    checkBotStatus(entry, entry.bot);
                });
            });
        } else if (AgentChatCommandClassifier.isStopCommand(message)) {
            BotManager.after(BotManager.randMs(900, 1100), () -> {
                BotManager.getInstance().issueStop(entry);
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                entry.nextGearSuggestionAt = 0;
                maybeSuggestGearToSiblings(entry, entry.bot);
                BotManager.after(BotManager.randMs(1400, 1600), () ->
                        BotManager.getInstance().botReply(entry, BotManager.randomReply(STOP_REPLIES)));
            });
        } else if (AgentChatCommandClassifier.isFidgetCommand(message)) {
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
                entry.spVariant = AgentBuildDialogueClassifier.ONE_HANDED_SP_VARIANT;
                BotManager.getInstance().botReply(entry, AgentDialogueCatalog.oneHandedSpVariantReply());
                BotBuildManager.autoAssignSp(entry, entry.bot);
            } else if (AgentBuildDialogueClassifier.isTwoHandedSpVariant(message)) {
                entry.spVariant = AgentBuildDialogueClassifier.TWO_HANDED_SP_VARIANT;
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

        String queriedItem = AgentTradeDialogueClassifier.matchItemQuery(message);
        if (queriedItem != null) {
            handleItemQuery(entry, queriedItem);
            return;
        }

        // Info commands
        if (AgentChatCommandClassifier.isRequestUpgradeCommand(message)) {
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
        if (AgentChatCommandClassifier.isMovementStatsQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportMovementStats(entry, entry.bot));
        if (AgentChatCommandClassifier.isRangeQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportRange(entry, entry.bot));
        if (AgentChatCommandClassifier.isBuildQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportBuild(entry, entry.bot));
        if (AgentChatCommandClassifier.isInventoryQuery(message))
            BotManager.after(BotManager.randMs(900, 1100), () -> reportInventory(entry, entry.bot));
        if (AgentChatCommandClassifier.isMesoQuery(message))
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
            Job advJob = AgentBuildDialogueClassifier.resolveJobChange(
                    entry.bot.getJob(), entry.bot.getLevel(), message.toLowerCase());
            if (advJob != null) {
                String jobName = AgentDialogueReportFormatter.jobDisplayName(advJob);
                String reply = AgentDialogueReportFormatter.jobChangeReply(
                        BotManager.randomReply(AgentDialogueCatalog.jobChangeReplyTemplates()), jobName);
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
        String choice = AgentChatCommandClassifier.normalizeCommandText(message);
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
        final String text = AgentDialogueReportFormatter.welcomeBackOfflineReply(
                BotManager.randomReply(WB_OFFLINE_PARTY_TEMPLATES), mapName);
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
                bot.getLevel(), AgentDialogueReportFormatter.jobDisplayName(bot.getJob()),
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
        queueBotReply(entry, AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
    }

    private static void reportBeginnerSkills(BotEntry entry, Character bot) {
        List<LearnedSkill> beginnerSkills = collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = getRemainingBeginnerSp(bot);

        if (beginnerSkills.isEmpty()) {
            queueBotReply(entry, AgentDialogueCatalog.noBeginnerSkillsReply(beginnerSpLeft));
            return;
        }

        queueBotReply(entry, AgentDialogueReportFormatter.beginnerSkillReport(toAgentSkillLines(beginnerSkills), beginnerSpLeft));
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

    static String buildMesoReport(int mesos) {
        return AgentDialogueReportFormatter.mesoReport(mesos, MESO_REPLIES);
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
                    BotManager.randomReply(OWNER_POT_SHORTAGE_REPLIES),
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

        Integer treeId = AgentBuildDialogueClassifier.resolveSkillTreeChoice(message, skillTrees.keySet());
        if (treeId == null) {
            queueBotReply(entry, AgentDialogueReportFormatter.skillTreeChoicePrompt(skillTrees.keySet()));
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
            queueBotReply(entry, AgentDialogueCatalog.noLearnedSkillsInReply(AgentDialogueReportFormatter.skillTreeLabel(treeId)));
            return;
        }

        for (String line : AgentDialogueReportFormatter.skillTreeReportLines(treeId, toAgentSkillLines(skills))) {
            queueBotReply(entry, line);
        }
    }

    private static List<AgentDialogueReportFormatter.AgentSkillLine> toAgentSkillLines(List<LearnedSkill> skills) {
        List<AgentDialogueReportFormatter.AgentSkillLine> lines = new ArrayList<>();
        for (LearnedSkill skill : skills) {
            lines.add(new AgentDialogueReportFormatter.AgentSkillLine(skill.id(), skill.name(), skill.level()));
        }
        return lines;
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
        String tradeCategory = AgentTradeDialogueClassifier.matchTradeCategory(message);
        if (tradeCategory != null) {
            return new TransferCommand(TransferMode.TRADE, tradeCategory);
        }

        String choiceCategory = AgentTradeDialogueClassifier.matchChoiceCategory(message);
        if (choiceCategory != null) {
            return new TransferCommand(TransferMode.CHOICE, choiceCategory);
        }

        return null;
    }

    private static String dropOrTradePrompt(String category, int count) {
        return AgentDialogueReportFormatter.dropOrTradePrompt(category, count, DROP_OR_TRADE_PROMPTS);
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
            BotManager.getInstance().botReply(entry, BotManager.randomReply(FAME_COOLDOWN_REPLIES));
            return;
        }
        if (status == Character.FameStatus.NOT_THIS_MONTH) {
            String reply = AgentDialogueReportFormatter.fameSamePersonReply(
                    BotManager.randomReply(FAME_SAME_PERSON_REPLIES), target.getName());
            BotManager.getInstance().botReply(entry, reply);
            return;
        }
        if (target.gainFame(1, bot, 1)) {
            bot.hasGivenFame(target);
            String template = BotManager.randomReply(FAME_OK_REPLIES);
            String reply = AgentDialogueReportFormatter.fameOkReply(template, target.getName());
            BotManager.getInstance().botReply(entry, reply);
        } else {
            BotManager.getInstance().botReply(entry, AgentDialogueCatalog.fameFailedReply());
        }
    }
}
