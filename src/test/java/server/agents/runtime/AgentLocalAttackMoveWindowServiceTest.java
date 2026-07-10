package server.agents.runtime;

import server.agents.capabilities.combat.AgentLocalAttackMoveWindowService;
import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.runtime.AgentModeStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AgentLocalAttackMoveWindowServiceTest {
    private static final int FOLLOW_DISTANCE = 60;
    private static final int STOP_DISTANCE = 30;
    private static final int FOLLOW_Y_CAP = 80;

    @Test
    void nullPositionsClearMoveWindow() {
        AgentRuntimeEntry entry = entry();
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry, 500);

        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry, null, new Point(100, 100), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(0, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void farReferenceSetsLongMoveWindow() {
        AgentRuntimeEntry entry = entry();

        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry, new Point(0, 100), new Point(250, 100), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(1000, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void mediumReferenceSetsShortMoveWindow() {
        AgentRuntimeEntry entry = entry();

        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry, new Point(0, 100), new Point(100, 100), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(200, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void nearReferenceSettlesImmediately() {
        AgentRuntimeEntry entry = entry();

        AgentLocalAttackMoveWindowService.setLocalAttackMoveWindow(
                entry, new Point(0, 100), new Point(30, 100), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(0, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void clearActionMoveWindowIfSettledUsesFollowStopBandAndVerticalCap() {
        AgentRuntimeEntry entry = entry();
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry, 200);

        AgentLocalAttackMoveWindowService.clearActionMoveWindowIfSettled(
                entry, new Point(0, 100), new Point(60, 180), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(0, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void clearActionMoveWindowIfSettledKeepsWindowWhenOutsideBand() {
        AgentRuntimeEntry entry = entry();
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry, 200);

        AgentLocalAttackMoveWindowService.clearActionMoveWindowIfSettled(
                entry, new Point(0, 100), new Point(61, 100), FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(200, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    @Test
    void followSnapshotClearRequiresFollowMode() {
        AgentRuntimeEntry entry = entry();
        AgentTargetSnapshot snapshot = snapshot(new Point(50, 100));
        AgentCombatCooldownStateRuntime.setMoveWindowMs(entry, 200);

        AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
                entry, new Point(0, 100), snapshot, FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(200, AgentCombatCooldownStateRuntime.moveWindowMs(entry));

        AgentModeStateRuntime.setFollowing(entry, true);
        AgentLocalAttackMoveWindowService.clearFollowActionMoveWindowIfSettled(
                entry, new Point(0, 100), snapshot, FOLLOW_DISTANCE, STOP_DISTANCE, FOLLOW_Y_CAP);

        assertEquals(0, AgentCombatCooldownStateRuntime.moveWindowMs(entry));
    }

    private static AgentTargetSnapshot snapshot(Point followTarget) {
        return new AgentTargetSnapshot(
                null,
                null,
                null,
                null,
                null,
                followTarget,
                null,
                null,
                null,
                followTarget,
                "follow-target");
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }
}
