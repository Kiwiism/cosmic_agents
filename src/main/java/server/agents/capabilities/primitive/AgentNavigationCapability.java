package server.agents.capabilities.primitive;

import server.agents.capabilities.movement.AgentClimbStateRuntime;
import server.agents.capabilities.movement.fidget.AgentProfileNavigationFidgetPolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.maps.Rope;

import java.awt.Point;

public final class AgentNavigationCapability
        implements AgentExecutableCapability<AgentNavigationCapability.Command> {
    public record Command(int mapId,
                          Point destination,
                          int tolerancePx,
                          boolean precise,
                          boolean allowClimbingArrival)
            implements AgentCapabilityCommand {
        public Command(int mapId, Point destination, int tolerancePx, boolean precise) {
            this(mapId, destination, tolerancePx, precise, false);
        }

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
            if (!gateway.grounded(context.agent())
                    && !matchesClimbingArrival(context, command)) {
                return AgentCapabilityStep.running("waiting to land at navigation destination", false);
            }
            gateway.stop(context.entry());
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("navigation destination reached"));
        }
        if (AgentProfileNavigationFidgetPolicy.tick(context, command.destination(), gateway)) {
            return AgentCapabilityStep.running("profile navigation fidget", true);
        }
        gateway.navigate(context.entry(), command.destination(), command.precise());
        return AgentCapabilityStep.running("delegating to reconstructed navigation", false);
    }

    private boolean matchesClimbingArrival(AgentCapabilityContext context, Command command) {
        Rope rope = AgentClimbStateRuntime.climbRope(context.entry());
        if (!command.allowClimbingArrival()) {
            return false;
        }
        if (matches(rope, command.destination())) {
            return true;
        }
        // A movement stop may clear transient climb state between ticks while
        // the character remains visibly attached to the ladder. The command
        // explicitly permits this arrival, so fall back to immutable map
        // geometry before deciding that the agent must land.
        return context.agent().getMap() != null
                && context.agent().getMap().getRopes().stream()
                .anyMatch(candidate -> matches(candidate, command.destination()));
    }

    private static boolean matches(Rope rope, Point destination) {
        return rope != null && destination != null
                && rope.x() == destination.x
                && destination.y >= rope.topY()
                && destination.y <= rope.bottomY();
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        AgentProfileNavigationFidgetPolicy.clear(context);
        gateway.stop(context.entry());
    }
}
