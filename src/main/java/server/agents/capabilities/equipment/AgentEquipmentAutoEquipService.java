package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import config.YamlConfig;
import constants.inventory.EquipSlot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.agents.capabilities.equipment.AgentAutoEquipThrottle;
import server.agents.capabilities.equipment.AgentEquipmentDebugReportFormatter;
import server.agents.capabilities.equipment.AgentEquipmentDpResult;
import server.agents.capabilities.equipment.AgentEquipmentOptimizer;
import server.agents.capabilities.equipment.AgentEquipmentOptimizerHooks;
import server.agents.capabilities.equipment.AgentEquipmentOptimizerResult;
import server.agents.capabilities.equipment.AgentEquipmentOptimizationService;
import server.agents.capabilities.equipment.AgentEquipmentPlanExecutor;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy.EquipUsefulnessHooks;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy.RelevantStat;
import server.agents.capabilities.equipment.AgentEquipmentReservePolicy.SelfReserveHooks;
import server.agents.capabilities.equipment.AgentEquipmentRecommendationPolicy.RecommendationScope;
import server.agents.capabilities.equipment.AgentEquipmentScoringPolicy;
import server.agents.capabilities.equipment.AgentEquipmentScore;
import server.agents.capabilities.equipment.AgentEquipmentSlotResolver;
import server.agents.capabilities.equipment.AgentEquipmentStatSnapshot;
import server.agents.capabilities.equipment.AgentEquipmentUnequipService;
import server.agents.capabilities.equipment.AgentMapDamageProfile;
import server.agents.capabilities.equipment.AgentWeaponCompatibilityPolicy;
import server.agents.capabilities.equipment.AgentWeaponScoreBreakdown;
import server.agents.capabilities.dialogue.AgentRangeReportService;
import server.agents.capabilities.equipment.AgentEquipmentRuntime;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.InventoryGateway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AgentEquipmentAutoEquipService {

    private static final Logger log = LoggerFactory.getLogger(AgentEquipmentAutoEquipService.class);
    private static final java.nio.file.Path EQUIP_LOG_DIR = java.nio.file.Path.of("logs", "bot-equip");
    private static final java.time.format.DateTimeFormatter EQUIP_LOG_FILE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");
    private static final java.time.format.DateTimeFormatter EQUIP_LOG_HEADER_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /**
     * Pareto-frontier DP across equipment slots. Outer loop iterates each viable weapon
     * (currently-wearable + stat-only-blocked, dominance-pruned); inner DP enumerates non-
     * weapon non-ring slot picks while pruning Pareto-dominated stat states. Each chosen
     * item must satisfy its requirements against the FINAL state (Option A: final-stat
     * model), which captures cross-slot stat chains that unlock otherwise-unreachable gear.
     *
     * Scoring (lex): damage > defense > useful-stat sum. Physical damage = max-base ×
     * expected-after-def × hit-chance × 1000 / weapon-cycle-ms. Mage damage = round(int*1.1)
     * + magic. Mob benchmark = highest-avoid mob on the bot's map.
     *
     * Rings are picked greedily after the main DP commits (4 slots over a small shared pool;
     * ring stat contribution rarely unlocks armor). Cash and {@code pendingOffer} excluded.
     * Called on mode change (follow / stop / grind).
     */
    public static void autoEquip(Character bot, Character owner, Item pendingOffer) {
        autoEquip(bot, owner, pendingOffer, false);
    }

    public static void autoEquip(Character bot, Character owner, Item pendingOffer, boolean force) {
        if (!shouldRunAutoEquip(bot, System.currentTimeMillis(), force)) {
            return;
        }

        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        AgentMapDamageProfile mob = AgentMapDamageProfile.snapshotByAvoid(bot);

        AgentEquipmentPlanExecutor.relocateEquippedStrays(bot, eqpInv, eqdInv);

        Map<Short, List<Equip>> bySlot = collectAutoEquipCandidates(bot, ii, eqpInv, eqdInv, pendingOffer);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) {
                currentBySlot.put(e.getPosition(), e);
            }
        }

        List<Short> dpSlots = AgentEquipmentSlotResolver.buildDpSlots(bySlot, currentBySlot);
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, ii);

        // Outer weapon pool: currently-wearable + stat-only-blocked + currently equipped.
        List<Equip> weaponPool = new ArrayList<>(bySlot.getOrDefault((short) -11, List.of()));
        Equip currentWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        if (currentWeapon != null && !weaponPool.contains(currentWeapon)) weaponPool.add(currentWeapon);
        if (weaponPool.isEmpty()) weaponPool.add(null);

        AgentEquipmentStatSnapshot naked = nakedBase(bot, ii, eqdInv);

        Map<Short, Equip> bestPicks = null;
        AgentEquipmentScore bestScore = null;
        Equip bestWeapon = currentWeapon;
        boolean anyCapHit = false;
        for (Equip w : weaponPool) {
            AgentEquipmentDpResult r = AgentEquipmentOptimizer.solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (r == null) continue;
            if (r.paretoCapHit()) anyCapHit = true;
            if (bestScore == null || AgentEquipmentOptimizer.compareScores(r.score(), bestScore) > 0) {
                bestScore = r.score();
                bestPicks = r.picks();
                bestWeapon = w;
            }
        }
        // Every weapon failed reqs — fall back to a no-weapon plan so the armor pass still runs.
        if (bestPicks == null && !weaponPool.contains(null)) {
            AgentEquipmentDpResult r = AgentEquipmentOptimizer.solveForWeapon(bot, ii, naked, null, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (r != null) {
                bestScore = r.score();
                bestPicks = r.picks();
                bestWeapon = null;
                if (r.paretoCapHit()) anyCapHit = true;
            }
        }

        if (bestPicks != null) {
            AgentEquipmentPlanExecutor.applyEquipPlan(bot, currentBySlot, bestPicks, bestWeapon, dpSlots);
            // Sweep currently-equipped items whose reqs aren't met against the bot's now-final
            // stats. This catches gear left equipped via prior trade-debug or stat changes that
            // would otherwise stick because applyEquipPlan only emits moves into occupied slots.
            AgentEquipmentPlanExecutor.unequipInfeasibleEquipped(bot);
        }

        if (anyCapHit) {
            // DP frontier overflowed — too many Pareto-incomparable items in the bot's bag for
            // the optimizer to exhaustively enumerate. The chosen set is best-effort under an
            // admissible-bound truncation; the owner should clean up redundant gear.
            try {
                AgentEquipmentRuntime.sayMapNow(bot,
                        "inventory's too cluttered, cant fully optimize gear - try selling/dropping spares");
            } catch (Throwable ignored) {
                // Don't let a chat error block the equip pass.
            }
        }
    }

    static boolean shouldRunAutoEquip(Character bot, long nowMs, boolean force) {
        return AgentAutoEquipThrottle.shouldRun(bot, nowMs, force);
    }

    public static List<String> autoEquipDebug(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        AgentMapDamageProfile mob = AgentMapDamageProfile.snapshotByAvoid(bot);

        List<String> out = new ArrayList<>();
        if (mob == null) {
            out.add("autoequip: no mob context (in town?) - cant benchmark");
        } else {
            out.add("autoequip mob: avd " + mob.mobAvoid()
                    + " wdef " + mob.mobWdef() + " lv " + mob.mobLevel());
        }

        Map<Short, List<Equip>> bySlot = collectAutoEquipCandidates(bot, ii, eqpInv, eqdInv, null);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !inventory.isCashItem(e.getItemId())) currentBySlot.put(e.getPosition(), e);
        }

        List<Short> dpSlots = AgentEquipmentSlotResolver.buildDpSlots(bySlot, currentBySlot);
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, ii);

        List<Equip> weaponPool = new ArrayList<>(bySlot.getOrDefault((short) -11, List.of()));
        Equip currentWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        if (currentWeapon != null && !weaponPool.contains(currentWeapon)) weaponPool.add(currentWeapon);
        if (weaponPool.isEmpty()) weaponPool.add(null);

        AgentEquipmentStatSnapshot naked = nakedBase(bot, ii, eqdInv);
        out.add("naked: str " + naked.str() + " dex " + naked.dex() + " int " + naked.int_()
                + " luk " + naked.luk() + " watk " + naked.watk() + " mag " + naked.magic()
                + " acc " + naked.totalAcc());

        record Branch(Equip weapon, AgentEquipmentDpResult result) {}
        List<Branch> branches = new ArrayList<>();
        boolean anyCap = false;
        for (Equip w : weaponPool) {
            AgentEquipmentDpResult r = AgentEquipmentOptimizer.solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (r != null) {
                branches.add(new Branch(w, r));
                if (r.paretoCapHit()) anyCap = true;
            }
        }
        // Last-resort fallback: every weapon's reqs failed against the bare snapshot. Try a
        // no-weapon branch so we still produce a best-effort armor plan instead of giving up.
        if (branches.isEmpty() && !weaponPool.contains(null)) {
            AgentEquipmentDpResult r = AgentEquipmentOptimizer.solveForWeapon(bot, ii, naked, null, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (r != null) branches.add(new Branch(null, r));
        }
        branches.sort((a, b) -> AgentEquipmentOptimizer.compareScores(b.result().score(), a.result().score()));

        if (branches.isEmpty()) {
            out.add("autoequip: no weapon found and no items wearable");
        } else {
            int show = Math.min(3, branches.size());
            for (int i = 0; i < show; i++) {
                Branch b = branches.get(i);
                String wName = b.weapon() == null ? "(no weapon)" : inventory.getItemName(b.weapon().getItemId());
                AgentEquipmentScore s = b.result().score();
                AgentEquipmentStatSnapshot branchSnap = AgentEquipmentOptimizer.snapshotForBranch(naked, b.weapon(), b.result().picks());
                WeaponType wt = b.weapon() != null ? inventory.getWeaponType(b.weapon().getItemId()) : null;
                AgentWeaponScoreBreakdown breakdown = AgentEquipmentOptimizer.weaponScoreBreakdown(branchSnap, b.weapon(), wt, mob);
                String tag = i == 0 ? "*" : " ";
                out.add(tag + " W=" + wName
                        + " dmg=" + s.damage()
                        + " rawMax=" + breakdown.rawMax()
                        + " preCycle=" + breakdown.preCycleDamage()
                        + " cycle=" + breakdown.cycleMs() + "ms"
                        + " stat=" + s.statSum());
            }

            // Diff vs current for the winning branch.
            Branch best = branches.get(0);
            List<String> diffs = new ArrayList<>();
            for (Map.Entry<Short, Equip> e : best.result().picks().entrySet()) {
                Equip cur = currentBySlot.get(e.getKey());
                if (cur != e.getValue()) {
                    diffs.add(AgentEquipmentSlotResolver.slotLabel(e.getKey()) + ":"
                            + (cur == null ? "-" : inventory.getItemName(cur.getItemId()))
                            + ">" + inventory.getItemName(e.getValue().getItemId()));
                }
            }
            Equip currentWp = (Equip) eqdInv.getItem((short) -11);
            if (best.weapon() != currentWp) {
                diffs.add(0, "weapon:" + (currentWp == null ? "-" : inventory.getItemName(currentWp.getItemId()))
                        + ">" + (best.weapon() == null ? "-" : inventory.getItemName(best.weapon().getItemId())));
            }
            if (diffs.isEmpty()) {
                out.add("changes: none (already optimal)");
            } else {
                // Chunk into lines of ≤3 changes to avoid one giant message.
                for (int i = 0; i < diffs.size(); i += 3) {
                    out.add("change: " + String.join(", ", diffs.subList(i, Math.min(i + 3, diffs.size()))));
                }
            }
            if (anyCap) out.add("WARN: pareto cap hit, result is best-effort");
        }

        out.add("range: " + AgentRangeReportService.rangeReport(bot));

        // Full dump to disk — chat is too narrow for inventory + per-branch breakdown.
        String filePath = writeAutoEquipDumpFile(bot, ii, eqpInv, eqdInv, mob, naked,
                bySlot, dpSlots, weaponPool, branches, anyCap);
        if (filePath != null) out.add("dump: " + filePath);

        String botName = bot != null ? bot.getName() : "?";
        log.info("autoEquipDebug[{}]:\n  {}", botName, String.join("\n  ", out));
        return out;
    }

    /**
     * Writes a comprehensive autoEquip decision dump to {@code logs/bot-equip/}, mirroring the
     * format of {@link server.agents.monitoring.AgentPathLogger}. Captures everything the optimizer saw: mob profile,
     * naked stats, currently-equipped items with stats/reqs, candidate inventory items with
     * stats/reqs, and per-weapon-branch DP results with chosen picks. Returns absolute path.
     */
    @SuppressWarnings("unchecked")
    private static String writeAutoEquipDumpFile(Character bot, ItemInformationProvider ii,
            Inventory eqpInv, Inventory eqdInv, AgentMapDamageProfile mob, AgentEquipmentStatSnapshot naked,
            Map<Short, List<Equip>> bySlot, List<Short> dpSlots, List<Equip> weaponPool,
            List<?> branches, boolean anyCap) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        InventoryGateway inventory = AgentInventoryGatewayRuntime.inventory();
        String botName = bot != null ? bot.getName() : "unknown";
        String safeName = botName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = "equiplog-" + safeName + "-" + now.format(EQUIP_LOG_FILE_FMT) + ".txt";

        StringBuilder sb = new StringBuilder(8192);
        sb.append("=== autoEquip dump ===\n");
        sb.append("time:    ").append(now.format(EQUIP_LOG_HEADER_FMT)).append('\n');
        sb.append("bot:     ").append(botName)
          .append(" job=").append(bot != null ? bot.getJob() : "?")
          .append(" lv=").append(bot != null ? bot.getLevel() : 0)
          .append(" fame=").append(bot != null ? bot.getFame() : 0).append('\n');
        sb.append("map:     ").append(AgentEquipmentDebugReportFormatter.safeMapId(bot)).append('\n');
        sb.append("mob:     ");
        if (mob == null) sb.append("(no mob context)\n");
        else sb.append("avd=").append(mob.mobAvoid())
                .append(" wdef=").append(mob.mobWdef())
                .append(" lv=").append(mob.mobLevel()).append('\n');
        sb.append("naked:   str=").append(naked.str())
          .append(" dex=").append(naked.dex())
          .append(" int=").append(naked.int_())
          .append(" luk=").append(naked.luk())
          .append(" watk=").append(naked.watk())
          .append(" mag=").append(naked.magic())
          .append(" acc=").append(naked.totalAcc()).append('\n');
        sb.append("range:   ").append(AgentRangeReportService.rangeReport(bot, mob)).append('\n');

        sb.append("\n--- equipped ---\n");
        sb.append(AgentEquipmentDebugReportFormatter.itemHeader(false));
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e) {
                AgentEquipmentDebugReportFormatter.appendItemRow(sb, inventory, e, e.getPosition(), null);
            }
        }

        sb.append("\n--- inventory (equip bag) ---\n");
        sb.append(AgentEquipmentDebugReportFormatter.itemHeader(true));
        for (Item it : eqpInv.list()) {
            if (it instanceof Equip e) {
                boolean reserveSelf = shouldReserveOwnedItem(bot, ii, e);
                AgentEquipmentDebugReportFormatter.appendItemRow(sb, inventory, e, e.getPosition(), reserveSelf);
            }
        }

        sb.append("\n--- candidate pools by slot ---\n");
        for (Map.Entry<Short, List<Equip>> en : bySlot.entrySet()) {
            sb.append(AgentEquipmentSlotResolver.slotLabel(en.getKey())).append(" (").append(en.getKey()).append("): ");
            if (en.getValue().isEmpty()) sb.append("(empty)\n");
            else {
                sb.append(en.getValue().size()).append(" cands: ");
                List<String> names = new ArrayList<>();
                for (Equip e : en.getValue()) names.add(inventory.getItemName(e.getItemId()) + "#" + e.getPosition());
                sb.append(String.join(", ", names)).append('\n');
            }
        }

        sb.append("\n--- weapon branches (sorted by score) ---\n");
        // branches is List<Branch> (record local to autoEquipDebug); reflect via toString fallback.
        // For type safety, recompute here from the same inputs:
        Equip currentWp = (Equip) eqdInv.getItem((short) -11);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !inventory.isCashItem(e.getItemId())) currentBySlot.put(e.getPosition(), e);
        }
        record Br(Equip w, AgentEquipmentDpResult r) {}
        List<Br> sorted = new ArrayList<>();
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, ii);
        for (Equip w : weaponPool) {
            AgentEquipmentDpResult r = AgentEquipmentOptimizer.solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob, reqRel);
            if (r != null) sorted.add(new Br(w, r));
        }
        sorted.sort((a, b) -> AgentEquipmentOptimizer.compareScores(b.r().score(), a.r().score()));
        for (int i = 0; i < sorted.size(); i++) {
            Br b = sorted.get(i);
            String wName = b.w() == null ? "(none)" : inventory.getItemName(b.w().getItemId());
            AgentEquipmentScore s = b.r().score();
            AgentEquipmentStatSnapshot branchSnap = AgentEquipmentOptimizer.snapshotForBranch(naked, b.w(), b.r().picks());
            WeaponType wt = b.w() != null ? inventory.getWeaponType(b.w().getItemId()) : null;
            AgentWeaponScoreBreakdown breakdown = AgentEquipmentOptimizer.weaponScoreBreakdown(branchSnap, b.w(), wt, mob);
            sb.append(i == 0 ? "[*] " : "[ ] ").append(wName)
              .append(" id=").append(b.w() == null ? 0 : b.w().getItemId())
              .append(" dmg=").append(s.damage())
              .append(" rawMax=").append(breakdown.rawMax())
              .append(" preCycle=").append(breakdown.preCycleDamage())
              .append(" cycle=").append(breakdown.cycleMs()).append("ms")
              .append(" stat=").append(s.statSum())
              .append(b.r().paretoCapHit() ? " (pareto-cap)" : "").append('\n');
            for (Map.Entry<Short, Equip> pick : b.r().picks().entrySet()) {
                Equip cur = currentBySlot.get(pick.getKey());
                String marker = cur == pick.getValue() ? "  =" : "  >";
                sb.append(marker).append(' ').append(AgentEquipmentSlotResolver.slotLabel(pick.getKey())).append(": ");
                if (cur != pick.getValue() && cur != null) {
                    sb.append(inventory.getItemName(cur.getItemId())).append(" -> ");
                }
                sb.append(inventory.getItemName(pick.getValue().getItemId())).append('\n');
            }
        }

        sb.append("\n--- summary ---\n");
        if (sorted.isEmpty()) {
            sb.append("no feasible set found\n");
        } else {
            Br winner = sorted.get(0);
            sb.append("winner weapon: ").append(winner.w() == null ? "(none)" : inventory.getItemName(winner.w().getItemId())).append('\n');
            sb.append("current weapon: ").append(currentWp == null ? "(none)" : inventory.getItemName(currentWp.getItemId())).append('\n');
            sb.append("pareto-cap-hit: ").append(anyCap).append('\n');
        }

        try {
            java.nio.file.Files.createDirectories(EQUIP_LOG_DIR);
            java.nio.file.Path file = EQUIP_LOG_DIR.resolve(filename);
            java.nio.file.Files.writeString(file, sb.toString());
            return file.toAbsolutePath().toString();
        } catch (java.io.IOException e) {
            log.warn("Failed to write autoEquip dump", e);
            return null;
        }
    }

    /**
     * Builds per-slot candidate pools for the optimizer. Includes currently-wearable items,
     * stat-only-blocked items (DP may unlock them), and currently-equipped items (so DP can
     * choose to keep them). Per-slot dominance-pruned with reqs honored.
     */
    private static Map<Short, List<Equip>> collectAutoEquipCandidates(
            Character bot, ItemInformationProvider ii, Inventory eqpInv,
            Inventory eqdInv, Item pendingOffer) {
        Map<Short, List<Equip>> bySlot = new LinkedHashMap<>();
        for (Item item : eqpInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            if (item == pendingOffer) continue;
            if (!(item instanceof Equip equip)) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == null || eslot == EquipSlot.PET_EQUIP) continue;
            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) continue;
            if (primary == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId()))) continue;
            if (ii.canWearEquipment(bot, equip, primary) || statOnlyBlocked(bot, ii, equip)) {
                bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
            }
        }
        // Include currently-equipped items (they're already legal).
        for (Item it : eqdInv.list()) {
            if (!(it instanceof Equip e) || ii.isCash(e.getItemId())) continue;
            short pos = e.getPosition();
            if (pos == (short) -11
                    && !AgentWeaponCompatibilityPolicy.isWeaponCompatible(bot, ii.getWeaponType(e.getItemId()))) continue;
            short key = AgentEquipmentSlotResolver.isRingSlot(pos) ? (short) -12 : pos;
            List<Equip> pool = bySlot.computeIfAbsent(key, k -> new ArrayList<>());
            if (!pool.contains(e)) pool.add(e);
        }
        Job botJob = bot.getJob();
        boolean[] reqRel = AgentEquipmentOptimizer.scanReqRelevantDims(bySlot, ii);
        for (Map.Entry<Short, List<Equip>> e : bySlot.entrySet()) {
            e.setValue(pruneDominatedWithReqs(ii, e.getValue(), botJob, reqRel));
        }
        return bySlot;
    }

    /**
     * Runs the autoEquip DP after merging {@code extras} into the receiver's candidate pool.
     * Used by the trade request/offer path so its recommendations match what autoEquip would
     * actually do. The {@code extras} are still subject to scope-appropriate requirement and
     * weapon-compat filters before entering the DP.
     */
    public static AgentEquipmentOptimizerResult runOptimizerWithExtras(Character bot, Collection<Equip> extras) {
        return AgentEquipmentOptimizationService.runOptimizerWithExtras(bot, extras);
    }

    public static AgentEquipmentOptimizerResult runOptimizerWithExtras(Character bot, Collection<Equip> extras,
                                                          RecommendationScope scope) {
        return AgentEquipmentOptimizationService.runOptimizerWithExtras(bot, extras, scope);
    }

    /** Naked stat snapshot: bot totals minus all currently-equipped non-cash gear. */
    private static AgentEquipmentStatSnapshot nakedBase(Character bot, ItemInformationProvider ii, Inventory eqdInv) {
        AgentEquipmentStatSnapshot sim = AgentEquipmentStatSnapshot.of(bot);
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) sim = sim.swap(e, null);
        }
        return sim;
    }

    static boolean statOnlyBlocked(Character bot, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.statOnlyBlocked(bot, ii, equip);
    }

    static boolean statOnlyBlocked(Character bot, EquipUsefulnessHooks hooks, Equip equip) {
        return AgentEquipmentReservePolicy.statOnlyBlocked(bot, hooks, equip);
    }

    /** Current level/fame wearability gate: only stat reqs are treated as satisfiable by gear. */
    static boolean isOwnClassEquip(Character bot, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.isOwnClassEquip(bot, ii, equip);
    }

    static boolean futureOnlyBlocked(Character bot, ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.futureOnlyBlocked(bot, ii, equip);
    }

    public static boolean shouldReserveOwnedItem(Character bot, Item item) {
        return AgentEquipmentReservePolicy.shouldReserveOwnedItem(bot, item);
    }

    public static boolean shouldReserveOwnedItem(Character bot, EquipUsefulnessHooks hooks, Equip item) {
        return AgentEquipmentReservePolicy.shouldReserveOwnedItem(bot, hooks, item);
    }

    public static boolean shouldReserveOwnedItem(Character bot, ItemInformationProvider ii, Equip item) {
        return AgentEquipmentReservePolicy.shouldReserveOwnedItem(bot, ii, item);
    }

    public static boolean wouldReserveIncomingItem(Character bot, ItemInformationProvider ii, Equip item) {
        return AgentEquipmentReservePolicy.wouldReserveIncomingItem(bot, ii, item);
    }

    static boolean isEquipUsefulToBot(Character recipient, ItemInformationProvider ii, Equip item) {
        return AgentEquipmentReservePolicy.isEquipUsefulToAgent(recipient, ii, item);
    }

    static EnumSet<RelevantStat> relevantStatsFor(Job job) {
        return AgentEquipmentReservePolicy.relevantStatsFor(job);
    }

    static Set<Equip> selectItemsBeatingBaseline(EnumSet<RelevantStat> relevant,
                                                 Collection<Equip> bagItems,
                                                 Collection<Equip> baseline) {
        return AgentEquipmentReservePolicy.selectItemsBeatingBaseline(relevant, bagItems, baseline);
    }

    static boolean isEquipUsefulToBot(Character recipient, EquipUsefulnessHooks hooks, Equip item) {
        return AgentEquipmentReservePolicy.isEquipUsefulToAgent(recipient, hooks, item);
    }

    static Set<Item> collectPotentialSelfUpgradeItems(Character bot) {
        return AgentEquipmentReservePolicy.collectPotentialSelfUpgradeItems(bot);
    }

    static Set<Equip> selectOwnedItemsForSelfReserve(Character bot, ItemInformationProvider ii,
                                                     Collection<Equip> ownedItems) {
        return AgentEquipmentReservePolicy.selectOwnedItemsForSelfReserve(bot, ii, ownedItems);
    }

    static Set<Equip> selectOwnedItemsForSelfReserve(Character bot, SelfReserveHooks hooks,
                                                     Collection<Equip> ownedItems) {
        return AgentEquipmentReservePolicy.selectOwnedItemsForSelfReserve(bot, hooks, ownedItems);
    }

    private static String textSlotKey(ItemInformationProvider ii, Equip equip) {
        return AgentEquipmentReservePolicy.textSlotKey(ii, equip);
    }

    private static boolean meetsReqsNaked(Character bot, ItemInformationProvider ii,
                                          AgentEquipmentStatSnapshot naked, Equip equip) {
        return ii.meetsEquipRequirements(equip, bot.getJob(), bot.getLevel(),
                naked.str(), naked.dex(), naked.int_(), naked.luk(), bot.getFame());
    }

    public static String unequipAll(Character bot) {
        return AgentEquipmentUnequipService.unequipAll(bot);
    }

    public static String unequipSlot(Character bot, short[] slots) {
        return AgentEquipmentUnequipService.unequipSlot(bot, slots);
    }

    private static Equip compatibleWeaponOrNull(Character bot, ItemInformationProvider ii, Equip equip) {
        if (equip == null) {
            return null;
        }
        return AgentWeaponCompatibilityPolicy.isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId())) ? equip : null;
    }

    // ---- helper records / pruning ------------------------------------------------------

    /**
     * Drops items strictly dominated by another in {@code items} (every relevant stat ≤,
     * at least one strictly less). Safe within a currently-wearable pool: legality already
     * passed for every item, so the surviving Pareto front preserves all useful trade-offs.
     * Skipped when the pool has 0 or 1 items.
     */
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

    /**
     * Prune for lookahead pools where reqs differ across items. {@code b} dominates {@code a}
     * only if b's stats ≥ a's AND b's reqs ≤ a's — otherwise a weaker but easier-to-wear
     * item is meaningful and must be retained.
     *
     * <p>Job-aware variant: dim selection focuses on the bot's job-primary stat + watk/magic
     * + job-conditional eff_acc + req-relevant str/dex/int/luk dims. Stats that don't matter
     * to the bot's damage formula and don't gate any candidate's reqs collapse into a
     * combat-effectiveness tiebreaker score. Much tighter than the legacy 14-dim raw vec —
     * shrinks per-slot pools so the DP step downstream sees fewer candidates.
     */
    private static List<Equip> pruneDominatedWithReqs(ItemInformationProvider ii, List<Equip> items,
                                                       Job job, boolean[] reqRel) {
        if (items == null || items.size() <= 1) return items;
        int[] priority = jobStatPriority(job);
        boolean isMage = AgentWeaponCompatibilityPolicy.isMageJob(job);
        boolean accRel = isAccRelevantJob(job);
        // Precompute vec + tiebreak per item; the dominance scan is O(N^2) but the per-item
        // computation is O(N).
        final int n = items.size();
        int[][] vecs = new int[n][];
        int[] tiebreak = new int[n];
        for (int i = 0; i < n; i++) {
            vecs[i] = dedupStatVec(items.get(i), priority, reqRel, isMage, accRel);
            tiebreak[i] = dedupTiebreak(items.get(i), priority);
        }
        List<Equip> kept = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Equip a = items.get(i);
            boolean dominated = false;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                Equip b = items.get(j);
                if (!dedupDominatesPre(vecs[j], vecs[i], tiebreak[j], tiebreak[i])) continue;
                if (!reqsAtLeastAsEasy(ii, b, a)) continue;
                dominated = true; break;
            }
            if (!dominated) kept.add(a);
        }
        return kept.isEmpty() ? items : kept;
    }

    /**
     * Per-job stat priority for the dedup vec / tiebreaker. Index 0 is the primary damage
     * stat; remaining indices are secondaries (added at 0.25 weight into eff_primary).
     * Stat indices: 0=str, 1=dex, 2=int, 3=luk.
     */
    private static int[] jobStatPriority(Job job) {
        if (job == null) return new int[]{0, 1};
        if (AgentWeaponCompatibilityPolicy.isMageJob(job)) return new int[]{2};
        int id = job.getId();
        int niche = (id / 100) % 10;
        return switch (niche) {
            case 1 -> new int[]{0, 1};       // warrior (str primary, dex secondary)
            case 3 -> new int[]{1, 0};       // bowman (dex primary, str secondary)
            case 4 -> new int[]{3, 1, 0};    // thief (luk primary, dex+str secondary)
            case 5 -> (id / 10 == 51 || id / 10 == 151)
                    ? new int[]{0, 1}        // brawler line: str primary
                    : new int[]{1, 0};       // gunslinger line: dex primary
            default -> new int[]{0, 1};
        };
    }

    /** Acc-contribution stats only earn a vec dim for jobs that actually need acc to hit. */
    private static boolean isAccRelevantJob(Job job) {
        if (job == null) return false;
        int id = job.getId();
        int niche = (id / 100) % 10;
        if (niche == 1) return true;             // warrior
        if (niche == 5) {
            int sub = id / 10;
            return sub == 51 || sub == 151;      // brawler / thunderbreaker
        }
        return false;
    }

    /**
     * Dims (in this order): job-primary stat (always); watk for non-mage / magic for mage;
     * eff_acc = acc + dex + luk*0.5 (warrior/brawler only); each of str/dex/int/luk where
     * reqRel[stat] is true (and not already emitted as primary).
     */
    private static int[] dedupStatVec(Equip e, int[] priority, boolean[] reqRel,
                                       boolean isMage, boolean accRelevant) {
        int primaryIdx = priority.length > 0 ? priority[0] : -1;
        boolean[] reqDim = new boolean[4];
        for (int i = 0; i < 4; i++) {
            reqDim[i] = reqRel != null && reqRel[i] && i != primaryIdx;
        }
        int count = (primaryIdx >= 0 ? 1 : 0) + 1 /* watk or magic */
                + (accRelevant ? 1 : 0);
        for (boolean b : reqDim) if (b) count++;
        int[] v = new int[count];
        int k = 0;
        int primary = primaryIdx >= 0 ? statByIdx(e, primaryIdx) : 0;
        if (primaryIdx >= 0) v[k++] = isMage ? primary : primary + e.getWatk() * 2;
        v[k++] = isMage ? e.getMatk() : e.getWatk() + primary / 5;
        if (accRelevant) {
            v[k++] = e.getAcc() + e.getDex() + (int) Math.round(e.getLuk() * 0.5);
        }
        for (int i = 0; i < 4; i++) {
            if (reqDim[i]) v[k++] = statByIdx(e, i);
        }
        return v;
    }

    /**
     * Tiebreaker score used when {@link #dedupStatVec} ties — captures combat-relevant
     * contributions that didn't survive into the vec. eff_primary = primary + secondaries*0.25
     * picks up secondary stats that aren't already raw dims (e.g. str for a thief when no
     * candidate has reqSTR). watk added at ×4 keeps damage signal dominant.
     */
    private static int dedupTiebreak(Equip e, int[] priority) {
        if (priority == null || priority.length == 0) return 0;
        int main = statByIdx(e, priority[0]);
        int secSum = 0;
        for (int i = 1; i < priority.length; i++) secSum += statByIdx(e, priority[i]);
        int effPrimary = main + (int) Math.round(secSum * 0.25);
        return effPrimary + e.getWatk() * 4;
    }

    private static int statByIdx(Equip e, int idx) {
        return switch (idx) {
            case 0 -> e.getStr();
            case 1 -> e.getDex();
            case 2 -> e.getInt();
            case 3 -> e.getLuk();
            default -> 0;
        };
    }

    private static boolean dedupDominatesPre(int[] bs, int[] as, int bTie, int aTie) {
        boolean strict = false;
        for (int i = 0; i < bs.length; i++) {
            if (bs[i] < as[i]) return false;
            if (bs[i] > as[i]) strict = true;
        }
        if (strict) return true;
        return bTie > aTie;
    }

    private static boolean reqsAtLeastAsEasy(ItemInformationProvider ii, Equip b, Equip a) {
        return AgentEquipmentReservePolicy.reqsAtLeastAsEasy(ii, b, a);
    }

    private static int[] statVec(Equip e) {
        return new int[]{e.getStr(), e.getDex(), e.getInt(), e.getLuk(),
                          e.getWatk(), e.getMatk(), e.getWdef(), e.getMdef(),
                          e.getAcc(), e.getAvoid(), e.getHp(), e.getMp(),
                          e.getSpeed(), e.getJump()};
    }
}
