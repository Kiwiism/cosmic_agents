package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.capabilities.contracts.AgentDisposition;
import server.agents.capabilities.inventory.AgentInventoryReservationRuntime;

import java.util.Map;

public final class AgentLootCapability
        implements AgentExecutableCapability<AgentLootCapability.Command> {
    public enum ProtectionPolicy {
        ANY_REQUIRED_ITEM,
        QUEST_ITEMS_ONLY
    }

    public record Command(Map<Integer, Integer> requiredItemCounts,
                          ProtectionPolicy protectionPolicy) implements AgentCapabilityCommand {
        public Command(Map<Integer, Integer> requiredItemCounts) {
            this(requiredItemCounts, ProtectionPolicy.ANY_REQUIRED_ITEM);
        }

        public Command {
            requiredItemCounts = requiredItemCounts == null ? Map.of() : Map.copyOf(requiredItemCounts);
            protectionPolicy = protectionPolicy == null ? ProtectionPolicy.ANY_REQUIRED_ITEM : protectionPolicy;
            if (requiredItemCounts.isEmpty()
                    || requiredItemCounts.entrySet().stream().anyMatch(entry -> entry.getKey() <= 0 || entry.getValue() <= 0)) {
                throw new IllegalArgumentException("positive item requirements are required");
            }
        }

        @Override
        public String type() {
            return "loot";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentLootCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentLootCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "loot";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentInventoryReservationRuntime.reserveObjectiveItems(
                context.entry(), command.requiredItemCounts(),
                AgentInventoryReservationRuntime.LOOT_CAPABILITY,
                AgentDisposition.QUEST_RESERVE, "active loot objective", 900, context.nowMs());
        if (command.protectionPolicy() == ProtectionPolicy.QUEST_ITEMS_ONLY
                && command.requiredItemCounts().keySet().stream().anyMatch(itemId -> !gateway.questItem(itemId))) {
            return AgentPrimitiveResults.blocked(
                    server.agents.capabilities.AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                    "loot policy permits only quest items");
        }
        boolean complete = command.requiredItemCounts().entrySet().stream().allMatch(entry ->
                gateway.itemCount(context.agent(), entry.getKey()) >= entry.getValue());
        if (complete) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("required loot verified"));
        }
        for (Integer itemId : command.requiredItemCounts().keySet()) {
            if (gateway.freeSlots(context.agent(), itemId) <= 0
                    && gateway.itemCount(context.agent(), itemId) < command.requiredItemCounts().get(itemId)) {
                return AgentPrimitiveResults.missing("inventory is full for required loot");
            }
        }
        gateway.lootNearby(context.agent(), command.requiredItemCounts().keySet());
        return AgentCapabilityStep.running("delegating to normal item pickup", false);
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        AgentInventoryReservationRuntime.releaseCapability(
                context.entry(), AgentInventoryReservationRuntime.LOOT_CAPABILITY);
    }
}
