package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.WeaponType;
import server.ItemInformationProvider;

import java.util.Map;

/**
 * Narrow item-data surface required by the equipment optimizer.
 */
public interface AgentEquipmentOptimizerHooks {
    boolean isTwoHanded(int itemId);
    WeaponType getWeaponType(int itemId);
    boolean isOverall(int itemId);
    boolean meetsReqs(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame);

    default Map<String, Integer> getEquipStats(int itemId) {
        return Map.of();
    }

    static AgentEquipmentOptimizerHooks from(ItemInformationProvider ii) {
        return new AgentEquipmentOptimizerHooks() {
            @Override
            public boolean isTwoHanded(int itemId) {
                return ii.isTwoHanded(itemId);
            }

            @Override
            public WeaponType getWeaponType(int itemId) {
                return ii.getWeaponType(itemId);
            }

            @Override
            public boolean isOverall(int itemId) {
                return "MaPn".equals(ii.getEquipmentSlot(itemId));
            }

            @Override
            public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame) {
                return ii.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
            }

            @Override
            public Map<String, Integer> getEquipStats(int itemId) {
                return ii.getEquipStats(itemId);
            }
        };
    }

    static AgentEquipmentOptimizerHooks futureFrom(ItemInformationProvider ii, Character agent) {
        final Job agentJob = agent != null ? agent.getJob() : null;
        final int effectiveLevel = agent != null && agent.getLevel() > 0 ? agent.getLevel() : Short.MAX_VALUE;
        final int effectiveFame = agent != null ? agent.getFame() : 0;
        final int max = Integer.MAX_VALUE / 4;
        return new AgentEquipmentOptimizerHooks() {
            @Override
            public boolean isTwoHanded(int itemId) {
                return ii.isTwoHanded(itemId);
            }

            @Override
            public WeaponType getWeaponType(int itemId) {
                return ii.getWeaponType(itemId);
            }

            @Override
            public boolean isOverall(int itemId) {
                return "MaPn".equals(ii.getEquipmentSlot(itemId));
            }

            @Override
            public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame) {
                return ii.meetsEquipRequirements(equip, agentJob, effectiveLevel, max, max, max, max, effectiveFame);
            }

            @Override
            public Map<String, Integer> getEquipStats(int itemId) {
                return ii.getEquipStats(itemId);
            }
        };
    }
}
