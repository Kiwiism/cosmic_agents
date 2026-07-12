package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentCapabilityOutput;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AgentInventoryInspectionCapability
        implements AgentExecutableCapability<AgentInventoryInspectionCapability.Command> {
    public record Snapshot(int itemId, int count, int freeSlots) implements AgentCapabilityOutput {
    }

    public record Command(int itemId, int requiredCount, int requiredFreeSlots)
            implements AgentCapabilityCommand {
        public Command {
            if (itemId <= 0 || requiredCount < 0 || requiredFreeSlots < 0) {
                throw new IllegalArgumentException("valid item and non-negative requirements are required");
            }
        }

        @Override
        public String type() {
            return "inventory-inspection";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentInventoryInspectionCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentInventoryInspectionCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "inventory-inspection";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        int count = gateway.itemCount(context.agent(), command.itemId());
        if (count < command.requiredCount()) {
            return AgentPrimitiveResults.missing("required item count is not satisfied");
        }
        int freeSlots = gateway.freeSlots(context.agent(), command.itemId());
        if (freeSlots < command.requiredFreeSlots()) {
            return AgentPrimitiveResults.missing("required inventory capacity is not available");
        }
        return AgentCapabilityStep.terminal(AgentCapabilityResult.success(
                "inventory requirements verified",
                new Snapshot(command.itemId(), count, freeSlots)));
    }
}
