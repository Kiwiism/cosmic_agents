package server.agents.economy.integration.cosmic;

import client.Character;
import client.inventory.Equip;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import server.agents.economy.activity.FarmSessionOutcome;
import server.economy.EconomyOperationKind;
import server.economy.EconomyTransactionCoordinator;
import tools.Randomizer;

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
                + " drops=" + outcome.itemDrops().size();
        EconomyTransactionCoordinator.execute("offscreen-farm:" + outcome.sessionId(), agent, null,
                EconomyOperationKind.OFFSCREEN_FARM_SETTLEMENT, summary, context ->
                        Randomizer.withLongSource(gameplayRandom, () -> mutate(agent, outcome)));
    }

    private static void mutate(Character agent, FarmSessionOutcome outcome) {
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
}
