package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotPotionRuntime;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BotPotionManagerTest {
    @Test
    @SuppressWarnings("unchecked")
    void ownerPotionShareSchedulesThroughAgentPotionRuntime() throws Exception {
        BotManager manager = BotManager.getInstance();
        Character owner = mock(Character.class);
        Character requestingBot = mock(Character.class);
        Character donorBot = mock(Character.class);
        BotEntry entry = new BotEntry(requestingBot, owner, null);
        BotEntry donorEntry = new BotEntry(donorBot, owner, null);

        when(owner.getId()).thenReturn(88);
        when(owner.getMapId()).thenReturn(100000000);
        when(owner.getTrade()).thenReturn(null);
        when(donorBot.getMapId()).thenReturn(100000000);

        Map<Integer, List<BotEntry>> bots = (Map<Integer, List<BotEntry>>) field(BotManager.class, "bots").get(manager);
        bots.put(owner.getId(), List.of(entry, donorEntry));

        try (MockedStatic<BotPotionManager> potions = mockStatic(BotPotionManager.class, CALLS_REAL_METHODS);
             MockedStatic<AgentBotPotionRuntime> scheduler = mockStatic(AgentBotPotionRuntime.class)) {
            potions.when(() -> BotPotionManager.countPotions(donorBot)).thenReturn(new int[]{400, 0});
            scheduler.when(() -> AgentBotPotionRuntime.randomDelayMs(900, 1400)).thenReturn(77L);

            assertEquals(BotPotionManager.OwnerPotShareResult.OFFERED,
                    BotPotionManager.offerPotShareToOwner(entry, true));

            scheduler.verify(() -> AgentBotPotionRuntime.randomDelayMs(900, 1400));
            scheduler.verify(() -> AgentBotPotionRuntime.afterDelay(eq(77L), any(Runnable.class)));
        } finally {
            bots.remove(owner.getId());
        }
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
