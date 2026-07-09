package server.agents.capabilities.supplies;

import server.agents.capabilities.trade.AgentPendingTradeStateRuntime;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.dialogue.AgentDialogueSelector;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.supplies.AgentAmmoSharePolicy.DonorScore;
import server.agents.capabilities.trade.AgentSupplyShareTradeService;
import server.agents.capabilities.supplies.AgentAmmoRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.runtime.AgentSessionLifecycleRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentAmmoService {

    private static final Map<String, Long> ammoShareBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> ammoShareCooldownUntil = new ConcurrentHashMap<>();

    private AgentAmmoService() {}

    public static void tickAmmoShareCheck(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        requestLowAmmoShare(entry, bot, false, inventory);
    }

    public static boolean requestLowAmmoShare(AgentRuntimeEntry entry, Character bot, boolean bypassShareLimits, InventoryGateway inventory) {
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!canRequestShare(weaponType)) {
            AgentAmmoStateRuntime.clearAmmoShareRequested(entry);
            return false;
        }

        int ammo = AgentCombatAmmoCounter.countAmmo(bot, weaponType);
        if (ammo >= AgentCombatConfig.cfg.AMMO_LOW_WARN) {
            AgentAmmoStateRuntime.clearAmmoShareRequested(entry);
            return false;
        }

        if ((!AgentAmmoStateRuntime.ammoShareRequested(entry) || bypassShareLimits)
                && requestAmmoShare(entry, bot, weaponType, ammo, bypassShareLimits, inventory)) {
            AgentAmmoStateRuntime.setAmmoShareRequested(entry, true);
            return true;
        }
        return false;
    }

    public static void checkAmmoShareOnModeStart(AgentRuntimeEntry entry, Character bot, InventoryGateway inventory) {
        AgentAmmoStateRuntime.clearAmmoShareRequested(entry);
        requestLowAmmoShare(entry, bot, false, inventory);
    }

    public static boolean requestAmmoShare(AgentRuntimeEntry entry, Character bot, WeaponType weaponType, int currentAmmo, InventoryGateway inventory) {
        return requestAmmoShare(entry, bot, weaponType, currentAmmo, false, inventory);
    }

    public static boolean requestAmmoShare(AgentRuntimeEntry entry,
                                           Character bot,
                                           WeaponType weaponType,
                                           int currentAmmo,
                                           boolean bypassShareLimits,
                                           InventoryGateway inventory) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null || bot.getTrade() != null || AgentPendingTradeStateRuntime.hasActiveSequence(entry)) {
            return false;
        }
        if (!canRequestShare(weaponType) || currentAmmo >= AgentCombatConfig.cfg.AMMO_LOW_WARN) {
            return false;
        }

        long now = System.currentTimeMillis();
        String backoffKey = owner.getId() + ":" + weaponType.name();
        if (!bypassShareLimits) {
            if (now < ammoShareBackoffUntil.getOrDefault(backoffKey, 0L)) {
                return false;
            }
            if (now < ammoShareCooldownUntil.getOrDefault(owner.getId(), 0L)) {
                return false;
            }
            ammoShareCooldownUntil.put(owner.getId(), now + 30_000L);
        }

        AgentAmmoRuntime.sayMapNow(bot, AgentDialogueSelector.randomReply(
                weaponType == WeaponType.BOW
                        ? AgentDialogueCatalog.arrowRequestReplies()
                        : AgentDialogueCatalog.boltRequestReplies()));

        AgentAmmoDonorPlan<AgentRuntimeEntry> plan = selectAmmoDonor(entry, bot, weaponType);
        if (plan == null) {
            if (!bypassShareLimits) {
                ammoShareBackoffUntil.put(backoffKey, now + 10 * 60_000L);
            }
            return true;
        }

        scheduleAmmoShare(plan, bot, weaponType, AgentAmmoRuntime.randomDelayMs(2000, 3000), inventory);
        return true;
    }

    public enum OwnerAmmoShareResult {
        OFFERED,
        NO_DONOR,
        BLOCKED
    }

    public static OwnerAmmoShareResult offerAmmoShareToOwner(AgentRuntimeEntry entry, WeaponType weaponType, InventoryGateway inventory) {
        Character owner = AgentRuntimeIdentityRuntime.owner(entry);
        if (owner == null || owner.getTrade() != null || !canRequestShare(weaponType)) {
            return OwnerAmmoShareResult.BLOCKED;
        }

        AgentAmmoDonorPlan<AgentRuntimeEntry> plan = selectAmmoDonorForRecipient(owner, weaponType);
        if (plan == null) {
            return OwnerAmmoShareResult.NO_DONOR;
        }

        scheduleAmmoShare(plan, owner, weaponType, AgentAmmoRuntime.randomDelayMs(900, 1400), inventory);
        return OwnerAmmoShareResult.OFFERED;
    }

    public static AgentAmmoDonorPlan<AgentRuntimeEntry> selectAmmoDonor(AgentRuntimeEntry needyEntry, Character needyBot, WeaponType needyWeaponType) {
        Character owner = AgentRuntimeIdentityRuntime.owner(needyEntry);
        if (owner == null || !canRequestShare(needyWeaponType)) {
            return null;
        }
        return selectAmmoDonor(owner.getId(), needyBot.getMapId(), needyEntry, needyWeaponType);
    }

    public static AgentAmmoDonorPlan<AgentRuntimeEntry> selectAmmoDonorForRecipient(Character recipient, WeaponType needyWeaponType) {
        if (recipient == null || !canRequestShare(needyWeaponType)) {
            return null;
        }
        return selectAmmoDonor(recipient.getId(), recipient.getMapId(), null, needyWeaponType);
    }

    private static AgentAmmoDonorPlan<AgentRuntimeEntry> selectAmmoDonor(int ownerId, int mapId, AgentRuntimeEntry excludedEntry, WeaponType needyWeaponType) {
        AgentAmmoDonorPlan<AgentRuntimeEntry> best = null;
        for (AgentRuntimeEntry sibling : AgentSessionLifecycleRuntime.getBotEntries(ownerId)) {
            Character donorBot = AgentRuntimeIdentityRuntime.bot(sibling);
            if (sibling == excludedEntry || donorBot == null || donorBot.getMapId() != mapId) {
                continue;
            }
            int count = AgentCombatAmmoCounter.countAmmo(donorBot, needyWeaponType);
            if (count < AgentCombatConfig.cfg.AMMO_LOW_WARN) {
                continue;
            }
            WeaponType donorWeaponType = AgentAttackExecutionProvider.getEquippedWeaponType(donorBot);
            boolean donorNeedsSameAmmo = donorWeaponType == needyWeaponType;
            int donationQty = AgentAmmoSharePolicy.donationQuantity(
                    count,
                    AgentCombatConfig.cfg.AMMO_LOW_WARN,
                    donorNeedsSameAmmo);
            if (donationQty <= 0) {
                continue;
            }
            AgentAmmoDonorPlan<AgentRuntimeEntry> candidate = new AgentAmmoDonorPlan<>(sibling, count, donorNeedsSameAmmo, donationQty);
            if (isBetterDonor(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static void scheduleAmmoShare(AgentAmmoDonorPlan<AgentRuntimeEntry> plan,
                                          Character recipient,
                                          WeaponType weaponType,
                                          long initialDelayMs,
                                          InventoryGateway inventory) {
        AgentRuntimeEntry donorEntry = plan.entry();
        Character donorBot = AgentRuntimeIdentityRuntime.bot(donorEntry);
        int maxQty = plan.donationQty();
        AgentAmmoRuntime.afterDelay(initialDelayMs, () -> {
            if (donorBot.getTrade() != null || AgentPendingTradeStateRuntime.hasActiveSequence(donorEntry) || recipient.getTrade() != null) {
                return;
            }
            List<Item> items = collectAmmoShareItems(donorBot, weaponType, maxQty, inventory);
            if (items.isEmpty()) {
                return;
            }
            AgentAmmoRuntime.sayMapNow(donorBot, AgentDialogueSelector.randomReply(AgentDialogueCatalog.ammoOfferReplies()));
            AgentAmmoRuntime.afterRandomDelay(900, 1100, () ->
                    AgentSupplyShareTradeService.startAmmoShareTransfer(items, recipient, donorEntry, donorBot, maxQty));
        });
    }

    public static List<Item> collectAmmoShareItems(Character donorBot,
                                                   WeaponType needyWeaponType,
                                                   int maxQty,
                                                   InventoryGateway inventory) {
        return AgentInventoryAmmoPolicy.collectShareItems(donorBot, needyWeaponType, maxQty,
                inventory::getProjectileWeaponAttack);
    }

    private static boolean isBetterDonor(AgentAmmoDonorPlan<AgentRuntimeEntry> candidate, AgentAmmoDonorPlan<AgentRuntimeEntry> best) {
        return AgentAmmoSharePolicy.isBetterDonor(
                new DonorScore(candidate.donorNeedsSameAmmo(), candidate.matchingAmmoCount()),
                best == null ? null : new DonorScore(best.donorNeedsSameAmmo(), best.matchingAmmoCount()));
    }

    private static boolean canRequestShare(WeaponType weaponType) {
        return AgentAmmoSharePolicy.canRequestShare(weaponType);
    }
}
