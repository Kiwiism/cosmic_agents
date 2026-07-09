package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import server.ItemInformationProvider;
import server.agents.capabilities.combat.data.AgentAttackDataProvider;
import server.agents.integration.InventoryGateway;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.combat.CombatFormulaProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed-weapon equipment DP optimizer lifted unchanged from the legacy bot implementation.
 */
public final class AgentEquipmentOptimizer {
    private static final int MAX_PARETO_STATES = 2000;

    private AgentEquipmentOptimizer() {
    }

    public static AgentEquipmentDpResult solveForWeapon(Character agent, ItemInformationProvider ii,
                                                        AgentEquipmentStatSnapshot naked, Equip weapon,
                                                        List<Short> dpSlots,
                                                        Map<Short, Equip> currentBySlot,
                                                        Map<Short, List<Equip>> bySlot,
                                                        AgentMapDamageProfile mob) {
        return solveForWeapon(agent, ii, naked, weapon, dpSlots, currentBySlot, bySlot,
                mob, scanReqRelevantDims(bySlot, ii));
    }

    public static AgentEquipmentDpResult solveForWeapon(Character agent, ItemInformationProvider ii,
                                                        AgentEquipmentStatSnapshot naked, Equip weapon,
                                                        List<Short> dpSlots,
                                                        Map<Short, Equip> currentBySlot,
                                                        Map<Short, List<Equip>> bySlot,
                                                        AgentMapDamageProfile mob,
                                                        boolean[] reqRel) {
        return solveForWeapon(agent, AgentEquipmentOptimizerHooks.from(ii), naked, weapon, dpSlots,
                currentBySlot, bySlot, mob, reqRel);
    }

    public static AgentEquipmentDpResult solveForWeapon(Character agent, AgentEquipmentOptimizerHooks hooks,
                                                        AgentEquipmentStatSnapshot naked, Equip weapon,
                                                        List<Short> dpSlots,
                                                        Map<Short, Equip> currentBySlot,
                                                        Map<Short, List<Equip>> bySlot,
                                                        AgentMapDamageProfile mob) {
        return solveForWeapon(agent, hooks, naked, weapon, dpSlots, currentBySlot, bySlot,
                mob, scanReqRelevantDims(bySlot, hooks));
    }

