package server.agents.capabilities.dialogue;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatReportFlow;
import server.agents.capabilities.dialogue.AgentChatReportOperationsRuntime;
import server.agents.capabilities.trade.AgentOfferRuntime;
import server.agents.integration.AgentReplyRuntime;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentChatReportDeliveryTest {
    @Test
    void reportHelpQueuesHelpLines() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        List<String> replies = new ArrayList<>();

        try (MockedStatic<AgentReplyRuntime> replyRuntime = mockStatic(AgentReplyRuntime.class)) {
            replyRuntime.when(() -> AgentReplyRuntime.queueReply(org.mockito.Mockito.eq(entry), org.mockito.Mockito.anyString()))
                    .thenAnswer(invocation -> {
                        replies.add(invocation.getArgument(1));
                        return null;
                    });

            AgentChatReportOperationsRuntime.reportHelp(entry);
        }

        assertEquals(AgentChatReportFlow.helpLines(), replies);
    }

    @Test
    void recommendedGearReportDelegatesToOfferActions() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        try (MockedStatic<AgentOfferRuntime> offers = mockStatic(AgentOfferRuntime.class)) {
            offers.when(() -> AgentOfferRuntime.recommendedGearActions(entry, bot, owner))
                    .thenReturn(new AgentChatReportRuntime.RecommendedGearActions() {
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

            AgentChatReportOperationsRuntime.reportRecommendedGear(entry, bot);

            offers.verify(() -> AgentOfferRuntime.recommendedGearActions(entry, bot, owner));
        }
    }
}
