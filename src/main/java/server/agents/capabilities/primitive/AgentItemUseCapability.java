package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AgentItemUseCapability
        implements AgentExecutableCapability<AgentItemUseCapability.Command> {
    public record Command(int itemId,
                          int initialCount,
                          int expectedMaximumCount,
                          Integer requiredQuestId,
                          Integer requiredQuestStatus)
            implements AgentCapabilityCommand {
        public Command(int itemId, int initialCount, int expectedMaximumCount) {
            this(itemId, initialCount, expectedMaximumCount, null, null);
        }

        public Command {
            if (itemId <= 0 || initialCount <= 0 || expectedMaximumCount < 0
                    || expectedMaximumCount >= initialCount
                    || (requiredQuestId == null) != (requiredQuestStatus == null)
                    || requiredQuestId != null && (requiredQuestId <= 0
                    || requiredQuestStatus < 0 || requiredQuestStatus > 2)) {
                throw new IllegalArgumentException("item use count contract is invalid");
            }
        }

        @Override
        public String type() {
            return "item-use";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentItemUseCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentItemUseCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "item-use";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (command.requiredQuestId() != null
                && gateway.questStatus(context.agent(), command.requiredQuestId())
                != command.requiredQuestStatus()) {
            return AgentPrimitiveResults.missing("required quest state for item use is not satisfied");
        }
        int count = gateway.itemCount(context.agent(), command.itemId());
        if (count <= command.expectedMaximumCount()) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("item use verified"));
        }
        if (count > command.initialCount()) {
            return AgentPrimitiveResults.mismatch("item count increased during item-use capability");
        }
        if (!gateway.useItem(context.agent(), command.itemId())) {
            return AgentCapabilityStep.retry("item use was not accepted");
        }
        return AgentCapabilityStep.running("item used; verifying live inventory state");
    }
}
