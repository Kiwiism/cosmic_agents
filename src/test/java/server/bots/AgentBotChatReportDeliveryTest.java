package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.integration.AgentBotChatReportRuntime;
import server.agents.integration.AgentBotOfferRuntime;
import server.agents.integration.AgentBotReplyRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotChatReportDeliveryTest {
    @Test
    void reportHelpQueuesHelpLines() {
        BotEntry entry = new BotEntry(null, null, null);
        List<String> replies = new ArrayList<>();

        try (MockedStatic<AgentBotReplyRuntime> replyRuntime = mockStatic(AgentBotReplyRuntime.class)) {
            replyRuntime.when(() -> AgentBotReplyRuntime.queueReply(org.mockito.Mockito.eq(entry), org.mockito.Mockito.anyString()))
                    .thenAnswer(invocation -> {
                        replies.add(invocation.getArgument(1));
                        return null;
                    });

            AgentBotChatReportRuntime.reportHelp(entry);
        }

        assertEquals(AgentChatReportFlow.helpLines(), replies);
    }

    @Test
    void recommendedGearReportDelegatesToOfferActions() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);

        try (MockedStatic<AgentBotOfferRuntime> offers = mockStatic(AgentBotOfferRuntime.class)) {
            offers.when(() -> AgentBotOfferRuntime.recommendedGearActions(entry, bot, owner))
                    .thenReturn(new server.agents.capabilities.dialogue.AgentChatReportRuntime.RecommendedGearActions() {
                        @Override
                        public boolean hasOwner() {
                            return true;
                        }

                        @Override
                        public boolean offerBestRecommendedGear() {
                            return true;
                        }

                        @Override
                        public void queueReply(String line) {
                        }
                    });

            AgentBotChatReportRuntime.reportRecommendedGear(entry, bot);

            offers.verify(() -> AgentBotOfferRuntime.recommendedGearActions(entry, bot, owner));
        }
    }
}
