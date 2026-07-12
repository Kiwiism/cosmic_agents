package server.agents.capabilities.objective;

import server.agents.capabilities.primitive.AgentInventoryInspectionCapability;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class InventoryUseObjectiveCapability
        implements AgentExecutableCapability<InventoryUseObjectiveCapability.Command> {
    public record Command(String objectiveId, int questId, int itemId) implements AgentCapabilityCommand {
        public Command {
            if (objectiveId == null || objectiveId.isBlank() || questId <= 0 || itemId <= 0) {
                throw new IllegalArgumentException("objective, quest, and item ids are required");
            }
        }

        @Override
        public String type() {
            return "inventory-use-objective";
        }
    }

    private final AmherstObjectiveCapabilitySupport support;

    public InventoryUseObjectiveCapability() {
        support = new AmherstObjectiveCapabilitySupport();
    }

    public InventoryUseObjectiveCapability(PrimitiveCapabilityGateway gateway) {
        support = new AmherstObjectiveCapabilitySupport(gateway);
    }

    @Override
    public String id() {
        return "inventory-use-objective";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityStep failure = support.propagateChildFailure(context);
        if (failure != null) {
            return failure;
        }
        int questStatus = support.gateway().questStatus(context.agent(), command.questId());
        int liveCount = support.gateway().itemCount(context.agent(), command.itemId());
        if (questStatus == 2 || questStatus == 1 && liveCount == 0) {
            return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                    command.objectiveId(), "inventory-use objective verified"));
        }
        if (questStatus != 1) {
            return AmherstObjectiveCapabilitySupport.missing("item-use quest is not started");
        }
        int phase = context.memory().intValue("phase", 0);
        if (phase == 0) {
            context.memory().putInt("phase", 1);
            return AgentCapabilityStep.handoff(support.inspect(command.itemId(), 1),
                    "inventory-use objective requests inventory inspection");
        }
        if (phase == 1) {
            if (context.childResult() != null
                    && context.childResult().output() instanceof AgentInventoryInspectionCapability.Snapshot snapshot) {
                context.memory().putInt("initialCount", snapshot.count());
            }
            int initialCount = context.memory().intValue("initialCount", liveCount);
            context.memory().putInt("phase", 2);
            return AgentCapabilityStep.handoff(support.useItem(command.itemId(), initialCount, command.questId()),
                    "inventory-use objective requests normal item use");
        }
        if (phase == 2) {
            context.memory().putInt("phase", 3);
            return AgentCapabilityStep.handoff(support.inspect(command.itemId(), 0),
                    "inventory-use objective requests final inventory verification");
        }
        int initialCount = context.memory().intValue("initialCount", 1);
        if (support.gateway().itemCount(context.agent(), command.itemId()) >= initialCount) {
            return AmherstObjectiveCapabilitySupport.missing("item use was not visible in live inventory");
        }
        return AgentCapabilityStep.terminal(AmherstObjectiveCapabilitySupport.success(
                command.objectiveId(), "inventory-use objective verified"));
    }
}
