package server.agents.runtime;

import server.agents.capabilities.shop.AgentShopVisitTickService;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.shop.AgentShopStateRuntime;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentShopVisitTickServiceTest {
    @Test
    void fallsThroughWhenShopVisitIsNotPending() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        AtomicInteger shopTicks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();

        AgentShopVisitTickService.Result result = AgentShopVisitTickService.tickShopVisitIfPending(
                entry,
                agent,
                true,
                hooks(shopTicks, movementSteps, new AtomicReference<>(), false));

        assertFalse(result.consumedTick());
        assertNull(result.targetPos());
        assertEquals(0, shopTicks.get());
        assertEquals(0, movementSteps.get());
    }

    @Test
    void pendingVisitWithApproachDelayConsumesWithoutMovementWhenShopTickDoesNotConsume() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point target = new Point(100, 50);
        AgentShopStateRuntime.startShopVisit(entry, new Point(120, 50), target, 250, 1_000L);
        AtomicInteger shopTicks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();
        AtomicReference<Point> movedTo = new AtomicReference<>();

        AgentShopVisitTickService.Result result = AgentShopVisitTickService.tickShopVisitIfPending(
                entry,
                agent,
                false,
                hooks(shopTicks, movementSteps, movedTo, false));

        assertTrue(result.consumedTick());
        assertEquals(target, result.targetPos());
        assertEquals(1, shopTicks.get());
        assertEquals(0, movementSteps.get());
        assertNull(movedTo.get());
    }

    @Test
    void pendingVisitStepsTowardActiveTargetWhenDelayIsClear() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Character agent = mock(Character.class);
        Point target = new Point(100, 50);
        AgentShopStateRuntime.startShopVisit(entry, new Point(120, 50), target, 0, 1_000L);
        AtomicInteger shopTicks = new AtomicInteger();
        AtomicInteger movementSteps = new AtomicInteger();
        AtomicReference<Point> movedTo = new AtomicReference<>();

        AgentShopVisitTickService.Result result = AgentShopVisitTickService.tickShopVisitIfPending(
                entry,
                agent,
                true,
                hooks(shopTicks, movementSteps, movedTo, false));

        assertTrue(result.consumedTick());
        assertEquals(target, result.targetPos());
        assertEquals(1, shopTicks.get());
        assertEquals(1, movementSteps.get());
        assertEquals(target, movedTo.get());
    }

    private static AgentShopVisitTickService.Hooks hooks(AtomicInteger shopTicks,
                                                         AtomicInteger movementSteps,
                                                         AtomicReference<Point> movedTo,
                                                         boolean shopTickConsumes) {
        return new AgentShopVisitTickService.Hooks(
                (entry, agent) -> {
                    shopTicks.incrementAndGet();
                    return shopTickConsumes;
                },
                (entry, targetPosition, runAiTick) -> {
                    movementSteps.incrementAndGet();
                    movedTo.set(targetPosition);
                });
    }
}
