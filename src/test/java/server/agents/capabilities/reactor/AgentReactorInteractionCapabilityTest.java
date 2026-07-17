package server.agents.capabilities.reactor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import server.agents.capabilities.AgentCapabilityStatus;
import server.maps.Reactor;

import java.awt.Point;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentReactorInteractionCapabilityTest {
    @AfterEach
    void clearTargetReservations() {
        AgentReactorTargetReservationRuntime.clear();
    }

    @Test
    void defaultScopeAllowsOnlyPioReactorQuestInAmherst() {
        AgentReactorInteractionCapability capability = new AgentReactorInteractionCapability();
        Reactor reactor = reactor(10, 1002000, "box", new Point(5, 0), true, true);

        AgentReactorInteractionResult allowed = capability.plan(List.of(reactor), request(1000000, 1008));
        assertEquals(AgentCapabilityStatus.NOT_READY, allowed.status());
        assertNotNull(allowed.target());

        AgentReactorInteractionResult wrongQuest = capability.plan(List.of(reactor), request(1000000, 1037));
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, wrongQuest.status());

        AgentReactorInteractionResult wrongMap = capability.plan(List.of(reactor), request(2000000, 1008));
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP, wrongMap.status());
    }

    @Test
    void selectorFindsNearestMatchingActiveReactor() {
        AgentReactorTargetSelector selector = new AgentReactorTargetSelector();
        Reactor farther = reactor(10, 1002000, "box", new Point(50, 0), true, true);
        Reactor nearest = reactor(11, 1002000, "box", new Point(10, 0), true, true);
        Reactor inactive = reactor(12, 1002000, "box", new Point(1, 0), true, false);
        AgentReactorInteractionRequest request = new AgentReactorInteractionRequest(
                1000000, 1008, AgentReactorInteractionMode.HIT, 1002000, "box", null, new Point(0, 0), 100);

        AgentReactorTarget selected = selector.select(List.of(farther, nearest, inactive), request).orElseThrow();

        assertEquals(11, selected.objectId());
        assertEquals(1002000, selected.reactorId());
        assertEquals("box", selected.reactorName());
        assertEquals(new Point(10, 0), selected.targetPosition());
    }

    @Test
    void reservedSelectionSpreadsAgentsAcrossNearestAvailableReactors() {
        AgentReactorTargetSelector selector = new AgentReactorTargetSelector();
        Reactor nearest = reactor(10, 1002000, "box", new Point(10, 0), true, true);
        Reactor farther = reactor(11, 1002000, "box", new Point(50, 0), true, true);
        AgentReactorInteractionRequest request = new AgentReactorInteractionRequest(
                1000000, 1008, AgentReactorInteractionMode.HIT,
                1002000, "box", null, new Point(0, 0), 100);
        Object mapScope = new Object();

        assertEquals(10, selector.selectReserved(
                List.of(farther, nearest), request, 1, mapScope).orElseThrow().objectId());
        assertEquals(11, selector.selectReserved(
                List.of(farther, nearest), request, 2, mapScope).orElseThrow().objectId());

        AgentReactorTargetReservationRuntime.release(1);
        assertEquals(10, selector.selectReserved(
                List.of(farther, nearest), request, 3, mapScope).orElseThrow().objectId());
    }

    @Test
    void selectorHonorsObjectIdNameAndRangeFilters() {
        AgentReactorTargetSelector selector = new AgentReactorTargetSelector();
        Reactor matching = reactor(10, 1002000, "questBox", new Point(20, 0), true, true);
        Reactor wrongName = reactor(11, 1002000, "otherBox", new Point(5, 0), true, true);
        AgentReactorInteractionRequest request = new AgentReactorInteractionRequest(
                1000000, 1008, AgentReactorInteractionMode.HIT, 1002000, "questBox", 10,
                new Point(0, 0), 30);

        assertEquals(10, selector.select(List.of(wrongName, matching), request).orElseThrow().objectId());

        AgentReactorInteractionRequest outOfRange = new AgentReactorInteractionRequest(
                1000000, 1008, AgentReactorInteractionMode.HIT, 1002000, "questBox", 10,
                new Point(0, 0), 10);
        assertTrue(selector.select(List.of(matching), outOfRange).isEmpty());
    }

    @Test
    void executionStaysNotReadyWithoutLiveAdapter() {
        AgentReactorInteractionCapability capability = new AgentReactorInteractionCapability();
        Reactor reactor = reactor(10, 1002000, "box", new Point(5, 0), true, true);

        AgentReactorInteractionResult result = capability.execute(List.of(reactor), request(1000000, 1008));

        assertEquals(AgentCapabilityStatus.NOT_READY, result.status());
        assertNotNull(result.target());
    }

    @Test
    void executionDelegatesToAdapterAfterSelection() {
        AtomicReference<AgentReactorInteractionRequest> executedRequest = new AtomicReference<>();
        AtomicReference<AgentReactorTarget> executedTarget = new AtomicReference<>();
        AgentReactorExecutionPort port = (request, target) -> {
            executedRequest.set(request);
            executedTarget.set(target);
            return AgentReactorInteractionResult.success("hit queued", target);
        };
        AgentReactorInteractionCapability capability = new AgentReactorInteractionCapability(
                new AgentReactorScopePolicy(Set.of(1000000), Set.of(1008)),
                new AgentReactorTargetSelector(),
                port);
        AgentReactorInteractionRequest request = request(1000000, 1008);
        Reactor reactor = reactor(10, 1002000, "box", new Point(5, 0), true, true);

        AgentReactorInteractionResult result = capability.execute(List.of(reactor), request);

        assertTrue(result.success());
        assertEquals(AgentCapabilityStatus.SUCCESS, result.status());
        assertSame(request, executedRequest.get());
        assertEquals(10, executedTarget.get().objectId());
    }

    @Test
    void planReturnsMissingRequirementWhenNoReactorMatches() {
        AgentReactorInteractionCapability capability = new AgentReactorInteractionCapability();
        Reactor reactor = reactor(10, 1002000, "box", new Point(5, 0), false, true);

        AgentReactorInteractionResult result = capability.plan(List.of(reactor), request(1000000, 1008));

        assertEquals(AgentCapabilityStatus.MISSING_REQUIREMENT, result.status());
    }

    private static AgentReactorInteractionRequest request(int mapId, int questId) {
        return new AgentReactorInteractionRequest(mapId, questId, AgentReactorInteractionMode.HIT,
                null, null, null, new Point(0, 0), 100);
    }

    private static Reactor reactor(int objectId, int reactorId, String name, Point position, boolean alive,
            boolean active) {
        Reactor reactor = mock(Reactor.class);
        when(reactor.getObjectId()).thenReturn(objectId);
        when(reactor.getId()).thenReturn(reactorId);
        when(reactor.getName()).thenReturn(name);
        when(reactor.getPosition()).thenReturn(position);
        when(reactor.isAlive()).thenReturn(alive);
        when(reactor.isActive()).thenReturn(active);
        when(reactor.getState()).thenReturn((byte) 0);
        when(reactor.getReactorType()).thenReturn(0);
        return reactor;
    }
}
