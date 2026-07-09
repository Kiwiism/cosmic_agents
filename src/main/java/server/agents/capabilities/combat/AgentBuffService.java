package server.agents.capabilities.combat;

import client.BuffStat;
import client.Character;
import client.Job;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import net.server.PlayerBuffValueHolder;
import net.server.channel.handlers.UseItemHandler;
import server.agents.capabilities.dialogue.AgentBuffDialogueReporter;
import server.agents.capabilities.inventory.AgentUseItemClassificationPolicy;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.integration.InventoryGateway;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;
import server.StatEffect;
import server.agents.runtime.AgentRuntimeEntry;
import server.combat.CombatFormulaProvider;
import server.life.Monster;
import tools.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages automatic use of buff consumable items from the bot's USE inventory.
 *
 * Validity check: AgentUseItemClassificationPolicy.isBuffConsumable (has statups) - same predicate used by
 * inv? and the trade/drop "buff" category, so all commands are consistent.
 *
 * Default: off. Configured via chat ("buff on/off", "buff cheap/max").
 */
public final class AgentBuffService {

    private static final long TICK_MS = 3_000;
    private static final double ACC_HIT_THRESHOLD = 0.90;

    /** Cheap mode skips WATK/MATK buffs stronger than this; pricier high-tier pots aren't worth it. */
    static final int CHEAP_ATK_CAP = 12;

    // Shared across bot ticks, so keep the cache concurrent.
    private static final Map<Integer, StatEffect> fxCache = new ConcurrentHashMap<>();

    private record SelectedBuff(Item item, StatEffect effect, List<BuffStat> statKey, int score) {}

    private record ActiveBuff(int itemId, String name, StatEffect effect, int remainingMs) {}

    private AgentBuffService() {}

    public static void tick(AgentRuntimeEntry entry, Character bot) {
        if (!AgentBuffStateRuntime.enabled(entry)) return;

        long now = System.currentTimeMillis();
        if (!AgentBuffStateRuntime.scanDue(entry, now, TICK_MS)) return;
        AgentBuffStateRuntime.markScanned(entry, now);

        if (bot.getMap().getAllMonsters().stream().noneMatch(Monster::isAlive)) return;

        List<SelectedBuff> selected = buildSelection(bot, AgentBuffStateRuntime.cheapMode(entry));
        if (selected.isEmpty()) {
            noteDecision(entry, "no buff pots in bag");
            return;
        }

        for (SelectedBuff choice : selected) {
            if (!needsAnyBuffStat(entry, bot, choice.effect())) {
                continue;
            }

            Item item = choice.item();
            short beforeQty = item.getQuantity();
            String itemName = itemName(item.getItemId());

            if (!UseItemHandler.consumeUseItem(bot, item.getPosition(), item.getItemId())) {
                noteDecision(entry, "tried " + itemName + " but apply failed");
                continue;
            }

            noteDecision(entry, "used " + itemName + " " + beforeQty + "->" + item.getQuantity()
                    + " [" + formatStatList(bot, choice.effect()) + "]");
            return;
        }

        noteDecision(entry, "buff pots ready, but all matching stats are already active");
    }

    public static String getChatSummary(boolean enabled, boolean cheapMode, Character bot) {
        List<ActiveBuff> active = collectActiveItemBuffs(bot);
        List<SelectedBuff> available = buildSelection(bot, cheapMode);

        return AgentBuffDialogueReporter.chatSummary(
                enabled,
                cheapMode,
                summarizeActive(active, 2, bot),
                summarizeAvailable(available, 2, bot));
    }

    public static List<String> getDebugLines(AgentRuntimeEntry entry, Character bot) {
        return AgentBuffDialogueReporter.debugLines(
                AgentBuffStateRuntime.enabled(entry),
                AgentBuffStateRuntime.cheapMode(entry),
                summarizeActive(collectActiveItemBuffs(bot), 5, bot),
                summarizeAvailable(buildSelection(bot, AgentBuffStateRuntime.cheapMode(entry)), 5, bot));
    }

    public static String formatDebugState(AgentRuntimeEntry entry) {
        return AgentBuffDialogueReporter.debugState(
                AgentBuffStateRuntime.enabled(entry),
                AgentBuffStateRuntime.cheapMode(entry));
    }

