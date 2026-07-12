package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AgentQuestStateCapability
        implements AgentExecutableCapability<AgentQuestStateCapability.Command> {
    public record Command(int questId, int expectedStatus) implements AgentCapabilityCommand {
        public Command {
            if (questId <= 0 || expectedStatus < 0 || expectedStatus > 2) {
                throw new IllegalArgumentException("quest id and status 0..2 are required");
            }
        }

        @Override
        public String type() {
            return "quest-state";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentQuestStateCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentQuestStateCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "quest-state";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        return gateway.questStatus(context.agent(), command.questId()) == command.expectedStatus()
                ? AgentCapabilityStep.terminal(AgentCapabilityResult.success("quest state verified"))
                : AgentPrimitiveResults.mismatch("quest state does not match expected status");
    }
}
