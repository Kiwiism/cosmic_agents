package server.agents.capabilities.combat;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotDegenerateAttackStateRuntime;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class AgentGrindNavigationTailServiceTest {
    @Test
    void crossRegionRetreatPositionWinsWithoutCallingNavigationSelector() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger navigationCalls = new AtomicInteger();
        Point retreat = new Point(500, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                retreat,
                new Point(300, 100),
                true,
                hooks(navigationCalls, null, false));

        assertEquals(retreat, result);
        assertEquals(0, navigationCalls.get());
    }

    @Test
    void aoeRepositionUsesNavigationSelectorWithoutRetreatFlag() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AtomicInteger navigationCalls = new AtomicInteger();
        Point aoe = new Point(300, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                null,
                aoe,
                false,
                hooks(navigationCalls, null, false));

        assertEquals(new Point(301, 100), result);
        assertEquals(1, navigationCalls.get());
    }

    @Test
    void convenientLootCanOverrideNormalNavigationWhenNotRetreating() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Point loot = new Point(150, 100);

        Point result = AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                null,
                null,
                false,
                hooks(new AtomicInteger(), loot, false));

        assertEquals(loot, result);
    }

    @Test
    void clearsDegenerateLatchAfterLeavingRetreatBand() {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        AgentBotDegenerateAttackStateRuntime.markDegenAttackDone(entry);

        AgentGrindNavigationTailService.resolveNavigationTarget(
                entry,
                new Point(100, 100),
                new Point(200, 100),
                WeaponType.CLAW,
                null,
                null,
                false,
                hooks(new AtomicInteger(), null, false));

        assertFalse(AgentBotDegenerateAttackStateRuntime.degenAttackDone(entry));
    }

    private static AgentGrindNavigationTailService.Hooks hooks(AtomicInteger navigationCalls,
                                                              Point convenientLoot,
                                                              boolean stillRetreating) {
        return new AgentGrindNavigationTailService.Hooks(
                (entry, agentPosition, combatTargetPosition, retreatChecked) -> {
                    navigationCalls.incrementAndGet();
                    return new Point(combatTargetPosition.x + (retreatChecked ? 2 : 1), combatTargetPosition.y);
                },
                (weaponType, agentPosition, targetPosition) -> stillRetreating,
                (entry, agentPosition, mobPosition) -> convenientLoot);
    }
}