    private static List<SelectedBuff> buildSelection(Character bot, boolean cheapMode) {
        Inventory use = bot.getInventory(InventoryType.USE);
        Map<List<BuffStat>, SelectedBuff> best = new LinkedHashMap<>();

        for (short slot = 1; slot <= use.getSlotLimit(); slot++) {
            Item item = use.getItem(slot);
            if (item == null || item.getQuantity() <= 0) continue;

            int itemId = item.getItemId();
            if (!AgentUseItemClassificationPolicy.isBuffConsumable(itemId)) continue;

            StatEffect fx = fxCache.computeIfAbsent(itemId, AgentUseItemClassificationPolicy::itemEffect);
            if (fx == null || fx.getStatups().isEmpty()) continue;

            List<BuffStat> statKey = buildStatKey(bot, fx);
            if (statKey.isEmpty()) continue;

            if (cheapMode && exceedsCheapAtkCap(bot, fx)) continue;

            SelectedBuff candidate = new SelectedBuff(item, fx, statKey, scoreEffect(bot, fx));
            SelectedBuff current = best.get(statKey);
            if (current == null || isBetterChoice(candidate, current, cheapMode)) {
                best.put(statKey, candidate);
            }
        }

        List<SelectedBuff> result = new ArrayList<>(best.values());
        result.sort((left, right) -> {
            int scoreCmp = cheapMode
                    ? Integer.compare(left.score(), right.score())
                    : Integer.compare(right.score(), left.score());
            if (scoreCmp != 0) {
                return scoreCmp;
            }
            return itemName(left.item().getItemId()).compareTo(itemName(right.item().getItemId()));
        });
        return result;
    }

