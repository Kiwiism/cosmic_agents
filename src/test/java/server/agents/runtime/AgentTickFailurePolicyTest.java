package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.agents.integration.AgentBotGrindTargetStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;
import server.agents.integration.AgentBotPendingActionStateRuntime;
import server.agents.integration.AgentBotTickFailureStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickFailurePolicyTest {
    @Test
    void handlesMissingEntryThroughHookOnly() {
        RuntimeException failure = new RuntimeException("boom");
        AtomicInteger missing = new AtomicInteger();

        AgentTickFailurePolicy.handleFailure(
                null,
                100,
                200,
                failure,
                1_000L,
                hooks(missing, new AtomicInteger(), new AtomicInteger(), new AtomicInteger(),
                        new AtomicInteger(), new AtomicInteger()));

        assertEquals(1, missing.get());
    }

    @Test
    void clearsVolatileActionsAndWarnsOnFirstFailure() {
        Character agent = character("Alpha", 100000000);
        Character leader = character("Leader", 100000000);
        BotEntry entry = new BotEntry(agent, leader, null);
        AgentBotPendingActionStateRuntime.setPendingAction(entry, "drop");
        AgentBotPendingActionStateRuntime.setPendingDropCategory(entry, "equip");
        AgentBotGrindTargetStateRuntime.setTarget(entry, mock(server.life.Monster.class));
        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, mock(server.maps.MapItem.class));
        AgentBotPatrolStateRuntime.setPatrolWanderTarget(entry, new Point(1, 2));
        AtomicInteger clearMovement = new AtomicInteger();
        AtomicInteger warnings = new AtomicInteger();

        AgentTickFailurePolicy.handleFailure(
                entry,
                100,
                200,
                new RuntimeException("boom"),
                1_000L,
                hooks(new AtomicInteger(), clearMovement, new AtomicInteger(), new AtomicInteger(),
                        new AtomicInteger(), warnings));

        assertFalse(AgentBotPendingActionStateRuntime.hasPendingAction(entry));
        assertFalse(AgentBotPendingActionStateRuntime.pendingDropCategory(entry) != null);
        assertFalse(AgentBotGrindTargetStateRuntime.target(entry) != null);
        assertFalse(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertFalse(AgentBotPatrolStateRuntime.patrolWanderTarget(entry) != null);
        assertEquals(1, clearMovement.get());
        assertEquals(1, warnings.get());
    }

    @Test
    void forcesIdleOnSecondFailureAndDisablesOnThirdFailure() {
        BotEntry entry = new BotEntry(character("Alpha", 1), character("Leader", 1), null);
        AtomicInteger forceIdle = new AtomicInteger();
        AtomicInteger disable = new AtomicInteger();

        AgentTickFailurePolicy.handleFailure(
                entry,
                100,
                200,
                new RuntimeException("first"),
                1_000L,
                hooks(new AtomicInteger(), new AtomicInteger(), disable, forceIdle,
                        new AtomicInteger(), new AtomicInteger()));
        AgentTickFailurePolicy.handleFailure(
                entry,
                100,
                200,
                new RuntimeException("second"),
                2_000L,
                hooks(new AtomicInteger(), new AtomicInteger(), disable, forceIdle,
                        new AtomicInteger(), new AtomicInteger()));
        AgentTickFailurePolicy.handleFailure(
                entry,
                100,
                200,
                new RuntimeException("third"),
                3_000L,
                hooks(new AtomicInteger(), new AtomicInteger(), disable, forceIdle,
                        new AtomicInteger(), new AtomicInteger()));

        assertEquals(1, forceIdle.get());
        assertEquals(1, disable.get());
    }

    @Test
    void escalatesFromWarningToIdleToDisableWithinWindow() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);

        AgentTickFailurePolicy.Decision first = AgentTickFailurePolicy.recordFailure(entry, 1_000L);
        AgentTickFailurePolicy.Decision second = AgentTickFailurePolicy.recordFailure(entry, 2_000L);
        AgentTickFailurePolicy.Decision third = AgentTickFailurePolicy.recordFailure(entry, 3_000L);

        assertEquals(1, first.failureCount());
        assertFalse(first.forceIdle());
        assertFalse(first.disableAgent());
        assertEquals(2, second.failureCount());
        assertTrue(second.forceIdle());
        assertFalse(second.disableAgent());
        assertEquals(3, third.failureCount());
        assertFalse(third.forceIdle());
        assertTrue(third.disableAgent());
    }

    @Test
    void resetsFailures() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AgentTickFailurePolicy.recordFailure(entry, 1_000L);

        AgentTickFailurePolicy.resetFailures(entry);

        assertFalse(AgentBotTickFailureStateRuntime.hasFailures(entry));
    }

    private static Character character(String name, int mapId) {
        Character character = mock(Character.class);
        when(character.getName()).thenReturn(name);
        when(character.getMapId()).thenReturn(mapId);
        return character;
    }

    private static AgentTickFailurePolicy.FailureHooks hooks(AtomicInteger missing,
                                                             AtomicInteger clearMovement,
                                                             AtomicInteger disable,
                                                             AtomicInteger forceIdle,
                                                             AtomicInteger disabledLogs,
                                                             AtomicInteger warningLogs) {
        return new AgentTickFailurePolicy.FailureHooks(
                (leaderCharId, agentCharId, failure) -> missing.incrementAndGet(),
                (entry, failure) -> clearMovement.incrementAndGet(),
                entry -> disable.incrementAndGet(),
                entry -> forceIdle.incrementAndGet(),
                (context, failure) -> disabledLogs.incrementAndGet(),
                (context, failure) -> warningLogs.incrementAndGet());
    }
}
