package server.agents.capabilities.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannel;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentUntargetedChatRouteServiceTest {
    @Test
    void followTargetCommandRunsBeforeOtherRouting() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        List<AgentRuntimeEntry> entries = List.of(entry(character(2, "Agent"), leader));

        AgentUntargetedChatRouteService.handleUntargetedChat(
                leader,
                entries,
                "follow Bob",
                AgentReplyChannel.PARTY,
                hooks(entries.get(0), true, calls));

        assertEquals(List.of("followMatcher:follow Bob", "applyFollow:Bob:1"), calls);
    }

    @Test
    void groupSupplyRequestRoutesToSingleResponder() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        AgentRuntimeEntry responder = entry(character(2, "Agent"), leader);

        AgentUntargetedChatRouteService.handleUntargetedChat(
                leader,
                List.of(responder, entry(character(3, "Other"), leader)),
                "need pots",
                AgentReplyChannel.PARTY,
                hooks(responder, false, calls));

        assertEquals(List.of(
                "followMatcher:need pots",
                "groupSupply:need pots",
                "selectResponder",
                "channel:Agent:PARTY",
                "chat:Agent:need pots"), calls);
    }

    @Test
    void typoSuggestionQueuesOnceOnFirstEntry() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        AgentRuntimeEntry first = entry(character(2, "Agent"), leader);

        AgentUntargetedChatRouteService.handleUntargetedChat(
                leader,
                List.of(first, entry(character(3, "Other"), leader)),
                "potsz",
                AgentReplyChannel.PARTY,
                hooks(null, false, calls));

        assertEquals(List.of(
                "followMatcher:potsz",
                "groupSupply:potsz",
                "typo:potsz",
                "channel:Agent:PARTY",
                "reply:Agent:did you mean 'pots'?"), calls);
    }

    @Test
    void broadcastsToEveryEntryWhenNoSpecialRouteMatches() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        AgentRuntimeEntry first = entry(character(2, "Agent"), leader);
        AgentRuntimeEntry second = entry(character(3, "Other"), leader);

        AgentUntargetedChatRouteService.handleUntargetedChat(
                leader,
                List.of(first, second),
                "hello",
                AgentReplyChannel.PARTY,
                hooks(null, false, calls));

        assertEquals(List.of(
                "followMatcher:hello",
                "groupSupply:hello",
                "typo:hello",
                "channel:Agent:PARTY",
                "chat:Agent:hello",
                "channel:Other:PARTY",
                "chat:Other:hello"), calls);
    }

    private static AgentUntargetedChatRouteService.Hooks<AgentRuntimeEntry> hooks(AgentRuntimeEntry groupResponder,
                                                               boolean typoDisabled,
                                                               List<String> calls) {
        return new AgentUntargetedChatRouteService.Hooks<AgentRuntimeEntry>(
                message -> {
                    calls.add("followMatcher:" + message);
                    return message.startsWith("follow ") ? message.substring("follow ".length()) : null;
                },
                (leader, entries, targetToken) -> calls.add("applyFollow:" + targetToken + ":" + entries.size()),
                message -> {
                    calls.add("groupSupply:" + message);
                    return message.equals("need pots");
                },
                (leader, entries) -> {
                    calls.add("selectResponder");
                    return groupResponder;
                },
                (entry, channel) -> calls.add("channel:" + entry.bot().getName() + ":" + channel),
                (entry, message) -> calls.add("chat:" + entry.bot().getName() + ":" + message),
                () -> !typoDisabled,
                message -> {
                    calls.add("typo:" + message);
                    return message.equals("potsz") ? "pots" : null;
                },
                (entry, reply) -> calls.add("reply:" + entry.bot().getName() + ":" + reply));
    }

    private static AgentRuntimeEntry entry(Character agent, Character leader) {
        return new AgentRuntimeEntry(agent, leader, null);
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
