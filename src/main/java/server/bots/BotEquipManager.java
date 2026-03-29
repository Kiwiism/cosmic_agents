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

    static final class EquipRecommendation {
        private final short targetSlot;
        private final Equip current;
        private final Equip candidate;

        private EquipRecommendation(short targetSlot, Equip current, Equip candidate) {
            this.targetSlot = targetSlot;
            this.current = current;
            this.candidate = candidate;
        }

        short targetSlot() {
            return targetSlot;
        }

        Equip current() {
            return current;
        }

        Equip candidate() {
            return candidate;
        }
    }

    private record EquipScore(int damage, int defense, int statSum) {}

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

    static List<EquipRecommendation> findRecommendedEquips(Character receiver, Character holder) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory holderEquipInv = holder.getInventory(InventoryType.EQUIP);
        Inventory receiverEquippedInv = receiver.getInventory(InventoryType.EQUIPPED);

        WeaponType weaponType = currentWeaponType(receiver, ii);
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : holderEquipInv.list()) {
            if (!(item instanceof Equip equip) || ii.isCash(item.getItemId())) {
                continue;
            }

            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == EquipSlot.PET_EQUIP) {
                continue;
            }

            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) {
                continue;
            }
            if (!ii.canWearEquipment(receiver, equip, primary)) {
                continue;
            }

            bySlot.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(equip);
        }

        List<EquipRecommendation> recommendations = new ArrayList<>();

        List<Short> nonRingSlots = bySlot.keySet().stream()
                .filter(slot -> !isRingSlot(slot))
                .sorted((a, b) -> a == -11 ? -1 : b == -11 ? 1 : Short.compare(a, b))
                .collect(Collectors.toList());
        for (short slot : nonRingSlots) {
            Equip current = (Equip) receiverEquippedInv.getItem(slot);
            Equip best = findBest(receiver, ii, weaponType, current, bySlot.get(slot));
            if (best != null && best != current && isBetterThanCurrent(receiver, ii, weaponType, current, best)) {
                recommendations.add(new EquipRecommendation(slot, current, best));
            }
        }

        if (bySlot.containsKey((short) -12)) {
            recommendations.addAll(findRecommendedRings(receiver, ii, weaponType, bySlot.get((short) -12), receiverEquippedInv));
        }

        return recommendations;
    }

    static List<Item> collectRecommendedItems(Character receiver, Character holder) {
        return new ArrayList<>(findRecommendedEquips(receiver, holder).stream()
                .map(EquipRecommendation::candidate)
                .toList());
    }

    static EquipRecommendation findRecommendationForItem(Character receiver, Character holder, Item holderItem) {
        if (!(holderItem instanceof Equip candidate)) {
            return null;
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (ii.isCash(candidate.getItemId())) {
            return null;
        }

        String textSlot = ii.getEquipmentSlot(candidate.getItemId());
        EquipSlot slot = EquipSlot.getFromTextSlot(textSlot);
        if (slot == EquipSlot.PET_EQUIP) {
            return null;
        }

        short primarySlot = (short) slot.getPrimarySlot();
        if (primarySlot == 0) {
            return null;
        }

        WeaponType weaponType = currentWeaponType(receiver, ii);
        Inventory receiverEquippedInv = receiver.getInventory(InventoryType.EQUIPPED);
        if (isRingSlot(primarySlot)) {
            return findRecommendedRingForItem(receiver, ii, weaponType, candidate, receiverEquippedInv);
        }

        if (!ii.canWearEquipment(receiver, candidate, primarySlot)) {
            return null;
        }

        Equip current = (Equip) receiverEquippedInv.getItem(primarySlot);
        if (!isBetterThanCurrent(receiver, ii, weaponType, current, candidate)) {
            return null;
        }

        return new EquipRecommendation(primarySlot, current, candidate);
    }

    static String recommendationSummary(Character receiver, Character holder, int maxItems) {
        List<EquipRecommendation> recommendations = findRecommendedEquips(receiver, holder);
        if (recommendations.isEmpty()) {
            return null;
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        StringBuilder summary = new StringBuilder("better gear for you: ");
        int count = Math.min(maxItems, recommendations.size());
        for (int i = 0; i < count; i++) {
            EquipRecommendation recommendation = recommendations.get(i);
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(slotLabel(recommendation.targetSlot()))
                    .append(" -> ")
                    .append(ii.getName(recommendation.candidate().getItemId()));
        }
        if (recommendations.size() > count) {
            summary.append(" +").append(recommendations.size() - count).append(" more");
        }
        return summary.toString();
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

    private static List<EquipRecommendation> findRecommendedRings(Character receiver, ItemInformationProvider ii, WeaponType wt,
                                                                  List<Equip> candidates, Inventory receiverEquippedInv) {
        List<EquipRecommendation> recommendations = new ArrayList<>();
        List<Equip> pool = new ArrayList<>(candidates);
        for (short ringSlot : RING_SLOTS) {
            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            List<Equip> eligible = pool.stream()
                    .filter(candidate -> ii.canWearEquipment(receiver, candidate, ringSlot))
                    .collect(Collectors.toList());
            Equip best = findBest(receiver, ii, wt, current, eligible);
            if (best != null && best != current && isBetterThanCurrent(receiver, ii, wt, current, best)) {
                recommendations.add(new EquipRecommendation(ringSlot, current, best));
                pool.remove(best);
            }
        }
        return recommendations;
    }

    private static EquipRecommendation findRecommendedRingForItem(Character receiver,
                                                                  ItemInformationProvider ii,
                                                                  WeaponType wt,
                                                                  Equip candidate,
                                                                  Inventory receiverEquippedInv) {
        EquipRecommendation bestRecommendation = null;
        EquipScore bestScore = null;
        for (short ringSlot : RING_SLOTS) {
            if (!ii.canWearEquipment(receiver, candidate, ringSlot)) {
                continue;
            }

            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            EquipScore currentScore = scoreEquip(receiver, ii, wt, current, current);
            EquipScore candidateScore = scoreEquip(receiver, ii, wt, current, candidate);
            if (compareScores(candidateScore, currentScore) <= 0) {
                continue;
            }

            if (bestRecommendation == null || compareScores(candidateScore, bestScore) > 0) {
                bestRecommendation = new EquipRecommendation(ringSlot, current, candidate);
                bestScore = candidateScore;
            }
        }

        return bestRecommendation;
    }

    // -------------------------------------------------------------------------

    private static Equip findBest(Character bot, ItemInformationProvider ii, WeaponType wt,
                                   Equip current, List<Equip> candidates) {
        if (candidates == null || candidates.isEmpty()) return null;

        Equip best    = current;
        EquipScore bestScore = scoreEquip(bot, ii, wt, current, current);

        for (Equip c : candidates) {
            EquipScore candidateScore = scoreEquip(bot, ii, wt, current, c);
            if (compareScores(candidateScore, bestScore) > 0) {
                best = c;
                bestScore = candidateScore;
            }
        }
        return best;
    }

    private static boolean isBetterThanCurrent(Character bot, ItemInformationProvider ii, WeaponType wt,
                                               Equip current, Equip candidate) {
        return compareScores(scoreEquip(bot, ii, wt, current, candidate), scoreEquip(bot, ii, wt, current, current)) > 0;
    }

    private static EquipScore scoreEquip(Character bot, ItemInformationProvider ii, WeaponType wt,
                                         Equip replacing, Equip candidate) {
        return new EquipScore(dmgScore(bot, ii, wt, replacing, candidate), defScore(candidate), statSum(candidate));
    }

    private static int compareScores(EquipScore left, EquipScore right) {
        int cmp = Integer.compare(left.damage(), right.damage());
        if (cmp != 0) {
            return cmp;
        }

        cmp = Integer.compare(left.defense(), right.defense());
        if (cmp != 0) {
            return cmp;
        }

        return Integer.compare(left.statSum(), right.statSum());
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

    private static String slotLabel(short slot) {
        return switch (slot) {
            case -11 -> "weapon";
            case -10 -> "shield";
            case -9 -> "cape";
            case -8 -> "top";
            case -7 -> "shoes";
            case -6 -> "bottom";
            case -5 -> "glove";
            case -4 -> "pants";
            case -3 -> "face";
            case -2 -> "eye";
            case -1 -> "hat";
            case -12, -13, -15, -16 -> "ring";
            case -17 -> "pendant";
            case -18 -> "tamed mob";
            case -19 -> "saddle";
            case -20 -> "medal";
            case -21 -> "belt";
            default -> "slot " + slot;
        };
    }

    private static WeaponType currentWeaponType(Character bot, ItemInformationProvider ii) {
        Item w = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        return w != null ? ii.getWeaponType(w.getItemId()) : null;
    }
}
