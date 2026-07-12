package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

public final class AgentRecoveryCapability
        implements AgentExecutableCapability<AgentRecoveryCapability.Command> {
    public record Command(boolean requireAlive, int maximumStuckMs) implements AgentCapabilityCommand {
        public Command(boolean requireAlive) {
            this(requireAlive, Integer.MAX_VALUE);
        }

        public Command {
            if (maximumStuckMs < 0) {
                throw new IllegalArgumentException("maximum stuck duration cannot be negative");
            }
        }

        @Override
        public String type() {
            return "recovery";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentRecoveryCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentRecoveryCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "recovery";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        boolean alive = gateway.alive(context.agent());
        boolean withinStuckBound = gateway.stuckDurationMs(context.entry()) <= command.maximumStuckMs();
        if ((!command.requireAlive() || alive) && withinStuckBound) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("recovery state verified"));
        }
        return AgentCapabilityStep.running("delegating to reconstructed recovery", false);
    }
}
