package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentChatAwayFlowTest {
    @Test
    void shouldRouteLogoutConfirmation() {
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatAwayFlow.handleOwnerAwayChoice("logout", true, callbacks);

        assertEquals("clear;logout;", callbacks.events);
    }

    @Test
    void shouldRouteTownConfirmationWhenTownWasOffered() {
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatAwayFlow.handleOwnerAwayChoice("town", true, callbacks);

        assertEquals("clear;town:true;", callbacks.events);
    }

    @Test
    void shouldTreatYesAsStayWhenTownWasNotOffered() {
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatAwayFlow.handleOwnerAwayChoice("yes", false, callbacks);

        assertEquals("clear;town:false;", callbacks.events);
    }

    @Test
    void shouldRouteExplicitStayOnlyWhenTownWasNotOffered() {
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatAwayFlow.handleOwnerAwayChoice("stay", false, callbacks);

        assertEquals("clear;stay;", callbacks.events);
    }

    @Test
    void shouldCancelUnmatchedChoices() {
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatAwayFlow.handleOwnerAwayChoice("later", true, callbacks);

        assertEquals("clear;cancel;", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatAwayFlow.AwayChoiceCallbacks {
        private String events = "";

        @Override
        public void clearPendingAction() {
            events += "clear;";
        }

        @Override
        public void logout() {
            events += "logout;";
        }

        @Override
        public void townOrStay(boolean townOffered) {
            events += "town:" + townOffered + ";";
        }

        @Override
        public void stay() {
            events += "stay;";
        }

        @Override
        public void cancel() {
            events += "cancel;";
        }
    }
}
