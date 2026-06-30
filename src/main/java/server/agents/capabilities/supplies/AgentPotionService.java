package server.agents.capabilities.supplies;

import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.combat.AgentCombatConfig;

import server.agents.runtime.AgentPerformanceMonitor;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.trade.AgentSupplyShareTradeService;
import server.agents.capabilities.supplies.AgentAutopotPolicy.AutopotChoice;
import server.agents.capabilities.supplies.AgentAutopotPolicy.PotionRanking;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMovementStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotPotionDonorPlan;
import server.agents.integration.AgentBotPotionRuntime;
import server.agents.integration.AgentBotPotionStateRuntime;
import server.agents.integration.AgentBotCombatAmmoCheckRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;
import server.bots.BotMovementManager;
import server.bots.BotPhysicsEngine;
import server.StatEffect;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class AgentPotionService {
    // ownerCharId -> shared HP/MP 30 s request cooldown
    private static final Map<Integer, Long> potShareCooldownUntil = new ConcurrentHashMap<>();
    // ownerCharId -> category-specific 10 min failed-request backoff
    private static final Map<Integer, Long> potShareHpBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> potShareMpBackoffUntil = new ConcurrentHashMap<>();

    private AgentPotionService() {
    }

    /** Single source of truth: items the bot has that count as recovery pots. */
    public static List<Item> recoveryPotions(Character bot) {
        long startedAt = AgentPerformanceMonitor.start();
        List<Item> result = new java.util.ArrayList<>();
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            if (item.getQuantity() <= 0) {
                continue;
            }
            if (AgentUseItemClassificationPolicy.isRecoveryPotion(item.getItemId())) {
                result.add(item);
            }
        }
        AgentPerformanceMonitor.recordSince("potion-recovery-scan", startedAt);
        return result;
    }

    public static int[] countPotions(Character bot) {
        long startedAt = AgentPerformanceMonitor.start();
        int[] counts = AgentPotionInventoryPolicy.countPureRecoveryPotions(
                bot.getInventory(InventoryType.USE).list(),
                AgentUseItemClassificationPolicy::itemEffect);
        AgentPerformanceMonitor.recordSince("potion-recovery-scan", startedAt);
        return counts;
    }

    public static int[] countPotions(List<Item> items, Function<Integer, StatEffect> effectLookup) {
        long startedAt = AgentPerformanceMonitor.start();
        int hp = 0;
        int mp = 0;
        for (Item item : items) {
            StatEffect effect = effectLookup.apply(item.getItemId());
            if (effect == null) {
                continue;
            }
            int quantity = item.getQuantity();
            if (effect.getHp() > 0 || effect.getHpRate() > 0) {
                hp += quantity;
            }
            if (effect.getMp() > 0 || effect.getMpRate() > 0) {
                mp += quantity;
            }
        }
        AgentPerformanceMonitor.recordSince("potion-recovery-count", startedAt);
        return new int[]{hp, mp};
    }

    /**
     * Autopot selection priority, best (lowest ordinal) → worst:
     *   1. FLAT_SINGLE — e.g. 50 HP only
     *   2. FLAT_MIXED  — e.g. 50 HP + 50 MP
     *   3. RATE_SINGLE — e.g. 20% HP only
     *   4. RATE_MIXED  — e.g. 20% HP + 20% MP
     * Within the same tier, the smaller recovery value wins (burn cheap pots first;
     * preserve big pots for emergencies). Buff potions (statups present) are excluded.
     */
    public static PotionRanking classifyForSlot(StatEffect fx, boolean hpSlot) {
        return AgentAutopotPolicy.classifyForSlot(fx, hpSlot);
    }

    /** Shared selection used by both keybind setup and the debug report. */
    public static AutopotChoice computeAutopotChoice(Character bot) {
        long startedAt = AgentPerformanceMonitor.start();
        AutopotChoice choice = AgentAutopotPolicy.computeChoice(
                bot.getInventory(InventoryType.USE).list(),
                AgentUseItemClassificationPolicy::itemEffect);
        AgentPerformanceMonitor.recordSince("potion-recovery-scan", startedAt);
        return choice;
    }

    public static void setupAutopotForBot(Character bot) {
        AutopotChoice choice = computeAutopotChoice(bot);

        if (choice.hpItemId() > 0) {
            bot.changeKeybinding(91, new KeyBinding(7, choice.hpItemId()));
            bot.setAutopotHpAlert(BotManager.cfg.AUTOPOT_HP_THRESH);
        } else {
            bot.getKeymap().remove(91);
            bot.setAutopotHpAlert(0f);
        }

        if (choice.mpItemId() > 0) {
            bot.changeKeybinding(92, new KeyBinding(7, choice.mpItemId()));
            bot.setAutopotMpAlert(BotManager.cfg.AUTOPOT_MP_THRESH);
        } else {
            bot.getKeymap().remove(92);
            bot.setAutopotMpAlert(0f);
        }
    }

    /** Owner-facing diagnostic: counts vs. selected items for each slot. */
    public static String autopotDebugReport(Character bot) {
        int[] cnt = countPotions(bot);
        AutopotChoice choice = computeAutopotChoice(bot);
        ItemInformationProvider iip = ItemInformationProvider.getInstance();
        return AgentSupplyDialogueReporter.autopotDebugReport(
                cnt[0],
                cnt[1],
                describeChoice(iip, choice.hpItemId(), choice.hpRank()),
                describeChoice(iip, choice.mpItemId(), choice.mpRank()));
    }

    private static String describeChoice(ItemInformationProvider iip, int itemId, PotionRanking rank) {
        if (itemId <= 0 || rank == null) {
            return "none";
        }
        String name = iip.getName(itemId);
        if (name == null) name = String.valueOf(itemId);
        return AgentSupplyDialogueReporter.autopotChoice(name, itemId, rank.tier().name(), rank.value());
    }

    public static String grindStartMessage(Character bot) {
        int[] pots = countPotions(bot);
        return AgentSupplyDialogueReporter.grindStartMessage(
                BotManager.randomReply(AgentDialogueCatalog.grindReplies()),
                pots[0],
                pots[1],
                BotManager.cfg.POT_LOW_WARN);
    }

    public static void tickPotionCheck(BotEntry entry, Character bot) {
        if (AgentBotPotionStateRuntime.hasPotCheckDelay(entry)) {
            AgentBotPotionStateRuntime.tickPotCheckDelay(entry, BotMovementManager::tickDown);
            return;
        }
        AgentBotPotionStateRuntime.setPotCheckTimerMs(
                entry,
                BotMovementManager.delayAfterCurrentTick(BotManager.cfg.POT_CHECK_INTERVAL_MS));

        long startedAt = AgentPerformanceMonitor.start();
        setupAutopotForBot(bot);
        AgentPerformanceMonitor.recordSince("potion-autopot", startedAt);

        startedAt = AgentPerformanceMonitor.start();
        AgentBotCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot,
                AgentCombatConfig.cfg.AMMO_LOW_WARN, BotManager.cfg.POT_LOW_WARN);
        AgentPerformanceMonitor.recordSince("potion-ammo-check", startedAt);

        if (!AgentBotModeStateRuntime.grinding(entry) && !AgentBotModeStateRuntime.following(entry)) {
            return;
        }
        startedAt = AgentPerformanceMonitor.start();
        AgentAmmoService.tickAmmoShareCheck(entry, bot);
        AgentPerformanceMonitor.recordSince("potion-ammo-share", startedAt);

        startedAt = AgentPerformanceMonitor.start();
        int[] pots = countPotions(bot);
        AgentPerformanceMonitor.recordSince("potion-count", startedAt);

        startedAt = AgentPerformanceMonitor.start();
        requestLowPotShare(entry, bot, pots[0], true, false);
        AgentPerformanceMonitor.recordSince("potion-share-hp", startedAt);

        startedAt = AgentPerformanceMonitor.start();
        requestLowPotShare(entry, bot, pots[1], false, false);
        AgentPerformanceMonitor.recordSince("potion-share-mp", startedAt);

        if (!AgentBotModeStateRuntime.grinding(entry)) {
            return;
        }
        startedAt = AgentPerformanceMonitor.start();
        if (pots[0] < BotManager.cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.4f) {
            BotManager.getInstance().issueFollowOwner(entry);
            AgentBotPotionRuntime.sayMapNow(bot, AgentDialogueCatalog.potLowReturnReply());
            bot.changeFaceExpression(AgentEmote.GLARE.getValue());
        }
        AgentPerformanceMonitor.recordSince("potion-grind-stop", startedAt);
    }

    public static void checkPotShareOnModeStart(BotEntry entry, Character bot) {
        AgentBotPotionStateRuntime.clearAllPotShareRequests(entry);
        AgentAmmoService.checkAmmoShareOnModeStart(entry, bot);
        requestLowPotShares(entry, bot, false);
    }

    public static boolean requestLowSuppliesFromOwnerAsk(BotEntry entry, Character bot) {
        boolean requestedPots = requestLowPotShares(entry, bot, true);
        boolean requestedAmmo = AgentAmmoService.requestLowAmmoShare(entry, bot, true);
        return requestedPots || requestedAmmo;
    }

    private static boolean requestLowPotShares(BotEntry entry, Character bot, boolean bypassShareLimits) {
        return requestLowPotShares(entry, bot, countPotions(bot), bypassShareLimits);
    }

    private static boolean requestLowPotShares(BotEntry entry, Character bot, int[] pots, boolean bypassShareLimits) {
        boolean requestedHp = requestLowPotShare(entry, bot, pots[0], true, bypassShareLimits);
        boolean requestedMp = requestLowPotShare(entry, bot, pots[1], false, bypassShareLimits);
        return requestedHp || requestedMp;
    }

    private static boolean requestLowPotShare(BotEntry entry,
                                              Character bot,
                                              int count,
                                              boolean forHp,
                                              boolean bypassShareLimits) {
        if (count >= BotManager.cfg.POT_LOW_WARN) {
            AgentBotPotionStateRuntime.clearPotShareRequested(entry, forHp);
            return false;
        }

        boolean alreadyRequested = AgentBotPotionStateRuntime.potShareRequested(entry, forHp);
        if ((alreadyRequested && !bypassShareLimits)
                || !requestPotShare(entry, bot, forHp, bypassShareLimits)) {
            return false;
        }
        AgentBotPotionStateRuntime.setPotShareRequested(entry, forHp, true);
        return true;
    }

    public static void tickPassiveRecovery(BotEntry entry, Character bot) {
        boolean hpFull = bot.getHp() >= bot.getCurrentMaxHp();
        boolean mpFull = bot.getMp() >= bot.getCurrentMaxMp();
        if (hpFull && mpFull) {
            AgentBotPotionStateRuntime.clearMpRecoveryTimer(entry);
            return;
        }
        if (AgentBotPotionStateRuntime.hasMpRecoveryDelay(entry)) {
            AgentBotPotionStateRuntime.tickMpRecoveryDelay(entry, BotMovementManager::tickDown);
            return;
        }

        AgentBotPotionStateRuntime.setMpRecoveryTimerMs(
                entry,
                BotMovementManager.delayAfterCurrentTick(BotManager.cfg.MP_RECOVERY_INTERVAL_MS));

        int hpRecovery = hpFull ? 0 : calculatePassiveHpRecovery(entry, bot);
        int mpRecovery = mpFull ? 0 : calculatePassiveMpRecovery(entry, bot);
        if (hpRecovery <= 0 && mpRecovery <= 0) {
            return;
        }

        bot.addMPHP(hpRecovery, mpRecovery);
    }

    public static boolean requestPotShare(BotEntry entry, Character bot, boolean forHp) {
        return requestPotShare(entry, bot, forHp, false);
    }

    public static boolean requestPotShare(BotEntry entry, Character bot, boolean forHp, boolean bypassShareLimits) {
        long startedAt = AgentPerformanceMonitor.start();
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null || bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)) {
            AgentPerformanceMonitor.recordSince("potion-request", startedAt);
            return false;
        }

        long now = System.currentTimeMillis();
        Map<Integer, Long> categoryBackoff = forHp ? potShareHpBackoffUntil : potShareMpBackoffUntil;
        if (!bypassShareLimits) {
            if (now < categoryBackoff.getOrDefault(owner.getId(), 0L)) {
                AgentPerformanceMonitor.recordSince("potion-request", startedAt);
                return false;
            }
            if (now < potShareCooldownUntil.getOrDefault(owner.getId(), 0L)) {
                AgentPerformanceMonitor.recordSince("potion-request", startedAt);
                return false;
            }
            potShareCooldownUntil.put(owner.getId(), now + 30_000L);
        }

        AgentBotPotionRuntime.sayMapNow(bot, BotManager.randomReply(
                forHp ? AgentDialogueCatalog.potRequestHpReplies() : AgentDialogueCatalog.potRequestMpReplies()));

        AgentBotPotionDonorPlan plan = selectPotDonor(owner, bot, entry, forHp);
        if (plan == null) {
            if (!bypassShareLimits) {
                categoryBackoff.put(owner.getId(), now + 10 * 60_000L);
            }
            AgentPerformanceMonitor.recordSince("potion-request", startedAt);
            return true;
        }

        if (!plan.qualifies()) {
            if (!bypassShareLimits) {
                categoryBackoff.put(owner.getId(), now + 10 * 60_000L);
            }
            String ownerName = owner.getName();
            AgentBotPotionRuntime.afterRandomDelay(4000, 6000, () ->
                    AgentBotPotionRuntime.sayMapNow(
                            AgentBotRuntimeIdentityRuntime.bot(plan.entry()),
                            AgentDialogueCatalog.formatPotDonorLowReply(
                                    BotManager.randomReply(AgentDialogueCatalog.potDonorLowTemplates()),
                                    ownerName)));
        } else {
            schedulePotShare(plan, bot, forHp, AgentBotPotionRuntime.randomDelayMs(2000, 3000));
        }
        AgentPerformanceMonitor.recordSince("potion-request", startedAt);
        return true;
    }

    public enum OwnerPotShareResult {
        OFFERED,
        NO_DONOR,
        BLOCKED
    }

    public static OwnerPotShareResult offerPotShareToOwner(BotEntry entry, boolean forHp) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null || owner.getTrade() != null) {
            return OwnerPotShareResult.BLOCKED;
        }

        AgentBotPotionDonorPlan plan = selectPotDonor(owner, owner, null, forHp);
        if (plan == null || !plan.qualifies()) {
            return OwnerPotShareResult.NO_DONOR;
        }

        schedulePotShare(plan, owner, forHp, AgentBotPotionRuntime.randomDelayMs(900, 1400));
        return OwnerPotShareResult.OFFERED;
    }

    private static AgentBotPotionDonorPlan selectPotDonor(Character owner, Character recipient, BotEntry excludedEntry, boolean forHp) {
        long startedAt = AgentPerformanceMonitor.start();
        BotEntry bestEntry = null;
        int bestCount = 0;
        for (BotEntry sibling : BotManager.getInstance().getBotEntries(owner.getId())) {
            Character siblingBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
            if (sibling == excludedEntry || siblingBot == null || siblingBot.getMapId() != recipient.getMapId()) {
                continue;
            }
            int[] pots = countPotions(siblingBot);
            int count = forHp ? pots[0] : pots[1];
            if (count > bestCount) {
                bestCount = count;
                bestEntry = sibling;
            }
        }
        AgentPerformanceMonitor.recordSince("potion-donor-select", startedAt);
        return bestEntry != null ? new AgentBotPotionDonorPlan(bestEntry, bestCount) : null;
    }

    private static void schedulePotShare(AgentBotPotionDonorPlan plan, Character recipient, boolean forHp, long initialDelayMs) {
        BotEntry donorEntry = plan.entry();
        Character donorBot = AgentBotRuntimeIdentityRuntime.bot(donorEntry);
        int maxQty = plan.donationQty();
        AgentBotPotionRuntime.afterDelay(initialDelayMs, () -> {
            if (donorBot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(donorEntry) || recipient.getTrade() != null) {
                return;
            }
            List<Item> items = collectPotShareItems(donorBot, forHp, maxQty);
            if (items.isEmpty()) {
                return;
            }
            AgentBotPotionRuntime.sayMapNow(donorBot, BotManager.randomReply(
                    forHp ? AgentDialogueCatalog.potOfferHpReplies() : AgentDialogueCatalog.potOfferMpReplies()));
            AgentBotPotionRuntime.afterRandomDelay(900, 1100, () ->
                    AgentSupplyShareTradeService.startPotShareTransfer(items, recipient, donorEntry, donorBot, maxQty));
        });
    }

    public static List<Item> collectPotShareItems(Character donorBot, boolean forHp, int maxQty) {
        return AgentPotionSharePolicy.collectShareItems(donorBot, forHp, maxQty,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentUseItemClassificationPolicy::itemEffect);
    }

    private static int calculatePassiveHpRecovery(BotEntry entry, Character bot) {
        return AgentPassiveRecoveryPolicy.hpRecovery(
                bot,
                BotManager.cfg.BASE_HP_RECOVERY,
                isStandingStillForRecovery(entry));
    }

    private static int calculatePassiveMpRecovery(BotEntry entry, Character bot) {
        return AgentPassiveRecoveryPolicy.mpRecovery(
                bot,
                BotManager.cfg.BASE_MP_RECOVERY,
                isStandingStillForRecovery(entry));
    }

    private static boolean isStandingStillForRecovery(BotEntry entry) {
        if (AgentBotMovementStateRuntime.inAir(entry) || AgentBotMovementStateRuntime.climbing(entry)) {
            return false;
        }
        return !AgentBotMovementStateRuntime.hasMoveDirection(entry)
                && BotPhysicsEngine.isStandingStance(BotPhysicsEngine.resolveStance(entry));
    }

}
