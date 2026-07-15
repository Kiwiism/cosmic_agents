package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeClassification;
import server.agents.capabilities.inventory.AgentEquipTradeGroupService.AgentEquipTradeGroups;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentEquipTradeClassificationService {
    private AgentEquipTradeClassificationService() {
    }

    public static AgentEquipTradeGroups classifyEquipTradeGroups(Character agent,
                                                                 ClassificationCallbacks callbacks) {
        long startedAt = callbacks.profileEquips() ? System.nanoTime() : 0L;
        long bagScanStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        List<Item> all = new ArrayList<>(callbacks.collectEquipBagItems(agent));
        long bagScanNs = startedAt != 0L ? System.nanoTime() - bagScanStartedAt : 0L;
        long selfKeepStartedAt = startedAt != 0L ? System.nanoTime() : 0L;
        Set<Item> selfKeep = callbacks.collectPotentialSelfUpgradeItems(agent);
        long selfKeepNs = startedAt != 0L ? System.nanoTime() - selfKeepStartedAt : 0L;

        AgentEquipTradeClassification classification = AgentEquipTradeGroupService.classifyEquipGroups(
                agent,
                all,
                selfKeep,
                callbacks::isReservedForOtherRecipients,
                startedAt != 0L);
        AgentEquipTradeGroups groups = classification.groups();
        if (startedAt != 0L) {
            long elapsedNs = System.nanoTime() - startedAt;
            if (elapsedNs >= callbacks.slowWarnNs()) {
                Character owner = callbacks.owner();
                callbacks.warnSlowClassification(new SlowClassificationReport(
                        elapsedNs,
                        agent != null ? agent.getName() : "?",
                        owner != null ? owner.getName() : "?",
                        all.size(),
                        selfKeep.size(),
                        groups.normal().size(),
                        groups.reservedForOther().size(),
                        groups.reservedForSelf().size(),
                        bagScanNs,
                        selfKeepNs,
                        classification.reservedOtherNs(),
                        classification.reservedOtherChecks(),
                        classification.reservedOtherHits(),
                        classification.sortNs()));
            }
        }
        return groups;
    }

    public record SlowClassificationReport(long elapsedNs,
                                           String agentName,
                                           String ownerName,
                                           int bagItems,
                                           int selfKeep,
                                           int normalItems,
                                           int reservedOtherItems,
                                           int reservedSelfItems,
                                           long bagScanNs,
                                           long selfKeepNs,
                                           long reservedOtherNs,
                                           int reservedOtherChecks,
                                           int reservedOtherHits,
                                           long sortNs) {}

    public interface ClassificationCallbacks {
        boolean profileEquips();
        long slowWarnNs();
        List<Item> collectEquipBagItems(Character agent);
        Set<Item> collectPotentialSelfUpgradeItems(Character agent);
        boolean isReservedForOtherRecipients(Item item);
        Character owner();
        void warnSlowClassification(SlowClassificationReport report);

        static ClassificationCallbacks of(BooleanSupplier profileEquips,
                                          Supplier<Long> slowWarnNs,
                                          EquipBagCollector collectEquipBagItems,
                                          SelfUpgradeCollector collectPotentialSelfUpgradeItems,
                                          Predicate<Item> isReservedForOtherRecipients,
                                          Supplier<Character> owner,
                                          SlowClassificationLogger warnSlowClassification) {
            return new ClassificationCallbacks() {
                @Override
                public boolean profileEquips() {
                    return profileEquips.getAsBoolean();
                }

                @Override
                public long slowWarnNs() {
                    return slowWarnNs.get();
                }

                @Override
                public List<Item> collectEquipBagItems(Character agent) {
                    return collectEquipBagItems.collect(agent);
                }

                @Override
                public Set<Item> collectPotentialSelfUpgradeItems(Character agent) {
                    return collectPotentialSelfUpgradeItems.collect(agent);
                }

                @Override
                public boolean isReservedForOtherRecipients(Item item) {
                    return isReservedForOtherRecipients.test(item);
                }

                @Override
                public Character owner() {
                    return owner.get();
                }

                @Override
                public void warnSlowClassification(SlowClassificationReport report) {
                    warnSlowClassification.warn(report);
                }
            };
        }

        static List<Item> collectEquipBag(Character agent,
                                          IntPredicate isQuestItem,
                                          IntPredicate allowsUntradeableItem) {
            return AgentInventoryCollectionService.collectFromBag(
                    agent,
                    InventoryType.EQUIP,
                    item -> true,
                    isQuestItem,
                    allowsUntradeableItem);
        }

        static List<Item> collectEquipBag(Character agent,
                                          IntPredicate isQuestItem,
                                          boolean untradeableItemsTradeable) {
            return collectEquipBag(agent, isQuestItem, itemId -> untradeableItemsTradeable);
        }
    }

    @FunctionalInterface
    public interface EquipBagCollector {
        List<Item> collect(Character agent);
    }

    @FunctionalInterface
    public interface SelfUpgradeCollector {
        Set<Item> collect(Character agent);
    }

    @FunctionalInterface
    public interface SlowClassificationLogger {
        void warn(SlowClassificationReport report);
    }
}
