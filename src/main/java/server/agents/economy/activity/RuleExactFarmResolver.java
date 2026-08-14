package server.agents.economy.activity;

import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.MonsterDropFact;
import server.agents.economy.catalog.GlobalDropFact;
import server.agents.economy.catalog.ItemCategory;
import server.agents.economy.scenario.NamedRandomStreams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Replays Cosmic's one-million-denominator drop rule over explicit calibrated kill counts. */
public final class RuleExactFarmResolver {
    public static final int DROP_DENOMINATOR = 1_000_000;
    private final EconomyCatalog catalog;

    public RuleExactFarmResolver(EconomyCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    public FarmSessionOutcome resolve(FarmSessionPlan plan, NamedRandomStreams random) {
        NamedRandomStreams.Stream loot = random.stream("activity.loot");
        List<FarmSessionOutcome.ItemDrop> itemDrops = new ArrayList<>();
        Map<Integer, Integer> kills = new LinkedHashMap<>();
        long experience = 0;
        long mesos = 0;
        for (FarmSessionPlan.MonsterWork work : plan.monsters()) {
            kills.merge(work.monsterId(), work.kills(), Math::addExact);
            experience = Math.addExact(experience,
                    Math.multiplyExact((long) work.kills(), work.experiencePerKill()));
            List<MonsterDropFact> table = catalog.monsterDrops(work.monsterId());
            for (int kill = 1; kill <= work.kills(); kill++) {
                for (MonsterDropFact drop : table) {
                    if (drop.questId() > 0 && !plan.activeQuestIds().contains(drop.questId())) continue;
                    int effectiveChance = (int) Math.min((long) drop.chance() * plan.dropRateMultiplier(),
                            Integer.MAX_VALUE);
                    if (loot.nextInt(DROP_DENOMINATOR) >= effectiveChance) continue;
                    int quantity = inclusive(loot, drop.minimumQuantity(), drop.maximumQuantity());
                    if (drop.itemId() == 0) {
                        mesos = Math.addExact(mesos, quantity);
                    } else {
                        appendDrop(itemDrops, plan, work, kill, drop.itemId(), quantity,
                                loot,
                                drop.questId(), drop.chance(), effectiveChance);
                    }
                }
                for (GlobalDropFact drop : catalog.globalDrops(plan.mapId())) {
                    if (loot.nextInt(DROP_DENOMINATOR) >= drop.chance()) continue;
                    int quantity = inclusive(loot, drop.minimumQuantity(), drop.maximumQuantity());
                    appendDrop(itemDrops, plan, work, kill, drop.itemId(), quantity,
                            loot,
                            drop.questId(), drop.chance(), drop.chance());
                }
            }
        }
        return new FarmSessionOutcome(plan.sessionId(), plan.calibrationId(), plan.agentId(), plan.mapId(),
                plan.startedAt().plus(plan.duration()), experience, mesos, itemDrops,
                plan.consumedItems(), kills);
    }

    private void appendDrop(List<FarmSessionOutcome.ItemDrop> result, FarmSessionPlan plan,
                            FarmSessionPlan.MonsterWork work, int kill, int itemId, int quantity,
                            NamedRandomStreams.Stream loot,
                            int questId, int baseChance, int effectiveChance) {
        boolean equipment = catalog.item(itemId).map(fact -> fact.categories().contains(ItemCategory.EQUIPMENT))
                .orElseThrow(() -> new IllegalStateException("item catalog missing " + itemId));
        int instances = equipment ? quantity : 1;
        int eachQuantity = equipment ? 1 : quantity;
        for (int instance = 0; instance < instances; instance++) {
            String lotId = plan.sessionId() + ":" + work.monsterId() + ":" + kill + ":" + itemId
                    + (equipment ? ":" + instance : "");
            Map<String, Integer> equipmentStats = equipment
                    ? catalog.rollEquipment(itemId, loot::nextDouble)
                    .orElseThrow(() -> new IllegalStateException("equipment roll unavailable " + itemId)).stats()
                    : Map.of();
            result.add(new FarmSessionOutcome.ItemDrop(lotId, work.monsterId(), kill,
                    itemId, eachQuantity, questId, baseChance, effectiveChance, equipmentStats));
        }
    }

    private static int inclusive(NamedRandomStreams.Stream random, int minimum, int maximum) {
        if (minimum == maximum) return minimum;
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
