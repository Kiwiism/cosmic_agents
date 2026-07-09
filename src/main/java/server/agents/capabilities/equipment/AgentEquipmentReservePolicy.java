package server.agents.capabilities.equipment;

import client.Character;
import client.Job;
import client.inventory.Equip;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import constants.inventory.EquipSlot;
import server.ItemInformationProvider;
import server.agents.integration.InventoryGateway;
import server.agents.integration.cosmic.CosmicAgentServerAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class AgentEquipmentReservePolicy {
    private AgentEquipmentReservePolicy() {
    }

    public interface EquipUsefulnessHooks {
        boolean isCash(int itemId);
        String getEquipmentSlot(int itemId);
        WeaponType getWeaponType(int itemId);
        boolean meetsReqs(Equip equip, Job job, int level, int str, int dex, int int_, int luk, int fame);

        static EquipUsefulnessHooks from(ItemInformationProvider ii) {
            return new EquipUsefulnessHooks() {
                @Override public boolean isCash(int itemId) { return ii.isCash(itemId); }
                @Override public String getEquipmentSlot(int itemId) { return ii.getEquipmentSlot(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return ii.getWeaponType(itemId); }
                @Override public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex,
                                                   int int_, int luk, int fame) {
                    return ii.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
                }
            };
        }

        static EquipUsefulnessHooks from(InventoryGateway inventory) {
            return new EquipUsefulnessHooks() {
                @Override public boolean isCash(int itemId) { return inventory.isCashItem(itemId); }
                @Override public String getEquipmentSlot(int itemId) { return inventory.getEquipmentSlot(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return inventory.getWeaponType(itemId); }
                @Override public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex,
                                                   int int_, int luk, int fame) {
                    return inventory.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
                }
            };
        }
    }

    public interface SelfReserveHooks extends EquipUsefulnessHooks {
        int getEquipLevelReq(int itemId);
        Map<String, Integer> getEquipStats(int itemId);

        static SelfReserveHooks from(ItemInformationProvider ii) {
            return new SelfReserveHooks() {
                @Override public boolean isCash(int itemId) { return ii.isCash(itemId); }
                @Override public String getEquipmentSlot(int itemId) { return ii.getEquipmentSlot(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return ii.getWeaponType(itemId); }
                @Override public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex,
                                                   int int_, int luk, int fame) {
                    return ii.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
                }
                @Override public int getEquipLevelReq(int itemId) { return ii.getEquipLevelReq(itemId); }
                @Override public Map<String, Integer> getEquipStats(int itemId) { return ii.getEquipStats(itemId); }
            };
        }

        static SelfReserveHooks from(InventoryGateway inventory) {
            return new SelfReserveHooks() {
                @Override public boolean isCash(int itemId) { return inventory.isCashItem(itemId); }
                @Override public String getEquipmentSlot(int itemId) { return inventory.getEquipmentSlot(itemId); }
                @Override public WeaponType getWeaponType(int itemId) { return inventory.getWeaponType(itemId); }
                @Override public boolean meetsReqs(Equip equip, Job job, int level, int str, int dex,
                                                   int int_, int luk, int fame) {
                    return inventory.meetsEquipRequirements(equip, job, level, str, dex, int_, luk, fame);
                }
                @Override public int getEquipLevelReq(int itemId) { return inventory.getEquipLevelRequirement(itemId); }
                @Override public Map<String, Integer> getEquipStats(int itemId) { return inventory.getEquipStats(itemId); }
            };
        }
    }

    /**
     * Stat dimensions that count when deciding whether to reserve a bag item for the agent's
     * own future use. Excludes WDEF/MDEF/HP/MP/AVD/SPD/JUMP.
     */
    public enum RelevantStat {
        STR, DEX, INT, LUK, WATK, MATK, ACC;

        public int of(Equip equip) {
            return switch (this) {
                case STR -> equip.getStr();
                case DEX -> equip.getDex();
                case INT -> equip.getInt();
                case LUK -> equip.getLuk();
                case WATK -> equip.getWatk();
                case MATK -> equip.getMatk();
                case ACC -> equip.getAcc();
            };
        }
    }

    private static final EnumSet<RelevantStat> ALL_RELEVANT_STATS = EnumSet.allOf(RelevantStat.class);

    public static boolean shouldReserveOwnedItem(Character agent, Item item) {
        if (item instanceof Equip equip) {
            InventoryGateway inventory = inventory();
            return selectOwnedItemsForSelfReserve(agent,
                    SelfReserveHooks.from(inventory),
                    collectOwnedEquips(agent, inventory)).contains(equip);
        }
        return false;
    }

    public static boolean shouldReserveOwnedItem(Character agent, EquipUsefulnessHooks hooks, Equip item) {
        return isEquipUsefulToAgent(agent, hooks, item);
    }

    public static boolean shouldReserveOwnedItem(Character agent, ItemInformationProvider ii, Equip item) {
        return selectOwnedItemsForSelfReserve(agent, SelfReserveHooks.from(ii), collectOwnedEquips(agent, ii)).contains(item);
    }

    public static boolean wouldReserveIncomingItem(Character agent, ItemInformationProvider ii, Equip item) {
        List<Equip> owned = collectOwnedEquips(agent, ii);
        owned.add(item);
        return selectOwnedItemsForSelfReserve(agent, SelfReserveHooks.from(ii), owned).contains(item);
    }

    public static boolean wouldReserveIncomingItem(Character agent, Equip item) {
        InventoryGateway inventory = inventory();
        List<Equip> owned = collectOwnedEquips(agent, inventory);
        owned.add(item);
        return selectOwnedItemsForSelfReserve(agent, SelfReserveHooks.from(inventory), owned).contains(item);
    }

    public static boolean isEquipUsefulToAgent(Character recipient, ItemInformationProvider ii, Equip item) {
        return isEquipUsefulToAgent(recipient, EquipUsefulnessHooks.from(ii), item);
    }

    public static boolean statOnlyBlocked(Character agent, ItemInformationProvider ii, Equip equip) {
        return statOnlyBlocked(agent, EquipUsefulnessHooks.from(ii), equip);
    }

    public static boolean statOnlyBlocked(Character agent, EquipUsefulnessHooks hooks, Equip equip) {
        return hooks.meetsReqs(equip, agent.getJob(), agent.getLevel(),
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, agent.getFame());
    }

    public static boolean isOwnClassEquip(Character agent, ItemInformationProvider ii, Equip equip) {
        return isOwnClassEquip(agent, EquipUsefulnessHooks.from(ii), equip);
    }

    public static boolean futureOnlyBlocked(Character agent, ItemInformationProvider ii, Equip equip) {
        return statOnlyBlocked(agent, ii, equip);
    }

    public static boolean reqsAtLeastAsEasy(ItemInformationProvider ii, Equip better, Equip worse) {
        return reqsAtLeastAsEasy(SelfReserveHooks.from(ii), better, worse);
    }

    public static boolean reqsAtLeastAsEasy(SelfReserveHooks hooks, Equip better, Equip worse) {
        if (hooks.getEquipLevelReq(better.getItemId()) > hooks.getEquipLevelReq(worse.getItemId())) return false;
        Map<String, Integer> betterStats = hooks.getEquipStats(better.getItemId());
        Map<String, Integer> worseStats = hooks.getEquipStats(worse.getItemId());
        if (betterStats == null || worseStats == null) return betterStats == worseStats;
        if (!reqJobAtLeastAsEasy(betterStats.getOrDefault("reqJob", 0), worseStats.getOrDefault("reqJob", 0))) {
            return false;
        }
        for (String key : new String[]{"reqSTR", "reqDEX", "reqINT", "reqLUK", "reqPOP"}) {
            if (betterStats.getOrDefault(key, 0) > worseStats.getOrDefault(key, 0)) return false;
        }
        return true;
    }

    public static boolean sameFutureTrack(ItemInformationProvider ii, Equip a, Equip b) {
        String slotA = ii.getEquipmentSlot(a.getItemId());
        String slotB = ii.getEquipmentSlot(b.getItemId());
        if (!Objects.equals(slotA, slotB)) return false;
        return ii.getWeaponType(a.getItemId()) == ii.getWeaponType(b.getItemId());
    }

    public static EnumSet<RelevantStat> relevantStatsFor(Job job) {
        if (job == null) return ALL_RELEVANT_STATS.clone();
        if (AgentWeaponCompatibilityPolicy.isMageJob(job)) return EnumSet.of(RelevantStat.INT, RelevantStat.LUK, RelevantStat.MATK);
        int id = job.getId();
        int branch = id / 100;
        if (branch == 1 || branch == 11 || branch == 21)
            return EnumSet.of(RelevantStat.STR, RelevantStat.DEX, RelevantStat.WATK, RelevantStat.ACC);
        if (branch == 3 || branch == 13)
            return EnumSet.of(RelevantStat.DEX, RelevantStat.STR, RelevantStat.WATK);
        if (branch == 4 || branch == 14)
            return EnumSet.of(RelevantStat.LUK, RelevantStat.DEX, RelevantStat.WATK);
        if (id == 500)
            return EnumSet.of(RelevantStat.STR, RelevantStat.DEX, RelevantStat.WATK, RelevantStat.ACC);
        if (id / 10 == 51 || branch == 15)
            return EnumSet.of(RelevantStat.STR, RelevantStat.DEX, RelevantStat.WATK, RelevantStat.ACC);
        if (id / 10 == 52)
            return EnumSet.of(RelevantStat.DEX, RelevantStat.STR, RelevantStat.WATK);
        return ALL_RELEVANT_STATS.clone();
    }

    public static Set<Equip> selectItemsBeatingBaseline(EnumSet<RelevantStat> relevant,
                                                        Collection<Equip> bagItems,
                                                        Collection<Equip> baseline) {
        Set<Equip> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Equip candidate : bagItems) {
            if (!hasPositiveRelevant(relevant, candidate)) continue;
            if (anyDominates(relevant, baseline, candidate)) continue;
            keep.add(candidate);
        }
        return keep;
    }

    public static boolean isEquipUsefulToAgent(Character recipient, EquipUsefulnessHooks hooks, Equip item) {
        if (!isOwnClassEquip(recipient, hooks, item)) return false;
        String slot = textSlotKey(hooks, item);
        if (slot == null) return false;
        String weaponTrack = null;
        if (isWeaponSlot(slot)) {
            weaponTrack = AgentWeaponCompatibilityPolicy.weaponUsefulnessTrackKey(recipient, hooks.getWeaponType(item.getItemId()));
            if (weaponTrack == null) return false;
        }
        EnumSet<RelevantStat> relevant = relevantStatsFor(recipient.getJob());
        if (!hasPositiveRelevant(relevant, item)) return false;
        Inventory equippedInv = recipient.getInventory(InventoryType.EQUIPPED);
        List<Equip> baseline = new ArrayList<>();
        for (Item it : equippedInv.list()) {
            if (!(it instanceof Equip equip) || hooks.isCash(equip.getItemId())) continue;
            if (!slot.equals(textSlotKey(hooks, equip))) continue;
            if (weaponTrack != null) {
                String equippedTrack = AgentWeaponCompatibilityPolicy.weaponUsefulnessTrackKey(recipient, hooks.getWeaponType(equip.getItemId()));
                if (!weaponTrack.equals(equippedTrack)) continue;
            }
            baseline.add(equip);
        }
        return !anyDominates(relevant, baseline, item);
    }

    public static Set<Item> collectPotentialSelfUpgradeItems(Character agent) {
        InventoryGateway inventory = inventory();
        Set<Item> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Equip> bagEquips = collectOwnedBagEquips(agent, inventory);
        Set<Equip> selected = selectOwnedItemsForSelfReserve(agent,
                SelfReserveHooks.from(inventory),
                collectOwnedEquips(agent, inventory));
        for (Equip equip : bagEquips) {
            if (selected.contains(equip)) {
                keep.add(equip);
            }
        }
        return keep;
    }

    public static Set<Equip> selectOwnedItemsForSelfReserve(Character agent, ItemInformationProvider ii,
                                                            Collection<Equip> ownedItems) {
        return selectOwnedItemsForSelfReserve(agent, SelfReserveHooks.from(ii), ownedItems);
    }

    public static Set<Equip> selectOwnedItemsForSelfReserve(Character agent, SelfReserveHooks hooks,
                                                            Collection<Equip> ownedItems) {
        EnumSet<RelevantStat> relevant = relevantStatsFor(agent.getJob());
        Map<String, List<Equip>> byTrack = new LinkedHashMap<>();
        for (Equip equip : ownedItems) {
            if (equip == null || hooks.isCash(equip.getItemId())) continue;
            if (!isFutureOwnClassEquip(agent, hooks, equip)) continue;
            if (!hasPositiveRelevant(relevant, equip)) continue;
            String track = selfReserveTrackKey(agent, hooks, equip);
            if (track == null) continue;
            byTrack.computeIfAbsent(track, ignored -> new ArrayList<>()).add(equip);
        }

        Set<Equip> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        for (List<Equip> trackItems : byTrack.values()) {
            for (Equip candidate : trackItems) {
                boolean dominated = false;
                for (Equip other : trackItems) {
                    if (other == candidate) continue;
                    if (dominatesForSelfReserve(hooks, relevant, agent, other, candidate)) {
                        dominated = true;
                        break;
                    }
                }
                if (!dominated) keep.add(candidate);
            }
        }
        return keep;
    }

    public static int usefulStatSum(Equip equip, Job job) {
        if (equip == null) return 0;
        int hpmp = (int) Math.round((equip.getHp() + equip.getMp()) * 0.1d);
        if (AgentWeaponCompatibilityPolicy.isMageJob(job)) {
            return equip.getInt() * 5 + equip.getMatk() * 4 + equip.getLuk()
                    + equip.getMdef() + hpmp;
        }
        double sum = equip.getStr() + equip.getDex() + equip.getInt() * 1.1 + equip.getLuk()
                + equip.getWatk() * 4
                + (equip.getWdef() + equip.getMdef()) * 0.25
                + equip.getAcc() + equip.getAvoid() + equip.getSpeed()
                + (equip.getHp() + equip.getMp()) * 0.1;
        return (int) Math.round(sum);
    }

    public static String textSlotKey(ItemInformationProvider ii, Equip equip) {
        return textSlotKey(EquipUsefulnessHooks.from(ii), equip);
    }

    private static List<Equip> collectOwnedEquips(Character agent, ItemInformationProvider ii) {
        List<Equip> owned = new ArrayList<>();
        owned.addAll(collectOwnedBagEquips(agent, ii));
        Inventory equippedInv = agent.getInventory(InventoryType.EQUIPPED);
        for (Item item : equippedInv.list()) {
            if (item instanceof Equip equip && !ii.isCash(equip.getItemId())) owned.add(equip);
        }
        return owned;
    }

    private static List<Equip> collectOwnedEquips(Character agent, InventoryGateway inventory) {
        List<Equip> owned = new ArrayList<>();
        owned.addAll(collectOwnedBagEquips(agent, inventory));
        Inventory equippedInv = agent.getInventory(InventoryType.EQUIPPED);
        for (Item item : equippedInv.list()) {
            if (item instanceof Equip equip && !inventory.isCashItem(equip.getItemId())) owned.add(equip);
        }
        return owned;
    }

    private static List<Equip> collectOwnedBagEquips(Character agent, ItemInformationProvider ii) {
        List<Equip> owned = new ArrayList<>();
        Inventory equipInv = agent.getInventory(InventoryType.EQUIP);
        for (Item item : equipInv.list()) {
            if (item instanceof Equip equip && !ii.isCash(equip.getItemId())) owned.add(equip);
        }
        return owned;
    }

    private static List<Equip> collectOwnedBagEquips(Character agent, InventoryGateway inventory) {
        List<Equip> owned = new ArrayList<>();
        Inventory equipInv = agent.getInventory(InventoryType.EQUIP);
        for (Item item : equipInv.list()) {
            if (item instanceof Equip equip && !inventory.isCashItem(equip.getItemId())) owned.add(equip);
        }
        return owned;
    }

    private static InventoryGateway inventory() {
        return CosmicAgentServerAdapter.INSTANCE.inventory();
    }

    private static String selfReserveTrackKey(Character agent, EquipUsefulnessHooks hooks, Equip equip) {
        String slot = textSlotKey(hooks, equip);
        if (slot == null) return null;
        if (!isWeaponSlot(slot)) return slot;
        String weaponTrack = AgentWeaponCompatibilityPolicy.weaponUsefulnessTrackKey(agent, hooks.getWeaponType(equip.getItemId()));
        return weaponTrack != null ? slot + ":" + weaponTrack : null;
    }

    private static boolean dominatesForSelfReserve(SelfReserveHooks hooks, EnumSet<RelevantStat> relevant,
                                                   Character agent, Equip better, Equip worse) {
        boolean relevantDominates = paretoDominates(relevant, better, worse);
        boolean duplicateTieBreakDominates = sameRequirementSignature(hooks, better, worse)
                && better.getItemId() == worse.getItemId()
                && relevantStatsEqual(relevant, better, worse)
                && usefulStatSum(better, agent.getJob()) > usefulStatSum(worse, agent.getJob());
        if (!relevantDominates && !duplicateTieBreakDominates) return false;
        if (!reqsAtLeastAsEasy(hooks, better, worse)
                && !hooks.meetsReqs(better, agent.getJob(), agent.getLevel(),
                agent.getStr(), agent.getDex(), agent.getInt(), agent.getLuk(), agent.getFame())) return false;
        return true;
    }

    private static boolean hasPositiveRelevant(EnumSet<RelevantStat> relevant, Equip equip) {
        for (RelevantStat stat : relevant) if (stat.of(equip) > 0) return true;
        return false;
    }

    private static boolean anyDominates(EnumSet<RelevantStat> relevant, Collection<Equip> pool, Equip candidate) {
        for (Equip other : pool) {
            if (other == candidate) continue;
            if (paretoDominates(relevant, other, candidate)) return true;
        }
        return false;
    }

    private static boolean paretoDominates(EnumSet<RelevantStat> relevant, Equip a, Equip b) {
        RelevantStat primary = primaryStatFor(relevant);
        boolean strictlyGreater = false;
        for (RelevantStat stat : relevant) {
            int va = effectiveStatValue(stat, primary, a);
            int vb = effectiveStatValue(stat, primary, b);
            if (va < vb) return false;
            if (va > vb) strictlyGreater = true;
        }
        return strictlyGreater;
    }

    private static int effectiveStatValue(RelevantStat stat, RelevantStat primary, Equip equip) {
        if (stat == RelevantStat.ACC) return equip.getAcc() + equip.getDex();
        if (primary != null && stat == RelevantStat.WATK) {
            return equip.getWatk() + primary.of(equip) / 5;
        }
        if (stat == primary) {
            return stat.of(equip) + equip.getWatk() * 2;
        }
        return stat.of(equip);
    }

    private static RelevantStat primaryStatFor(EnumSet<RelevantStat> relevant) {
        if (relevant == null || !relevant.contains(RelevantStat.WATK)) return null;
        if (relevant.contains(RelevantStat.LUK)) return RelevantStat.LUK;
        if (relevant.contains(RelevantStat.STR) && relevant.contains(RelevantStat.ACC)) return RelevantStat.STR;
        if (relevant.contains(RelevantStat.DEX)) return RelevantStat.DEX;
        if (relevant.contains(RelevantStat.STR)) return RelevantStat.STR;
        return null;
    }

    private static boolean relevantStatsEqual(EnumSet<RelevantStat> relevant, Equip a, Equip b) {
        for (RelevantStat stat : relevant) {
            if (stat.of(a) != stat.of(b)) return false;
        }
        return true;
    }

    private static boolean sameRequirementSignature(SelfReserveHooks hooks, Equip a, Equip b) {
        if (hooks.getEquipLevelReq(a.getItemId()) != hooks.getEquipLevelReq(b.getItemId())) return false;
        Map<String, Integer> as = hooks.getEquipStats(a.getItemId());
        Map<String, Integer> bs = hooks.getEquipStats(b.getItemId());
        if (as == null || bs == null) return as == bs;
        for (String key : new String[]{"reqJob", "reqSTR", "reqDEX", "reqINT", "reqLUK", "reqPOP"}) {
            if (as.getOrDefault(key, 0).intValue() != bs.getOrDefault(key, 0).intValue()) return false;
        }
        return true;
    }

    private static boolean isOwnClassEquip(Character agent, EquipUsefulnessHooks hooks, Equip equip) {
        int level = agent.getLevel() > 0 ? agent.getLevel() : Short.MAX_VALUE;
        return hooks.meetsReqs(equip, agent.getJob(), level,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, agent.getFame());
    }

    private static boolean isFutureOwnClassEquip(Character agent, EquipUsefulnessHooks hooks, Equip equip) {
        return hooks.meetsReqs(equip, agent.getJob(), Short.MAX_VALUE,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4,
                Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4, Short.MAX_VALUE);
    }

    private static String textSlotKey(EquipUsefulnessHooks hooks, Equip equip) {
        String textSlot = hooks.getEquipmentSlot(equip.getItemId());
        if (textSlot == null) return null;
        EquipSlot slot = EquipSlot.getFromTextSlot(textSlot);
        if (slot == null || slot == EquipSlot.PET_EQUIP) return null;
        return textSlot;
    }

    private static boolean isWeaponSlot(String textSlot) {
        return "Wp".equals(textSlot) || "WpSi".equals(textSlot) || "WpSp".equals(textSlot);
    }

    private static boolean reqJobAtLeastAsEasy(int betterReqJob, int worseReqJob) {
        return betterReqJob == 0 || betterReqJob == worseReqJob;
    }
}
