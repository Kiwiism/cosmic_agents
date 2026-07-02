package server.agents.capabilities.trade;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotTargetedCommandMatch;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentPendingOfferResponseServiceTest {
    @Test
    void routesSinglePendingOfferResponseToOnlyMatch() {
        Character speaker = mock(Character.class);
        BotEntry match = new BotEntry(mock(Character.class), speaker, null);
        BotEntry other = new BotEntry(mock(Character.class), speaker, null);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(match, other)),
                speaker,
                "yes",
                hooks(match, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "expire",
                "target:true",
                "expire",
                "target:false",
                "resolve:1:yes",
                "handle:yes"), calls);
    }

    @Test
    void targetedResponseUsesResolvedCommandText() {
        Character speaker = mock(Character.class);
        BotEntry first = new BotEntry(mock(Character.class), speaker, null);
        BotEntry second = new BotEntry(mock(Character.class), speaker, null);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(first, second)),
                speaker,
                "agent yes",
                new AgentPendingOfferResponseService.Hooks(
                        entry -> calls.add("expire"),
                        (entry, tickSpeaker) -> true,
                        (entries, message) -> {
                            calls.add("resolve:" + entries.size());
                            return new AgentBotTargetedCommandMatch(second, "yes", null);
                        },
                        (entry, tickSpeaker, message) -> {
                            calls.add("handle:" + (entry == second) + ":" + message);
                            return true;
                        },
                        (tickSpeaker, feedback) -> calls.add("feedback:" + feedback)));

        assertTrue(handled);
        assertEquals(List.of("expire", "expire", "resolve:2", "handle:true:yes"), calls);
    }

    @Test
    void ambiguousConfirmationProducesLegacyFeedback() {
        Character speaker = mock(Character.class);
        BotEntry first = new BotEntry(mock(Character.class), speaker, null);
        BotEntry second = new BotEntry(mock(Character.class), speaker, null);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(first, second)),
                speaker,
                "yes",
                new AgentPendingOfferResponseService.Hooks(
                        entry -> { },
                        (entry, tickSpeaker) -> true,
                        (entries, message) -> new AgentBotTargetedCommandMatch(null, null, null),
                        (entry, tickSpeaker, message) -> {
                            calls.add("handle");
                            return true;
                        },
                        (tickSpeaker, feedback) -> calls.add(feedback)));

        assertTrue(handled);
        assertEquals(List.of("More than one bot is waiting on you. Say '<botname> yes' or '<slot> yes'."), calls);
    }

    @Test
    void returnsFalseWhenNoOfferMatches() {
        Character speaker = mock(Character.class);
        BotEntry entry = new BotEntry(mock(Character.class), speaker, null);

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(entry)),
                speaker,
                "hello",
                hooks(null, new ArrayList<>()));

        assertFalse(handled);
    }

    private static AgentPendingOfferResponseService.Hooks hooks(BotEntry match, List<String> calls) {
        return new AgentPendingOfferResponseService.Hooks(
                entry -> calls.add("expire"),
                (entry, speaker) -> {
                    boolean result = entry == match;
                    calls.add("target:" + result);
                    return result;
                },
                (entries, message) -> {
                    calls.add("resolve:" + entries.size() + ":" + message);
                    return new AgentBotTargetedCommandMatch(null, null, null);
                },
                (entry, speaker, message) -> {
                    calls.add("handle:" + message);
                    return true;
                },
                (speaker, feedback) -> calls.add("feedback:" + feedback));
    }
}
