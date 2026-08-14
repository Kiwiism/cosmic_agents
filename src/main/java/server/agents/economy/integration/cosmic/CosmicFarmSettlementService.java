package server.agents.economy.integration.cosmic;

import client.Character;
import client.QuestStatus;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import server.agents.economy.activity.FarmSessionOutcome;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;
import server.DeathPenaltyService;
import server.maps.MapleMap;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.recovery.AgentRespawnHealthPolicy;
import tools.Randomizer;
import tools.StringUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Applies an already-resolved activity through real Character and inventory rules. */
public final class CosmicFarmSettlementService {
    public void settle(Character agent, FarmSessionOutcome outcome, LongSupplier gameplayRandom) {
        Objects.requireNonNull(agent); Objects.requireNonNull(outcome); Objects.requireNonNull(gameplayRandom);
        if (agent.getClient() == null) throw new IllegalStateException("live agent client is required");
        if (outcome.mesos() > Integer.MAX_VALUE || outcome.experience() > Integer.MAX_VALUE)
            throw new IllegalStateException("farm settlement exceeds Character limits");
        String summary = "activity=" + outcome.sessionId() + " map=" + outcome.mapId()
                + " exp=" + outcome.experience() + " mesos=" + outcome.mesos()
                + " drops=" + outcome.itemDrops().size() + " died=" + outcome.death().died()
                + " downtimeMs=" + outcome.death().downtimeMillis();
        EconomyTransactionCoordinator.execute("offscreen-farm:" + outcome.sessionId(), agent, null,
                EconomyOperationKind.OFFSCREEN_FARM_SETTLEMENT, summary, context -> {
                    MutationEvidence evidence = Randomizer.withLongSource(gameplayRandom,
                            () -> mutate(agent, outcome));
                    context.recordEvidence("questKillProgress", evidence.questProgress());
                    context.recordEvidence("death", evidence.death());
                });
    }

    private static MutationEvidence mutate(Character agent, FarmSessionOutcome outcome) {
        for (var consumed : outcome.consumedItems()) {
            InventoryType type = ItemConstants.getInventoryType(consumed.itemId());
            if (agent.getInventory(type).countById(consumed.itemId()) < consumed.quantity())
                throw new IllegalStateException("calibrated consumable is no longer owned: " + consumed.itemId());
            InventoryManipulator.removeById(agent.getClient(), type, consumed.itemId(),
                    consumed.quantity(), false, true);
        }
        for (FarmSessionOutcome.ItemDrop drop : outcome.itemDrops()) {
            Item item = drop.equipmentStats().isEmpty()
                    ? new Item(drop.itemId(), (short) 0, (short) drop.quantity())
                    : equipment(drop.itemId(), drop.equipmentStats());
            if (!InventoryManipulator.addFromDrop(agent.getClient(), item, false))
                throw new IllegalStateException("inventory capacity changed before farm settlement");
        }
        if (outcome.mesos() > 0) {
            if (!agent.canHoldMeso((int) outcome.mesos()))
                throw new IllegalStateException("farm mesos exceed wallet capacity");
            agent.gainMeso((int) outcome.mesos(), false);
        }
        if (outcome.experience() > 0) agent.gainExp((int) outcome.experience(), false, false);
        Map<String, Object> questProgress = advanceQuestKills(agent, outcome.killCounts());
        Map<String, Object> death = applyDeath(agent, outcome);
        return new MutationEvidence(questProgress, death);
    }