    public static AgentEquipmentDpResult solveForWeapon(Character agent, AgentEquipmentOptimizerHooks hooks,
                                                        AgentEquipmentStatSnapshot naked, Equip weapon,
                                                        List<Short> dpSlots,
                                                        Map<Short, Equip> currentBySlot,
                                                        Map<Short, List<Equip>> bySlot,
                                                        AgentMapDamageProfile mob,
                                                        boolean[] reqRel) {
        AgentEquipmentStatSnapshot init = weapon != null ? naked.swap(null, weapon) : naked;
        boolean is2H = weapon != null && hooks.isTwoHanded(weapon.getItemId());
        WeaponType wt = weapon != null ? hooks.getWeaponType(weapon.getItemId()) : null;
        boolean[] capHit = {false};

        int n = dpSlots.size();
        int overallIdx = dpSlots.indexOf((short) -5);
        DpNode start = pinSafeSingletonSlots(init, hooks, dpSlots, bySlot, n);
        List<DpNode> frontier = new ArrayList<>();
        frontier.add(start);

        for (int i = 0; i < n; i++) {
            short slot = dpSlots.get(i);
            if (start.picks[i] != null) {
                continue;
            }
            boolean ringSlot = AgentEquipmentSlotResolver.isRingSlot(slot);
            List<Equip> pool = ringSlot
                    ? bySlot.getOrDefault((short) -12, List.of())
                    : bySlot.getOrDefault(slot, List.of());
            List<DpNode> next = new ArrayList<>(Math.max(8, frontier.size() * (pool.size() + 1)));
            for (DpNode prev : frontier) {
                next.add(prev);
                if (slot == (short) -10 && is2H) continue;
                boolean blockedByOverall = (slot == (short) -6 && overallIdx >= 0
                        && prev.picks[overallIdx] != null
                        && hooks.isOverall(prev.picks[overallIdx].getItemId()));
                if (blockedByOverall) continue;
                candLoop:
                for (Equip cand : pool) {
                    if (cand == null) continue;
                    if (ringSlot) {
                        for (int j = 0; j < i; j++) {
                            if (AgentEquipmentSlotResolver.isRingSlot(dpSlots.get(j)) && prev.picks[j] == cand) {
                                continue candLoop;
                            }
                        }
                    }
                    AgentEquipmentStatSnapshot ns = prev.snap.swap(null, cand);
                    int nHp = prev.hp + cand.getHp();
                    int nMp = prev.mp + cand.getMp();
                    int nStat = prev.statSum + AgentEquipmentScoringPolicy.usefulStatSum(cand, ns.job());
                    Equip[] picks = prev.picks.clone();
                    picks[i] = cand;
                    next.add(new DpNode(ns, nHp, nMp, nStat, picks));
                }
            }
            frontier = paretoPruneNodes(next, capHit, hooks, dpSlots, wt, reqRel);
        }

        DpNode best = null;
        AgentEquipmentScore bestScore = null;
        for (DpNode node : frontier) {
            if (!validateReqs(hooks, node, dpSlots, weapon)) continue;
            AgentEquipmentScore score = scoreNode(node, weapon, wt, mob);
            if (bestScore == null || compareScores(score, bestScore) > 0) {
                bestScore = score;
                best = node;
            }
        }
        if (best == null) {
            for (DpNode node : frontier) {
                DpNode relaxed = relaxToFeasible(hooks, node, dpSlots, weapon);
                if (relaxed == null) continue;
                AgentEquipmentScore score = scoreNode(relaxed, weapon, wt, mob);
                if (bestScore == null || compareScores(score, bestScore) > 0) {
                    bestScore = score;
                    best = relaxed;
                }
            }
        }
        if (best == null) return null;

        Map<Short, Equip> picks = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (best.picks[i] != null) picks.put(dpSlots.get(i), best.picks[i]);
        }
        return new AgentEquipmentDpResult(picks, bestScore, capHit[0]);
    }

    public static int compareScores(AgentEquipmentScore left, AgentEquipmentScore right) {
        int cmp = Integer.compare(left.damage(), right.damage());
        if (cmp != 0) return cmp;
        return Integer.compare(left.statSum(), right.statSum());
    }

    public static AgentEquipmentStatSnapshot snapshotForBranch(AgentEquipmentStatSnapshot naked,
                                                               Equip weapon,
                                                               Map<Short, Equip> picks) {
        AgentEquipmentStatSnapshot snap = weapon != null ? naked.swap(null, weapon) : naked;
        for (Equip pick : picks.values()) {
            if (pick != null) {
                snap = snap.swap(null, pick);
            }
        }
        return snap;
    }

    public static AgentWeaponScoreBreakdown weaponScoreBreakdown(AgentEquipmentStatSnapshot sim,
                                                                 Equip weapon,
                                                                 WeaponType wt,
                                                                 AgentMapDamageProfile mob) {
        if (AgentWeaponCompatibilityPolicy.isMageJob(sim.job()) || wt == null) {
            return new AgentWeaponScoreBreakdown(0, 0, 0, 0);
        }
        int rawMax = rawPhysicalMax(sim, wt);
        int preCycleDamage = damageWith(sim, wt, mob);
        int cycleMs = weapon != null ? weaponCycleMs(weapon.getItemId()) : 0;
        int normalizedDamage = preCycleDamage;
        if (cycleMs > 0) {
            normalizedDamage = (int) (preCycleDamage * 1000.0 / cycleMs);
        }
        return new AgentWeaponScoreBreakdown(rawMax, preCycleDamage, cycleMs, normalizedDamage);
    }

    public static boolean[] scanReqRelevantDims(Map<Short, List<Equip>> bySlot, ItemInformationProvider ii) {
        return scanReqRelevantDims(bySlot, ii::getEquipStats);
    }

    public static boolean[] scanReqRelevantDims(Map<Short, List<Equip>> bySlot, AgentEquipmentOptimizerHooks hooks) {
        return scanReqRelevantDims(bySlot, hooks::getEquipStats);
    }

    private static DpNode pinSafeSingletonSlots(AgentEquipmentStatSnapshot init, AgentEquipmentOptimizerHooks hooks,
                                                List<Short> dpSlots, Map<Short, List<Equip>> bySlot,
                                                int n) {
        AgentEquipmentStatSnapshot snap = init;
        int hp = 0, mp = 0, statSum = 0;
        Equip[] picks = new Equip[n];
        for (int i = 0; i < n; i++) {
            short slot = dpSlots.get(i);
            if (!canPinSingletonSlot(slot)) continue;
            List<Equip> pool = bySlot.getOrDefault(slot, List.of());
            if (pool.size() != 1) continue;
            Equip cand = pool.get(0);
            if (cand == null) continue;
            if (!hooks.meetsReqs(cand, snap.job(), snap.level(),
                    snap.str(), snap.dex(), snap.int_(), snap.luk(), snap.fame())) continue;
            snap = snap.swap(null, cand);
            hp += cand.getHp();
            mp += cand.getMp();
            statSum += AgentEquipmentScoringPolicy.usefulStatSum(cand, snap.job());
            picks[i] = cand;
        }
        return new DpNode(snap, hp, mp, statSum, picks);
    }

    private static boolean canPinSingletonSlot(short slot) {
        return slot != (short) -5
                && slot != (short) -6
                && slot != (short) -10
                && !AgentEquipmentSlotResolver.isRingSlot(slot);
    }

    private static final class DpNode {
        final AgentEquipmentStatSnapshot snap;
        final int hp;
        final int mp;
        final int statSum;
        final Equip[] picks;

        DpNode(AgentEquipmentStatSnapshot snap, int hp, int mp, int statSum, Equip[] picks) {
            this.snap = snap;
            this.hp = hp;
            this.mp = mp;
            this.statSum = statSum;
            this.picks = picks;
        }
    }

    private static List<DpNode> paretoPruneNodes(List<DpNode> nodes, boolean[] capHitOut,
                                                 AgentEquipmentOptimizerHooks hooks, List<Short> dpSlots,
                                                 WeaponType wt, boolean[] reqRel) {
        if (nodes.size() <= 1) return nodes;
        nodes = dedupEquivalentNodes(nodes, wt, reqRel, hooks, dpSlots);
        if (nodes.size() <= 1) return nodes;
        final int size = nodes.size();
        int[][] vecs = new int[size][];
        boolean[] picksOk = new boolean[size];
        for (int index = 0; index < size; index++) {
            DpNode node = nodes.get(index);
            vecs[index] = nodeVec(node, wt, reqRel);
            picksOk[index] = allPicksMeetReqs(node, hooks, dpSlots);
        }
        List<DpNode> kept = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int[] a = vecs[i];
            boolean dominated = false;
            for (int j = 0; j < size; j++) {
                if (i == j) continue;
                if (!picksOk[j]) continue;
                if (vecDominates(vecs[j], a)) {
                    dominated = true;
                    break;
                }
            }
            if (!dominated) kept.add(nodes.get(i));
        }
        if (kept.size() > MAX_PARETO_STATES) {
            kept.sort((x, y) -> Integer.compare(damagePotential(y, wt), damagePotential(x, wt)));
            kept = new ArrayList<>(kept.subList(0, MAX_PARETO_STATES));
            if (capHitOut != null && capHitOut.length > 0) capHitOut[0] = true;
        }
        return kept;
    }

    private static int damagePotential(DpNode node, WeaponType wt) {
        return AgentWeaponCompatibilityPolicy.isMageJob(node.snap.job())
                ? magicScore(node.snap)
                : rawPhysicalMax(node.snap, wt);
    }

    private static List<DpNode> dedupEquivalentNodes(List<DpNode> nodes, WeaponType wt, boolean[] reqRel,
                                                     AgentEquipmentOptimizerHooks hooks, List<Short> dpSlots) {
        Map<DpSignature, DpNode> bestValidBySignature = new LinkedHashMap<>();
        Map<DpSignature, DpNode> bestSpeculativeBySignature = new LinkedHashMap<>();
        for (DpNode node : nodes) {
            DpSignature signature = DpSignature.from(node, wt, reqRel);
            Map<DpSignature, DpNode> bucket = allPicksMeetReqs(node, hooks, dpSlots)
                    ? bestValidBySignature
                    : bestSpeculativeBySignature;
            DpNode existing = bucket.get(signature);
            if (existing == null || node.statSum > existing.statSum) {
                bucket.put(signature, node);
            }
        }
        int keptSize = bestValidBySignature.size() + bestSpeculativeBySignature.size();
        if (keptSize == nodes.size()) return nodes;
        List<DpNode> kept = new ArrayList<>(keptSize);
        kept.addAll(bestValidBySignature.values());
        kept.addAll(bestSpeculativeBySignature.values());
        return kept;
    }

    private record DpSignature(int damage, int acc, int str, int dex, int int_, int luk) {
        static DpSignature from(DpNode node, WeaponType wt, boolean[] reqRel) {
            AgentEquipmentStatSnapshot snapshot = node.snap;
            return new DpSignature(
                    damagePotential(node, wt),
                    AgentWeaponCompatibilityPolicy.isMageJob(snapshot.job()) ? 0 : snapshot.totalAcc(),
                    reqRel != null && reqRel[0] ? snapshot.str() : 0,
                    reqRel != null && reqRel[1] ? snapshot.dex() : 0,
                    reqRel != null && reqRel[2] ? snapshot.int_() : 0,
                    reqRel != null && reqRel[3] ? snapshot.luk() : 0);
        }
    }

    private static int[] nodeVec(DpNode node, WeaponType wt, boolean[] reqRel) {
        AgentEquipmentStatSnapshot snapshot = node.snap;
        int reqCount = 0;
        if (reqRel != null) {
            for (boolean relevant : reqRel) {
                if (relevant) reqCount++;
            }
        }
        int[] vec = new int[2 + reqCount];
        int index = 0;
        vec[index++] = damagePotential(node, wt);
        vec[index++] = AgentWeaponCompatibilityPolicy.isMageJob(snapshot.job()) ? 0 : snapshot.totalAcc();
        if (reqRel != null) {
            for (int i = 0; i < reqRel.length; i++) {
                if (reqRel[i]) vec[index++] = statByIdx(snapshot, i);
            }
        }
        return vec;
    }

    private static boolean vecDominates(int[] b, int[] a) {
        boolean strict = false;
        for (int i = 0; i < b.length; i++) {
            if (b[i] < a[i]) return false;
            if (b[i] > a[i]) strict = true;
        }
        return strict;
    }

    private static boolean allPicksMeetReqs(DpNode node, AgentEquipmentOptimizerHooks hooks, List<Short> dpSlots) {
        AgentEquipmentStatSnapshot snapshot = node.snap;
        for (int i = 0; i < dpSlots.size(); i++) {
            Equip pick = node.picks[i];
            if (pick == null) continue;
            AgentEquipmentStatSnapshot withoutSelf = snapshot.swap(pick, null);
            if (!hooks.meetsReqs(pick, withoutSelf.job(), withoutSelf.level(),
                    withoutSelf.str(), withoutSelf.dex(), withoutSelf.int_(),
                    withoutSelf.luk(), withoutSelf.fame())) {
                return false;
            }
        }
        return true;
    }

    private static boolean validateReqs(AgentEquipmentOptimizerHooks hooks, DpNode node,
                                        List<Short> dpSlots, Equip weapon) {
        AgentEquipmentStatSnapshot snapshot = node.snap;
        if (weapon != null) {
            AgentEquipmentStatSnapshot withoutSelf = snapshot.swap(weapon, null);
            if (!hooks.meetsReqs(weapon, withoutSelf.job(), withoutSelf.level(),
                    withoutSelf.str(), withoutSelf.dex(), withoutSelf.int_(),
                    withoutSelf.luk(), withoutSelf.fame())) return false;
        }
        for (int i = 0; i < dpSlots.size(); i++) {
            Equip pick = node.picks[i];
            if (pick == null) continue;
            AgentEquipmentStatSnapshot withoutSelf = snapshot.swap(pick, null);
            if (!hooks.meetsReqs(pick, withoutSelf.job(), withoutSelf.level(),
                    withoutSelf.str(), withoutSelf.dex(), withoutSelf.int_(),
                    withoutSelf.luk(), withoutSelf.fame())) return false;
        }
        return true;
    }

    private static DpNode relaxToFeasible(AgentEquipmentOptimizerHooks hooks, DpNode node,
                                          List<Short> dpSlots, Equip weapon) {
        AgentEquipmentStatSnapshot snapshot = node.snap;
        int hp = node.hp;
        int mp = node.mp;
        int statSum = node.statSum;
        Equip[] picks = node.picks.clone();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < dpSlots.size(); i++) {
                Equip pick = picks[i];
                if (pick == null) continue;
                AgentEquipmentStatSnapshot withoutSelf = snapshot.swap(pick, null);
                if (!hooks.meetsReqs(pick, withoutSelf.job(), withoutSelf.level(),
                        withoutSelf.str(), withoutSelf.dex(), withoutSelf.int_(),
                        withoutSelf.luk(), withoutSelf.fame())) {
                    snapshot = withoutSelf;
                    hp -= pick.getHp();
                    mp -= pick.getMp();
                    statSum -= AgentEquipmentScoringPolicy.usefulStatSum(pick, snapshot.job());
                    picks[i] = null;
                    changed = true;
                }
            }
        }
        if (weapon != null) {
            AgentEquipmentStatSnapshot withoutWeapon = snapshot.swap(weapon, null);
            if (!hooks.meetsReqs(weapon, withoutWeapon.job(), withoutWeapon.level(),
                    withoutWeapon.str(), withoutWeapon.dex(), withoutWeapon.int_(),
                    withoutWeapon.luk(), withoutWeapon.fame())) return null;
        }
        return new DpNode(snapshot, hp, mp, statSum, picks);
    }

    private static AgentEquipmentScore scoreNode(DpNode node, Equip weapon, WeaponType wt, AgentMapDamageProfile mob) {
        if (AgentWeaponCompatibilityPolicy.isMageJob(node.snap.job())) {
            return new AgentEquipmentScore(magicScore(node.snap), node.statSum);
        }
        if (wt == null) return new AgentEquipmentScore(0, node.statSum);
        int damage = damageWith(node.snap, wt, mob);
        int cycleMs = weapon != null ? weaponCycleMs(weapon.getItemId()) : 0;
        if (cycleMs > 0) damage = (int) (damage * 1000.0 / cycleMs);
        return new AgentEquipmentScore(damage, node.statSum);
    }

    private static int damageWith(AgentEquipmentStatSnapshot sim, WeaponType wtype,
                                  AgentMapDamageProfile mobProfile) {
        int rawMax = rawPhysicalMax(sim, wtype);
        if (rawMax <= 0) return 0;
        if (mobProfile == null) {
            return rawMax;
        }
        double expectedAfterDef = AgentEquipmentScoringPolicy.expectedDamageAfterDef(rawMax, mobProfile.mobWdef());
        double hitChance;
        try {
            hitChance = CombatFormulaProvider.getInstance().calculatePhysicalMobHitChance(
                    sim.totalAcc(), sim.level(), mobProfile.mobLevel(), mobProfile.mobAvoid());
        } catch (Throwable t) {
            hitChance = 1.0;
        }
        return Math.max(1, (int) Math.round(expectedAfterDef * hitChance * 1000.0));
    }

    private static int rawPhysicalMax(AgentEquipmentStatSnapshot sim, WeaponType wtype) {
        if (wtype == null) return 0;
        WeaponType effective = wtype;
        if (sim.job() != null && sim.job().isA(Job.THIEF) && effective == WeaponType.DAGGER_OTHER) {
            effective = WeaponType.DAGGER_THIEVES;
        }
        int main;
        int secondary;
        if (effective == WeaponType.BOW || effective == WeaponType.CROSSBOW || effective == WeaponType.GUN) {
            main = sim.dex();
            secondary = sim.str();
        } else if (effective == WeaponType.CLAW || effective == WeaponType.DAGGER_THIEVES) {
            main = sim.luk();
            secondary = sim.dex() + sim.str();
        } else {
            main = sim.str();
            secondary = sim.dex();
        }
        double multiplier = switch (effective) {
            case SPEAR_STAB -> (WeaponType.SPEAR_STAB.getMaxDamageMultiplier()
                    + WeaponType.SPEAR_SWING.getMaxDamageMultiplier()) / 2.0;
            case POLE_ARM_SWING -> (WeaponType.POLE_ARM_SWING.getMaxDamageMultiplier()
                    + WeaponType.POLE_ARM_STAB.getMaxDamageMultiplier()) / 2.0;
            default -> effective.getMaxDamageMultiplier();
        };
        return (int) Math.ceil((multiplier * main + secondary) / 100.0 * sim.watk());
    }

    private static int weaponCycleMs(int itemId) {
        try {
            AgentAttackDataProvider provider = AgentAttackDataProvider.getInstance();
            AgentAttackDataProvider.NormalAttackProfile profile = provider.getNormalAttackProfile(itemId);
            if (profile == null) {
                return 0;
            }
            WeaponType weaponType = inventory().getWeaponType(itemId);
            AgentAttackDataProvider.AttackAnimationSpec attackSpec =
                    provider.getBasicAttackSpec(profile.getAttack(), weaponType);
            int rawAnimationDelayMs = provider.getBodyStanceDurationMs(attackSpec.primaryAction());
            if (rawAnimationDelayMs <= 0) {
                return 0;
            }
            return server.agents.capabilities.combat.data.AgentAttackTiming.adjustDelayMillis(
                    rawAnimationDelayMs, profile.getAttackSpeed());
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int magicScore(AgentEquipmentStatSnapshot sim) {
        return (int) Math.round(sim.int_() * 1.1d) + sim.magic();
    }

    private interface EquipStatsLookup {
        Map<String, Integer> getEquipStats(int itemId);
    }

    private static InventoryGateway inventory() {
        return CosmicAgentServerAdapter.INSTANCE.inventory();
    }

    private static boolean[] scanReqRelevantDims(Map<Short, List<Equip>> bySlot,
                                                 EquipStatsLookup lookup) {
        boolean[] relevant = new boolean[4];
        for (List<Equip> list : bySlot.values()) {
            if (list == null) continue;
            for (Equip equip : list) {
                if (equip == null) continue;
                Map<String, Integer> stats = lookup.getEquipStats(equip.getItemId());
                if (stats == null || stats.isEmpty()) continue;
                if (stats.getOrDefault("reqSTR", 0) > 0) relevant[0] = true;
                if (stats.getOrDefault("reqDEX", 0) > 0) relevant[1] = true;
                if (stats.getOrDefault("reqINT", 0) > 0) relevant[2] = true;
                if (stats.getOrDefault("reqLUK", 0) > 0) relevant[3] = true;
            }
        }
        return relevant;
    }

    private static int statByIdx(AgentEquipmentStatSnapshot snapshot, int idx) {
        return switch (idx) {
            case 0 -> snapshot.str();
            case 1 -> snapshot.dex();
            case 2 -> snapshot.int_();
            case 3 -> snapshot.luk();
            default -> 0;
        };
    }
}
