package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.inventory.EquipSlot;
import server.ItemInformationProvider;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationScope;
import server.agents.integration.InventoryGateway;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent-owned optimizer orchestration for recommendation and offered-item paths.
 */
public final class AgentEquipmentOptimizationService {
    private AgentEquipmentOptimizationService() {
    }

    public static AgentEquipmentOptimizerResult runOptimizerWithExtras(Character agent, Collection<Equip> extras) {
        return runOptimizerWithExtras(agent, extras, RecommendationScope.IMMEDIATE);
    }

    public static AgentEquipmentOptimizerResult runOptimizerWithExtras(Character agent,
                                                                       Collection<Equip> extras,
                                                                       RecommendationScope scope) {
        ItemInformationProvider itemInfo = ItemInformationProvider.getInstance();
        InventoryGateway inventory = CosmicAgentServerAdapter.INSTANCE.inventory();
        Inventory equipInventory = agent.getInventory(InventoryType.EQUIP);
        Inventory equippedInventory = agent.getInventory(InventoryType.EQUIPPED);
        AgentMapDamageProfile mob = AgentMapDamageProfile.snapshotByAvoid(agent);

        Map<Short, List<Equip>> bySlot = scope == RecommendationScope.IMMEDIATE
                ? collectAutoEquipCandidates(agent, itemInfo, equipInventory, equippedInventory, null)
                : collectFutureEquipCandidates(agent, itemInfo, equipInventory, equippedInventory);
        AgentEquipmentRecommendationPolicy.RecommendationHooks recommendationHooks =
                AgentEquipmentRecommendationPolicy.RecommendationHooks.from(inventory);
        for (Equip extra : extras) {
            if (extra == null || itemInfo.isCash(extra.getItemId())) {
                continue;
            }
            String textSlot = itemInfo.getEquipmentSlot(extra.getItemId());
            if (textSlot == null) {
                continue;
            }
            EquipSlot equipSlot = EquipSlot.getFromTextSlot(textSlot);
            if (equipSlot == null || equipSlot == EquipSlot.PET_EQUIP) {
                continue;
            }
            short primarySlot = (short) equipSlot.getPrimarySlot();
            if (primarySlot == 0) {
                continue;
            }
            if (primarySlot == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent,
                    itemInfo.getWeaponType(extra.getItemId()))) {
                continue;
            }
            if (!AgentEquipmentRecommendationPolicy.isRecommendationCandidate(
                    agent, recommendationHooks, extra, primarySlot, scope)) {
                continue;
            }
            short key = AgentEquipmentSlotResolver.isRingSlot(primarySlot) ? (short) -12 : primarySlot;
            List<Equip> pool = bySlot.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!pool.contains(extra)) {
                pool.add(extra);
            }
        }

        Job receiverJob = agent.getJob();
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, itemInfo);
        for (Map.Entry<Short, List<Equip>> entry : bySlot.entrySet()) {
            entry.setValue(pruneDominatedWithReqs(itemInfo, entry.getValue(), receiverJob, reqRel));
        }

        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item item : equippedInventory.list()) {
            if (item instanceof Equip equip && !itemInfo.isCash(equip.getItemId())) {
                currentBySlot.put(equip.getPosition(), equip);
            }
        }

        List<Short> dpSlots = AgentEquipmentSlotResolver.buildDpSlots(bySlot, currentBySlot);

        List<Equip> weaponPool = new ArrayList<>(bySlot.getOrDefault((short) -11, List.of()));
        Equip currentWeapon = compatibleWeaponOrNull(agent, itemInfo, (Equip) equippedInventory.getItem((short) -11));
        if (currentWeapon != null && !weaponPool.contains(currentWeapon)) {
            weaponPool.add(currentWeapon);
        }
        if (weaponPool.isEmpty()) {
            weaponPool.add(null);
        }

        AgentEquipmentStatSnapshot naked = nakedBase(agent, itemInfo, equippedInventory);
        AgentEquipmentOptimizerHooks hooks = scope == RecommendationScope.IMMEDIATE
                ? AgentEquipmentOptimizerHooks.from(itemInfo)
                : AgentEquipmentOptimizerHooks.futureFrom(itemInfo, agent);
        Map<Short, Equip> bestPicks = null;
        AgentEquipmentScore bestScore = null;
        Equip bestWeapon = null;
        for (Equip weapon : weaponPool) {
            AgentEquipmentDpResult result = AgentEquipmentOptimizer.solveForWeapon(
                    agent, hooks, naked, weapon, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (result == null) {
                continue;
            }
            if (bestScore == null || AgentEquipmentOptimizer.compareScores(result.score(), bestScore) > 0) {
                bestScore = result.score();
                bestPicks = result.picks();
                bestWeapon = weapon;
            }
        }
        if (bestPicks == null && !weaponPool.contains(null)) {
            AgentEquipmentDpResult result = AgentEquipmentOptimizer.solveForWeapon(
                    agent, hooks, naked, null, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (result != null) {
                bestPicks = result.picks();
                bestWeapon = null;
            }
        }
        return new AgentEquipmentOptimizerResult(bestWeapon, bestPicks != null ? bestPicks : Map.of());
    }

    private static Map<Short, List<Equip>> collectAutoEquipCandidates(Character agent,
                                                                      ItemInformationProvider itemInfo,
                                                                      Inventory equipInventory,
                                                                      Inventory equippedInventory,
                                                                      Item pendingOffer) {
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : equipInventory.list()) {
            if (itemInfo.isCash(item.getItemId())) {
                continue;
            }
            if (item == pendingOffer) {
                continue;
            }
            if (!(item instanceof Equip equip)) {
                continue;
            }
            String textSlot = itemInfo.getEquipmentSlot(item.getItemId());
            EquipSlot equipSlot = EquipSlot.getFromTextSlot(textSlot);
            if (equipSlot == null || equipSlot == EquipSlot.PET_EQUIP) {
                continue;
            }
            short primary = (short) equipSlot.getPrimarySlot();
            if (primary == 0) {
                continue;
            }
            if (primary == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, itemInfo.getWeaponType(equip.getItemId()))) {
                continue;
            }
            if (itemInfo.canWearEquipment(agent, equip, primary)
                    || AgentEquipmentReservePolicy.statOnlyBlocked(agent, itemInfo, equip)) {
                bySlot.computeIfAbsent(primary, ignored -> new ArrayList<>()).add(equip);
            }
        }
        for (Item item : equippedInventory.list()) {
            if (!(item instanceof Equip equip) || itemInfo.isCash(equip.getItemId())) {
                continue;
            }
            short position = equip.getPosition();
            if (position == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, itemInfo.getWeaponType(equip.getItemId()))) {
                continue;
            }
            short key = AgentEquipmentSlotResolver.isRingSlot(position) ? (short) -12 : position;
            List<Equip> pool = bySlot.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!pool.contains(equip)) {
                pool.add(equip);
            }
        }
        Job agentJob = agent.getJob();
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, itemInfo);
        for (Map.Entry<Short, List<Equip>> entry : bySlot.entrySet()) {
            entry.setValue(pruneDominatedWithReqs(itemInfo, entry.getValue(), agentJob, reqRel));
        }
        return bySlot;
    }

    private static Map<Short, List<Equip>> collectFutureEquipCandidates(Character agent,
                                                                        ItemInformationProvider itemInfo,
                                                                        Inventory equipInventory,
                                                                        Inventory equippedInventory) {
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : equipInventory.list()) {
            if (itemInfo.isCash(item.getItemId())) {
                continue;
            }
            if (!(item instanceof Equip equip)) {
                continue;
            }
            String textSlot = itemInfo.getEquipmentSlot(item.getItemId());
            EquipSlot equipSlot = EquipSlot.getFromTextSlot(textSlot);
            if (equipSlot == null || equipSlot == EquipSlot.PET_EQUIP) {
                continue;
            }
            short primary = (short) equipSlot.getPrimarySlot();
            if (primary == 0) {
                continue;
            }
            if (primary == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, itemInfo.getWeaponType(equip.getItemId()))) {
                continue;
            }
            if (!AgentEquipmentReservePolicy.futureOnlyBlocked(agent, itemInfo, equip)) {
                continue;
            }
            short key = AgentEquipmentSlotResolver.isRingSlot(primary) ? (short) -12 : primary;
            bySlot.computeIfAbsent(key, ignored -> new ArrayList<>()).add(equip);
        }
        for (Item item : equippedInventory.list()) {
            if (!(item instanceof Equip equip) || itemInfo.isCash(equip.getItemId())) {
                continue;
            }
            short position = equip.getPosition();
            if (position == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, itemInfo.getWeaponType(equip.getItemId()))) {
                continue;
            }
            if (!AgentEquipmentReservePolicy.futureOnlyBlocked(agent, itemInfo, equip)) {
                continue;
            }
            short key = AgentEquipmentSlotResolver.isRingSlot(position) ? (short) -12 : position;
            List<Equip> pool = bySlot.computeIfAbsent(key, ignored -> new ArrayList<>());
            if (!pool.contains(equip)) {
                pool.add(equip);
            }
        }
        for (Map.Entry<Short, List<Equip>> entry : bySlot.entrySet()) {
            entry.setValue(pruneDominated(itemInfo, entry.getValue()));
        }
        return bySlot;
    }

    private static AgentEquipmentStatSnapshot nakedBase(Character agent,
                                                        ItemInformationProvider itemInfo,
                                                        Inventory equippedInventory) {
        AgentEquipmentStatSnapshot snapshot = AgentEquipmentStatSnapshot.of(agent);
        for (Item item : equippedInventory.list()) {
            if (item instanceof Equip equip && !itemInfo.isCash(equip.getItemId())) {
                snapshot = snapshot.swap(equip, null);
            }
        }
        return snapshot;
    }

    private static Equip compatibleWeaponOrNull(Character agent, ItemInformationProvider itemInfo, Equip equip) {
        if (equip == null) {
            return null;
        }
        return AgentWeaponCompatibilityPolicy.isWeaponCompatible(agent, itemInfo.getWeaponType(equip.getItemId()))
                ? equip
                : null;
    }

    private static boolean dominates(Equip better, Equip worse) {
        int[] betterStats = statVec(better);
        int[] worseStats = statVec(worse);
        boolean strictlyBetter = false;
        for (int i = 0; i < betterStats.length; i++) {
            if (betterStats[i] < worseStats[i]) {
                return false;
            }
            if (betterStats[i] > worseStats[i]) {
                strictlyBetter = true;
            }
        }
        return strictlyBetter;
    }

    private static List<Equip> pruneDominated(ItemInformationProvider itemInfo, List<Equip> items) {
        if (items == null || items.size() <= 1) {
            return items;
        }
        List<Equip> kept = new ArrayList<>(items.size());
        for (Equip candidate : items) {
            boolean dominated = false;
            for (Equip other : items) {
                if (candidate == other) {
                    continue;
                }
                if (!AgentEquipmentReservePolicy.sameFutureTrack(itemInfo, candidate, other)) {
                    continue;
                }
                if (dominates(other, candidate)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept.isEmpty() ? items : kept;
    }

    private static List<Equip> pruneDominatedWithReqs(ItemInformationProvider itemInfo,
                                                       List<Equip> items,
                                                       Job job,
                                                       boolean[] reqRel) {
        if (items == null || items.size() <= 1) {
            return items;
        }
        int[] priority = jobStatPriority(job);
        boolean isMage = AgentWeaponCompatibilityPolicy.isMageJob(job);
        boolean accRelevant = isAccRelevantJob(job);
        int itemCount = items.size();
        int[][] vectors = new int[itemCount][];
        int[] tiebreaks = new int[itemCount];
        for (int i = 0; i < itemCount; i++) {
            vectors[i] = dedupStatVec(items.get(i), priority, reqRel, isMage, accRelevant);
            tiebreaks[i] = dedupTiebreak(items.get(i), priority);
        }
        List<Equip> kept = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            Equip candidate = items.get(i);
            boolean dominated = false;
            for (int j = 0; j < itemCount; j++) {
                if (i == j) {
                    continue;
                }
                Equip other = items.get(j);
                if (!dedupDominatesPre(vectors[j], vectors[i], tiebreaks[j], tiebreaks[i])) {
                    continue;
                }
                if (!AgentEquipmentReservePolicy.reqsAtLeastAsEasy(itemInfo, other, candidate)) {
                    continue;
                }
                dominated = true;
                break;
            }
            if (!dominated) {
                kept.add(candidate);
            }
        }
        return kept.isEmpty() ? items : kept;
    }

    private static int[] jobStatPriority(Job job) {
        if (job == null) {
            return new int[]{0, 1};
        }
        if (AgentWeaponCompatibilityPolicy.isMageJob(job)) {
            return new int[]{2};
        }
        int id = job.getId();
        int niche = (id / 100) % 10;
        return switch (niche) {
            case 1 -> new int[]{0, 1};
            case 3 -> new int[]{1, 0};
            case 4 -> new int[]{3, 1, 0};
            case 5 -> (id / 10 == 51 || id / 10 == 151) ? new int[]{0, 1} : new int[]{1, 0};
            default -> new int[]{0, 1};
        };
    }

    private static boolean isAccRelevantJob(Job job) {
        if (job == null) {
            return false;
        }
        int id = job.getId();
        int niche = (id / 100) % 10;
        if (niche == 1) {
            return true;
        }
        if (niche == 5) {
            int sub = id / 10;
            return sub == 51 || sub == 151;
        }
        return false;
    }

    private static int[] dedupStatVec(Equip equip, int[] priority, boolean[] reqRel,
                                      boolean isMage, boolean accRelevant) {
        int primaryIdx = priority.length > 0 ? priority[0] : -1;
        boolean[] reqDim = new boolean[4];
        for (int i = 0; i < 4; i++) {
            reqDim[i] = reqRel != null && reqRel[i] && i != primaryIdx;
        }
        int count = (primaryIdx >= 0 ? 1 : 0) + 1 + (accRelevant ? 1 : 0);
        for (boolean dimension : reqDim) {
            if (dimension) {
                count++;
            }
        }
        int[] vector = new int[count];
        int index = 0;
        int primary = primaryIdx >= 0 ? statByIdx(equip, primaryIdx) : 0;
        if (primaryIdx >= 0) {
            vector[index++] = isMage ? primary : primary + equip.getWatk() * 2;
        }
        vector[index++] = isMage ? equip.getMatk() : equip.getWatk() + primary / 5;
        if (accRelevant) {
            vector[index++] = equip.getAcc() + equip.getDex() + (int) Math.round(equip.getLuk() * 0.5);
        }
        for (int i = 0; i < 4; i++) {
            if (reqDim[i]) {
                vector[index++] = statByIdx(equip, i);
            }
        }
        return vector;
    }

    private static int dedupTiebreak(Equip equip, int[] priority) {
        if (priority == null || priority.length == 0) {
            return 0;
        }
        int main = statByIdx(equip, priority[0]);
        int secondarySum = 0;
        for (int i = 1; i < priority.length; i++) {
            secondarySum += statByIdx(equip, priority[i]);
        }
        int effectivePrimary = main + (int) Math.round(secondarySum * 0.25);
        return effectivePrimary + equip.getWatk() * 4;
    }

    private static int statByIdx(Equip equip, int idx) {
        return switch (idx) {
            case 0 -> equip.getStr();
            case 1 -> equip.getDex();
            case 2 -> equip.getInt();
            case 3 -> equip.getLuk();
            default -> 0;
        };
    }

    private static boolean dedupDominatesPre(int[] betterStats, int[] worseStats, int betterTie, int worseTie) {
        boolean strict = false;
        for (int i = 0; i < betterStats.length; i++) {
            if (betterStats[i] < worseStats[i]) {
                return false;
            }
            if (betterStats[i] > worseStats[i]) {
                strict = true;
            }
        }
        if (strict) {
            return true;
        }
        return betterTie > worseTie;
    }

    private static int[] statVec(Equip equip) {
        return new int[]{
                equip.getStr(), equip.getDex(), equip.getInt(), equip.getLuk(),
                equip.getWatk(), equip.getMatk(), equip.getWdef(), equip.getMdef(),
                equip.getAcc(), equip.getAvoid(), equip.getHp(), equip.getMp(),
                equip.getSpeed(), equip.getJump()
        };
    }
}
