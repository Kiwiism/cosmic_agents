package server.bots;

import config.YamlConfig;
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
import constants.skills.Crusader;
import constants.skills.DragonKnight;
import constants.skills.Fighter;
import constants.skills.Page;
import constants.skills.Paladin;
import constants.skills.Pirate;
import constants.skills.Rogue;
import constants.skills.Spearman;
import constants.skills.WhiteKnight;
import server.ItemInformationProvider;
import server.bots.combat.BotAttackDataProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * {@code pendingOffer} is excluded so a pending gear offer to the owner stays tradeable.
     */
    static void autoEquip(Character bot, Character owner, Item pendingOffer) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);

        WeaponType weaponType = currentWeaponType(bot, ii);

        // Group unequipped candidates by primary equip slot
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : eqpInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            if (item == pendingOffer) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == EquipSlot.PET_EQUIP) continue;
            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) continue;
            if (!ii.canWearEquipment(bot, (Equip) item, primary)) continue;
            if (primary == (short) -11 && !isWeaponCompatible(bot, ii.getWeaponType(item.getItemId()))) continue;
            bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add((Equip) item);
        }

        // Ensure we also check slots that only have an equipped item (no unequipped candidate)
        for (Item item : eqdInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            short slot = item.getPosition();
            short key = isRingSlot(slot) ? -12 : slot;
            bySlot.computeIfAbsent(key, k -> new ArrayList<>());
        }

        // Sort slots: weapon (-11) first so damage scoring reflects the new weapon;
        // overall/top (-5) before pants (-6) so overall blocks pants correctly.
        List<Short> nonRingSlots = bySlot.keySet().stream()
                .filter(s -> !isRingSlot(s))
                .sorted((a, b) -> {
                    if (a == -11) return -1; if (b == -11) return 1;
                    if (a == -5)  return -1; if (b == -5)  return 1;
                    return Short.compare(a, b);
                })
                .collect(Collectors.toList());

        boolean overallEquipped = isOverall(eqdInv.getItem((short) -5), ii);
        for (short slot : nonRingSlots) {
            // Skip shield if a 2H weapon is active — would unequip the weapon causing a loop.
            Item wSlot = eqdInv.getItem((short) -11);
            if (slot == (short) -10 && wSlot != null && ii.isTwoHanded(wSlot.getItemId())) continue;
            // Skip pants if an overall occupies the top+bottom slot.
            if (slot == (short) -6 && overallEquipped) continue;

            Equip current = (Equip) eqdInv.getItem(slot);
            Equip effectiveCurrent = slot == (short) -11 ? compatibleWeaponOrNull(bot, ii, current) : current;
            Equip best = findBest(bot, ii, weaponType, effectiveCurrent, bySlot.get(slot));
            // 2H weapon displaces shield — only upgrade if 2H beats current weapon+shield combined.
            if (slot == (short) -11 && best != null && best != current && ii.isTwoHanded(best.getItemId())) {
                Equip shield = (Equip) eqdInv.getItem((short) -10);
                if (compareScores(scoreEquipWithLoss(bot, ii, weaponType, effectiveCurrent, best, shield),
                                  scoreEquip(bot, ii, weaponType, effectiveCurrent, effectiveCurrent)) <= 0) best = effectiveCurrent;
            }
            // Overall displaces pants — only upgrade if overall beats current top+pants combined.
            if (slot == (short) -5 && best != null && best != current && isOverall(best, ii)) {
                Equip pants = (Equip) eqdInv.getItem((short) -6);
                if (compareScores(scoreEquipWithLoss(bot, ii, weaponType, effectiveCurrent, best, pants),
                                  scoreEquip(bot, ii, weaponType, effectiveCurrent, effectiveCurrent)) <= 0) best = effectiveCurrent;
            }
            if (best != null && best != current) {
                InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, best.getPosition(), slot, (short) 1);
                if (slot == -11) weaponType = currentWeaponType(bot, ii);
                if (slot == -5)  overallEquipped = isOverall(eqdInv.getItem((short) -5), ii);
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
            if (item.isUntradeable() && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE) {
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
            if (primary == (short) -11 && !isWeaponCompatible(receiver, ii.getWeaponType(item.getItemId()))) {
                continue;
            }

            bySlot.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(equip);
        }

        List<EquipRecommendation> recommendations = new ArrayList<>();

        List<Short> nonRingSlots = bySlot.keySet().stream()
                .filter(slot -> !isRingSlot(slot))
                .sorted((a, b) -> {
                    if (a == -11) return -1; if (b == -11) return 1;
                    if (a == -5)  return -1; if (b == -5)  return 1;
                    return Short.compare(a, b);
                })
                .collect(Collectors.toList());
        Item receiverWeapon = receiverEquippedInv.getItem((short) -11);
        boolean receiverHas2H = receiverWeapon != null && ii.isTwoHanded(receiverWeapon.getItemId());
        boolean overallRec = isOverall(receiverEquippedInv.getItem((short) -5), ii);
        for (short slot : nonRingSlots) {
            if (slot == (short) -10 && receiverHas2H) continue;
            if (slot == (short) -6 && overallRec) continue;
            Equip current = (Equip) receiverEquippedInv.getItem(slot);
            Equip effectiveCurrent = slot == (short) -11 ? compatibleWeaponOrNull(receiver, ii, current) : current;
            Equip best = findBest(receiver, ii, weaponType, effectiveCurrent, bySlot.get(slot));
            // 2H weapon displaces shield — only recommend if 2H beats current weapon+shield combined.
            if (slot == (short) -11 && best != null && best != current && ii.isTwoHanded(best.getItemId())) {
                Equip shield = (Equip) receiverEquippedInv.getItem((short) -10);
                if (compareScores(scoreEquipWithLoss(receiver, ii, weaponType, effectiveCurrent, best, shield),
                                  scoreEquip(receiver, ii, weaponType, effectiveCurrent, effectiveCurrent)) <= 0) best = effectiveCurrent;
            }
            // Overall displaces pants — only recommend if overall beats current top+pants combined.
            if (slot == (short) -5 && best != null && best != current && isOverall(best, ii)) {
                Equip pants = (Equip) receiverEquippedInv.getItem((short) -6);
                if (compareScores(scoreEquipWithLoss(receiver, ii, weaponType, effectiveCurrent, best, pants),
                                  scoreEquip(receiver, ii, weaponType, effectiveCurrent, effectiveCurrent)) <= 0) best = effectiveCurrent;
            }
            if (best != null && best != current && isBetterThanCurrent(receiver, ii, weaponType, effectiveCurrent, best)) {
                recommendations.add(new EquipRecommendation(slot, current, best));
                if (slot == (short) -5) overallRec = isOverall(best, ii);
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
        if (holderItem.isUntradeable() && !YamlConfig.config.server.UNTRADEABLE_ITEMS_TRADEABLE) {
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
        if (primarySlot == (short) -11 && !isWeaponCompatible(receiver, ii.getWeaponType(candidate.getItemId()))) {
            return null;
        }

        // Shield is unusable with a 2H weapon.
        if (primarySlot == (short) -10) {
            Item weapon = receiverEquippedInv.getItem((short) -11);
            if (weapon != null && ii.isTwoHanded(weapon.getItemId())) return null;
        }

        Equip current = (Equip) receiverEquippedInv.getItem(primarySlot);
        if (primarySlot == (short) -11) {
            current = compatibleWeaponOrNull(receiver, ii, current);
        }
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

    static String unequipSlot(Character bot, short[] slots) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);

        List<Short> toUnequip = new ArrayList<>();
        for (short slot : slots) {
            Item item = eqdInv.getItem(slot);
            if (item != null && !ii.isCash(item.getItemId())) {
                toUnequip.add(slot);
            }
        }
        if (toUnequip.isEmpty()) {
            return "nothing equipped there";
        }
        if (eqpInv.getNumFreeSlot() < toUnequip.size()) {
            return "equip bag full";
        }
        StringBuilder names = new StringBuilder();
        for (short src : toUnequip) {
            Item item = eqdInv.getItem(src);
            short dst = eqpInv.getNextFreeSlot();
            if (dst < 0) return "ran out of equip slots";
            InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, src, dst, (short) 1);
            if (!names.isEmpty()) names.append(", ");
            names.append(ii.getName(item.getItemId()));
        }
        return "unequipped " + names;
    }

    /** Returns the equipped slot(s) that match the given slot name from chat. Empty array = unknown. */
    static short[] slotsFromName(String name) {
        return switch (name.trim().toLowerCase().replaceAll("\\s+", "")) {
            case "weapon", "wep" -> new short[]{-11};
            case "shield", "offhand" -> new short[]{-10};
            case "cape" -> new short[]{-9};
            case "hat", "helm", "helmet" -> new short[]{-1};
            case "top", "shirt" -> new short[]{-8};
            case "bottom", "pants" -> new short[]{-5};
            case "ear", "earring", "earrings" -> new short[]{-4}; // untested
            case "shoes", "boots" -> new short[]{-7};
            case "glove", "gloves" -> new short[]{-6};
            case "face", "faceacc", "faceaccessory" -> new short[]{-3};
            case "eye", "eyeacc", "eyeaccessory", "eyepiece" -> new short[]{-2};
            case "ring" -> RING_SLOTS.clone();
            case "ring1" -> new short[]{-12};
            case "ring2" -> new short[]{-13};
            case "ring3" -> new short[]{-15};
            case "ring4" -> new short[]{-16};
            case "pendant" -> new short[]{-17};
            case "medal" -> new short[]{-20};
            case "belt" -> new short[]{-21};
            default -> new short[0];
        };
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
        return scoreEquipWithLoss(bot, ii, wt, replacing, candidate, null);
    }

    private static EquipScore scoreEquipWithLoss(Character bot, ItemInformationProvider ii, WeaponType wt,
                                                  Equip replacing, Equip candidate, Equip loss) {
        return new EquipScore(dmgScore(bot, ii, wt, replacing, candidate, loss), defScore(candidate), statSum(candidate));
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
     * Simulates max damage replacing {@code replacing} with {@code candidate},
     * and simultaneously removing {@code loss} (e.g. pants when equipping an overall,
     * or shield when equipping a 2H weapon). {@code loss} may be null.
     */
    private static int dmgScore(Character bot, ItemInformationProvider ii, WeaponType wt,
                                  Equip replacing, Equip candidate, Equip loss) {
        int str  = bot.getTotalStr()  + delta(candidate, replacing, e -> e.getStr())  - (loss != null ? loss.getStr()  : 0);
        int dex  = bot.getTotalDex()  + delta(candidate, replacing, e -> e.getDex())  - (loss != null ? loss.getDex()  : 0);
        int luk  = bot.getTotalLuk()  + delta(candidate, replacing, e -> e.getLuk())  - (loss != null ? loss.getLuk()  : 0);
        int watk = bot.getTotalWatk() + delta(candidate, replacing, e -> e.getWatk()) - (loss != null ? loss.getWatk() : 0);

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
        // Spear/polearm: bot alternates stab and swing — average the two multipliers.
        double mult = switch (wtype) {
            case SPEAR_STAB     -> (WeaponType.SPEAR_STAB.getMaxDamageMultiplier()    + WeaponType.SPEAR_SWING.getMaxDamageMultiplier())  / 2.0;
            case POLE_ARM_SWING -> (WeaponType.POLE_ARM_SWING.getMaxDamageMultiplier() + WeaponType.POLE_ARM_STAB.getMaxDamageMultiplier()) / 2.0;
            default             -> wtype.getMaxDamageMultiplier();
        };
        int dmg = (int) Math.ceil((mult * main + sec) / 100.0 * watk);
        // For weapon comparisons, convert max-hit to DPS (hit * 1000 / cycleMs) to avoid
        // slow-but-high-damage bias.
        if (candidate != null && ItemConstants.isWeapon(candidate.getItemId())) {
            int cycleMs = weaponCycleMs(candidate.getItemId());
            if (cycleMs > 0) dmg = (int) (dmg * 1000.0 / cycleMs);
        }
        return dmg;
    }

    private static int dmgScore(Character bot, ItemInformationProvider ii, WeaponType wt,
                                  Equip replacing, Equip candidate) {
        return dmgScore(bot, ii, wt, replacing, candidate, null);
    }

    /**
     * Returns the effective attack cycle in milliseconds for a weapon using the same formula
     * as BotCombatManager: {@code rawAnimationMs / (1.7 - attackSpeed / 10)}.
     * The raw animation comes from the weapon's WZ XML; the speed value scales playback rate,
     * so two weapons with the same speed tier but different base animations have different DPS.
     * Returns 0 if no WZ profile is available — caller skips DPS scaling.
     */
    private static int weaponCycleMs(int itemId) {
        BotAttackDataProvider provider = BotAttackDataProvider.getInstance();
        BotAttackDataProvider.NormalAttackProfile profile = provider.getNormalAttackProfile(itemId);
        if (profile == null) {
            return 0;
        }
        WeaponType weaponType = ItemInformationProvider.getInstance().getWeaponType(itemId);
        BotAttackDataProvider.AttackAnimationSpec attackSpec = provider.getBasicAttackSpec(profile.getAttack(), weaponType);
        int rawAnimationDelayMs = provider.getBodyStanceDurationMs(attackSpec.primaryAction());
        if (rawAnimationDelayMs <= 0) {
            return 0;
        }
        return server.bots.combat.BotAttackTiming.adjustDelayMillis(rawAnimationDelayMs, profile.getAttackSpeed());
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

    private static boolean isOverall(Item item, ItemInformationProvider ii) {
        if (item == null) return false;
        return "MaPn".equals(ii.getEquipmentSlot(item.getItemId()));
    }

    static boolean isWeaponCompatible(Character bot, WeaponType weaponType) {
        if (weaponType == null || weaponType == WeaponType.NOT_A_WEAPON) {
            return true;
        }

        Job job = bot.getJob();
        if (job == Job.THIEF) {
            if (bot.getSkillLevel(Rogue.LUCKY_SEVEN) > 0) {
                return weaponType == WeaponType.CLAW;
            }
            if (bot.getSkillLevel(Rogue.DOUBLE_STAB) > 0) {
                return isThiefDagger(weaponType);
            }
        }
        if (job == Job.PIRATE) {
            boolean gunBuild = bot.getSkillLevel(Pirate.DOUBLE_SHOT) > 0;
            boolean knuckleBuild = bot.getSkillLevel(Pirate.FLASH_FIST) > 0
                    || bot.getSkillLevel(Pirate.SOMERSAULT_KICK) > 0;
            if (gunBuild && !knuckleBuild) {
                return weaponType == WeaponType.GUN;
            }
            if (knuckleBuild && !gunBuild) {
                return weaponType == WeaponType.KNUCKLE;
            }
            return weaponType == WeaponType.GUN || weaponType == WeaponType.KNUCKLE;
        }

        return switch (job) {
            case BOWMAN -> weaponType == WeaponType.BOW || weaponType == WeaponType.CROSSBOW;
            case FIGHTER -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Fighter.SWORD_MASTERY, Fighter.SWORD_BOOSTER},
                    new int[]{Fighter.AXE_MASTERY, Fighter.AXE_BOOSTER});
            case CRUSADER -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Crusader.SWORD_COMA, Crusader.SWORD_PANIC},
                    new int[]{Crusader.AXE_COMA, Crusader.AXE_PANIC});
            case HERO -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Crusader.SWORD_COMA, Crusader.SWORD_PANIC},
                    new int[]{Crusader.AXE_COMA, Crusader.AXE_PANIC});
            case PAGE -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Page.SWORD_MASTERY, Page.SWORD_BOOSTER},
                    new int[]{Page.BW_MASTERY, Page.BW_BOOSTER});
            case WHITEKNIGHT -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{WhiteKnight.SWORD_FIRE_CHARGE, WhiteKnight.SWORD_ICE_CHARGE, WhiteKnight.SWORD_LIT_CHARGE},
                    new int[]{WhiteKnight.BW_FIRE_CHARGE, WhiteKnight.BW_ICE_CHARGE, WhiteKnight.BW_LIT_CHARGE});
            case PALADIN -> matchesWarriorWeaponFamily(bot,
                    isSword(weaponType), isGeneralWeapon(weaponType),
                    new int[]{Paladin.SWORD_HOLY_CHARGE},
                    new int[]{Paladin.BW_HOLY_CHARGE});
            case SPEARMAN -> matchesWarriorWeaponFamily(bot,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{Spearman.SPEAR_MASTERY, Spearman.SPEAR_BOOSTER},
                    new int[]{Spearman.POLEARM_MASTERY, Spearman.POLEARM_BOOSTER});
            case DRAGONKNIGHT -> matchesWarriorWeaponFamily(bot,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY},
                    new int[]{DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY});
            case DARKKNIGHT -> matchesWarriorWeaponFamily(bot,
                    isSpearWeapon(weaponType), isPolearmWeapon(weaponType),
                    new int[]{DragonKnight.SPEAR_CRUSHER, DragonKnight.SPEAR_DRAGON_FURY},
                    new int[]{DragonKnight.POLE_ARM_CRUSHER, DragonKnight.POLE_ARM_DRAGON_FURY});
            case MAGICIAN, FP_WIZARD, FP_MAGE, FP_ARCHMAGE, IL_WIZARD, IL_MAGE, IL_ARCHMAGE, CLERIC, PRIEST, BISHOP ->
                    weaponType == WeaponType.WAND || weaponType == WeaponType.STAFF;
            case HUNTER, RANGER, BOWMASTER -> weaponType == WeaponType.BOW;
            case CROSSBOWMAN, SNIPER, MARKSMAN -> weaponType == WeaponType.CROSSBOW;
            case ASSASSIN, HERMIT, NIGHTLORD -> weaponType == WeaponType.CLAW;
            case BANDIT, CHIEFBANDIT, SHADOWER -> isThiefDagger(weaponType);
            case BRAWLER, MARAUDER, BUCCANEER -> weaponType == WeaponType.KNUCKLE;
            case GUNSLINGER, OUTLAW, CORSAIR -> weaponType == WeaponType.GUN;
            default -> true;
        };
    }

    private static Equip compatibleWeaponOrNull(Character bot, ItemInformationProvider ii, Equip equip) {
        if (equip == null) {
            return null;
        }
        return isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId())) ? equip : null;
    }

    private static boolean matchesWarriorWeaponFamily(Character bot,
                                                      boolean firstFamilyMatch,
                                                      boolean secondFamilyMatch,
                                                      int[] firstFamilySkills,
                                                      int[] secondFamilySkills) {
        boolean firstFamilyChosen = hasAnySkill(bot, firstFamilySkills);
        boolean secondFamilyChosen = hasAnySkill(bot, secondFamilySkills);
        if (firstFamilyChosen && !secondFamilyChosen) {
            return firstFamilyMatch;
        }
        if (secondFamilyChosen && !firstFamilyChosen) {
            return secondFamilyMatch;
        }
        return firstFamilyMatch || secondFamilyMatch;
    }

    private static boolean hasAnySkill(Character bot, int... skillIds) {
        for (int skillId : skillIds) {
            if (bot.getSkillLevel(skillId) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSword(WeaponType weaponType) {
        return weaponType == WeaponType.SWORD1H || weaponType == WeaponType.SWORD2H;
    }

    private static boolean isGeneralWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.GENERAL1H_SWING
                || weaponType == WeaponType.GENERAL1H_STAB
                || weaponType == WeaponType.GENERAL2H_SWING
                || weaponType == WeaponType.GENERAL2H_STAB;
    }

    private static boolean isSpearWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.SPEAR_STAB || weaponType == WeaponType.SPEAR_SWING;
    }

    private static boolean isPolearmWeapon(WeaponType weaponType) {
        return weaponType == WeaponType.POLE_ARM_SWING || weaponType == WeaponType.POLE_ARM_STAB;
    }

    private static boolean isThiefDagger(WeaponType weaponType) {
        return weaponType == WeaponType.DAGGER_OTHER || weaponType == WeaponType.DAGGER_THIEVES;
    }

    private static WeaponType currentWeaponType(Character bot, ItemInformationProvider ii) {
        Item w = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        return w != null ? ii.getWeaponType(w.getItemId()) : null;
    }
}
