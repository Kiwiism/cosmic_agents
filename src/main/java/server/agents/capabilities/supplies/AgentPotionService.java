package server.agents.capabilities.supplies;

import server.agents.capabilities.trade.AgentPendingTradeStateRuntime;

import server.agents.capabilities.movement.AgentMovementTimers;

import server.agents.capabilities.dialogue.AgentEmote;
import server.agents.capabilities.combat.AgentCombatConfig;

import server.agents.monitoring.AgentPerformanceMonitor;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.keybind.KeyBinding;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.dialogue.AgentSupplyDialogueReporter;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.movement.AgentMovementPoseService;
import server.agents.capabilities.trade.AgentSupplyShareTradeService;
import server.agents.capabilities.supplies.AgentAutopotPolicy.AutopotChoice;
import server.agents.capabilities.supplies.AgentAutopotPolicy.PotionRanking;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.capabilities.movement.AgentMovementCommandRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.supplies.AgentPotionRuntime;
import server.agents.capabilities.supplies.AgentCombatAmmoCheckRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentRelationshipRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentSessionLifecycleRuntime;
import server.agents.runtime.AgentRuntimeConfig;
import server.agents.runtime.AgentRuntimeEntry;
import server.StatEffect;
import server.agents.coordination.AgentCoordinationRuntime;
import server.agents.coordination.AgentSupplyNeedMessage;
import server.agents.capabilities.contracts.AgentResourceCategory;
import server.agents.capabilities.contracts.AgentSupplyNeed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class AgentPotionService {
    // ownerCharId -> shared HP/MP 30 s request cooldown
    private static final Map<Long, Long> potShareCooldownUntil = new ConcurrentHashMap<>();
    // ownerCharId -> category-specific 10 min failed-request backoff
    private static final Map<Long, Long> potShareHpBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Long, Long> potShareMpBackoffUntil = new ConcurrentHashMap<>();

    private AgentPotionService() {
    }

    public static void clearLeaderRuntimeState(int leaderId) {
        potShareCooldownUntil.remove((long) leaderId);
        potShareHpBackoffUntil.remove((long) leaderId);
        potShareMpBackoffUntil.remove((long) leaderId);
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
            bot.setAutopotHpAlert(AgentRuntimeConfig.cfg.AUTOPOT_HP_THRESH);
        } else {
            bot.getKeymap().remove(91);
            bot.setAutopotHpAlert(0f);
        }

        if (choice.mpItemId() > 0) {
            bot.changeKeybinding(92, new KeyBinding(7, choice.mpItemId()));
            bot.setAutopotMpAlert(AgentRuntimeConfig.cfg.AUTOPOT_MP_THRESH);
        } else {
            bot.getKeymap().remove(92);
            bot.setAutopotMpAlert(0f);
        }
    }

    /** Owner-facing diagnostic: counts vs. selected items for each slot. */
    public static String autopotDebugReport(Character bot, InventoryGateway inventory) {
        int[] cnt = countPotions(bot);
        AutopotChoice choice = computeAutopotChoice(bot);
        return AgentSupplyDialogueReporter.autopotDebugReport(
                cnt[0],
                cnt[1],
                describeChoice(choice.hpItemId(), choice.hpRank(), inventory),
                describeChoice(choice.mpItemId(), choice.mpRank(), inventory));
    }

    private static String describeChoice(int itemId, PotionRanking rank, InventoryGateway inventory) {
        if (itemId <= 0 || rank == null) {
            return "none";
        }
        String name = inventory.getItemName(itemId);
        if (name == null) name = String.valueOf(itemId);
        return AgentSupplyDialogueReporter.autopotChoice(name, itemId, rank.tier().name(), rank.value());
    }

    public static String grindStartMessage(Character bot) {
        int[] pots = countPotions(bot);
        return AgentSupplyDialogueReporter.grindStartMessage(
                AgentDialogueSelector.randomReply(AgentDialogueCatalog.grindReplies()),
                pots[0],
                pots[1],
                AgentRuntimeConfig.cfg.POT_LOW_WARN);
    }

    public static void tickPotionCheck(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        if (AgentPotionStateRuntime.hasPotCheckDelay(entry)) {
            AgentPotionStateRuntime.tickPotCheckDelay(entry, AgentMovementTimers::tickDown);
            return;
        }
        AgentPotionStateRuntime.setPotCheckTimerMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(AgentRuntimeConfig.cfg.POT_CHECK_INTERVAL_MS));

        long startedAt = AgentPerformanceMonitor.start();
        setupAutopotForBot(bot);
        AgentPerformanceMonitor.recordSince("potion-autopot", startedAt);

        startedAt = AgentPerformanceMonitor.start();
        AgentCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot,
                AgentCombatConfig.cfg.AMMO_LOW_WARN, AgentRuntimeConfig.cfg.POT_LOW_WARN);
        AgentPerformanceMonitor.recordSince("potion-ammo-check", startedAt);

        if (!AgentModeStateRuntime.grinding(entry) && !AgentModeStateRuntime.following(entry)) {
            return;
        }
        startedAt = AgentPerformanceMonitor.start();
        AgentAmmoService.tickAmmoShareCheck(entry, bot, inventory);
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

        if (!AgentModeStateRuntime.grinding(entry)) {
            return;
        }
        startedAt = AgentPerformanceMonitor.start();
        if (pots[0] < AgentRuntimeConfig.cfg.POT_STOP && bot.getHp() < bot.getMaxHp() * 0.4f) {
            if (AgentRelationshipRuntime.followTarget(entry) != null) {
                AgentMovementCommandRuntime.followConfiguredTarget(entry);
            } else {
                AgentMovementCommandRuntime.stop(entry);
            }
            AgentPotionRuntime.sayMapNow(bot, AgentDialogueCatalog.potLowReturnReply());
            bot.changeFaceExpression(AgentEmote.GLARE.getValue());
        }
        AgentPerformanceMonitor.recordSince("potion-grind-stop", startedAt);
    }

    public static void checkPotShareOnModeStart(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        AgentPotionStateRuntime.clearAllPotShareRequests(entry);
        AgentAmmoService.checkAmmoShareOnModeStart(entry, bot, inventory);
        requestLowPotShares(entry, bot, false);
    }

    public static boolean requestLowSuppliesFromOwnerAsk(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        boolean requestedPots = requestLowPotShares(entry, bot, true);
        boolean requestedAmmo = AgentAmmoService.requestLowAmmoShare(entry, bot, true, inventory);
        return requestedPots || requestedAmmo;
    }

    private static boolean requestLowPotShares(AgentRuntimeEntry entry, Character bot, boolean bypassShareLimits) {
        return requestLowPotShares(entry, bot, countPotions(bot), bypassShareLimits);
    }

    private static boolean requestLowPotShares(AgentRuntimeEntry entry, Character bot, int[] pots, boolean bypassShareLimits) {
        boolean requestedHp = requestLowPotShare(entry, bot, pots[0], true, bypassShareLimits);
        boolean requestedMp = requestLowPotShare(entry, bot, pots[1], false, bypassShareLimits);
        return requestedHp || requestedMp;
    }

    private static boolean requestLowPotShare(AgentRuntimeEntry entry,
                                              Character bot,
                                              int count,
                                              boolean forHp,
                                              boolean bypassShareLimits) {
        long observedAtMs = System.currentTimeMillis();
        AgentResourceCategory category = forHp
                ? AgentResourceCategory.HP_POTION : AgentResourceCategory.MP_POTION;
        AgentSupplyNeed previous = entry.capabilityStates()
                .require(AgentResourcePlanningState.STATE_KEY).need(category);
        AgentSupplyNeed observed = AgentResourcePlanningRuntime.observe(
                entry,
                category,
                count,
                AgentRuntimeConfig.cfg.POT_LOW_WARN,
                AgentRuntimeConfig.cfg.POT_STOP,
                Math.max(AgentRuntimeConfig.cfg.POT_LOW_WARN * 2, Math.max(0, count)),
                observedAtMs);
        boolean thresholdEventPublished = previous == null
                || previous.urgency() != observed.urgency();
        if (count >= AgentRuntimeConfig.cfg.POT_LOW_WARN) {
            AgentPotionStateRuntime.clearPotShareRequested(entry, forHp);
            return false;
        }

        boolean alreadyRequested = AgentPotionStateRuntime.potShareRequested(entry, forHp);
        if ((alreadyRequested && !bypassShareLimits)
                || !requestPotShare(entry, bot, forHp, bypassShareLimits, thresholdEventPublished)) {
            return false;
        }
        AgentPotionStateRuntime.setPotShareRequested(entry, forHp, true);
        return true;
    }

    public static void tickPassiveRecovery(AgentRuntimeEntry entry, Character bot) {
        boolean hpFull = bot.getHp() >= bot.getCurrentMaxHp();
        boolean mpFull = bot.getMp() >= bot.getCurrentMaxMp();
        if (hpFull && mpFull) {
            AgentPotionStateRuntime.clearMpRecoveryTimer(entry);
            return;
        }
        if (AgentPotionStateRuntime.hasMpRecoveryDelay(entry)) {
            AgentPotionStateRuntime.tickMpRecoveryDelay(entry, AgentMovementTimers::tickDown);
            return;
        }

        AgentPotionStateRuntime.setMpRecoveryTimerMs(
                entry,
                AgentMovementTimers.delayAfterCurrentTick(AgentRuntimeConfig.cfg.MP_RECOVERY_INTERVAL_MS));

        int hpRecovery = hpFull ? 0 : calculatePassiveHpRecovery(entry, bot);
        int mpRecovery = mpFull ? 0 : calculatePassiveMpRecovery(entry, bot);
        if (hpRecovery <= 0 && mpRecovery <= 0) {
            return;
        }

        bot.addMPHP(hpRecovery, mpRecovery);
    }

    public static boolean requestPotShare(AgentRuntimeEntry entry, Character bot, boolean forHp) {
        return requestPotShare(entry, bot, forHp, false);
    }

    public static boolean requestPotShare(AgentRuntimeEntry entry, Character bot, boolean forHp, boolean bypassShareLimits) {
        return requestPotShare(entry, bot, forHp, bypassShareLimits, false);
    }

    private static boolean requestPotShare(AgentRuntimeEntry entry,
                                           Character bot,
                                           boolean forHp,
                                           boolean bypassShareLimits,
                                           boolean thresholdEventPublished) {
        long startedAt = AgentPerformanceMonitor.start();
        if (bot.getTrade() != null || AgentPendingTradeStateRuntime.hasActiveSequence(entry)) {
            AgentPerformanceMonitor.recordSince("potion-request", startedAt);
            return false;
        }

        long now = System.currentTimeMillis();
        long cohortId = AgentRelationshipRuntime.cohortId(entry);
        Map<Long, Long> categoryBackoff = forHp ? potShareHpBackoffUntil : potShareMpBackoffUntil;
        if (!bypassShareLimits) {
            if (now < categoryBackoff.getOrDefault(cohortId, 0L)) {
                AgentPerformanceMonitor.recordSince("potion-request", startedAt);
                return false;
            }
            if (now < potShareCooldownUntil.getOrDefault(cohortId, 0L)) {
                AgentPerformanceMonitor.recordSince("potion-request", startedAt);
                return false;
            }
            potShareCooldownUntil.put(cohortId, now + 30_000L);
        }

        int[] currentPots = bot.getInventory(InventoryType.USE) == null
                ? new int[]{-1, -1}
                : countPotions(bot);
        int currentCount = forHp ? currentPots[0] : currentPots[1];
        if (bypassShareLimits && !thresholdEventPublished) {
            AgentCoordinationRuntime.publish(new AgentSupplyNeedMessage(
                    bot.getId(),
                    cohortId,
                    bot.getMapId(),
                    forHp ? AgentSupplyNeedMessage.SupplyKind.HP_POTION
                            : AgentSupplyNeedMessage.SupplyKind.MP_POTION,
                    currentCount,
                    "",
                    now));
            AgentPotionRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(
                    forHp ? AgentDialogueCatalog.potRequestHpReplies()
                            : AgentDialogueCatalog.potRequestMpReplies()));
        }

        AgentPotionDonorPlan<AgentRuntimeEntry> plan = selectPotDonor(entry, bot, entry, forHp);
        if (plan == null) {
            if (!bypassShareLimits) {
                categoryBackoff.put(cohortId, now + 10 * 60_000L);
            }
            AgentPerformanceMonitor.recordSince("potion-request", startedAt);
            return true;
        }

        if (!plan.qualifies()) {
            if (!bypassShareLimits) {
                categoryBackoff.put(cohortId, now + 10 * 60_000L);
            }
            String ownerName = bot.getName();
            AgentPotionRuntime.afterRandomDelay(entry, 4000, 6000, () ->
                    AgentPotionRuntime.sayMapNow(
                            AgentRuntimeIdentityRuntime.bot(plan.entry()),
                            AgentDialogueCatalog.formatPotDonorLowReply(
                                    AgentDialogueSelector.randomReply(AgentDialogueCatalog.potDonorLowTemplates()),
                                    ownerName)));
        } else {
            schedulePotShare(plan, bot, forHp, AgentPotionRuntime.randomDelayMs(2000, 3000));
        }
        AgentPerformanceMonitor.recordSince("potion-request", startedAt);
        return true;
    }

    public enum OwnerPotShareResult {
        OFFERED,
        NO_DONOR,
        BLOCKED
    }

    public static OwnerPotShareResult offerPotShareToOwner(AgentRuntimeEntry entry, boolean forHp) {
        Character owner = AgentRelationshipRuntime.interactionTarget(entry);
        if (owner == null || owner.getTrade() != null) {
            return OwnerPotShareResult.BLOCKED;
        }

        AgentPotionDonorPlan<AgentRuntimeEntry> plan = selectPotDonor(entry, owner, null, forHp);
        if (plan == null || !plan.qualifies()) {
            return OwnerPotShareResult.NO_DONOR;
        }

        schedulePotShare(plan, owner, forHp, AgentPotionRuntime.randomDelayMs(900, 1400));
        return OwnerPotShareResult.OFFERED;
    }

    private static AgentPotionDonorPlan<AgentRuntimeEntry> selectPotDonor(AgentRuntimeEntry requesterEntry, Character recipient, AgentRuntimeEntry excludedEntry, boolean forHp) {
        long startedAt = AgentPerformanceMonitor.start();
        AgentRuntimeEntry bestEntry = null;
        int bestCount = 0;
        for (AgentRuntimeEntry sibling : AgentSessionLifecycleRuntime.getCohortEntries(requesterEntry)) {
            Character siblingBot = AgentRuntimeIdentityRuntime.bot(sibling);
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
        return bestEntry != null ? new AgentPotionDonorPlan<>(bestEntry, bestCount) : null;
    }

    private static void schedulePotShare(AgentPotionDonorPlan<AgentRuntimeEntry> plan, Character recipient, boolean forHp, long initialDelayMs) {
        AgentRuntimeEntry donorEntry = plan.entry();
        Character donorBot = AgentRuntimeIdentityRuntime.bot(donorEntry);
        int maxQty = plan.donationQty();
        AgentPotionRuntime.afterDelay(donorEntry, initialDelayMs, () -> {
            if (donorBot.getTrade() != null || AgentPendingTradeStateRuntime.hasActiveSequence(donorEntry) || recipient.getTrade() != null) {
                return;
            }
            List<Item> items = collectPotShareItems(donorBot, forHp, maxQty);
            if (items.isEmpty()) {
                return;
            }
            AgentPotionRuntime.sayMapNow(donorBot, AgentDialogueSelector.randomReply(
                    forHp ? AgentDialogueCatalog.potOfferHpReplies() : AgentDialogueCatalog.potOfferMpReplies()));
            AgentPotionRuntime.afterRandomDelay(donorEntry, 900, 1100, () ->
                    AgentSupplyShareTradeService.startPotShareTransfer(items, recipient, donorEntry, donorBot, maxQty));
        });
    }

    public static List<Item> collectPotShareItems(Character donorBot, boolean forHp, int maxQty) {
        return AgentPotionSharePolicy.collectShareItems(donorBot, forHp, maxQty,
                AgentUseItemClassificationPolicy::isRecoveryPotion,
                AgentUseItemClassificationPolicy::itemEffect);
    }

    private static int calculatePassiveHpRecovery(AgentRuntimeEntry entry, Character bot) {
        return AgentPassiveRecoveryPolicy.hpRecovery(
                bot,
                AgentRuntimeConfig.cfg.BASE_HP_RECOVERY,
                isStandingStillForRecovery(entry));
    }

    private static int calculatePassiveMpRecovery(AgentRuntimeEntry entry, Character bot) {
        return AgentPassiveRecoveryPolicy.mpRecovery(
                bot,
                AgentRuntimeConfig.cfg.BASE_MP_RECOVERY,
                isStandingStillForRecovery(entry));
    }

    private static boolean isStandingStillForRecovery(AgentRuntimeEntry entry) {
        if (AgentMovementStateRuntime.inAir(entry) || AgentMovementStateRuntime.climbing(entry)) {
            return false;
        }
        return !AgentMovementStateRuntime.hasMoveDirection(entry)
                && AgentMovementPoseService.isStandingResolvedStance(entry);
    }

}
