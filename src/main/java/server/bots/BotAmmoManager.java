package server.bots;

import client.Character;
import client.inventory.Item;
import client.inventory.WeaponType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BotAmmoManager {
    private static final Map<String, Long> ammoShareBackoffUntil = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> ammoShareCooldownUntil = new ConcurrentHashMap<>();

    private static final List<String> ARROW_REQUEST_MSGS = List.of(
            "low on arrows, anyone have spare?",
            "need arrows soon, anyone got extras?",
            "running low on arrows, can someone share?");
    private static final List<String> BOLT_REQUEST_MSGS = List.of(
            "low on bolts, anyone have spare?",
            "need crossbow bolts soon, anyone got extras?",
            "running low on bolts, can someone share?");
    private static final List<String> AMMO_OFFER_MSGS = List.of(
            "i have spare ammo, inv u",
            "got some ammo for you, trading",
            "i can spare ammo, one sec");

    private BotAmmoManager() {}

    static void tickAmmoShareCheck(BotEntry entry, Character bot) {
        WeaponType weaponType = BotAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!canRequestShare(weaponType)) {
            entry.ammoShareRequested = false;
            return;
        }

        int ammo = BotCombatManager.countAmmo(bot, weaponType);
        if (ammo >= BotCombatManager.cfg.AMMO_LOW_WARN) {
            entry.ammoShareRequested = false;
            return;
        }

        if (!entry.ammoShareRequested && requestAmmoShare(entry, bot, weaponType, ammo)) {
            entry.ammoShareRequested = true;
        }
    }

    static void checkAmmoShareOnModeStart(BotEntry entry, Character bot) {
        entry.ammoShareRequested = false;
        tickAmmoShareCheck(entry, bot);
    }

    static boolean requestAmmoShare(BotEntry entry, Character bot, WeaponType weaponType, int currentAmmo) {
        Character owner = entry.owner;
        if (owner == null || bot.getTrade() != null || entry.pendingTradeCategory != null) {
            return false;
        }
        if (!canRequestShare(weaponType) || currentAmmo >= BotCombatManager.cfg.AMMO_LOW_WARN) {
            return false;
        }

        long now = System.currentTimeMillis();
        String backoffKey = owner.getId() + ":" + weaponType.name();
        if (now < ammoShareBackoffUntil.getOrDefault(backoffKey, 0L)) {
            return false;
        }
        if (now < ammoShareCooldownUntil.getOrDefault(owner.getId(), 0L)) {
            return false;
        }
        ammoShareCooldownUntil.put(owner.getId(), now + 30_000L);

        BotManager.getInstance().botSay(bot, BotManager.randomReply(
                weaponType == WeaponType.BOW ? ARROW_REQUEST_MSGS : BOLT_REQUEST_MSGS));

        AmmoDonorPlan plan = selectAmmoDonor(entry, bot, weaponType);
        if (plan == null) {
            ammoShareBackoffUntil.put(backoffKey, now + 10 * 60_000L);
            return true;
        }

        BotEntry donorEntry = plan.entry();
        Character donorBot = donorEntry.bot;
        Character needyBot = bot;
        int maxQty = plan.donationQty();
        BotManager.after(BotManager.randMs(2000, 3000), () -> {
            if (donorBot.getTrade() != null || donorEntry.pendingTradeCategory != null) {
                return;
            }
            List<Item> items = BotInventoryManager.collectAmmoShareItems(donorBot, weaponType, maxQty);
            if (items.isEmpty()) {
                return;
            }
            BotManager.getInstance().botSay(donorBot, BotManager.randomReply(AMMO_OFFER_MSGS));
            BotManager.after(BotManager.randMs(900, 1100), () ->
                    BotInventoryManager.startAmmoShareTransfer(items, needyBot, donorEntry, donorBot, maxQty));
        });
        return true;
    }

    static AmmoDonorPlan selectAmmoDonor(BotEntry needyEntry, Character needyBot, WeaponType needyWeaponType) {
        Character owner = needyEntry.owner;
        if (owner == null || !canRequestShare(needyWeaponType)) {
            return null;
        }

        AmmoDonorPlan best = null;
        for (BotEntry sibling : BotManager.getInstance().getBotEntries(owner.getId())) {
            if (sibling == needyEntry || sibling.bot == null || sibling.bot.getMapId() != needyBot.getMapId()) {
                continue;
            }
            Character donorBot = sibling.bot;
            int count = BotCombatManager.countAmmo(donorBot, needyWeaponType);
            if (count < BotCombatManager.cfg.AMMO_LOW_WARN) {
                continue;
            }
            WeaponType donorWeaponType = BotAttackExecutionProvider.getEquippedWeaponType(donorBot);
            boolean donorNeedsSameAmmo = donorWeaponType == needyWeaponType;
            int donationQty = donorNeedsSameAmmo ? (count - BotCombatManager.cfg.AMMO_LOW_WARN) / 2 : count;
            if (donationQty <= 0) {
                continue;
            }
            AmmoDonorPlan candidate = new AmmoDonorPlan(sibling, count, donorNeedsSameAmmo, donationQty);
            if (isBetterDonor(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isBetterDonor(AmmoDonorPlan candidate, AmmoDonorPlan best) {
        if (best == null) {
            return true;
        }
        if (candidate.donorNeedsSameAmmo() != best.donorNeedsSameAmmo()) {
            return !candidate.donorNeedsSameAmmo();
        }
        return candidate.matchingAmmoCount() > best.matchingAmmoCount();
    }

    private static boolean canRequestShare(WeaponType weaponType) {
        return weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW;
    }

    record AmmoDonorPlan(BotEntry entry, int matchingAmmoCount, boolean donorNeedsSameAmmo, int donationQty) {}
}
