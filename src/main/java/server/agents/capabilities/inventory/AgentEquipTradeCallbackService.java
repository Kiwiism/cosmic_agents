package server.agents.capabilities.inventory;

import client.Character;
import client.inventory.Item;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AgentEquipTradeCallbackService {
    private AgentEquipTradeCallbackService() {
    }

    public static AgentEquipTradeClassificationService.ClassificationCallbacks equipTradeCallbacks(
            BooleanSupplier profileEquips,
            Supplier<Long> slowWarnNs,
            AgentEquipTradeClassificationService.EquipBagCollector collectEquipBagItems,
            AgentEquipTradeClassificationService.SelfUpgradeCollector collectPotentialSelfUpgradeItems,
            Predicate<Item> isReservedForOtherRecipients,
            Supplier<Character> owner,
            AgentEquipTradeClassificationService.SlowClassificationLogger warnSlowClassification) {
        return AgentEquipTradeClassificationService.ClassificationCallbacks.of(
                profileEquips,
                slowWarnNs,
                collectEquipBagItems,
                collectPotentialSelfUpgradeItems,
                isReservedForOtherRecipients,
                owner,
                warnSlowClassification);
    }
}
