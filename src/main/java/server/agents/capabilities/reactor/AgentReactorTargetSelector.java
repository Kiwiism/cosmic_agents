package server.agents.capabilities.reactor;

import server.maps.Reactor;

import java.awt.Point;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class AgentReactorTargetSelector {
    public Optional<AgentReactorTarget> select(List<Reactor> reactors, AgentReactorInteractionRequest request) {
        return matchingTargets(reactors, request).stream().findFirst();
    }

    public Optional<AgentReactorTarget> selectReserved(List<Reactor> reactors,
                                                       AgentReactorInteractionRequest request,
                                                       int agentId,
                                                       Object mapScope) {
        return AgentReactorTargetReservationRuntime.reserveNearest(
                agentId, mapScope, matchingTargets(reactors, request));
    }

    private List<AgentReactorTarget> matchingTargets(
            List<Reactor> reactors, AgentReactorInteractionRequest request) {
        if (reactors == null || request == null) {
            return List.of();
        }

        return reactors.stream()
                .filter(reactor -> matches(reactor, request))
                .sorted(Comparator.comparingLong(
                        reactor -> distanceSq(request.agentPosition(), reactor.getPosition())))
                .map(this::toTarget)
                .toList();
    }

    private boolean matches(Reactor reactor, AgentReactorInteractionRequest request) {
        if (reactor == null || !reactor.isAlive() || !reactor.isActive()) {
            return false;
        }
        if (request.hasObjectIdFilter() && reactor.getObjectId() != request.objectId()) {
            return false;
        }
        if (request.hasReactorIdFilter() && reactor.getId() != request.reactorId()) {
            return false;
        }
        if (request.hasNameFilter() && !request.reactorName().equalsIgnoreCase(reactor.getName())) {
            return false;
        }
        if (request.hasRangeFilter()) {
            long maxRangeSq = (long) request.maxRangePx() * request.maxRangePx();
            return distanceSq(request.agentPosition(), reactor.getPosition()) <= maxRangeSq;
        }
        return true;
    }

    private AgentReactorTarget toTarget(Reactor reactor) {
        Point position = reactor.getPosition();
        return new AgentReactorTarget(
                reactor.getObjectId(),
                reactor.getId(),
                reactor.getName(),
                position,
                position,
                reactor.getState(),
                reactor.getReactorType());
    }

    private static long distanceSq(Point source, Point target) {
        if (source == null || target == null) {
            return 0L;
        }
        long dx = source.x - target.x;
        long dy = source.y - target.y;
        return dx * dx + dy * dy;
    }
}