    private static Map<String, Object> applyDeath(Character agent, FarmSessionOutcome outcome) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("died", outcome.death().died());
        evidence.put("calibratedProbabilityPerHour", outcome.death().calibratedProbabilityPerHour());
        evidence.put("downtimeMillis", outcome.death().downtimeMillis());
        evidence.put("mapId", outcome.mapId());
        if (!outcome.death().died()) return Map.copyOf(evidence);
        MapleMap deathMap = agent.getClient().getChannelServer().getMapFactory().getMap(outcome.mapId());
        if (deathMap == null) throw new IllegalStateException("calibrated death map is unavailable: " + outcome.mapId());
        int hpBefore = agent.getHp();
        DeathPenaltyService.Result result = agent.applyLogicalDeathConsequences(
                deathMap, outcome.death().occurredAt().toEpochMilli());
        int restoredHp = AgentRespawnHealthPolicy.restoredHp(agent.getMaxHp(),
                AgentCombatConfig.cfg.RESPAWN_HP_PERCENT);
        agent.updateHp(restoredHp);
        evidence.put("occurredAt", outcome.death().occurredAt().toString());
        evidence.put("experienceLost", result.experienceLost());
        evidence.put("consumedCharmItemId", result.consumedCharmItemId());
        evidence.put("prevented", result.prevented());
        evidence.put("reason", result.reason().name());
        evidence.put("town", deathMap.isTown());
        evidence.put("fieldLimit", deathMap.getFieldLimit());
        evidence.put("hpBefore", hpBefore);
        evidence.put("respawnHp", restoredHp);
        return Map.copyOf(evidence);
    }

    private static Map<String, Object> advanceQuestKills(Character agent, Map<Integer, Integer> kills) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        for (QuestStatus status : agent.getStartedQuests()) {
            Map<Integer, Integer> applied = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> kill : kills.entrySet()) {
                int required = status.getQuest().getMobAmountNeeded(kill.getKey());
                if (required <= 0) continue;
                int before = parseProgress(status.getProgress(kill.getKey()));
                int after = Math.min(required, Math.addExact(before, kill.getValue()));
                if (after <= before) continue;
                status.setProgress(kill.getKey(), StringUtil.getLeftPaddedStr(
                        Integer.toString(after), '0', 3));
                applied.put(kill.getKey(), after - before);
            }
            if (!applied.isEmpty()) evidence.put(Short.toString(status.getQuestID()), applied);
        }
        return Map.copyOf(evidence);
    }

    private static int parseProgress(String progress) {
        if (progress == null || progress.isBlank()) return 0;
        try { return Integer.parseInt(progress); }
        catch (NumberFormatException failure) {
            throw new IllegalStateException("invalid Cosmic quest kill progress " + progress, failure);
        }
    }

    private static Equip equipment(int itemId, Map<String, Integer> stats) {
        Equip equip = Equip.restored(itemId, (short) 0);
        equip.setQuantity((short) 1); equip.setUpgradeSlots(value(stats, "upgradeSlots"));
        equip.setLevel((byte) value(stats, "level")); equip.setStr(value(stats, "STR"));
        equip.setDex(value(stats, "DEX")); equip.setInt(value(stats, "INT")); equip.setLuk(value(stats, "LUK"));
        equip.setHp(value(stats, "MHP")); equip.setMp(value(stats, "MMP"));
        equip.setWatk(value(stats, "PAD")); equip.setMatk(value(stats, "MAD"));
        equip.setWdef(value(stats, "PDD")); equip.setMdef(value(stats, "MDD"));
        equip.setAcc(value(stats, "ACC")); equip.setAvoid(value(stats, "EVA"));
        equip.setHands(value(stats, "hands")); equip.setSpeed(value(stats, "Speed"));
        equip.setJump(value(stats, "Jump")); equip.setVicious(value(stats, "vicious"));
        equip.setItemLevel((byte) value(stats, "itemLevel")); equip.setItemExp(stats.getOrDefault("itemExp", 0));
        equip.setRingId(stats.getOrDefault("ringId", -1)); equip.setFlag(value(stats, "flag"));
        return equip;
    }

    private static short value(Map<String, Integer> stats, String key) {
        return (short) stats.getOrDefault(key, 0).intValue();
    }

    private record MutationEvidence(Map<String, Object> questProgress, Map<String, Object> death) { }
}
