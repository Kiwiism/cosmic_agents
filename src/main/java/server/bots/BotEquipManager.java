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
import server.combat.CombatFormulaProvider;
import server.life.LifeFactory;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.SpawnPoint;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

class BotEquipManager {

    private static final short[] RING_SLOTS = {-12, -13, -15, -16};
    /** Cap autoEquip cascade depth; in practice converges in 2-3. */
    private static final int MAX_AUTO_EQUIP_PASSES = 6;

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
     * Scoring is mob-aware (uses avg map-mob defense + avoidability when available) and
     * does single-step lookahead at the weapon slot: a non-weapon candidate is allowed
     * to win if its stat boost would unlock a higher-tier weapon currently failing only
     * on stat requirements. Wraps the slot loop in a fixed-point iteration so cascade
     * upgrades after each commit are picked up on the next pass.
     *
     * Scoring priority: damage (mob-adjusted, DPS-scaled) > defense > stat sum.
     * Cash items are skipped. Called on mode change (follow / stop / grind).
     * {@code pendingOffer} is excluded so a pending gear offer to the owner stays tradeable.
     */
    static void autoEquip(Character bot, Character owner, Item pendingOffer) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        for (int pass = 0; pass < MAX_AUTO_EQUIP_PASSES; pass++) {
            if (!autoEquipPass(bot, ii, eqpInv, eqdInv, pendingOffer)) {
                return;
            }
        }
    }

    /** Returns true if any slot was upgraded during this pass (caller should run again). */
    private static boolean autoEquipPass(Character bot, ItemInformationProvider ii,
                                         Inventory eqpInv, Inventory eqdInv, Item pendingOffer) {
        WeaponType weaponType = currentWeaponType(bot, ii);

        // Group unequipped candidates by primary equip slot. Currently-wearable for non-weapon
        // slots; for the weapon slot we ALSO retain items where only the stat req is unmet
        // (job/level/fame pass) so they can win via the lookahead pool.
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        List<Equip> weaponLookaheadPool = buildWeaponLookaheadPool(bot, ii, eqpInv);
        for (Item item : eqpInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            if (item == pendingOffer) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == EquipSlot.PET_EQUIP) continue;
            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) continue;
            Equip equip = (Equip) item;
            boolean wearableNow = ii.canWearEquipment(bot, equip, primary);
            if (primary == (short) -11) {
                if (!isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId()))) continue;
                if (wearableNow) {
                    bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
                }
            } else if (wearableNow) {
                bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
            }
        }

        // Per-slot dominance pruning over currently-wearable candidates. Within a wearable pool
        // dropping items strictly beaten on every stat is safe — legality already passed.
        // Lookahead-only weapons are NOT pruned (their reqs differ).
        for (Map.Entry<Short, List<Equip>> e : bySlot.entrySet()) {
            e.setValue(pruneDominated(e.getValue()));
        }

        // Ensure we also check slots that only have an equipped item (no unequipped candidate)
        for (Item item : eqdInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            short slot = item.getPosition();
            short key = isRingSlot(slot) ? -12 : slot;
            bySlot.computeIfAbsent(key, k -> new ArrayList<>());
        }

        MapDamageProfile mobProfile = MapDamageProfile.snapshot(bot);

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

        boolean changed = false;
        boolean overallEquipped = isOverall(eqdInv.getItem((short) -5), ii);
        for (short slot : nonRingSlots) {
            // Skip shield if a 2H weapon is active — would unequip the weapon causing a loop.
            Item wSlot = eqdInv.getItem((short) -11);
            if (slot == (short) -10 && wSlot != null && ii.isTwoHanded(wSlot.getItemId())) continue;
            // Skip pants if an overall occupies the top+bottom slot.
            if (slot == (short) -6 && overallEquipped) continue;

            Equip current = (Equip) eqdInv.getItem(slot);
            Equip effectiveCurrent = slot == (short) -11 ? compatibleWeaponOrNull(bot, ii, current) : current;
            Equip currentWeapon = (Equip) eqdInv.getItem((short) -11);
            Equip effectiveWeapon = compatibleWeaponOrNull(bot, ii, currentWeapon);
            // Lookahead applies only when scoring non-weapon slots.
            List<Equip> lookahead = slot == (short) -11 ? null : weaponLookaheadPool;
            Equip best = findBestWithLookahead(bot, ii, weaponType, effectiveCurrent,
                                                bySlot.get(slot), mobProfile, lookahead, effectiveWeapon);
            // 2H weapon displaces shield — only upgrade if 2H beats current weapon+shield combined.
            if (slot == (short) -11 && best != null && best != current && ii.isTwoHanded(best.getItemId())) {
                Equip shield = (Equip) eqdInv.getItem((short) -10);
                if (compareScores(scoreEquipFull(bot, ii, weaponType, effectiveCurrent, best, shield, mobProfile, null, effectiveWeapon),
                                  scoreEquipFull(bot, ii, weaponType, effectiveCurrent, effectiveCurrent, null, mobProfile, null, effectiveWeapon)) <= 0) best = effectiveCurrent;
            }
            // Overall displaces pants — only upgrade if overall beats current top+pants combined.
            if (slot == (short) -5 && best != null && best != current && isOverall(best, ii)) {
                Equip pants = (Equip) eqdInv.getItem((short) -6);
                if (compareScores(scoreEquipFull(bot, ii, weaponType, effectiveCurrent, best, pants, mobProfile, lookahead, effectiveWeapon),
                                  scoreEquipFull(bot, ii, weaponType, effectiveCurrent, effectiveCurrent, null, mobProfile, lookahead, effectiveWeapon)) <= 0) best = effectiveCurrent;
            }
            // Top+pants combo may beat an overall even when no top beats it alone.
            if (slot == (short) -5 && best == effectiveCurrent && isOverall(current, ii)) {
                List<Equip> topCands = bySlot.getOrDefault(slot, List.of()).stream()
                        .filter(e -> !isOverall(e, ii)).collect(Collectors.toList());
                List<Equip> pantsCands = bySlot.getOrDefault((short) -6, List.of());
                if (!topCands.isEmpty() && !pantsCands.isEmpty()) {
                    Equip bestTop = findBestWithLookahead(bot, ii, weaponType, null, topCands, mobProfile, lookahead, effectiveWeapon);
                    Equip bestPants = findBestWithLookahead(bot, ii, weaponType, null, pantsCands, mobProfile, lookahead, effectiveWeapon);
                    if (bestTop != null && bestPants != null &&
                            compareScores(
                                scoreEquipFull(bot, ii, weaponType, current, bestTop, null, mobProfile, lookahead, effectiveWeapon),
                                scoreEquipFull(bot, ii, weaponType, current, current, bestPants, mobProfile, lookahead, effectiveWeapon)) > 0) {
                        best = bestTop;
                    }
                }
            }
            if (best != null && best != current) {
                InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, best.getPosition(), slot, (short) 1);
                changed = true;
                if (slot == -11) weaponType = currentWeaponType(bot, ii);
                if (slot == -5)  overallEquipped = isOverall(eqdInv.getItem((short) -5), ii);
            }
        }

        // Ring slots: greedy — find best unequipped ring per slot from a shared pool
        if (bySlot.containsKey((short) -12)) {
            if (autoEquipRings(bot, ii, weaponType, bySlot.get((short) -12), eqdInv, mobProfile,
                                weaponLookaheadPool)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * True if the bot meets job/level/fame for {@code equip} but fails only on stat
     * requirements. Used to seed the weapon lookahead pool.
     */
    private static boolean statOnlyBlocked(Character bot, ItemInformationProvider ii, Equip equip) {
        // Pass huge stat values: if it still fails, level/job/fame is blocking, skip.
        return ii.meetsEquipRequirements(equip, bot.getJob(), bot.getLevel(),
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, bot.getFame());
    }

    /**
     * Builds a weapon lookahead pool from the given equip inventory. Includes both
     * currently-wearable weapons and stat-only-blocked weapons so that non-weapon slot
     * scoring can benchmark against any weapon the character could unlock.
     */
    private static List<Equip> buildWeaponLookaheadPool(Character bot, ItemInformationProvider ii, Inventory equipInv) {
        List<Equip> pool = new ArrayList<>();
        for (Item item : equipInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            if (!(item instanceof Equip equip)) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            short primary = (short) eslot.getPrimarySlot();
            if (primary != (short) -11) continue;
            if (!isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId()))) continue;
            if (ii.canWearEquipment(bot, equip, primary)) {
                pool.add(equip);
            } else if (statOnlyBlocked(bot, ii, equip)) {
                pool.add(equip);
            }
        }
        return pool;
    }

    static List<EquipRecommendation> findRecommendedEquips(Character receiver, Character holder) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory holderEquipInv = holder.getInventory(InventoryType.EQUIP);
        Inventory receiverEquippedInv = receiver.getInventory(InventoryType.EQUIPPED);
        Inventory receiverEquipInv = receiver.getInventory(InventoryType.EQUIP);

        WeaponType weaponType = currentWeaponType(receiver, ii);

        // Build weapon lookahead pool from the receiver's unequipped weapons — mirrors autoEquipPass.
        // This ensures sibling suggestions score stat items (rings, gloves, etc.) against weapons
        // the receiver could unlock, not just the currently-equipped weapon. Without this, a
        // +STR ring could be recommended over a +ACC ring even when the +ACC ring unlocks a
        // higher-tier weapon producing more expected DPS (hitrate-adjusted).
        List<Equip> weaponLookaheadPool = buildWeaponLookaheadPool(receiver, ii, receiverEquipInv);

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
        for (Map.Entry<Short, List<Equip>> e : bySlot.entrySet()) {
            e.setValue(pruneDominated(e.getValue()));
        }

        MapDamageProfile mobProfile = MapDamageProfile.snapshot(receiver);
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
            Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
            // Lookahead applies only when scoring non-weapon slots (mirrors autoEquipPass).
            List<Equip> lookahead = slot == (short) -11 ? null : weaponLookaheadPool;
            Equip best = findBestWithLookahead(receiver, ii, weaponType, effectiveCurrent,
                                                bySlot.get(slot), mobProfile, lookahead, effectiveWeapon);
            // 2H weapon displaces shield — only recommend if 2H beats current weapon+shield combined.
            if (slot == (short) -11 && best != null && best != current && ii.isTwoHanded(best.getItemId())) {
                Equip shield = (Equip) receiverEquippedInv.getItem((short) -10);
                if (compareScores(scoreEquipFull(receiver, ii, weaponType, effectiveCurrent, best, shield, mobProfile, null, effectiveWeapon),
                                  scoreEquipFull(receiver, ii, weaponType, effectiveCurrent, effectiveCurrent, null, mobProfile, null, effectiveWeapon)) <= 0) best = effectiveCurrent;
            }
            // Overall displaces pants — only recommend if overall beats current top+pants combined.
            if (slot == (short) -5 && best != null && best != current && isOverall(best, ii)) {
                Equip pants = (Equip) receiverEquippedInv.getItem((short) -6);
                if (compareScores(scoreEquipFull(receiver, ii, weaponType, effectiveCurrent, best, pants, mobProfile, lookahead, effectiveWeapon),
                                  scoreEquipFull(receiver, ii, weaponType, effectiveCurrent, effectiveCurrent, null, mobProfile, lookahead, effectiveWeapon)) <= 0) best = effectiveCurrent;
            }
            // Top+pants combo may beat an overall even when no top beats it alone.
            if (slot == (short) -5 && best == effectiveCurrent && isOverall(current, ii)) {
                List<Equip> topCands = bySlot.getOrDefault(slot, List.of()).stream()
                        .filter(e -> !isOverall(e, ii)).collect(Collectors.toList());
                List<Equip> pantsCands = bySlot.getOrDefault((short) -6, List.of());
                if (!topCands.isEmpty() && !pantsCands.isEmpty()) {
                    Equip bestTop = findBestWithLookahead(receiver, ii, weaponType, null, topCands, mobProfile, lookahead, effectiveWeapon);
                    Equip bestPants = findBestWithLookahead(receiver, ii, weaponType, null, pantsCands, mobProfile, lookahead, effectiveWeapon);
                    if (bestTop != null && bestPants != null &&
                            compareScores(
                                scoreEquipFull(receiver, ii, weaponType, current, bestTop, null, mobProfile, lookahead, effectiveWeapon),
                                scoreEquipFull(receiver, ii, weaponType, current, current, bestPants, mobProfile, lookahead, effectiveWeapon)) > 0) {
                        best = bestTop;
                    }
                }
            }
            if (best != null && best != current) {
                recommendations.add(new EquipRecommendation(slot, current, best));
                if (slot == (short) -5) overallRec = isOverall(best, ii);
            }
        }

        if (bySlot.containsKey((short) -12)) {
            recommendations.addAll(findRecommendedRings(receiver, ii, weaponType, bySlot.get((short) -12), receiverEquippedInv, mobProfile, weaponLookaheadPool));
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
        Inventory receiverEquipInv = receiver.getInventory(InventoryType.EQUIP);
        MapDamageProfile mobProfile = MapDamageProfile.snapshot(receiver);

        // Build weapon lookahead pool from receiver's unequipped weapons (mirrors findRecommendedEquips).
        List<Equip> weaponLookaheadPool = buildWeaponLookaheadPool(receiver, ii, receiverEquipInv);

        if (isRingSlot(primarySlot)) {
            return findRecommendedRingForItem(receiver, ii, weaponType, candidate, receiverEquippedInv, mobProfile, weaponLookaheadPool);
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

        // Pants need a top — when an overall is worn, only suggest if pants+best-available-top beats it.
        if (primarySlot == (short) -6 && isOverall(receiverEquippedInv.getItem((short) -5), ii)) {
            Equip overall = (Equip) receiverEquippedInv.getItem((short) -5);
            Equip effWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
            List<Equip> topCands = new ArrayList<>();
            for (Item it : receiverEquipInv.list()) {
                if (!(it instanceof Equip e)) continue;
                String ts = ii.getEquipmentSlot(e.getItemId());
                if (ts == null) continue;
                short ps = (short) EquipSlot.getFromTextSlot(ts).getPrimarySlot();
                if (ps == (short) -5 && !isOverall(e, ii) && ii.canWearEquipment(receiver, e, ps)) topCands.add(e);
            }
            if (topCands.isEmpty()) return null;
            Equip bestTop = findBestWithLookahead(receiver, ii, weaponType, null, topCands, mobProfile, weaponLookaheadPool, effWeapon);
            if (bestTop == null || compareScores(
                    scoreEquipFull(receiver, ii, weaponType, overall, bestTop, null, mobProfile, weaponLookaheadPool, effWeapon),
                    scoreEquipFull(receiver, ii, weaponType, overall, overall, candidate, mobProfile, weaponLookaheadPool, effWeapon)) <= 0) return null;
        }

        Equip current = (Equip) receiverEquippedInv.getItem(primarySlot);
        if (primarySlot == (short) -11) {
            current = compatibleWeaponOrNull(receiver, ii, current);
        }
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        // Lookahead applies only when scoring non-weapon slots.
        List<Equip> lookahead = primarySlot == (short) -11 ? null : weaponLookaheadPool;
        EquipScore candidateScore = scoreEquipFull(receiver, ii, weaponType, current, candidate, null, mobProfile, lookahead, effectiveWeapon);
        // Overall displaces pants — score candidate against current top+pants combined.
        if (primarySlot == (short) -5 && isOverall(candidate, ii)) {
            Equip pants = (Equip) receiverEquippedInv.getItem((short) -6);
            candidateScore = scoreEquipFull(receiver, ii, weaponType, current, candidate, pants, mobProfile, lookahead, effectiveWeapon);
        }
        EquipScore currentScore = scoreEquipFull(receiver, ii, weaponType, current, current, null, mobProfile, lookahead, effectiveWeapon);
        if (compareScores(candidateScore, currentScore) <= 0) {
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
            case "hat", "helm", "helmet" -> new short[]{-1};
            case "face", "faceacc", "faceaccessory" -> new short[]{-2};
            case "eye", "eyeacc", "eyeaccessory", "eyepiece" -> new short[]{-3};
            case "ear", "earring", "earrings" -> new short[]{-4};
            case "top", "shirt", "overall" -> new short[]{-5};
            case "bottom", "pants" -> new short[]{-6};
            case "shoes", "boots" -> new short[]{-7};
            case "glove", "gloves" -> new short[]{-8};
            case "cape" -> new short[]{-9};
            case "shield", "offhand" -> new short[]{-10};
            case "weapon", "wep" -> new short[]{-11};
            case "ring" -> RING_SLOTS.clone();
            case "ring1" -> new short[]{-12};
            case "ring2" -> new short[]{-13};
            case "ring3" -> new short[]{-15};
            case "ring4" -> new short[]{-16};
            case "petwear" -> new short[]{-14};
            case "pendant" -> new short[]{-17};
            case "medal" -> new short[]{-21};
            case "belt" -> new short[]{-22};
            default -> new short[0];
        };
    }

    private static boolean autoEquipRings(Character bot, ItemInformationProvider ii, WeaponType wt,
                                           List<Equip> candidates, Inventory eqdInv,
                                           MapDamageProfile mobProfile,
                                           List<Equip> weaponLookaheadPool) {
        boolean changed = false;
        List<Equip> pool = new ArrayList<>(candidates);
        Equip effectiveWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        for (short rs : RING_SLOTS) {
            Equip current = (Equip) eqdInv.getItem(rs);
            List<Equip> eligible = pool.stream()
                    .filter(c -> ii.canWearEquipment(bot, c, rs))
                    .collect(Collectors.toList());
            Equip best = findBestWithLookahead(bot, ii, wt, current, eligible, mobProfile,
                                                weaponLookaheadPool, effectiveWeapon);
            if (best != null && best != current) {
                InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP, best.getPosition(), rs, (short) 1);
                pool.remove(best);
                changed = true;
            }
        }
        return changed;
    }

    private static List<EquipRecommendation> findRecommendedRings(Character receiver, ItemInformationProvider ii, WeaponType wt,
                                                                  List<Equip> candidates, Inventory receiverEquippedInv,
                                                                  MapDamageProfile mobProfile,
                                                                  List<Equip> weaponLookaheadPool) {
        List<EquipRecommendation> recommendations = new ArrayList<>();
        List<Equip> pool = new ArrayList<>(candidates);
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        for (short ringSlot : RING_SLOTS) {
            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            List<Equip> eligible = pool.stream()
                    .filter(candidate -> ii.canWearEquipment(receiver, candidate, ringSlot))
                    .collect(Collectors.toList());
            Equip best = findBestWithLookahead(receiver, ii, wt, current, eligible, mobProfile, weaponLookaheadPool, effectiveWeapon);
            if (best != null && best != current) {
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
                                                                  Inventory receiverEquippedInv,
                                                                  MapDamageProfile mobProfile,
                                                                  List<Equip> weaponLookaheadPool) {
        EquipRecommendation bestRecommendation = null;
        EquipScore bestScore = null;
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        for (short ringSlot : RING_SLOTS) {
            if (!ii.canWearEquipment(receiver, candidate, ringSlot)) {
                continue;
            }

            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            EquipScore currentScore = scoreEquipFull(receiver, ii, wt, current, current, null, mobProfile, weaponLookaheadPool, effectiveWeapon);
            EquipScore candidateScore = scoreEquipFull(receiver, ii, wt, current, candidate, null, mobProfile, weaponLookaheadPool, effectiveWeapon);
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

    /**
     * Picks the best candidate for a slot. {@code weaponLookaheadPool} is non-null only when
     * scoring a non-weapon slot — its contents are weapons (currently wearable + stat-blocked)
     * that the candidate may unlock. Pass null to disable lookahead.
     */
    private static Equip findBestWithLookahead(Character bot, ItemInformationProvider ii, WeaponType wt,
                                                Equip current, List<Equip> candidates,
                                                MapDamageProfile mobProfile,
                                                List<Equip> weaponLookaheadPool, Equip currentWeapon) {
        if (candidates == null || candidates.isEmpty()) return null;
        Equip best = current;
        EquipScore bestScore = scoreEquipFull(bot, ii, wt, current, current, null, mobProfile,
                                              weaponLookaheadPool, currentWeapon);
        for (Equip c : candidates) {
            EquipScore cs = scoreEquipFull(bot, ii, wt, current, c, null, mobProfile,
                                            weaponLookaheadPool, currentWeapon);
            if (compareScores(cs, bestScore) > 0) {
                best = c;
                bestScore = cs;
            }
        }
        return best;
    }

    /**
     * Mob-aware score with optional weapon-slot lookahead. {@code loss} simulates an extra
     * displaced item (pants on overall, shield on 2H). When {@code weaponLookaheadPool} is
     * non-null and the candidate is not itself a weapon, this method probes the pool for
     * the best weapon wearable under the simulated stat totals; if that beats the current
     * weapon, the score reflects the unlocked weapon's damage. Otherwise behavior matches
     * a straight "swap candidate for replacing" damage projection.
     */
    private static EquipScore scoreEquipFull(Character bot, ItemInformationProvider ii, WeaponType wt,
                                              Equip replacing, Equip candidate, Equip loss,
                                              MapDamageProfile mobProfile,
                                              List<Equip> weaponLookaheadPool, Equip currentWeapon) {
        StatSnapshot sim = StatSnapshot.of(bot).swap(replacing, candidate);
        if (loss != null) sim = sim.swap(loss, null);

        boolean candidateIsWeapon = candidate != null && ItemConstants.isWeapon(candidate.getItemId());
        Equip simWeapon = currentWeapon;
        WeaponType simWt = wt;
        if (candidateIsWeapon) {
            simWeapon = candidate;
            WeaponType cWt = ii.getWeaponType(candidate.getItemId());
            if (cWt != null) simWt = cWt;
        } else if (weaponLookaheadPool != null && !weaponLookaheadPool.isEmpty()) {
            // Probe lookahead: does swapping a stat-blocked weapon into the simulated state
            // produce more damage than keeping the current weapon?
            int baselineDmg = damageWith(sim, ii, simWt, mobProfile);
            int bestDmg = baselineDmg;
            StatSnapshot bestSim = sim;
            for (Equip w : weaponLookaheadPool) {
                if (w == currentWeapon) continue;
                if (!ii.meetsEquipRequirements(w, sim.job(), sim.level(),
                        sim.str(), sim.dex(), sim.int_(), sim.luk(), sim.fame())) continue;
                StatSnapshot simWithW = sim.swap(currentWeapon, w);
                WeaponType wt2 = ii.getWeaponType(w.getItemId());
                int d = damageWith(simWithW, ii, wt2 != null ? wt2 : simWt, mobProfile);
                int cycleMs = weaponCycleMs(w.getItemId());
                if (cycleMs > 0) d = (int) (d * 1000.0 / cycleMs);
                if (d > bestDmg) {
                    bestDmg = d;
                    simWeapon = w;
                    simWt = wt2 != null ? wt2 : simWt;
                    bestSim = simWithW;
                }
            }
            sim = bestSim;
        }

        if (isMageJob(sim.job())) {
            return new EquipScore(magicScore(sim), defScore(candidate), usefulStatSum(candidate, sim.job()));
        }
        if (simWt == null) {
            return new EquipScore(0, defScore(candidate), usefulStatSum(candidate, sim.job()));
        }
        int dmg = damageWith(sim, ii, simWt, mobProfile);
        // DPS scaling: scale by the cycle of the weapon currently in the simulated state.
        int cycleMs = simWeapon != null ? weaponCycleMs(simWeapon.getItemId()) : 0;
        if (cycleMs > 0) dmg = (int) (dmg * 1000.0 / cycleMs);
        return new EquipScore(dmg, defScore(candidate), usefulStatSum(candidate, sim.job()));
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
     * Computes max-base damage from a simulated stat snapshot, then if a {@link MapDamageProfile}
     * is available approximates expected per-hit damage as the integral of
     * {@code max(1, uniform[min,max] - wdef)} where min ≈ max/2 (low-mastery proxy). Falls back
     * to raw max when no map context (town, recommendations from trade).
     */
    private static int damageWith(StatSnapshot sim, ItemInformationProvider ii, WeaponType wtype,
                                   MapDamageProfile mobProfile) {
        if (wtype == null) return 0;
        WeaponType effective = wtype;
        if (sim.job() != null && sim.job().isA(Job.THIEF) && effective == WeaponType.DAGGER_OTHER) {
            effective = WeaponType.DAGGER_THIEVES;
        }
        int main, sec;
        if (effective == WeaponType.BOW || effective == WeaponType.CROSSBOW || effective == WeaponType.GUN) {
            main = sim.dex(); sec = sim.str();
        } else if (effective == WeaponType.CLAW || effective == WeaponType.DAGGER_THIEVES) {
            main = sim.luk(); sec = sim.dex() + sim.str();
        } else {
            main = sim.str(); sec = sim.dex();
        }
        // Spear/polearm: bot alternates stab and swing — average the two multipliers.
        double mult = switch (effective) {
            case SPEAR_STAB     -> (WeaponType.SPEAR_STAB.getMaxDamageMultiplier()    + WeaponType.SPEAR_SWING.getMaxDamageMultiplier())  / 2.0;
            case POLE_ARM_SWING -> (WeaponType.POLE_ARM_SWING.getMaxDamageMultiplier() + WeaponType.POLE_ARM_STAB.getMaxDamageMultiplier()) / 2.0;
            default             -> effective.getMaxDamageMultiplier();
        };
        int rawMax = (int) Math.ceil((mult * main + sec) / 100.0 * sim.watk());
        if (mobProfile == null) {
            return rawMax;
        }
        double expectedAfterDef = expectedDamageAfterDef(rawMax, mobProfile.mobWdef());
        double hitChance;
        try {
            hitChance = CombatFormulaProvider.getInstance().calculatePhysicalMobHitChance(
                    sim.totalAcc(), sim.level(), mobProfile.mobLevel(), mobProfile.mobAvoid());
        } catch (Throwable t) {
            hitChance = 1.0;
        }
        // Scale by 1000 so hitChance and small expectedAfterDef differences survive the int cast.
        return Math.max(1, (int) Math.round(expectedAfterDef * hitChance * 1000.0));
    }

    /**
     * Expected per-hit damage when each roll is {@code uniform[rawMax/2, rawMax] - wdef} clamped
     * to 1. The previous {@code (rawMin + rawMax)/2 - wdef} approximation collapsed to the floor
     * once wdef exceeded the midpoint, erasing the upper-tail damage that higher STR/WATK
     * actually delivers — so two candidates with different rawMax both scored ≈1 against a
     * high-WDEF mob, even when one cleared the defense and the other barely did.
     */
    static double expectedDamageAfterDef(int rawMax, int wdef) {
        if (rawMax <= 0) return 1.0;
        double rawMin = rawMax * 0.5;
        if (wdef <= rawMin) {
            return Math.max(1.0, (rawMin + rawMax) / 2.0 - wdef);
        }
        if (wdef >= rawMax) {
            return 1.0;
        }
        // Partial clamp: fraction below wdef floors to 1; above-tail integrates as a triangle.
        double range = rawMax - rawMin;
        double clampedFraction = (wdef - rawMin) / range;
        double aboveTail = rawMax - wdef;
        double aboveContribution = (aboveTail * aboveTail) / (2.0 * range);
        return Math.max(1.0, clampedFraction + aboveContribution);
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

    private static int defScore(Equip e)  { return e != null ? e.getWdef() + e.getMdef() : 0; }

    static int usefulStatSum(Equip e, Job job) {
        if (e == null) return 0;
        if (isMageJob(job)) {
            return e.getInt() * 4 + e.getMatk() * 5 + e.getLuk()
                    + e.getMdef() + e.getHp() + e.getMp();
        }
        return e.getStr() + e.getDex() + e.getInt() + e.getLuk()
             + e.getWatk() + e.getMatk() + e.getWdef() + e.getMdef()
             + e.getAcc() + e.getAvoid() + e.getSpeed() + e.getHp() + e.getMp();
    }

    private static int magicScore(StatSnapshot sim) {
        return sim.magic() * 100 + sim.int_();
    }

    static boolean isMageJob(Job job) {
        return job == Job.MAGICIAN
                || job == Job.FP_WIZARD || job == Job.FP_MAGE || job == Job.FP_ARCHMAGE
                || job == Job.IL_WIZARD || job == Job.IL_MAGE || job == Job.IL_ARCHMAGE
                || job == Job.CLERIC || job == Job.PRIEST || job == Job.BISHOP;
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

    // ---- helper records / pruning ------------------------------------------------------

    /**
     * Snapshot of bot totals plus job/level/fame for non-mutating wearability checks.
     * {@code flatAcc} = total accuracy minus its derived (dex/luk) component, so {@link #swap}
     * can recompute total accuracy after stat changes without re-reading the live bot state.
     */
    private record StatSnapshot(int str, int dex, int int_, int luk, int watk, int magic, int flatAcc,
                                int level, int fame, Job job) {
        static StatSnapshot of(Character bot) {
            int totalAcc = CombatFormulaProvider.getInstance().getTotalAccuracy(bot);
            int derived = (int) Math.floor(bot.getTotalDex() * 0.8d + bot.getTotalLuk() * 0.5d);
            int flatAcc = Math.max(0, totalAcc - Math.max(0, derived));
            return new StatSnapshot(bot.getTotalStr(), bot.getTotalDex(), bot.getTotalInt(),
                    bot.getTotalLuk(), bot.getTotalWatk(), bot.getTotalMagic(), flatAcc,
                    bot.getLevel(), bot.getFame(), bot.getJob());
        }

        StatSnapshot swap(Equip removed, Equip added) {
            return new StatSnapshot(
                    str + d(added, removed, e -> (int) e.getStr()),
                    dex + d(added, removed, e -> (int) e.getDex()),
                    int_ + d(added, removed, e -> (int) e.getInt()),
                    luk + d(added, removed, e -> (int) e.getLuk()),
                    watk + d(added, removed, e -> (int) e.getWatk()),
                    magic + d(added, removed, e -> (int) e.getInt()) + d(added, removed, e -> (int) e.getMatk()),
                    flatAcc + d(added, removed, e -> (int) e.getAcc()),
                    level, fame, job);
        }

        int totalAcc() {
            int derived = (int) Math.floor(dex * 0.8d + luk * 0.5d);
            return Math.max(0, derived + flatAcc);
        }

        private static int d(Equip a, Equip r, ToIntFunction<Equip> g) {
            return (a != null ? g.applyAsInt(a) : 0) - (r != null ? g.applyAsInt(r) : 0);
        }
    }

    /**
     * Stats of the highest-level non-friendly mob on the bot's map. Includes currently
     * alive mobs and normal spawn templates, so an equip pass that runs while the room is
     * briefly clear still benchmarks against the map's mobs instead of raw damage.
     * Returns null when no map context is available or no map mobs are present.
     */
    record MapDamageProfile(int mobWdef, int mobAvoid, int mobLevel) {
        static MapDamageProfile snapshot(Character bot) {
            return fromStats(collectCandidates(bot));
        }

        static MapDamageProfile snapshotByAvoid(Character bot) {
            return fromStatsByAvoid(collectCandidates(bot));
        }

        private static List<MonsterStats> collectCandidates(Character bot) {
            if (bot == null) return null;
            MapleMap map;
            try { map = bot.getMap(); } catch (Throwable t) { return null; }
            if (map == null) return null;
            List<MonsterStats> candidates = new ArrayList<>();
            List<Monster> mobs;
            try { mobs = map.getAllMonsters(); } catch (Throwable t) { return null; }
            if (mobs != null) {
                for (Monster m : mobs) {
                    if (m == null || !m.isAlive()) continue;
                    MonsterStats s = m.getStats();
                    if (s != null) candidates.add(s);
                }
            }
            try {
                for (SpawnPoint spawn : map.getMonsterSpawn()) {
                    if (spawn == null || spawn.getDenySpawn() || spawn.getMobTime() < 0) continue;
                    Monster template = LifeFactory.getMonster(spawn.getMonsterId());
                    if (template != null && template.getStats() != null) {
                        candidates.add(template.getStats());
                    }
                }
            } catch (Throwable ignored) {
                // Live mobs are enough; spawn templates are only a fallback/stabilizer.
            }
            return candidates;
        }

        static MapDamageProfile fromStats(List<MonsterStats> candidates) {
            if (candidates == null || candidates.isEmpty()) return null;
            MonsterStats picked = null;
            for (MonsterStats s : candidates) {
                if (s == null || s.isFriendly()) continue;
                if (picked == null
                        || s.getLevel() > picked.getLevel()
                        || (s.getLevel() == picked.getLevel() && s.getAvoidability() > picked.getAvoidability())
                        || (s.getLevel() == picked.getLevel()
                            && s.getAvoidability() == picked.getAvoidability()
                            && s.getPDDamage() > picked.getPDDamage())) {
                    picked = s;
                }
            }
            if (picked == null) return null;
            return new MapDamageProfile(picked.getPDDamage(), picked.getAvoidability(), picked.getLevel());
        }

        static MapDamageProfile fromStatsByAvoid(List<MonsterStats> candidates) {
            if (candidates == null || candidates.isEmpty()) return null;
            MonsterStats picked = null;
            for (MonsterStats s : candidates) {
                if (s == null || s.isFriendly()) continue;
                if (picked == null
                        || s.getAvoidability() > picked.getAvoidability()
                        || (s.getAvoidability() == picked.getAvoidability() && s.getLevel() > picked.getLevel())) {
                    picked = s;
                }
            }
            if (picked == null) return null;
            return new MapDamageProfile(picked.getPDDamage(), picked.getAvoidability(), picked.getLevel());
        }
    }

    /**
     * Drops items strictly dominated by another in {@code items} (every relevant stat ≤,
     * at least one strictly less). Safe within a currently-wearable pool: legality already
     * passed for every item, so the surviving Pareto front preserves all useful trade-offs.
     * Skipped when the pool has 0 or 1 items.
     */
    private static List<Equip> pruneDominated(List<Equip> items) {
        if (items == null || items.size() <= 1) return items;
        List<Equip> kept = new ArrayList<>(items.size());
        for (Equip a : items) {
            boolean dominated = false;
            for (Equip b : items) {
                if (a == b) continue;
                if (dominates(b, a)) { dominated = true; break; }
            }
            if (!dominated) kept.add(a);
        }
        return kept.isEmpty() ? items : kept;
    }

    private static boolean dominates(Equip b, Equip a) {
        int[] bs = statVec(b);
        int[] as = statVec(a);
        boolean strictlyBetter = false;
        for (int i = 0; i < bs.length; i++) {
            if (bs[i] < as[i]) return false;
            if (bs[i] > as[i]) strictlyBetter = true;
        }
        return strictlyBetter;
    }

    private static int[] statVec(Equip e) {
        return new int[]{e.getStr(), e.getDex(), e.getInt(), e.getLuk(),
                          e.getWatk(), e.getMatk(), e.getWdef(), e.getMdef(),
                          e.getAcc(), e.getAvoid(), e.getHp(), e.getMp(),
                          e.getSpeed(), e.getJump()};
    }
}
