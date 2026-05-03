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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.bots.combat.BotAttackDataProvider;
import server.combat.CombatFormulaProvider;
import server.life.LifeFactory;
import server.life.Monster;
import server.life.MonsterStats;
import server.life.SpawnPoint;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

class BotEquipManager {

    private static final Logger log = LoggerFactory.getLogger(BotEquipManager.class);
    private static final java.nio.file.Path EQUIP_LOG_DIR = java.nio.file.Path.of("logs", "bot-equip");
    private static final java.time.format.DateTimeFormatter EQUIP_LOG_FILE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss");
    private static final java.time.format.DateTimeFormatter EQUIP_LOG_HEADER_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final short[] RING_SLOTS = {-12, -13, -15, -16};
    /** Hard cap on Pareto-frontier size per DP step to bound worst-case runtime. */
    private static final int MAX_PARETO_STATES = 500;

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

    record EquipScore(int damage, int defense, int statSum) {}

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
    static void autoEquip(Character bot, Character owner, Item pendingOffer) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        MapDamageProfile mob = MapDamageProfile.snapshotByAvoid(bot);

        Map<Short, List<Equip>> bySlot = collectAutoEquipCandidates(bot, ii, eqpInv, eqdInv, pendingOffer);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) {
                currentBySlot.put(e.getPosition(), e);
            }
        }

        // Non-ring, non-weapon slots in canonical order — also include slots that only have
        // an equipped item so the validator sees them.
        Set<Short> dpSlotSet = new HashSet<>();
        for (Short s : bySlot.keySet()) {
            if (s != (short) -11 && !isRingSlot(s)) dpSlotSet.add(s);
        }
        for (Short s : currentBySlot.keySet()) {
            if (s != (short) -11 && !isRingSlot(s)) dpSlotSet.add(s);
        }
        List<Short> dpSlots = new ArrayList<>(dpSlotSet);
        // Descending order so top (-5) is processed before pants (-6); the overall→pants
        // block at slot -6 reads picks[-5], which must already be set.
        dpSlots.sort((a, b) -> Short.compare(b, a));

        // Outer weapon pool: currently-wearable + stat-only-blocked + currently equipped.
        List<Equip> weaponPool = new ArrayList<>(bySlot.getOrDefault((short) -11, List.of()));
        Equip currentWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        if (currentWeapon != null && !weaponPool.contains(currentWeapon)) weaponPool.add(currentWeapon);
        if (weaponPool.isEmpty()) weaponPool.add(null);

        StatSnapshot naked = nakedBase(bot, ii, eqdInv);

        Map<Short, Equip> bestPicks = null;
        EquipScore bestScore = null;
        Equip bestWeapon = currentWeapon;
        boolean anyCapHit = false;
        for (Equip w : weaponPool) {
            DpResult r = solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob);
            if (r == null) continue;
            if (r.paretoCapHit()) anyCapHit = true;
            if (bestScore == null || compareScores(r.score(), bestScore) > 0) {
                bestScore = r.score();
                bestPicks = r.picks();
                bestWeapon = w;
            }
        }
        // Every weapon failed reqs — fall back to a no-weapon plan so the armor pass still runs.
        if (bestPicks == null && !weaponPool.contains(null)) {
            DpResult r = solveForWeapon(bot, ii, naked, null, dpSlots, currentBySlot, bySlot, mob);
            if (r != null) {
                bestScore = r.score();
                bestPicks = r.picks();
                bestWeapon = null;
                if (r.paretoCapHit()) anyCapHit = true;
            }
        }

        if (bestPicks != null) {
            applyEquipPlan(bot, ii, eqdInv, currentBySlot, bestPicks, bestWeapon, dpSlots);
            // Sweep currently-equipped items whose reqs aren't met against the bot's now-final
            // stats. This catches gear left equipped via prior trade-debug or stat changes that
            // would otherwise stick because applyEquipPlan only emits moves into occupied slots.
            unequipInfeasibleEquipped(bot, ii);
        }

        if (anyCapHit) {
            // DP frontier overflowed — too many Pareto-incomparable items in the bot's bag for
            // the optimizer to exhaustively enumerate. The chosen set is best-effort under an
            // admissible-bound truncation; the owner should clean up redundant gear.
            try {
                BotManager.getInstance().botSay(bot,
                        "inventory's too cluttered, cant fully optimize gear — try selling/dropping spares");
            } catch (Throwable ignored) {
                // Don't let a chat error block the equip pass.
            }
        }

        // Rings — greedy under post-DP equipped state.
        if (bySlot.containsKey((short) -12)) {
            Map<Short, List<Equip>> lookaheadAfter = buildLookaheadBySlot(bot, ii, eqpInv);
            autoEquipRings(bot, ii, currentWeaponType(bot, ii), bySlot.get((short) -12),
                           eqdInv, mob, lookaheadAfter);
        }
    }

    /**
     * Diagnostic dump of what {@link #autoEquip} would do, without applying any moves.
     * Returns multiple short lines suitable for sequential bot chat. Includes mob benchmark,
     * naked stats, per-weapon score (top 3), changed slots vs current, and pareto-cap status.
     */
    static List<String> autoEquipDebug(Character bot) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        Inventory eqpInv = bot.getInventory(InventoryType.EQUIP);
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        MapDamageProfile mob = MapDamageProfile.snapshotByAvoid(bot);

        List<String> out = new ArrayList<>();
        if (mob == null) {
            out.add("autoequip: no mob context (in town?) — cant benchmark");
        } else {
            out.add("autoequip mob: avd " + mob.mobAvoid()
                    + " wdef " + mob.mobWdef() + " lv " + mob.mobLevel());
        }

        Map<Short, List<Equip>> bySlot = collectAutoEquipCandidates(bot, ii, eqpInv, eqdInv, null);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) currentBySlot.put(e.getPosition(), e);
        }

        Set<Short> dpSlotSet = new HashSet<>();
        for (Short s : bySlot.keySet()) if (s != (short) -11 && !isRingSlot(s)) dpSlotSet.add(s);
        for (Short s : currentBySlot.keySet()) if (s != (short) -11 && !isRingSlot(s)) dpSlotSet.add(s);
        List<Short> dpSlots = new ArrayList<>(dpSlotSet);
        // Descending order so top (-5) is processed before pants (-6); the overall→pants
        // block at slot -6 reads picks[-5], which must already be set.
        dpSlots.sort((a, b) -> Short.compare(b, a));

        List<Equip> weaponPool = new ArrayList<>(bySlot.getOrDefault((short) -11, List.of()));
        Equip currentWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        if (currentWeapon != null && !weaponPool.contains(currentWeapon)) weaponPool.add(currentWeapon);
        if (weaponPool.isEmpty()) weaponPool.add(null);

        StatSnapshot naked = nakedBase(bot, ii, eqdInv);
        out.add("naked: str " + naked.str() + " dex " + naked.dex() + " int " + naked.int_()
                + " luk " + naked.luk() + " watk " + naked.watk() + " mag " + naked.magic()
                + " acc " + naked.totalAcc());

        record Branch(Equip weapon, DpResult result) {}
        List<Branch> branches = new ArrayList<>();
        boolean anyCap = false;
        for (Equip w : weaponPool) {
            DpResult r = solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob);
            if (r != null) {
                branches.add(new Branch(w, r));
                if (r.paretoCapHit()) anyCap = true;
            }
        }
        // Last-resort fallback: every weapon's reqs failed against the bare snapshot. Try a
        // no-weapon branch so we still produce a best-effort armor plan instead of giving up.
        if (branches.isEmpty() && !weaponPool.contains(null)) {
            DpResult r = solveForWeapon(bot, ii, naked, null, dpSlots, currentBySlot, bySlot, mob);
            if (r != null) branches.add(new Branch(null, r));
        }
        branches.sort((a, b) -> compareScores(b.result().score(), a.result().score()));

        if (branches.isEmpty()) {
            out.add("autoequip: no weapon found and no items wearable");
        } else {
            int show = Math.min(3, branches.size());
            for (int i = 0; i < show; i++) {
                Branch b = branches.get(i);
                String wName = b.weapon() == null ? "(no weapon)" : ii.getName(b.weapon().getItemId());
                EquipScore s = b.result().score();
                String tag = i == 0 ? "*" : " ";
                out.add(tag + " W=" + wName + " dmg=" + s.damage() + " def=" + s.defense()
                        + " stat=" + s.statSum());
            }

            // Diff vs current for the winning branch.
            Branch best = branches.get(0);
            List<String> diffs = new ArrayList<>();
            for (Map.Entry<Short, Equip> e : best.result().picks().entrySet()) {
                Equip cur = currentBySlot.get(e.getKey());
                if (cur != e.getValue()) {
                    diffs.add(slotLabel(e.getKey()) + ":"
                            + (cur == null ? "-" : ii.getName(cur.getItemId()))
                            + ">" + ii.getName(e.getValue().getItemId()));
                }
            }
            Equip currentWp = (Equip) eqdInv.getItem((short) -11);
            if (best.weapon() != currentWp) {
                diffs.add(0, "weapon:" + (currentWp == null ? "-" : ii.getName(currentWp.getItemId()))
                        + ">" + (best.weapon() == null ? "-" : ii.getName(best.weapon().getItemId())));
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
     * format of {@link BotPathLogger}. Captures everything the optimizer saw: mob profile,
     * naked stats, currently-equipped items with stats/reqs, candidate inventory items with
     * stats/reqs, and per-weapon-branch DP results with chosen picks. Returns absolute path.
     */
    @SuppressWarnings("unchecked")
    private static String writeAutoEquipDumpFile(Character bot, ItemInformationProvider ii,
            Inventory eqpInv, Inventory eqdInv, MapDamageProfile mob, StatSnapshot naked,
            Map<Short, List<Equip>> bySlot, List<Short> dpSlots, List<Equip> weaponPool,
            List<?> branches, boolean anyCap) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
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
        sb.append("map:     ").append(safeMapId(bot)).append('\n');
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

        sb.append("\n--- equipped ---\n");
        sb.append(itemHeader());
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e) appendItemRow(sb, ii, e, e.getPosition());
        }

        sb.append("\n--- inventory (equip bag) ---\n");
        sb.append(itemHeader());
        for (Item it : eqpInv.list()) {
            if (it instanceof Equip e) appendItemRow(sb, ii, e, e.getPosition());
        }

        sb.append("\n--- candidate pools by slot ---\n");
        for (Map.Entry<Short, List<Equip>> en : bySlot.entrySet()) {
            sb.append(slotLabel(en.getKey())).append(" (").append(en.getKey()).append("): ");
            if (en.getValue().isEmpty()) sb.append("(empty)\n");
            else {
                sb.append(en.getValue().size()).append(" cands: ");
                List<String> names = new ArrayList<>();
                for (Equip e : en.getValue()) names.add(ii.getName(e.getItemId()) + "#" + e.getPosition());
                sb.append(String.join(", ", names)).append('\n');
            }
        }

        sb.append("\n--- weapon branches (sorted by score) ---\n");
        // branches is List<Branch> (record local to autoEquipDebug); reflect via toString fallback.
        // For type safety, recompute here from the same inputs:
        Equip currentWp = (Equip) eqdInv.getItem((short) -11);
        Map<Short, Equip> currentBySlot = new HashMap<>();
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) currentBySlot.put(e.getPosition(), e);
        }
        record Br(Equip w, DpResult r) {}
        List<Br> sorted = new ArrayList<>();
        for (Equip w : weaponPool) {
            DpResult r = solveForWeapon(bot, ii, naked, w, dpSlots, currentBySlot, bySlot, mob);
            if (r != null) sorted.add(new Br(w, r));
        }
        sorted.sort((a, b) -> compareScores(b.r().score(), a.r().score()));
        for (int i = 0; i < sorted.size(); i++) {
            Br b = sorted.get(i);
            String wName = b.w() == null ? "(none)" : ii.getName(b.w().getItemId());
            EquipScore s = b.r().score();
            sb.append(i == 0 ? "[*] " : "[ ] ").append(wName)
              .append(" id=").append(b.w() == null ? 0 : b.w().getItemId())
              .append(" dmg=").append(s.damage())
              .append(" def=").append(s.defense())
              .append(" stat=").append(s.statSum())
              .append(b.r().paretoCapHit() ? " (pareto-cap)" : "").append('\n');
            for (Map.Entry<Short, Equip> pick : b.r().picks().entrySet()) {
                Equip cur = currentBySlot.get(pick.getKey());
                String marker = cur == pick.getValue() ? "  =" : "  >";
                sb.append(marker).append(' ').append(slotLabel(pick.getKey())).append(": ");
                if (cur != pick.getValue() && cur != null) {
                    sb.append(ii.getName(cur.getItemId())).append(" -> ");
                }
                sb.append(ii.getName(pick.getValue().getItemId())).append('\n');
            }
        }

        sb.append("\n--- summary ---\n");
        if (sorted.isEmpty()) {
            sb.append("no feasible set found\n");
        } else {
            Br winner = sorted.get(0);
            sb.append("winner weapon: ").append(winner.w() == null ? "(none)" : ii.getName(winner.w().getItemId())).append('\n');
            sb.append("current weapon: ").append(currentWp == null ? "(none)" : ii.getName(currentWp.getItemId())).append('\n');
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

    private static String itemHeader() {
        return String.format("%-3s %-30s %-7s %4s %4s %4s %4s %4s %4s %4s %4s %4s %4s %5s %5s   reqs%n",
                "pos", "name", "slot", "STR", "DEX", "INT", "LUK", "WAK", "MAK", "WDF", "MDF", "ACC", "AVD", "HP", "MP");
    }

    private static void appendItemRow(StringBuilder sb, ItemInformationProvider ii, Equip e, short pos) {
        String name = ii.getName(e.getItemId());
        if (name == null) name = "id=" + e.getItemId();
        if (name.length() > 30) name = name.substring(0, 30);
        String textSlot = ii.getEquipmentSlot(e.getItemId());
        sb.append(String.format("%-3d %-30s %-7s %4d %4d %4d %4d %4d %4d %4d %4d %4d %4d %5d %5d   ",
                pos, name, textSlot == null ? "?" : textSlot,
                e.getStr(), e.getDex(), e.getInt(), e.getLuk(),
                e.getWatk(), e.getMatk(), e.getWdef(), e.getMdef(),
                e.getAcc(), e.getAvoid(), e.getHp(), e.getMp()));
        // Reqs from WZ stat map.
        Map<String, Integer> stats = ii.getEquipStats(e.getItemId());
        if (stats != null) {
            int rl = ii.getEquipLevelReq(e.getItemId());
            int rj = stats.getOrDefault("reqJob", 0);
            int rs = stats.getOrDefault("reqSTR", 0);
            int rd = stats.getOrDefault("reqDEX", 0);
            int ri = stats.getOrDefault("reqINT", 0);
            int rk = stats.getOrDefault("reqLUK", 0);
            int rp = stats.getOrDefault("reqPOP", 0);
            sb.append("lv").append(rl).append(" job").append(rj);
            if (rs > 0) sb.append(" str").append(rs);
            if (rd > 0) sb.append(" dex").append(rd);
            if (ri > 0) sb.append(" int").append(ri);
            if (rk > 0) sb.append(" luk").append(rk);
            if (rp > 0) sb.append(" pop").append(rp);
        }
        sb.append('\n');
    }

    private static int safeMapId(Character bot) {
        if (bot == null) return -1;
        try { return bot.getMap() != null ? bot.getMap().getId() : -1; }
        catch (Throwable t) { return -1; }
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
                    && !isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId()))) continue;
            if (ii.canWearEquipment(bot, equip, primary) || statOnlyBlocked(bot, ii, equip)) {
                bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
            }
        }
        // Include currently-equipped items (they're already legal).
        for (Item it : eqdInv.list()) {
            if (!(it instanceof Equip e) || ii.isCash(e.getItemId())) continue;
            short pos = e.getPosition();
            if (pos == (short) -11
                    && !isWeaponCompatible(bot, ii.getWeaponType(e.getItemId()))) continue;
            short key = isRingSlot(pos) ? (short) -12 : pos;
            List<Equip> pool = bySlot.computeIfAbsent(key, k -> new ArrayList<>());
            if (!pool.contains(e)) pool.add(e);
        }
        for (Map.Entry<Short, List<Equip>> e : bySlot.entrySet()) {
            e.setValue(pruneDominatedWithReqs(ii, e.getValue()));
        }
        return bySlot;
    }

    record DpResult(Map<Short, Equip> picks, EquipScore score, boolean paretoCapHit) {
        DpResult(Map<Short, Equip> picks, EquipScore score) { this(picks, score, false); }
    }

    /**
     * Narrow surface of {@link ItemInformationProvider} that the optimizer's DP needs.
     * Tests stub these directly with lambdas — Mockito cannot instrument II in unit tests
     * because of its static WZ-data initializer.
     */
    interface OptimizerHooks {
        boolean isTwoHanded(int itemId);
        WeaponType getWeaponType(int itemId);
        boolean isOverall(int itemId);
        boolean meetsReqs(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame);

        static OptimizerHooks from(ItemInformationProvider ii) {
            return new OptimizerHooks() {
                @Override public boolean isTwoHanded(int itemId) { return ii.isTwoHanded(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return ii.getWeaponType(itemId); }
                @Override public boolean isOverall(int itemId) {
                    return "MaPn".equals(ii.getEquipmentSlot(itemId));
                }
                @Override public boolean meetsReqs(Equip e, Job job, int lvl, int s, int d, int i, int l, int f) {
                    return ii.meetsEquipRequirements(e, job, lvl, s, d, i, l, f);
                }
            };
        }
    }

    /**
     * Pareto-DP across {@code dpSlots} for a fixed weapon. Frontier carries (StatSnapshot,
     * def, hp, mp, statSum, picks[]); per-slot transition tries each candidate plus an
     * "empty" option, then prunes dominated states. Slot-collision constraints (2H↔shield,
     * overall↔pants) enforced inline. Returns the best validated final state, or null.
     */
    static DpResult solveForWeapon(Character bot, ItemInformationProvider ii,
                                            StatSnapshot naked, Equip weapon,
                                            List<Short> dpSlots,
                                            Map<Short, Equip> currentBySlot,
                                            Map<Short, List<Equip>> bySlot,
                                            MapDamageProfile mob) {
        return solveForWeapon(bot, OptimizerHooks.from(ii), naked, weapon, dpSlots,
                              currentBySlot, bySlot, mob);
    }

    static DpResult solveForWeapon(Character bot, OptimizerHooks hooks,
                                            StatSnapshot naked, Equip weapon,
                                            List<Short> dpSlots,
                                            Map<Short, Equip> currentBySlot,
                                            Map<Short, List<Equip>> bySlot,
                                            MapDamageProfile mob) {
        StatSnapshot init = weapon != null ? naked.swap(null, weapon) : naked;
        boolean is2H = weapon != null && hooks.isTwoHanded(weapon.getItemId());
        WeaponType wt = weapon != null ? hooks.getWeaponType(weapon.getItemId()) : null;
        boolean[] capHit = {false};

        int n = dpSlots.size();
        int overallIdx = dpSlots.indexOf((short) -5);
        DpNode start = new DpNode(init, 0, 0, 0, 0, new Equip[n]);
        List<DpNode> frontier = new ArrayList<>();
        frontier.add(start);

        for (int i = 0; i < n; i++) {
            short slot = dpSlots.get(i);
            List<Equip> pool = bySlot.getOrDefault(slot, List.of());
            List<DpNode> next = new ArrayList<>(Math.max(8, frontier.size() * (pool.size() + 1)));
            for (DpNode prev : frontier) {
                // Empty option always available — always carry forward.
                next.add(prev);
                if (slot == (short) -10 && is2H) continue; // shield blocked by 2H weapon
                boolean blockedByOverall = (slot == (short) -6 && overallIdx >= 0
                        && prev.picks[overallIdx] != null
                        && hooks.isOverall(prev.picks[overallIdx].getItemId()));
                if (blockedByOverall) continue;
                for (Equip cand : pool) {
                    if (cand == null) continue;
                    StatSnapshot ns = prev.snap.swap(null, cand);
                    int nDef = prev.def + cand.getWdef() + cand.getMdef();
                    int nHp = prev.hp + cand.getHp();
                    int nMp = prev.mp + cand.getMp();
                    int nStat = prev.statSum + usefulStatSum(cand, ns.job());
                    Equip[] picks = prev.picks.clone();
                    picks[i] = cand;
                    next.add(new DpNode(ns, nDef, nHp, nMp, nStat, picks));
                }
            }
            frontier = paretoPruneNodes(next, capHit);
        }

        DpNode best = null;
        EquipScore bestScore = null;
        for (DpNode node : frontier) {
            if (!validateReqs(hooks, node, dpSlots, weapon)) continue;
            EquipScore s = scoreNode(node, weapon, wt, mob);
            if (bestScore == null || compareScores(s, bestScore) > 0) {
                bestScore = s;
                best = node;
            }
        }
        // Best-effort fallback: Pareto pruning may have dropped the all-empty state in favor
        // of higher-stat states that include picks whose reqs aren't met against the final
        // snapshot (e.g. boots needing dex60 when no item in the pool adds dex). Rather than
        // reporting "no feasible set", relax each frontier node by dropping infeasible picks
        // (cascading until stable) and pick the best-scoring result. Returns null only if the
        // weapon itself fails reqs against the bare snapshot — caller can try a null weapon.
        if (best == null) {
            for (DpNode node : frontier) {
                DpNode relaxed = relaxToFeasible(hooks, node, dpSlots, weapon);
                if (relaxed == null) continue;
                EquipScore s = scoreNode(relaxed, weapon, wt, mob);
                if (bestScore == null || compareScores(s, bestScore) > 0) {
                    bestScore = s;
                    best = relaxed;
                }
            }
        }
        if (best == null) return null;

        Map<Short, Equip> picks = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            if (best.picks[i] != null) picks.put(dpSlots.get(i), best.picks[i]);
        }
        return new DpResult(picks, bestScore, capHit[0]);
    }

    private static final class DpNode {
        final StatSnapshot snap;
        final int def, hp, mp, statSum;
        final Equip[] picks;
        DpNode(StatSnapshot snap, int def, int hp, int mp, int statSum, Equip[] picks) {
            this.snap = snap; this.def = def; this.hp = hp; this.mp = mp;
            this.statSum = statSum; this.picks = picks;
        }
    }

    private static List<DpNode> paretoPruneNodes(List<DpNode> nodes, boolean[] capHitOut) {
        if (nodes.size() <= 1) return nodes;
        List<int[]> vecs = new ArrayList<>(nodes.size());
        for (DpNode n : nodes) vecs.add(nodeVec(n));
        List<DpNode> kept = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            int[] a = vecs.get(i);
            boolean dominated = false;
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;
                if (vecDominates(vecs.get(j), a)) { dominated = true; break; }
            }
            if (!dominated) kept.add(nodes.get(i));
        }
        if (kept.size() > MAX_PARETO_STATES) {
            // Admissible bound: total of damage-relevant stats. Keeps states with the most
            // raw firepower potential; drops Pareto-front fringe under load.
            kept.sort((x, y) -> Integer.compare(damagePotential(y), damagePotential(x)));
            kept = new ArrayList<>(kept.subList(0, MAX_PARETO_STATES));
            if (capHitOut != null && capHitOut.length > 0) capHitOut[0] = true;
        }
        return kept;
    }

    private static int damagePotential(DpNode n) {
        StatSnapshot s = n.snap;
        return s.str() + s.dex() + s.int_() + s.luk() + s.watk() + s.magic() + n.def;
    }

    private static int[] nodeVec(DpNode n) {
        StatSnapshot s = n.snap;
        return new int[]{s.str(), s.dex(), s.int_(), s.luk(), s.watk(), s.magic(),
                          s.totalAcc(), n.def, n.hp, n.mp, n.statSum};
    }

    private static boolean vecDominates(int[] b, int[] a) {
        boolean strict = false;
        for (int i = 0; i < b.length; i++) {
            if (b[i] < a[i]) return false;
            if (b[i] > a[i]) strict = true;
        }
        return strict;
    }

    private static boolean validateReqs(OptimizerHooks hooks, DpNode node,
                                         List<Short> dpSlots, Equip weapon) {
        StatSnapshot s = node.snap;
        if (weapon != null && !hooks.meetsReqs(weapon, s.job(), s.level(),
                s.str(), s.dex(), s.int_(), s.luk(), s.fame())) return false;
        for (int i = 0; i < dpSlots.size(); i++) {
            Equip p = node.picks[i];
            if (p == null) continue;
            if (!hooks.meetsReqs(p, s.job(), s.level(),
                    s.str(), s.dex(), s.int_(), s.luk(), s.fame())) return false;
        }
        return true;
    }

    /**
     * Iteratively drops picks whose reqs aren't met against the current snapshot until the
     * remaining set is self-consistent. Cascading: dropping a stat-giving pick may invalidate
     * another. Returns null iff the weapon itself fails reqs against the bare snapshot.
     */
    private static DpNode relaxToFeasible(OptimizerHooks hooks, DpNode node,
                                           List<Short> dpSlots, Equip weapon) {
        StatSnapshot s = node.snap;
        int def = node.def, hp = node.hp, mp = node.mp, statSum = node.statSum;
        Equip[] picks = node.picks.clone();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = 0; i < dpSlots.size(); i++) {
                Equip p = picks[i];
                if (p == null) continue;
                if (!hooks.meetsReqs(p, s.job(), s.level(),
                        s.str(), s.dex(), s.int_(), s.luk(), s.fame())) {
                    s = s.swap(p, null);
                    def -= p.getWdef() + p.getMdef();
                    hp -= p.getHp();
                    mp -= p.getMp();
                    statSum -= usefulStatSum(p, s.job());
                    picks[i] = null;
                    changed = true;
                }
            }
        }
        if (weapon != null && !hooks.meetsReqs(weapon, s.job(), s.level(),
                s.str(), s.dex(), s.int_(), s.luk(), s.fame())) return null;
        return new DpNode(s, def, hp, mp, statSum, picks);
    }

    private static EquipScore scoreNode(DpNode node, Equip weapon, WeaponType wt, MapDamageProfile mob) {
        if (isMageJob(node.snap.job())) {
            return new EquipScore(magicScore(node.snap), node.def, node.statSum);
        }
        if (wt == null) return new EquipScore(0, node.def, node.statSum);
        int dmg = damageWith(node.snap, null, wt, mob);
        int cycleMs = weapon != null ? weaponCycleMs(weapon.getItemId()) : 0;
        if (cycleMs > 0) dmg = (int) (dmg * 1000.0 / cycleMs);
        return new EquipScore(dmg, node.def, node.statSum);
    }

    /** Naked stat snapshot: bot totals minus all currently-equipped non-cash gear. */
    private static StatSnapshot nakedBase(Character bot, ItemInformationProvider ii, Inventory eqdInv) {
        StatSnapshot sim = StatSnapshot.of(bot);
        for (Item it : eqdInv.list()) {
            if (it instanceof Equip e && !ii.isCash(e.getItemId())) sim = sim.swap(e, null);
        }
        return sim;
    }

    /**
     * Emits equip moves to realize the optimizer's chosen set. Conservative: only emits a
     * move when the target differs from the currently-equipped item AND the target sits in
     * the EQUIP inventory (positive position). Manipulator handles displacement of the old
     * item (and 2H↔shield / overall↔pants auto-unequips). Does NOT proactively unequip
     * gear when target is empty — that would downgrade without a replacement.
     */
    private static void applyEquipPlan(Character bot, ItemInformationProvider ii, Inventory eqdInv,
                                        Map<Short, Equip> currentBySlot, Map<Short, Equip> picks,
                                        Equip targetWeapon, List<Short> dpSlots) {
        // Order: weapon first (handles 2H↔1H eviction), overall before pants, then others.
        List<Short> order = new ArrayList<>();
        order.add((short) -11);
        if (dpSlots.contains((short) -5)) order.add((short) -5);
        if (dpSlots.contains((short) -6)) order.add((short) -6);
        for (Short s : dpSlots) {
            if (s != (short) -5 && s != (short) -6) order.add(s);
        }
        Map<Short, Equip> full = new HashMap<>(picks);
        full.put((short) -11, targetWeapon);
        for (Short slot : order) {
            Equip target = full.get(slot);
            Equip current = currentBySlot.get(slot);
            if (target == null) continue;
            if (target == current) continue;
            short pos = target.getPosition();
            if (pos <= 0) continue; // already in an EQUIPPED slot — skip to avoid swap loops
            InventoryManipulator.handleItemMove(bot.getClient(), InventoryType.EQUIP,
                    pos, slot, (short) 1);
        }
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
     * Builds a slot-keyed lookahead pool from the given equip inventory. Slot {@code -11}
     * holds weapons (currently-wearable + stat-only-blocked) so non-weapon scoring can
     * project damage under any weapon the character could unlock. Other non-ring slots
     * hold stat-only-blocked items only — currently-wearable non-weapons are presumed to
     * already be on the character (autoEquip handles them); the lookahead only models
     * items the character could wear if a candidate's stats unlocked them. Ring slots
     * are omitted (rings are scored greedily across slots, not via lookahead).
     * Each slot's list is dominance-pruned: an item B prunes A only if B's stats ≥ A's
     * AND B's job/level/stat reqs ≤ A's, so a weaker-but-easier item isn't dropped.
     */
    private static Map<Short, List<Equip>> buildLookaheadBySlot(Character bot, ItemInformationProvider ii, Inventory equipInv) {
        Map<Short, List<Equip>> bySlot = new HashMap<>();
        for (Item item : equipInv.list()) {
            if (ii.isCash(item.getItemId())) continue;
            if (!(item instanceof Equip equip)) continue;
            String textSlot = ii.getEquipmentSlot(item.getItemId());
            if (textSlot == null) continue;
            EquipSlot eslot = EquipSlot.getFromTextSlot(textSlot);
            if (eslot == EquipSlot.PET_EQUIP) continue;
            short primary = (short) eslot.getPrimarySlot();
            if (primary == 0) continue;
            if (isRingSlot(primary)) continue;
            if (primary == (short) -11) {
                if (!isWeaponCompatible(bot, ii.getWeaponType(equip.getItemId()))) continue;
                if (ii.canWearEquipment(bot, equip, primary) || statOnlyBlocked(bot, ii, equip)) {
                    bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
                }
            } else {
                if (ii.canWearEquipment(bot, equip, primary)) continue;
                if (!statOnlyBlocked(bot, ii, equip)) continue;
                bySlot.computeIfAbsent(primary, k -> new ArrayList<>()).add(equip);
            }
        }
        for (Map.Entry<Short, List<Equip>> e : bySlot.entrySet()) {
            e.setValue(pruneDominatedWithReqs(ii, e.getValue()));
        }
        return bySlot;
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
        Map<Short, List<Equip>> lookaheadBySlot = buildLookaheadBySlot(receiver, ii, receiverEquipInv);

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

        MapDamageProfile mobProfile = MapDamageProfile.snapshotByAvoid(receiver);
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
            Map<Short, List<Equip>> lookahead = slot == (short) -11 ? null : lookaheadBySlot;
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
            // Joint top×pants enumeration captures cases where the pair's stats unlock a
            // stronger weapon via lookahead. Recommending just the top here unblocks the
            // slot=-6 iteration (overallRec becomes false below) which then emits the
            // corresponding pants recommendation.
            if (slot == (short) -5 && best == effectiveCurrent && isOverall(current, ii)) {
                List<Equip> topCands = bySlot.getOrDefault(slot, List.of()).stream()
                        .filter(e -> !isOverall(e, ii)).collect(Collectors.toList());
                List<Equip> pantsCands = bySlot.getOrDefault((short) -6, List.of());
                TopPantsCombo combo = bestTopPantsCombo(receiver, ii, weaponType, current, topCands, pantsCands,
                                                       mobProfile, lookahead, effectiveWeapon);
                if (combo != null && compareScores(combo.score(),
                        scoreEquipFull(receiver, ii, weaponType, current, current, null, mobProfile, lookahead, effectiveWeapon)) > 0) {
                    best = combo.top();
                }
            }
            if (best != null && best != current) {
                recommendations.add(new EquipRecommendation(slot, current, best));
                if (slot == (short) -5) overallRec = isOverall(best, ii);
            }
        }

        if (bySlot.containsKey((short) -12)) {
            recommendations.addAll(findRecommendedRings(receiver, ii, weaponType, bySlot.get((short) -12), receiverEquippedInv, mobProfile, lookaheadBySlot));
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
        MapDamageProfile mobProfile = MapDamageProfile.snapshotByAvoid(receiver);

        // Build weapon lookahead pool from receiver's unequipped weapons (mirrors findRecommendedEquips).
        Map<Short, List<Equip>> lookaheadBySlot = buildLookaheadBySlot(receiver, ii, receiverEquipInv);

        if (isRingSlot(primarySlot)) {
            return findRecommendedRingForItem(receiver, ii, weaponType, candidate, receiverEquippedInv, mobProfile, lookaheadBySlot);
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

        // Pants need a top — when an overall is worn, only suggest if (best-available-top + this pants) beats the overall.
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
            // Joint enumeration: pants is fixed (the candidate being asked about), pick the
            // best partner top under the resulting combo's stats (incl. weapon lookahead).
            TopPantsCombo combo = bestTopPantsCombo(receiver, ii, weaponType, overall, topCands, List.of(candidate),
                                                   mobProfile, lookaheadBySlot, effWeapon);
            if (combo == null || compareScores(combo.score(),
                    scoreEquipFull(receiver, ii, weaponType, overall, overall, null, mobProfile, lookaheadBySlot, effWeapon)) <= 0) return null;
        }

        Equip current = (Equip) receiverEquippedInv.getItem(primarySlot);
        if (primarySlot == (short) -11) {
            current = compatibleWeaponOrNull(receiver, ii, current);
        }
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        // Lookahead applies only when scoring non-weapon slots.
        Map<Short, List<Equip>> lookahead = primarySlot == (short) -11 ? null : lookaheadBySlot;
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

    /**
     * Unequips any currently-worn non-cash item whose reqs no longer hold against the bot's
     * current totals. Used after {@link #applyEquipPlan} to clean up gear the optimizer chose
     * to leave bare (e.g. boots whose dex prereq was satisfied only by a now-removed overall).
     */
    private static void unequipInfeasibleEquipped(Character bot, ItemInformationProvider ii) {
        Inventory eqdInv = bot.getInventory(InventoryType.EQUIPPED);
        List<Short> bad = new ArrayList<>();
        for (Item it : eqdInv.list()) {
            if (!(it instanceof Equip e)) continue;
            if (ii.isCash(e.getItemId())) continue;
            if (!ii.canWearEquipment(bot, e, e.getPosition())) bad.add(e.getPosition());
        }
        if (bad.isEmpty()) return;
        short[] arr = new short[bad.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = bad.get(i);
        unequipSlot(bot, arr);
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
            case "bottom", "pant", "pants" -> new short[]{-6};
            case "shoe", "shoes", "boot", "boots" -> new short[]{-7};
            case "glove", "gloves" -> new short[]{-8};
            case "cape", "capes" -> new short[]{-9};
            case "shield", "shields", "offhand" -> new short[]{-10};
            case "weapon", "weapons", "wep" -> new short[]{-11};
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
                                           Map<Short, List<Equip>> lookaheadBySlot) {
        boolean changed = false;
        List<Equip> pool = new ArrayList<>(candidates);
        Equip effectiveWeapon = compatibleWeaponOrNull(bot, ii, (Equip) eqdInv.getItem((short) -11));
        for (short rs : RING_SLOTS) {
            Equip current = (Equip) eqdInv.getItem(rs);
            List<Equip> eligible = pool.stream()
                    .filter(c -> ii.canWearEquipment(bot, c, rs))
                    .collect(Collectors.toList());
            Equip best = findBestWithLookahead(bot, ii, wt, current, eligible, mobProfile,
                                                lookaheadBySlot, effectiveWeapon);
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
                                                                  Map<Short, List<Equip>> lookaheadBySlot) {
        List<EquipRecommendation> recommendations = new ArrayList<>();
        List<Equip> pool = new ArrayList<>(candidates);
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        for (short ringSlot : RING_SLOTS) {
            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            List<Equip> eligible = pool.stream()
                    .filter(candidate -> ii.canWearEquipment(receiver, candidate, ringSlot))
                    .collect(Collectors.toList());
            Equip best = findBestWithLookahead(receiver, ii, wt, current, eligible, mobProfile, lookaheadBySlot, effectiveWeapon);
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
                                                                  Map<Short, List<Equip>> lookaheadBySlot) {
        EquipRecommendation bestRecommendation = null;
        EquipScore bestScore = null;
        Equip effectiveWeapon = compatibleWeaponOrNull(receiver, ii, (Equip) receiverEquippedInv.getItem((short) -11));
        for (short ringSlot : RING_SLOTS) {
            if (!ii.canWearEquipment(receiver, candidate, ringSlot)) {
                continue;
            }

            Equip current = (Equip) receiverEquippedInv.getItem(ringSlot);
            EquipScore currentScore = scoreEquipFull(receiver, ii, wt, current, current, null, mobProfile, lookaheadBySlot, effectiveWeapon);
            EquipScore candidateScore = scoreEquipFull(receiver, ii, wt, current, candidate, null, mobProfile, lookaheadBySlot, effectiveWeapon);
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
     * Picks the best candidate for a slot. {@code lookaheadBySlot} is non-null only when
     * scoring a non-weapon slot — its contents are weapons (currently wearable + stat-blocked)
     * that the candidate may unlock. Pass null to disable lookahead.
     */
    private static Equip findBestWithLookahead(Character bot, ItemInformationProvider ii, WeaponType wt,
                                                Equip current, List<Equip> candidates,
                                                MapDamageProfile mobProfile,
                                                Map<Short, List<Equip>> lookaheadBySlot, Equip currentWeapon) {
        if (candidates == null || candidates.isEmpty()) return null;
        Equip best = current;
        EquipScore bestScore = scoreEquipFull(bot, ii, wt, current, current, null, mobProfile,
                                              lookaheadBySlot, currentWeapon);
        for (Equip c : candidates) {
            EquipScore cs = scoreEquipFull(bot, ii, wt, current, c, null, mobProfile,
                                            lookaheadBySlot, currentWeapon);
            if (compareScores(cs, bestScore) > 0) {
                best = c;
                bestScore = cs;
            }
        }
        return best;
    }

    /**
     * Mob-aware score with optional weapon-slot lookahead. {@code loss} simulates an extra
     * displaced item (pants on overall, shield on 2H). When {@code lookaheadBySlot} is
     * non-null and the candidate is not itself a weapon, this method probes the pool for
     * the best weapon wearable under the simulated stat totals; if that beats the current
     * weapon, the score reflects the unlocked weapon's damage. Otherwise behavior matches
     * a straight "swap candidate for replacing" damage projection.
     */
    private static EquipScore scoreEquipFull(Character bot, ItemInformationProvider ii, WeaponType wt,
                                              Equip replacing, Equip candidate, Equip loss,
                                              MapDamageProfile mobProfile,
                                              Map<Short, List<Equip>> lookaheadBySlot, Equip currentWeapon) {
        return scoreEquipCombo(bot, ii, wt, replacing, candidate, loss, null,
                               mobProfile, lookaheadBySlot, currentWeapon);
    }

    private record TopPantsCombo(Equip top, Equip pants, EquipScore score) {}

    /**
     * Joint enumeration of top × pants pairs. Each pair is scored as "swap {@code overall}
     * for the top, gain the pants" so the combined stats feed into damage, weapon lookahead
     * (a pant that unlocks a stronger weapon when paired with a specific top is detected),
     * and def/statSum tiebreakers. Returns null if either list is empty.
     */
    private static TopPantsCombo bestTopPantsCombo(Character bot, ItemInformationProvider ii, WeaponType wt,
                                                    Equip overall, List<Equip> tops, List<Equip> pants,
                                                    MapDamageProfile mobProfile,
                                                    Map<Short, List<Equip>> lookaheadBySlot, Equip currentWeapon) {
        if (tops == null || tops.isEmpty() || pants == null || pants.isEmpty()) return null;
        TopPantsCombo best = null;
        for (Equip t : tops) {
            for (Equip p : pants) {
                EquipScore s = scoreEquipCombo(bot, ii, wt, overall, t, null, p,
                                                mobProfile, lookaheadBySlot, currentWeapon);
                if (best == null || compareScores(s, best.score()) > 0) {
                    best = new TopPantsCombo(t, p, s);
                }
            }
        }
        return best;
    }

    /**
     * Like {@link #scoreEquipFull} but with an additional {@code gain} item added to the
     * simulated state. Used to score combos that occupy multiple slots — e.g. swapping an
     * overall for a top while simultaneously equipping pants. The {@code gain}'s stats
     * contribute to damage and to the def/statSum tiebreakers.
     */
    private static EquipScore scoreEquipCombo(Character bot, ItemInformationProvider ii, WeaponType wt,
                                               Equip replacing, Equip candidate, Equip loss, Equip gain,
                                               MapDamageProfile mobProfile,
                                               Map<Short, List<Equip>> lookaheadBySlot, Equip currentWeapon) {
        StatSnapshot sim = StatSnapshot.of(bot).swap(replacing, candidate);
        if (loss != null) sim = sim.swap(loss, null);
        if (gain != null) sim = sim.swap(null, gain);

        // Lock in candidate-as-weapon early so the unlock probe uses the right weapon type
        // when scoring damage-driven slot unlocks.
        boolean candidateIsWeapon = candidate != null && ItemConstants.isWeapon(candidate.getItemId());
        Equip simWeapon = currentWeapon;
        WeaponType simWt = wt;
        if (candidateIsWeapon) {
            simWeapon = candidate;
            WeaponType cWt = ii.getWeaponType(candidate.getItemId());
            if (cWt != null) simWt = cWt;
        }

        // Iteratively probe non-weapon slot unlocks: a candidate's stats may unlock a hat,
        // whose stats in turn unlock pants, etc. Each pass lets the simulated state catch
        // up; we re-run the weapon probe afterward so weapon damage reflects the final
        // post-unlock stat totals. Selection metric is projected damage (magic for mages),
        // so a +11-secondary item doesn't beat a +10-main one when main drives more damage.
        int defDelta = 0;
        int statDelta = 0;
        if (lookaheadBySlot != null && !lookaheadBySlot.isEmpty()) {
            Map<Short, Equip> simSlot = new HashMap<>();
            for (Item it : bot.getInventory(InventoryType.EQUIPPED).list()) {
                if (it instanceof Equip e) simSlot.put(it.getPosition(), e);
            }
            if (replacing != null) simSlot.put(replacing.getPosition(), candidate);
            if (loss != null)      simSlot.remove(loss.getPosition());
            if (gain != null) {
                short gs = primarySlotOf(ii, gain);
                if (gs != 0) simSlot.put(gs, gain);
            }
            if (candidate != null && isOverall(candidate, ii)) simSlot.remove((short) -6);

            Set<Equip> applied = new HashSet<>();
            boolean changed = true;
            while (changed) {
                changed = false;
                List<Short> slots = lookaheadBySlot.keySet().stream().sorted().collect(Collectors.toList());
                for (Short slotKey : slots) {
                    short slot = slotKey;
                    if (slot == (short) -11) continue;
                    List<Equip> pool = lookaheadBySlot.get(slot);
                    if (pool == null || pool.isEmpty()) continue;
                    Equip currentInSlot = simSlot.get(slot);
                    int currentObj = unlockObjective(sim, ii, simWt, mobProfile);
                    Equip bestUnlock = null;
                    StatSnapshot bestTrial = sim;
                    int bestObj = currentObj;
                    for (Equip cand : pool) {
                        if (applied.contains(cand)) continue;
                        if (cand == currentInSlot) continue;
                        if (!ii.meetsEquipRequirements(cand, sim.job(), sim.level(),
                                sim.str(), sim.dex(), sim.int_(), sim.luk(), sim.fame())) continue;
                        StatSnapshot trial = sim.swap(currentInSlot, cand);
                        int obj = unlockObjective(trial, ii, simWt, mobProfile);
                        if (obj > bestObj) { bestObj = obj; bestUnlock = cand; bestTrial = trial; }
                    }
                    if (bestUnlock != null) {
                        sim = bestTrial;
                        defDelta += defScore(bestUnlock) - defScore(currentInSlot);
                        statDelta += usefulStatSum(bestUnlock, sim.job()) - usefulStatSum(currentInSlot, sim.job());
                        applied.add(bestUnlock);
                        simSlot.put(slot, bestUnlock);
                        changed = true;
                    }
                }
            }
        }

        // Weapon probe: existing semantics — if candidate is a weapon, simWt is already set;
        // else probe the weapon pool under post-unlock stats for the highest-damage option.
        List<Equip> weaponPool = lookaheadBySlot != null ? lookaheadBySlot.get((short) -11) : null;
        if (!candidateIsWeapon && weaponPool != null && !weaponPool.isEmpty()) {
            int baselineDmg = damageWith(sim, ii, simWt, mobProfile);
            int bestDmg = baselineDmg;
            StatSnapshot bestSim = sim;
            for (Equip w : weaponPool) {
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

        int def = defScore(candidate) + defScore(gain) + defDelta;
        int stat = usefulStatSum(candidate, sim.job()) + usefulStatSum(gain, sim.job()) + statDelta;
        if (isMageJob(sim.job())) {
            return new EquipScore(magicScore(sim), def, stat);
        }
        if (simWt == null) {
            return new EquipScore(0, def, stat);
        }
        int dmg = damageWith(sim, ii, simWt, mobProfile);
        // DPS scaling: scale by the cycle of the weapon currently in the simulated state.
        int cycleMs = simWeapon != null ? weaponCycleMs(simWeapon.getItemId()) : 0;
        if (cycleMs > 0) dmg = (int) (dmg * 1000.0 / cycleMs);
        return new EquipScore(dmg, def, stat);
    }

    /**
     * Single objective for non-weapon unlock selection: damage for physical jobs, magic
     * score for mages, 0 when no weapon is in play. Aligns probe selection with the same
     * metric used to score the candidate as a whole, so a +secondary unlock can't beat a
     * +primary unlock when primary drives more damage.
     */
    private static int unlockObjective(StatSnapshot sim, ItemInformationProvider ii,
                                        WeaponType simWt, MapDamageProfile mobProfile) {
        if (isMageJob(sim.job())) return magicScore(sim);
        if (simWt == null) return 0;
        return damageWith(sim, ii, simWt, mobProfile);
    }

    private static short primarySlotOf(ItemInformationProvider ii, Equip e) {
        if (e == null) return 0;
        String ts = ii.getEquipmentSlot(e.getItemId());
        if (ts == null) return 0;
        EquipSlot es = EquipSlot.getFromTextSlot(ts);
        return es == null ? 0 : (short) es.getPrimarySlot();
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
        try {
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
        } catch (Throwable t) {
            // WZ data may not be initialized in unit-test contexts; fall back to no DPS scaling.
            return 0;
        }
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
        // Weights INT and MAGIC almost equally — INT*1.1 nudges main-stat ties toward INT
        // (matches v83 mage growth where INT scales magic damage and unlocks better gear).
        return (int) Math.round(sim.int_() * 1.1d) + sim.magic();
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
            case -8 -> "glove";
            case -7 -> "shoes";
            case -6 -> "pants";
            case -5 -> "top";
            case -4 -> "earring";
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
    record StatSnapshot(int str, int dex, int int_, int luk, int watk, int magic, int flatAcc,
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

    /**
     * Prune for lookahead pools where reqs differ across items. {@code b} dominates {@code a}
     * only if b's stats ≥ a's AND b's reqs ≤ a's — otherwise a weaker but easier-to-wear
     * item is meaningful and must be retained.
     */
    private static List<Equip> pruneDominatedWithReqs(ItemInformationProvider ii, List<Equip> items) {
        if (items == null || items.size() <= 1) return items;
        List<Equip> kept = new ArrayList<>(items.size());
        for (Equip a : items) {
            boolean dominated = false;
            for (Equip b : items) {
                if (a == b) continue;
                if (dominates(b, a) && reqsAtLeastAsEasy(ii, b, a)) { dominated = true; break; }
            }
            if (!dominated) kept.add(a);
        }
        return kept.isEmpty() ? items : kept;
    }

    private static boolean reqsAtLeastAsEasy(ItemInformationProvider ii, Equip b, Equip a) {
        if (ii.getEquipLevelReq(b.getItemId()) > ii.getEquipLevelReq(a.getItemId())) return false;
        Map<String, Integer> bs = ii.getEquipStats(b.getItemId());
        Map<String, Integer> as = ii.getEquipStats(a.getItemId());
        if (bs == null || as == null) return bs == as;
        // reqJob is a job mask — different masks aren't comparable; require equal to be safe.
        if (bs.getOrDefault("reqJob", 0).intValue() != as.getOrDefault("reqJob", 0).intValue()) return false;
        for (String key : new String[]{"reqSTR", "reqDEX", "reqINT", "reqLUK", "reqPOP"}) {
            if (bs.getOrDefault(key, 0) > as.getOrDefault(key, 0)) return false;
        }
        return true;
    }

    private static int[] statVec(Equip e) {
        return new int[]{e.getStr(), e.getDex(), e.getInt(), e.getLuk(),
                          e.getWatk(), e.getMatk(), e.getWdef(), e.getMdef(),
                          e.getAcc(), e.getAvoid(), e.getHp(), e.getMp(),
                          e.getSpeed(), e.getJump()};
    }
}
