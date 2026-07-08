package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.supplies.AgentAmmoStateRuntime;
import server.agents.integration.AgentCombatAmmoCheckRuntime;
import server.agents.integration.AgentCombatRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentCombatAmmoCheckRuntimeTest {
    @Test
    void warnsOnceWhenProjectileAmmoIsLow() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentCombatAmmoCounter> ammo = mockStatic(AgentCombatAmmoCounter.class);
             MockedStatic<AgentCombatRuntime> runtime = mockStatic(AgentCombatRuntime.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);
            ammo.when(() -> AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.BOW)).thenReturn(true);
            ammo.when(() -> AgentCombatAmmoCounter.countAmmo(bot, WeaponType.BOW)).thenReturn(10);

            AgentCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot, 100, 5);

            assertTrue(AgentAmmoStateRuntime.ammoWarnSent(entry));
            assertFalse(AgentAmmoStateRuntime.noAmmo(entry));
            runtime.verify(() -> AgentCombatRuntime.sayMapNow(eq(bot), anyString()));
        }
    }

    @Test
    void clearsWarningStateForWeaponsWithoutAmmoNeed() {
        Character bot = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, null, null);
        AgentAmmoStateRuntime.setAmmoWarnSent(entry, true);
        AgentAmmoStateRuntime.setNoAmmo(entry, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentCombatAmmoCounter> ammo = mockStatic(AgentCombatAmmoCounter.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);
            ammo.when(() -> AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.SWORD1H)).thenReturn(false);

            AgentCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot, 100, 5);

            assertFalse(AgentAmmoStateRuntime.ammoWarnSent(entry));
            assertFalse(AgentAmmoStateRuntime.noAmmo(entry));
        }
    }
}
