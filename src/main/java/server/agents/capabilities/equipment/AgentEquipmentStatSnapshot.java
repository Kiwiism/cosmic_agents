package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import server.combat.CombatFormulaProvider;

import java.util.function.ToIntFunction;

/**
 * Snapshot of Agent totals plus job/level/fame for non-mutating wearability checks.
 * {@code flatAcc} is total accuracy minus its derived DEX/LUK component, so swaps can
 * recompute total accuracy after stat changes without re-reading live character state.
 */
public record AgentEquipmentStatSnapshot(int str,
                                         int dex,
                                         int int_,
                                         int luk,
                                         int watk,
                                         int magic,
                                         int flatAcc,
                                         int level,
                                         int fame,
                                         Job job) {
    public static AgentEquipmentStatSnapshot of(Character agent) {
        int totalAcc = CombatFormulaProvider.getInstance().getTotalAccuracy(agent);
        int derived = (int) Math.floor(agent.getTotalDex() * 0.8d + agent.getTotalLuk() * 0.5d);
        int flatAcc = Math.max(0, totalAcc - Math.max(0, derived));
        return new AgentEquipmentStatSnapshot(
                agent.getTotalStr(),
                agent.getTotalDex(),
                agent.getTotalInt(),
                agent.getTotalLuk(),
                agent.getTotalWatk(),
                agent.getTotalMagic(),
                flatAcc,
                agent.getLevel(),
                agent.getFame(),
                agent.getJob());
    }

    public AgentEquipmentStatSnapshot swap(Equip removed, Equip added) {
        return new AgentEquipmentStatSnapshot(
                str + d(added, removed, e -> (int) e.getStr()),
                dex + d(added, removed, e -> (int) e.getDex()),
                int_ + d(added, removed, e -> (int) e.getInt()),
                luk + d(added, removed, e -> (int) e.getLuk()),
                watk + d(added, removed, e -> (int) e.getWatk()),
                magic + d(added, removed, e -> (int) e.getInt()) + d(added, removed, e -> (int) e.getMatk()),
                flatAcc + d(added, removed, e -> (int) e.getAcc()),
                level,
                fame,
                job);
    }

    public int totalAcc() {
        int derived = (int) Math.floor(dex * 0.8d + luk * 0.5d);
        return Math.max(0, derived + flatAcc);
    }

    private static int d(Equip added, Equip removed, ToIntFunction<Equip> getter) {
        return (added != null ? getter.applyAsInt(added) : 0)
                - (removed != null ? getter.applyAsInt(removed) : 0);
    }
}
