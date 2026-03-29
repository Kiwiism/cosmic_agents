package server.bots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotChatManagerTest {
    @Test
    void shouldParseTradeMesosAsAllWhenNoAmountIsSpecified() {
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade mesos"));
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade me all your mesos"));
    }

    @Test
    void shouldParseTradeMesosWithExplicitAmounts() {
        assertEquals("mesos:1000000", BotChatManager.matchTradeCategory("trade 1m mesos"));
        assertEquals("mesos:1250000", BotChatManager.matchTradeCategory("trade 1,250,000 mesos"));
        assertEquals("mesos:1500000", BotChatManager.matchTradeCategory("trade 1.5m mesos"));
    }

    @Test
    void shouldParseAdditionalMesoTransferPhrasings() {
        assertEquals("mesos:5000000", BotChatManager.matchTradeCategory("give me 5m"));
        assertEquals("mesos:200000", BotChatManager.matchTradeCategory("gimme 200000"));
        assertEquals("mesos", BotChatManager.matchTradeCategory("trade meso"));
        assertEquals("mesos:10000000", BotChatManager.matchTradeCategory("give meso 10m"));
        assertEquals("mesos:10000000", BotChatManager.matchTradeCategory("trade 10m"));
    }

    @Test
    void shouldStillParseNamedItemTrades() {
        assertEquals("name:chaos scroll", BotChatManager.matchTradeCategory("trade chaos scroll"));
    }

    @Test
    void shouldParseRecommendedGearTrades() {
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade recommended gear"));
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade me upgrades"));
        assertEquals("recommended", BotChatManager.matchTradeCategory("trade better equipment"));
    }
}
