package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentGrindNavigationTailServiceTest {
    @Test
    void crossRegionRetreatPositionWinsWithoutCallingNavigationSelector() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger navigationCalls = new AtomicInteger();
        Point retreat = new Point(500, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                AgentAttackRoute.RANGED,
                retreat,
                new Point(300, 100),
                true,
                false,
                hooks(navigationCalls, null, false));

        assertEquals(retreat, result);
        assertEquals(0, navigationCalls.get());
    }

    @Test
    void aoeRepositionSuppressesAnAdditionalSafeSpotSearch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger navigationCalls = new AtomicInteger();
        Point aoe = new Point(300, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                AgentAttackRoute.RANGED,
                null,
                aoe,
                false,
                false,
                hooks(navigationCalls, null, false));

        assertEquals(new Point(302, 100), result);
        assertEquals(1, navigationCalls.get());
    }

    @Test
    void convenientLootCanOverrideNormalNavigationWhenNotRetreating() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        Point loot = new Point(150, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                AgentAttackRoute.RANGED,
                null,
                null,
                false,
                false,
                hooks(new AtomicInteger(), loot, false));

        assertEquals(loot, result);
    }

    @Test
    void clearsDegenerateLatchAfterLeavingRetreatBand() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);

        AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                AgentAttackRoute.RANGED,
                null,
                null,
                false,
                false,
                hooks(new AtomicInteger(), null, false));

        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));
    }

    @Test
    void clearsDegenerateLatchWhenNoRetreatPositionIsReachable() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
        AgentDegenerateAttackStateRuntime.markDegenAttackDone(entry);
        Point mobPosition = new Point(200, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(165, 100),
                mobPosition,
                WeaponType.GUN,
                AgentAttackRoute.RANGED,
                null,
                null,
                true,
                false,
                new AgentGrindNavigationTailService.Hooks(
                        (ignoredEntry, ignoredAgent, target, ignoredWeapon, ignoredRoute,
                         ignoredRetreatChecked) -> target,
                        (ignoredWeapon, ignoredAgent, ignoredTarget) -> true,
                        (ignoredEntry, ignoredAgent, ignoredMob) -> null));

        assertEquals(new Point(165, 100), result);
        assertFalse(AgentDegenerateAttackStateRuntime.degenAttackDone(entry));
    }

    private static AgentGrindNavigationTailService.Hooks hooks(AtomicInteger navigationCalls,
                                                              Point convenientLoot,
                                                              boolean stillRetreating) {
        return new AgentGrindNavigationTailService.Hooks(
                (entry, agentPosition, combatTargetPosition, weaponType, route, retreatChecked) -> {
                    navigationCalls.incrementAndGet();
                    return new Point(combatTargetPosition.x + (retreatChecked ? 2 : 1), combatTargetPosition.y);
                },
                (weaponType, agentPosition, targetPosition) -> stillRetreating,
                (entry, agentPosition, mobPosition) -> convenientLoot);
    }
}
