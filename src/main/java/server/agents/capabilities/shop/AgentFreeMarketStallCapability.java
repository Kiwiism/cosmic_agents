package server.agents.capabilities.shop;

import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.capabilities.primitive.AgentNavigationCapability;
import server.agents.capabilities.runtime.AgentCapabilityCommand;
import server.agents.capabilities.runtime.AgentCapabilityContext;
import server.agents.capabilities.runtime.AgentCapabilityInvocation;
import server.agents.capabilities.runtime.AgentCapabilityReasonCode;
import server.agents.capabilities.runtime.AgentCapabilityResult;
import server.agents.capabilities.runtime.AgentCapabilityStep;
import server.agents.capabilities.runtime.AgentExecutableCapability;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.maps.reservation.CharacterSpaceOwner;
import server.maps.reservation.CharacterSpaceReservationRuntime;
import server.maps.reservation.FreeMarketCharacterSpaceCatalog;
import server.maps.reservation.FreeMarketStorePlacementService;

import java.awt.Point;
import java.util.List;

public final class AgentFreeMarketStallCapability
        implements AgentExecutableCapability<AgentFreeMarketStallCapability.Command> {
    private static final int ARRIVAL_RANGE_PX = 12;
    private static final long NAVIGATION_TIMEOUT_MS = 30_000L;

    public record Command(
            int mapId,
            String description,
            int permitItemId,
            List<AgentFreeMarketStallService.Listing> listings) implements AgentCapabilityCommand {
        public Command {
            if (!FreeMarketCharacterSpaceCatalog.isRoom(mapId)
                    || description == null || listings == null || listings.isEmpty()
                    || listings.size() > AgentFreeMarketStallService.MAX_LISTINGS) {
                throw new IllegalArgumentException("agent Free Market stall command is invalid");
            }
            listings = List.copyOf(listings);
        }

        @Override
        public String type() {
            return "free-market-stall";
        }
    }

    private final PrimitiveCapabilityGateway gateway;
    private final AgentFreeMarketStallService stallService;

    public AgentFreeMarketStallCapability() {
        this(AgentPrimitiveCapabilityGatewayRuntime.gateway(), new AgentFreeMarketStallService());
    }

    AgentFreeMarketStallCapability(
            PrimitiveCapabilityGateway gateway,
            AgentFreeMarketStallService stallService) {
        this.gateway = gateway;
        this.stallService = stallService;
    }

    @Override
    public String id() {
        return "free-market-stall";
    }

    @Override
    public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
        if (gateway.mapId(context.agent()) != command.mapId()) {
            return AgentCapabilityStep.terminal(new AgentCapabilityResult(
                    AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                    AgentCapabilityReasonCode.BLOCKED_BY_SCOPE,
                    "agent must reach the requested Free Market room before opening a stall"));
        }
        if (context.agent().getPlayerShop() != null
                && context.agent().getPlayerShop().isOwner(context.agent())
                && context.agent().getPlayerShop().isOpen()) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("agent stall is already open"));
        }

        var reservation = FreeMarketStorePlacementService.reservation(context.agent());
        if (reservation.isEmpty()) {
            reservation = FreeMarketStorePlacementService.reserveNearest(context.agent());
        }
        if (reservation.isEmpty()) {
            return AgentCapabilityStep.retry("no designated Free Market stall spot is available nearby");
        }
        Point destination = reservation.get().position();
        if (gateway.position(context.agent()).distanceSq(destination)
                > (long) ARRIVAL_RANGE_PX * ARRIVAL_RANGE_PX) {
            return AgentCapabilityStep.handoff(
                    new AgentCapabilityInvocation<>(
                            new AgentNavigationCapability(gateway),
                            new AgentNavigationCapability.Command(
                                    command.mapId(), destination, ARRIVAL_RANGE_PX, true),
                            NAVIGATION_TIMEOUT_MS,
                            1),
                    "walking to reserved Free Market stall spot #"
                            + reservation.get().centerSpace().spotNumber());
        }

        AgentFreeMarketStallService.Result result = stallService.open(
                context.agent(),
                command.description(),
                command.permitItemId(),
                command.listings(),
                reservation.get());
        if (!result.success()) {
            return AgentCapabilityStep.terminal(AgentCapabilityResult.failed(
                    AgentCapabilityReasonCode.MISSING_REQUIREMENT, result.message()));
        }
        return AgentCapabilityStep.terminal(AgentCapabilityResult.success(
                result.message() + " at spot #" + reservation.get().centerSpace().spotNumber()));
    }

    @Override
    public void onTerminal(AgentCapabilityContext context, Command command, AgentCapabilityResult result) {
        if (result.status() != AgentCapabilityStatus.SUCCESS) {
            CharacterSpaceReservationRuntime.release(
                    CharacterSpaceOwner.character(context.agent().getId()));
        }
    }
}
