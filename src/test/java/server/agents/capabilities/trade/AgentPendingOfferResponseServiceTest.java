package server.agents.capabilities.trade;

import client.Character;
import client.inventory.Item;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentTargetedCommandMatch;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPendingOfferResponseServiceTest {
    @Test
    void routesSinglePendingOfferResponseToOnlyMatch() {
        Character speaker = mock(Character.class);
        AgentRuntimeEntry match = new AgentRuntimeEntry(mock(Character.class), speaker, null);
        AgentRuntimeEntry other = new AgentRuntimeEntry(mock(Character.class), speaker, null);
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
        AgentRuntimeEntry first = new AgentRuntimeEntry(mock(Character.class), speaker, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(mock(Character.class), speaker, null);
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
                            return new AgentTargetedCommandMatch<>(second, "yes", null);
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
        AgentRuntimeEntry first = new AgentRuntimeEntry(mock(Character.class), speaker, null);
        AgentRuntimeEntry second = new AgentRuntimeEntry(mock(Character.class), speaker, null);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(first, second)),
                speaker,
                "yes",
                new AgentPendingOfferResponseService.Hooks(
                        entry -> { },
                        (entry, tickSpeaker) -> true,
                        (entries, message) -> new AgentTargetedCommandMatch<>(null, null, null),
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
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), speaker, null);

        boolean handled = AgentPendingOfferResponseService.handlePendingOfferResponse(
                List.of(List.of(entry)),
                speaker,
                "hello",
                hooks(null, new ArrayList<>()));

        assertFalse(handled);
    }

    @Test
    void defaultTargetCheckRequiresOfferRecipientAndSameMap() {
        Character speaker = mock(Character.class);
        Character agent = mock(Character.class);
        when(speaker.getId()).thenReturn(100);
        when(speaker.getMapId()).thenReturn(20000);
        when(agent.getMapId()).thenReturn(20000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, mock(Character.class), null);
        AgentOfferStateRuntime.setPendingLootOffer(entry, mock(Item.class), 100, Long.MAX_VALUE, false);

        assertTrue(AgentPendingOfferChatRouteService.isPendingOfferTarget(entry, speaker));

        when(speaker.getMapId()).thenReturn(30000);
        assertFalse(AgentPendingOfferChatRouteService.isPendingOfferTarget(entry, speaker));

        Character otherSpeaker = mock(Character.class);
        when(otherSpeaker.getId()).thenReturn(101);
        when(otherSpeaker.getMapId()).thenReturn(20000);
        assertFalse(AgentPendingOfferChatRouteService.isPendingOfferTarget(entry, otherSpeaker));

        assertFalse(AgentPendingOfferChatRouteService.isPendingOfferTarget(null, speaker));
    }

    private static AgentPendingOfferResponseService.Hooks<AgentRuntimeEntry> hooks(AgentRuntimeEntry match, List<String> calls) {
        return new AgentPendingOfferResponseService.Hooks(
                entry -> calls.add("expire"),
                (entry, speaker) -> {
                    boolean result = entry == match;
                    calls.add("target:" + result);
                    return result;
                },
                (entries, message) -> {
                    calls.add("resolve:" + entries.size() + ":" + message);
                    return new AgentTargetedCommandMatch<>(null, null, null);
                },
                (entry, speaker, message) -> {
                    calls.add("handle:" + message);
                    return true;
                },
                (speaker, feedback) -> calls.add("feedback:" + feedback));
    }
}

