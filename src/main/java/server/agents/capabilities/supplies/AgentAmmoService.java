package server.agents.capabilities.supplies;

import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatConfig;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;
import server.ItemInformationProvider;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.capabilities.inventory.AgentInventoryAmmoPolicy;
import server.agents.capabilities.supplies.AgentAmmoSharePolicy;
import server.agents.capabilities.supplies.AgentAmmoSharePolicy.DonorScore;
import server.agents.capabilities.trade.AgentSupplyShareTradeService;
import server.agents.integration.AgentBotAmmoDonorPlan;
import server.agents.integration.AgentBotAmmoRuntime;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotPendingTradeStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentAmmoService {

    private static final Map<String, Long> ammoShareBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> ammoShareCooldownUntil = new ConcurrentHashMap<>();

    private AgentAmmoService() {}

    public static void tickAmmoShareCheck(BotEntry entry, Character bot) {
        requestLowAmmoShare(entry, bot, false);
    }

    public static boolean requestLowAmmoShare(BotEntry entry, Character bot, boolean bypassShareLimits) {
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!canRequestShare(weaponType)) {
            AgentBotAmmoStateRuntime.clearAmmoShareRequested(entry);
            return false;
        }

        int ammo = AgentCombatAmmoCounter.countAmmo(bot, weaponType);
        if (ammo >= AgentCombatConfig.cfg.AMMO_LOW_WARN) {
            AgentBotAmmoStateRuntime.clearAmmoShareRequested(entry);
            return false;
        }

        if ((!AgentBotAmmoStateRuntime.ammoShareRequested(entry) || bypassShareLimits)
                && requestAmmoShare(entry, bot, weaponType, ammo, bypassShareLimits)) {
            AgentBotAmmoStateRuntime.setAmmoShareRequested(entry, true);
            return true;
        }
        return false;
    }

    public static void checkAmmoShareOnModeStart(BotEntry entry, Character bot) {
        AgentBotAmmoStateRuntime.clearAmmoShareRequested(entry);
        requestLowAmmoShare(entry, bot, false);
    }

    public static boolean requestAmmoShare(BotEntry entry, Character bot, WeaponType weaponType, int currentAmmo) {
        return requestAmmoShare(entry, bot, weaponType, currentAmmo, false);
    }

    public static boolean requestAmmoShare(BotEntry entry, Character bot, WeaponType weaponType, int currentAmmo, boolean bypassShareLimits) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null || bot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(entry)) {
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

        AgentBotAmmoRuntime.sayMapNow(bot, BotManager.randomReply(
                weaponType == WeaponType.BOW
                        ? AgentDialogueCatalog.arrowRequestReplies()
                        : AgentDialogueCatalog.boltRequestReplies()));

        AgentBotAmmoDonorPlan plan = selectAmmoDonor(entry, bot, weaponType);
        if (plan == null) {
            if (!bypassShareLimits) {
                ammoShareBackoffUntil.put(backoffKey, now + 10 * 60_000L);
            }
            return true;
        }

        scheduleAmmoShare(plan, bot, weaponType, AgentBotAmmoRuntime.randomDelayMs(2000, 3000));
        return true;
    }

    public enum OwnerAmmoShareResult {
        OFFERED,
        NO_DONOR,
        BLOCKED
    }

    public static OwnerAmmoShareResult offerAmmoShareToOwner(BotEntry entry, WeaponType weaponType) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(entry);
        if (owner == null || owner.getTrade() != null || !canRequestShare(weaponType)) {
            return OwnerAmmoShareResult.BLOCKED;
        }

        AgentBotAmmoDonorPlan plan = selectAmmoDonorForRecipient(owner, weaponType);
        if (plan == null) {
            return OwnerAmmoShareResult.NO_DONOR;
        }

        scheduleAmmoShare(plan, owner, weaponType, AgentBotAmmoRuntime.randomDelayMs(900, 1400));
        return OwnerAmmoShareResult.OFFERED;
    }

    public static AgentBotAmmoDonorPlan selectAmmoDonor(BotEntry needyEntry, Character needyBot, WeaponType needyWeaponType) {
        Character owner = AgentBotRuntimeIdentityRuntime.owner(needyEntry);
        if (owner == null || !canRequestShare(needyWeaponType)) {
            return null;
        }
        return selectAmmoDonor(owner.getId(), needyBot.getMapId(), needyEntry, needyWeaponType);
    }

    public static AgentBotAmmoDonorPlan selectAmmoDonorForRecipient(Character recipient, WeaponType needyWeaponType) {
        if (recipient == null || !canRequestShare(needyWeaponType)) {
            return null;
        }
        return selectAmmoDonor(recipient.getId(), recipient.getMapId(), null, needyWeaponType);
    }

    private static AgentBotAmmoDonorPlan selectAmmoDonor(int ownerId, int mapId, BotEntry excludedEntry, WeaponType needyWeaponType) {
        AgentBotAmmoDonorPlan best = null;
        for (BotEntry sibling : BotManager.getInstance().getBotEntries(ownerId)) {
            Character donorBot = AgentBotRuntimeIdentityRuntime.bot(sibling);
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
            AgentBotAmmoDonorPlan candidate = new AgentBotAmmoDonorPlan(sibling, count, donorNeedsSameAmmo, donationQty);
            if (isBetterDonor(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static void scheduleAmmoShare(AgentBotAmmoDonorPlan plan, Character recipient, WeaponType weaponType, long initialDelayMs) {
        BotEntry donorEntry = plan.entry();
        Character donorBot = AgentBotRuntimeIdentityRuntime.bot(donorEntry);
        int maxQty = plan.donationQty();
        AgentBotAmmoRuntime.afterDelay(initialDelayMs, () -> {
            if (donorBot.getTrade() != null || AgentBotPendingTradeStateRuntime.hasActiveSequence(donorEntry) || recipient.getTrade() != null) {
                return;
            }
            List<Item> items = collectAmmoShareItems(donorBot, weaponType, maxQty);
            if (items.isEmpty()) {
                return;
            }
            AgentBotAmmoRuntime.sayMapNow(donorBot, BotManager.randomReply(AgentDialogueCatalog.ammoOfferReplies()));
            AgentBotAmmoRuntime.afterRandomDelay(900, 1100, () ->
                    AgentSupplyShareTradeService.startAmmoShareTransfer(items, recipient, donorEntry, donorBot, maxQty));
        });
    }

    public static List<Item> collectAmmoShareItems(Character donorBot, WeaponType needyWeaponType, int maxQty) {
        return AgentInventoryAmmoPolicy.collectShareItems(donorBot, needyWeaponType, maxQty,
                ItemInformationProvider.getInstance()::getWatkForProjectile);
    }

    private static boolean isBetterDonor(AgentBotAmmoDonorPlan candidate, AgentBotAmmoDonorPlan best) {
        return AgentAmmoSharePolicy.isBetterDonor(
                new DonorScore(candidate.donorNeedsSameAmmo(), candidate.matchingAmmoCount()),
                best == null ? null : new DonorScore(best.donorNeedsSameAmmo(), best.matchingAmmoCount()));
    }

    private static boolean canRequestShare(WeaponType weaponType) {
        return AgentAmmoSharePolicy.canRequestShare(weaponType);
    }
}
