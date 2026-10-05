package server.agents.capabilities.dialogue.jev;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatFallThroughCommandsTest {
    @Test
    void movementCanonicalPhrasesAreHandledWithoutTheJudge() {
        for (AgentChatIntent intent : List.of(AgentChatIntent.FOLLOW, AgentChatIntent.STOP, AgentChatIntent.MOVE_HERE,
                AgentChatIntent.GRIND, AgentChatIntent.PATROL, AgentChatIntent.SENTRY, AgentChatIntent.FIDGET)) {
            assertTrue(AgentChatFallThroughCommands.handledDeterministically(intent.canonicalCommand()),
                    intent + " canonical phrase '" + intent.canonicalCommand() + "' is acted on by the movement flow");
        }
    }

    @Test
    void reportQueriesGreetingsAndJobNamesFallThroughButAreHandled() {
        assertTrue(AgentChatFallThroughCommands.handledDeterministically("stats"));
        assertTrue(AgentChatFallThroughCommands.handledDeterministically("hi"));
        assertTrue(AgentChatFallThroughCommands.handledDeterministically("fighter"));
    }

    @Test
    void liveActOnAFallThroughCanonicalCountsAsHandled() {
        assertTrue(AgentChatIntentRuntime.actHandled("grind", false), "movement flow acted, orchestrator said false");
        assertTrue(AgentChatIntentRuntime.actHandled("stop", null));
        assertTrue(AgentChatIntentRuntime.actHandled("autoequip", true));
        assertFalse(AgentChatIntentRuntime.actHandled("autoequip", false), "a real dispatcher miss stays a miss");
    }

    @Test
    void regexMissesStillReachTheJudge() {
        assertFalse(AgentChatFallThroughCommands.handledDeterministically("can u farm around here pls"));
        assertFalse(AgentChatFallThroughCommands.handledDeterministically("stick with me for a bit"));
        assertFalse(AgentChatFallThroughCommands.handledDeterministically("what a nice day"));
        assertFalse(AgentChatFallThroughCommands.handledDeterministically(""));
        assertFalse(AgentChatFallThroughCommands.handledDeterministically(null));
    }
}
