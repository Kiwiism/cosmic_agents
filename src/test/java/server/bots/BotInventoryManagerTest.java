package server.bots;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.Trade;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotInventoryManagerTest {
    @Test
    void shouldOnlyAnnounceTradeInviteOnFirstBatchOfSequence() throws Exception {
        BotEntry entry = new BotEntry(mock(Character.class), mock(Character.class), null);
        Character bot = entry.bot;
        Character owner = entry.owner;
        BotManager manager = spy(BotManager.getInstance());
        Method startTradeSequence = method(BotInventoryManager.class,
                "startTradeSequence",
                String.class, Character.class, List.class, int.class, boolean.class, BotEntry.class, Character.class);
        Method openTradeBatch = method(BotInventoryManager.class,
                "openTradeBatch",
                BotEntry.class, Character.class, List.class, int.class);

        when(owner.getId()).thenReturn(42);
        when(owner.getTrade()).thenReturn(null);
        when(bot.getTrade()).thenReturn(null);
        doAnswer(invocation -> null).when(manager).botReply(eq(entry), anyString());

        try (MockedStatic<BotManager> botManagers = mockStatic(BotManager.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             MockedStatic<Trade> trades = mockStatic(Trade.class)) {
            botManagers.when(BotManager::getInstance).thenReturn(manager);

            startTradeSequence.invoke(null, "trash", owner, List.of(mock(Item.class)), 0, false, entry, bot);
            openTradeBatch.invoke(null, entry, bot, List.of(mock(Item.class)), 0);

            verify(manager, times(1)).botReply(eq(entry), anyString());
        }
    }

    @Test
    void shouldExposeExpandedTradeMessagePools() throws Exception {
        List<String> invitationMsgs = listField("TRADE_INVITATION_MSGS");
        List<String> freebieMsgs = listField("TRADE_FREEBIE_QUIPS");

        assertTrue(invitationMsgs.size() >= 20);
        assertTrue(invitationMsgs.contains("ready when u are"));
        assertTrue(invitationMsgs.contains("trade time"));

        assertTrue(freebieMsgs.size() >= 16);
        assertTrue(freebieMsgs.contains("enjoy"));
        assertTrue(freebieMsgs.contains(":)"));
        assertTrue(freebieMsgs.contains("hope that helps"));
    }

    @SuppressWarnings("unchecked")
    private static List<String> listField(String name) throws Exception {
        return (List<String>) field(BotInventoryManager.class, name).get(null);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) throws Exception {
        Method method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }
}
