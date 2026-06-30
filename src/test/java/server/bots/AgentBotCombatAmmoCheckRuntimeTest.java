package server.bots;

import client.Character;
import client.inventory.WeaponType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.integration.AgentBotAmmoStateRuntime;
import server.agents.integration.AgentBotCombatAmmoCheckRuntime;
import server.agents.integration.AgentBotCombatRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotCombatAmmoCheckRuntimeTest {
    @Test
    void warnsOnceWhenProjectileAmmoIsLow() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentCombatAmmoCounter> ammo = mockStatic(AgentCombatAmmoCounter.class);
             MockedStatic<AgentBotCombatRuntime> runtime = mockStatic(AgentBotCombatRuntime.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.BOW);
            ammo.when(() -> AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.BOW)).thenReturn(true);
            ammo.when(() -> AgentCombatAmmoCounter.countAmmo(bot, WeaponType.BOW)).thenReturn(10);

            AgentBotCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot, 100, 5);

            assertTrue(AgentBotAmmoStateRuntime.ammoWarnSent(entry));
            assertFalse(AgentBotAmmoStateRuntime.noAmmo(entry));
            runtime.verify(() -> AgentBotCombatRuntime.sayMapNow(eq(bot), anyString()));
        }
    }

    @Test
    void clearsWarningStateForWeaponsWithoutAmmoNeed() {
        Character bot = mock(Character.class);
        BotEntry entry = new BotEntry(bot, null, null);
        AgentBotAmmoStateRuntime.setAmmoWarnSent(entry, true);
        AgentBotAmmoStateRuntime.setNoAmmo(entry, true);

        try (MockedStatic<AgentAttackExecutionProvider> attacks = mockStatic(AgentAttackExecutionProvider.class);
             MockedStatic<AgentCombatAmmoCounter> ammo = mockStatic(AgentCombatAmmoCounter.class)) {
            attacks.when(() -> AgentAttackExecutionProvider.getEquippedWeaponType(bot)).thenReturn(WeaponType.SWORD1H);
            ammo.when(() -> AgentCombatAmmoCounter.isRangedAmmoWeapon(WeaponType.SWORD1H)).thenReturn(false);

            AgentBotCombatAmmoCheckRuntime.tickAmmoCheck(entry, bot, 100, 5);

            assertFalse(AgentBotAmmoStateRuntime.ammoWarnSent(entry));
            assertFalse(AgentBotAmmoStateRuntime.noAmmo(entry));
        }
    }
}
