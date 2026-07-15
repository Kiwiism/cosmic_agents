package server.agents.capabilities.primitive;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.awt.Point;

public final class AgentPortalTravelCapability
        implements AgentExecutableCapability<AgentPortalTravelCapability.Command> {
    private static final int PORTAL_APPROACH_RANGE_PX = 60;
    private static final long NAVIGATION_TIMEOUT_MS = 60_000L;
    private static final int NAVIGATION_RETRIES = 2;
    private static final long DESTINATION_SETTLE_MS = 1_000L;

    public record Command(int sourceMapId, int portalId, int destinationMapId, boolean requireAmherstScope)
            implements AgentCapabilityCommand {
        public Command {
            if (sourceMapId <= 0 || portalId < 0 || destinationMapId <= 0) {
                throw new IllegalArgumentException("source map, portal, and destination map are required");
            }
        }

        @Override
        public String type() {
            return "portal-travel";
        }
    }

    private final PrimitiveCapabilityGateway gateway;
    private final AmherstScopePolicy scopePolicy;

    public AgentPortalTravelCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AmherstScopePolicy());
    }

    public AgentPortalTravelCapability(PrimitiveCapabilityGateway gateway, AmherstScopePolicy scopePolicy) {
        this.gateway = gateway;
        this.scopePolicy = scopePolicy;
    }

    @Override
    public String id() {
        return "portal-travel";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        AgentCapabilityResult child = context.childResult();
        if (child != null && child.status() != AgentCapabilityStatus.SUCCESS) {
            return AgentCapabilityStep.terminal(child);
        }
        int currentMap = gateway.mapId(context.agent());
        if (currentMap == command.destinationMapId()) {
            if (!gateway.grounded(context.agent())) {
                return AgentCapabilityStep.running("portal destination reached; waiting to land");
            }
            long settleStartedAtMs = context.memory().longValue("destinationSettleStartedAtMs", -1L);
            if (settleStartedAtMs < 0L) {
                gateway.stop(context.entry());
                context.memory().putLong("destinationSettleStartedAtMs", context.nowMs());
                return AgentCapabilityStep.running("portal destination reached; settling at entry");
            }
            if (context.nowMs() - settleStartedAtMs < DESTINATION_SETTLE_MS) {
                return AgentCapabilityStep.running("settling at portal destination");
            }
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("portal destination reached"));
        }
        if (currentMap != command.sourceMapId()) {
            return AgentPrimitiveResults.mismatch("agent is not on the portal source map");
        }
        if (command.requireAmherstScope()) {
            var scope = scopePolicy.checkMap(command.destinationMapId());
            if (!scope.allowed()) {
                return AgentPrimitiveResults.blocked(scope.status(), scope.reason());
            }
        }
        if (!gateway.portalPresent(context.agent(), command.portalId())) {
            return AgentPrimitiveResults.missing("portal is not present on the source map");
        }
        Point portalPosition = gateway.portalPosition(context.agent(), command.portalId());
        if (portalPosition == null) {
            return AgentPrimitiveResults.missing("portal position is unavailable on the source map");
        }
        if (gateway.position(context.agent()).distanceSq(portalPosition)
                > (long) PORTAL_APPROACH_RANGE_PX * PORTAL_APPROACH_RANGE_PX) {
            return AgentCapabilityStep.handoff(
                    new server.agents.capabilities.runtime.AgentCapabilityInvocation<>(
                            new AgentNavigationCapability(gateway),
                            new AgentNavigationCapability.Command(
                                    command.sourceMapId(), portalPosition, PORTAL_APPROACH_RANGE_PX, true),
                            NAVIGATION_TIMEOUT_MS,
                            NAVIGATION_RETRIES),
                    "portal travel requests navigation to portal");
        }
        if (!gateway.enterPortal(context.agent(), command.portalId())) {
            return AgentCapabilityStep.retry("portal entry was not accepted");
        }
        return AgentCapabilityStep.running("portal entered; verifying destination");
    }
}
