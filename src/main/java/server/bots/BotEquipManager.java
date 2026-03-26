package server.bots;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.EquipSlot;
import constants.inventory.ItemConstants;
import server.ItemInformationProvider;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

class BotEquipManager {

    private static final short[] RING_SLOTS = {-12, -13, -15, -16};

    /**
     * Scans the bot's EQUIP inventory and equips any item that improves current gear.
     * Scoring priority: max damage > total defense > total stat sum.
     * Cash items are skipped. Called on mode change (follow / stop / grind).
     */
    static void autoEquip(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);

        WeaponType weaponType = currentWeaponType(bot, ii);

        // Group unequipped candidates by primary equip slot
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : eqpInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == EquipSlot.PET_EQUIP) continue;
            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) continue;
            if (!ii.canWearEquipment(bot, (Equip) item, primary)) continue;
            bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add((Equip) item);
        }

        // Ensure we also check slots that only have an equipped item (no unequipped candidate)
        for (Item item : eqdInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            short slot = item.getPosition();
            short key = isRingSlot(slot) ? -12 : slot;
            bySlot.computeIfAbsent(key, k -> new ArrayList<>());
        }

        // Sort slots: weapon (-11) first so damage scoring reflects the new weapon
        List<Short> nonRingSlots = bySlot.keySet().stream()
                .filter(s -> !isRingSlot(s))
                .sorted((a, b) -> a == -11 ? -1 : b == -11 ? 1 : Short.compare(a, b))
                .collect(Collectors.toList());

        for (short slot : nonRingSlots) {
            Equip current = (Equip) eqdInv.getItem(slot);
            Equip best = findBest(bot, ii, weaponType, current, bySlot.get(slot));
            if (best != null && best != current) {
                InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, best.getPosition(), slot, (short) 1);
                if (slot == -11) weaponType = currentWeaponType(bot, ii);
            }
        }

        // Ring slots: greedy — find best unequipped ring per slot from a shared pool
        if (bySlot.containsKey((short) -12)) {
            autoEquipRings(bot, ii, weaponType, bySlot.get((short) -12), eqdInv);
        }
    }

    static String unequipAll(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);

        List<Short> equippedSlots = new ArrayList<>();
        for (Item item : eqdInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            equippedSlots.add(item.getPosition());
        }
        if (equippedSlots.isEmpty()) return "nothing to unequip";

        int freeSlots = eqpInv.getNumFreeSlot();
        if (freeSlots < equippedSlots.size()) {
            return "need " + equippedSlots.size() + " free equip slots, only have " + freeSlots;
        }

        equippedSlots.sort(Short::compare);
        for (short src : equippedSlots) {
            short dst = eqpInv.getNextFreeSlot();
            if (dst < 0) {
                return "ran out of equip slots while unequipping";
            }
            InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, src, dst, (short) 1);
        }
        return "unequipped " + equippedSlots.size() + " item" + (equippedSlots.size() != 1 ? "s" : "");
    }

    private static void autoEquipRings(Character bot, ItemInformationProvider ii, WeaponType wt,
                                        List<Equip> candidates, Inventory eqdInv) {
        List<Equip> pool = new ArrayList<>(candidates);
        for (short rs : RING_SLOTS) {
            Equip current = (Equip) eqdInv.getItem(rs);
            List<Equip> eligible = pool.stream()
                    .filter(c -> ii.canWearEquipment(bot, c, rs))
                    .collect(Collectors.toList());
            Equip best = findBest(bot, ii, wt, current, eligible);
            if (best != null && best != current) {
                InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, best.getPosition(), rs, (short) 1);
                pool.remove(best);
            }
        }
    }

    // -------------------------------------------------------------------------

    private static Equip findBest(Character bot, ItemInformationProvider ii, WeaponType wt,
                                   Equip current, List<Equip> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        Equip best    = current;
        int bestDmg   = dmgScore(bot, ii, wt, current, current); // delta=0 = baseline
        int bestDef   = defScore(current);
        int bestSum   = statSum(current);

        for (Equip c : candidates) {
            int dmg = dmgScore(bot, ii, wt, current, c);
            int def = defScore(c);
            int sum = statSum(c);
            if (dmg > bestDmg
                    || (dmg == bestDmg && def > bestDef)
                    || (dmg == bestDmg && def == bestDef && sum > bestSum)) {
                best = c; bestDmg = dmg; bestDef = def; bestSum = sum;
            }
        }
        return best;
    }

    /**
     * Simulates max damage if we replace {@code replacing} (currently equipped in its slot)
     * with {@code candidate}. Null means the slot is empty.
     */
    private static int dmgScore(Character bot, ItemInformationProvider ii, WeaponType wt,
                                  Equip replacing, Equip candidate) {
        int str  = bot.getTotalStr()  + delta(candidate, replacing, e -> e.getStr());
        int dex  = bot.getTotalDex()  + delta(candidate, replacing, e -> e.getDex());
        int luk  = bot.getTotalLuk()  + delta(candidate, replacing, e -> e.getLuk());
        int watk = bot.getTotalWatk() + delta(candidate, replacing, e -> e.getWatk());

        WeaponType wtype = wt;
        if (candidate != null && ItemConstants.isWeapon(candidate.getItemId())) {
            WeaponType cWT = ii.getWeaponType(candidate.getItemId());
            if (cWT != null) wtype = cWT;
        }
        if (wtype == null) return 0;

        Job job = bot.getJob();
        if (job.isA(Job.THIEF) && wtype == WeaponType.DAGGER_OTHER) wtype = WeaponType.DAGGER_THIEVES;

        int main, sec;
        if (wtype == WeaponType.BOW || wtype == WeaponType.CROSSBOW || wtype == WeaponType.GUN) {
            main = dex; sec = str;
        } else if (wtype == WeaponType.CLAW || wtype == WeaponType.DAGGER_THIEVES) {
            main = luk; sec = dex + str;
        } else {
            main = str; sec = dex;
        }
        return (int) Math.ceil((wtype.getMaxDamageMultiplier() * main + sec) / 100.0 * watk);
    }

    private static int delta(Equip candidate, Equip replacing, ToIntFunction<Equip> getter) {
        return (candidate != null ? getter.applyAsInt(candidate) : 0)
             - (replacing  != null ? getter.applyAsInt(replacing)  : 0);
    }

    private static int defScore(Equip e)  { return e != null ? e.getWdef() + e.getMdef() : 0; }

    private static int statSum(Equip e) {
        if (e == null) return 0;
        return e.getStr() + e.getDex() + e.getInt() + e.getLuk()
             + e.getWatk() + e.getMatk() + e.getWdef() + e.getMdef()
             + e.getAcc() + e.getAvoid() + e.getSpeed() + e.getHp() + e.getMp();
    }

    private static boolean isRingSlot(short slot) {
        for (short rs : RING_SLOTS) if (slot == rs) return true;
        return false;
    }

    private static WeaponType currentWeaponType(Character bot, ItemInformationProvider ii) {
        Item w = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        return w != null ? ii.getWeaponType(w.getItemId()) : null;
    }
}
