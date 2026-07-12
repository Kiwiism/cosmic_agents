package server.agents.capabilities.primitive;

import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;

public final class AgentNavigationCapability
        implements AgentExecutableCapability<AgentNavigationCapability.Command> {
    public record Command(int mapId, Point destination, int tolerancePx, boolean precise)
            implements AgentCapabilityCommand {
        public Command {
            if (destination == null || tolerancePx < 0) {
                throw new IllegalArgumentException("destination and non-negative tolerance are required");
            }
            destination = new Point(destination);
        }

        @Override
        public String type() {
            return "navigation";
        }
    }

    private final PrimitiveCapabilityGateway gateway;

    public AgentNavigationCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    public AgentNavigationCapability(PrimitiveCapabilityGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public String id() {
        return "navigation";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (gateway.mapId(context.agent()) != command.mapId()) {
            return AgentPrimitiveResults.blocked(
                    server.agents.capabilities.AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                    "navigation destination is not on the current map");
        }
        if (gateway.position(context.agent()).distanceSq(command.destination())
                <= (long) command.tolerancePx() * command.tolerancePx()) {
            if (!gateway.grounded(context.agent())) {
                return AgentCapabilityStep.running("waiting to land at navigation destination", false);
            }
            gateway.stop(context.entry());
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("navigation destination reached"));
        }
        gateway.navigate(context.entry(), command.destination(), command.precise());
        return AgentCapabilityStep.running("delegating to reconstructed navigation", false);
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        gateway.stop(context.entry());
    }
}
