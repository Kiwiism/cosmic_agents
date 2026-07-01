package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentInventoryTradePolicy.EquipsGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentEquipTradeGroupService {
    private AgentEquipTradeGroupService() {
    }

    public record AgentEquipTradeGroups(List<Item> normal,
                                        List<Item> reservedForOther,
                                        List<Item> reservedForSelf) {
        public List<Item> itemsFor(EquipsGroup group) {
            return switch (group) {
                case NORMAL -> normal;
                case RESERVED_FOR_OTHER -> reservedForOther;
                case RESERVED_FOR_SELF -> reservedForSelf;
            };
        }
    }

    public record AgentEquipTradeClassification(AgentEquipTradeGroups groups,
                                                long reservedOtherNs,
                                                int reservedOtherChecks,
                                                int reservedOtherHits,
                                                long sortNs) {}

    public static List<Item> allTradeItems(AgentEquipTradeGroups groups) {
        List<Item> result = new ArrayList<>();
        for (EquipsGroup group : EquipsGroup.values()) {
            result.addAll(groups.itemsFor(group));
        }
        return result;
    }

    public static List<Item> reservedEquips(AgentEquipTradeGroups groups) {
        return new ArrayList<>(groups.reservedForSelf());
    }

    public static List<Item> reservedEquipTradePage(String category, AgentEquipTradeGroups groups) {
        return AgentInventoryTradePolicy.reservedEquipsPageItems(category, reservedEquips(groups));
    }

    public static String reservedEquipsPageMessage(String category, AgentEquipTradeGroups groups) {
        return AgentInventoryTradePolicy.reservedEquipsPageMessage(category, reservedEquips(groups).size());
    }

    public static String equipsGroupMessage(String category,
                                            Supplier<String> reservedForOtherMessage,
                                            Supplier<String> reservedForSelfMessage) {
        EquipsGroup group = AgentInventoryTradePolicy.equipsGroupFromCategory(category);
        if (group == null) {
            return null;
        }
        return switch (group) {
            case RESERVED_FOR_OTHER -> reservedForOtherMessage.get();
            case RESERVED_FOR_SELF -> reservedForSelfMessage.get();
            default -> null;
        };
    }

    public static String nextEquipsGroup(String category, AgentEquipTradeGroups groups) {
        return AgentInventoryTradePolicy.nextAvailableEquipsGroupCategory(category,
                group -> !groups.itemsFor(group).isEmpty());
    }

    public static EquipsGroup firstAvailableGroup(AgentEquipTradeGroups groups) {
        return AgentInventoryTradePolicy.firstAvailableEquipsGroup(group -> !groups.itemsFor(group).isEmpty());
    }

    public static AgentEquipTradeClassification classifyEquipGroups(Character agent,
                                                                    List<Item> all,
                                                                    Set<Item> selfKeep,
                                                                    Predicate<Item> isReservedForOther,
                                                                    boolean profile) {
        List<Item> normal = new ArrayList<>();
        List<Item> reservedForOther = new ArrayList<>();
        List<Item> reservedForSelf = new ArrayList<>();
        long reservedOtherNs = 0L;
        int reservedOtherChecks = 0;
        int reservedOtherHits = 0;
        for (Item item : all) {
            if (selfKeep.contains(item)) {
                reservedForSelf.add(item);
                continue;
            }
            long reservedOtherStartedAt = profile ? System.nanoTime() : 0L;
            boolean isOther = isReservedForOther.test(item);
            if (profile) {
                reservedOtherNs += System.nanoTime() - reservedOtherStartedAt;
                reservedOtherChecks++;
                if (isOther) {
                    reservedOtherHits++;
                }
            }
            if (isOther) {
                reservedForOther.add(item);
            } else {
                normal.add(item);
            }
        }

        long sortStartedAt = profile ? System.nanoTime() : 0L;
        List<Item> normalSorted = AgentInventoryTradePolicy.sortEquipsByItemId(normal);
        List<Item> reservedForOtherSorted = AgentInventoryTradePolicy.sortEquipsByItemId(reservedForOther);
        List<Item> reservedForSelfSorted = AgentInventoryTradePolicy.sortReservedEquipsByTradeScore(reservedForSelf, agent);
        long sortNs = profile ? System.nanoTime() - sortStartedAt : 0L;
        return new AgentEquipTradeClassification(
                new AgentEquipTradeGroups(normalSorted, reservedForOtherSorted, reservedForSelfSorted),
                reservedOtherNs,
                reservedOtherChecks,
                reservedOtherHits,
                sortNs);
    }
}
