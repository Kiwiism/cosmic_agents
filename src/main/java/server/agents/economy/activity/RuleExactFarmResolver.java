package server.agents.economy.activity;

import server.agents.economy.catalog.EconomyCatalog;
import server.agents.economy.catalog.MonsterDropFact;
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
                        String lotId = plan.sessionId() + ":" + work.monsterId() + ":" + kill
                                + ":" + drop.itemId();
                        itemDrops.add(new FarmSessionOutcome.ItemDrop(lotId, work.monsterId(), kill,
                                drop.itemId(), quantity, drop.questId(), drop.chance(), effectiveChance));
                    }
                }
            }
        }
        return new FarmSessionOutcome(plan.sessionId(), plan.calibrationId(), plan.agentId(), plan.mapId(),
                plan.startedAt().plus(plan.duration()), experience, mesos, itemDrops,
                plan.consumedItems(), kills);
    }

    private static int inclusive(NamedRandomStreams.Stream random, int minimum, int maximum) {
        if (minimum == maximum) return minimum;
        return minimum + random.nextInt(maximum - minimum + 1);
    }
}