    private static boolean needsAnyBuffStat(AgentRuntimeEntry entry, Character bot, StatEffect fx) {
        for (Pair<BuffStat, Integer> statup : fx.getStatups()) {
            BuffStat stat = statup.getLeft();
            if (bot.getBuffedValue(stat) != null) {
                continue;
            }
            if (!isRelevantBuffStat(bot, stat)) {
                continue;
            }
            if (stat == BuffStat.ACC && !needsAccBuff(entry, bot)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean needsAccBuff(AgentRuntimeEntry entry, Character bot) {
        Monster ref = AgentGrindTargetStateRuntime.target(entry);
        if (ref == null) {
            for (Monster monster : bot.getMap().getAllMonsters()) {
                if (monster.isAlive()) {
                    ref = monster;
                    break;
                }
            }
        }
        if (ref == null) return false;
        return CombatFormulaProvider.getInstance().calculateMobHitChance(bot, ref) < ACC_HIT_THRESHOLD;
    }

    private static List<ActiveBuff> collectActiveItemBuffs(Character bot) {
        List<ActiveBuff> active = new ArrayList<>();

        for (PlayerBuffValueHolder holder : bot.getAllBuffs()) {
            StatEffect effect = holder.effect;
            if (effect == null || effect.isSkill()) {
                continue;
            }

            int itemId = effect.getSourceId();
            if (!AgentUseItemClassificationPolicy.isBuffConsumable(itemId)) {
                continue;
            }
            if (buildStatKey(bot, effect).isEmpty()) {
                continue;
            }

            int remainingMs = effect.getDuration() > 0
                    ? Math.max(0, effect.getDuration() - holder.usedTime)
                    : 0;
            active.add(new ActiveBuff(itemId,
                    itemName(itemId),
                    effect,
                    remainingMs));
        }

        active.sort(Comparator.comparing(ActiveBuff::name));
        return active;
    }

    private static List<BuffStat> buildStatKey(Character bot, StatEffect fx) {
        List<BuffStat> key = new ArrayList<>();
        for (Pair<BuffStat, Integer> statup : fx.getStatups()) {
            BuffStat stat = statup.getLeft();
            if (isRelevantBuffStat(bot, stat) && !key.contains(stat)) {
                key.add(stat);
            }
        }
        key.sort(Comparator.naturalOrder());
        return key;
    }

    private static int scoreEffect(Character bot, StatEffect fx) {
        int score = 0;
        for (Pair<BuffStat, Integer> statup : fx.getStatups()) {
            if (isRelevantBuffStat(bot, statup.getLeft())) {
                score += Math.abs(statup.getRight());
            }
        }
        return score;
    }

    /** True if a relevant WATK/MATK statup exceeds {@link #CHEAP_ATK_CAP}; cheap mode skips these. */
    public static boolean exceedsCheapAtkCap(Character bot, StatEffect fx) {
        for (Pair<BuffStat, Integer> statup : fx.getStatups()) {
            BuffStat stat = statup.getLeft();
            if ((stat == BuffStat.WATK || stat == BuffStat.MATK)
                    && isRelevantBuffStat(bot, stat)
                    && statup.getRight() > CHEAP_ATK_CAP) {
                return true;
            }
        }
        return false;
    }

    public static boolean isRelevantBuffStat(Character bot, BuffStat stat) {
        boolean mageJob = bot.getJobStyle() == Job.MAGICIAN;
        if (stat == BuffStat.WATK) {
            return !mageJob;
        }
        if (stat == BuffStat.MATK) {
            return mageJob;
        }
        return true;
    }

    private static boolean isBetterChoice(SelectedBuff candidate, SelectedBuff current, boolean cheapMode) {
        if (candidate.score() != current.score()) {
            return cheapMode ? candidate.score() < current.score() : candidate.score() > current.score();
        }

        if (candidate.effect().getDuration() != current.effect().getDuration()) {
            return cheapMode
                    ? candidate.effect().getDuration() < current.effect().getDuration()
                    : candidate.effect().getDuration() > current.effect().getDuration();
        }

        return cheapMode
                ? candidate.item().getItemId() < current.item().getItemId()
                : candidate.item().getItemId() > current.item().getItemId();
    }

    private static String summarizeActive(List<ActiveBuff> active, int limit, Character bot) {
        if (active.isEmpty()) {
            return "none";
        }

        StringJoiner joiner = new StringJoiner(", ");
        int count = Math.min(limit, active.size());
        for (int i = 0; i < count; i++) {
            ActiveBuff buff = active.get(i);
            String suffix = buff.remainingMs() > 0 ? " " + formatAge(buff.remainingMs()) + " left" : "";
            joiner.add(buff.name() + " [" + formatStatList(bot, buff.effect()) + suffix + "]");
        }
        if (active.size() > limit) {
            joiner.add("+" + (active.size() - limit) + " more");
        }
        return joiner.toString().toLowerCase(Locale.ROOT);
    }

    private static String summarizeAvailable(List<SelectedBuff> available, int limit, Character bot) {
        if (available.isEmpty()) {
            return "none in bag";
        }

        StringJoiner joiner = new StringJoiner(", ");
        int count = Math.min(limit, available.size());
        for (int i = 0; i < count; i++) {
            SelectedBuff buff = available.get(i);
            joiner.add(itemName(buff.item().getItemId()) + " x" + buff.item().getQuantity()
                    + " [" + formatStatList(bot, buff.effect()) + "]");
        }
        if (available.size() > limit) {
            joiner.add("+" + (available.size() - limit) + " more");
        }
        return joiner.toString().toLowerCase(Locale.ROOT);
    }

    private static String formatStatList(Character bot, StatEffect fx) {
        StringJoiner joiner = new StringJoiner("/");
        for (Pair<BuffStat, Integer> statup : fx.getStatups()) {
            if (!isRelevantBuffStat(bot, statup.getLeft())) {
                continue;
            }
            int value = statup.getRight();
            joiner.add(statup.getLeft().name() + (value >= 0 ? "+" : "") + value);
        }
        return joiner.toString();
    }

    private static String formatAge(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0) {
            return seconds + "s";
        }
        return minutes + "m" + seconds + "s";
    }

    private static void noteDecision(AgentRuntimeEntry entry, String summary) {
        AgentBuffStateRuntime.noteDecision(entry, System.currentTimeMillis(), summary);
    }

    private static String itemName(int itemId) {
        return safeName(inventory().getItemName(itemId), itemId);
    }

    private static InventoryGateway inventory() {
        return CosmicAgentServerAdapter.INSTANCE.inventory();
    }

    private static String safeName(String name, int itemId) {
        return name == null || name.isBlank() ? String.valueOf(itemId) : name;
    }
}
