package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.plans.AgentTask;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptTaskStartServiceTest {
    @Test
    void startsMoveToTask() {
        AgentRuntimeEntry entry = entry();
        Hooks hooks = new Hooks();
        Point target = new Point(10, 20);

        AgentScriptTaskStartService.start(entry, AgentTask.moveTo(target, true), hooks.startHooks());

        assertEquals(target, hooks.movePoint.get());
        assertEquals(true, hooks.movePrecise.get());
    }

    @Test
    void startsFollowOwnerTaskWithEntryOwner() {
        Character owner = character(100);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200), owner, null);
        Hooks hooks = new Hooks();

        AgentScriptTaskStartService.start(entry, AgentTask.followOwner(), hooks.startHooks());

        assertSame(owner, hooks.followTarget.get());
    }

    @Test
    void startsFollowTargetTasksWithResolvedTarget() {
        Character target = character(300);
        AgentRuntimeEntry entry = entry();
        Hooks hooks = new Hooks();
        hooks.resolvedTarget.set(target);

        AgentScriptTaskStartService.start(entry, AgentTask.follow(target), hooks.startHooks());

        assertSame(target, hooks.followTarget.get());
        assertEquals(target.getId(), hooks.resolvedTargetId.get());

        hooks.followTarget.set(null);
        hooks.resolvedTargetId.set(0);
        AgentScriptTaskStartService.start(entry, AgentTask.followUntilNear(target, 25), hooks.startHooks());

        assertSame(target, hooks.followTarget.get());
        assertEquals(target.getId(), hooks.resolvedTargetId.get());
    }

    @Test
    void startsModeTasks() {
        AgentRuntimeEntry entry = entry();
        Hooks hooks = new Hooks();

        AgentScriptTaskStartService.start(entry, AgentTask.grind(), hooks.startHooks());
        AgentScriptTaskStartService.start(entry, AgentTask.stop(), hooks.startHooks());

        assertEquals(1, hooks.grinds.get());
        assertEquals(1, hooks.stops.get());
    }

    @Test
    void startsDropItemTask() {
        AgentRuntimeEntry entry = entry();
        Hooks hooks = new Hooks();

        AgentScriptTaskStartService.start(
                entry, AgentTask.dropItem(InventoryType.USE, 2000000, (short) 3), hooks.startHooks());

        assertEquals(InventoryType.USE, hooks.dropType.get());
        assertEquals(2000000, hooks.dropItemId.get());
        assertEquals((short) 3, hooks.dropQuantity.get());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(character(200), character(100), null);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }

    private static final class Hooks {
        private final AtomicReference<Point> movePoint = new AtomicReference<>();
        private final AtomicReference<Boolean> movePrecise = new AtomicReference<>();
        private final AtomicReference<Character> followTarget = new AtomicReference<>();
        private final AtomicReference<Character> resolvedTarget = new AtomicReference<>();
        private final AtomicInteger resolvedTargetId = new AtomicInteger();
        private final AtomicInteger grinds = new AtomicInteger();
        private final AtomicInteger stops = new AtomicInteger();
        private final AtomicReference<InventoryType> dropType = new AtomicReference<>();
        private final AtomicInteger dropItemId = new AtomicInteger();
        private final AtomicReference<Short> dropQuantity = new AtomicReference<>();

        private AgentScriptTaskStartService.StartHooks startHooks() {
            return new AgentScriptTaskStartService.StartHooks(
                    (point, precise) -> {
                        movePoint.set(point);
                        movePrecise.set(precise);
                    },
                    followTarget::set,
                    targetId -> {
                        resolvedTargetId.set(targetId);
                        return resolvedTarget.get();
                    },
                    grinds::incrementAndGet,
                    stops::incrementAndGet,
                    (type, itemId, quantity) -> {
                        dropType.set(type);
                        dropItemId.set(itemId);
                        dropQuantity.set(quantity);
                        return true;
                    });
        }
    }
}
