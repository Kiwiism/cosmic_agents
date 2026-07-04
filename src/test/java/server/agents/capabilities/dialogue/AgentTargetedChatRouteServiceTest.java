package server.agents.capabilities.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTargetedChatRouteServiceTest {
    @Test
    void returnsFalseWhenNoTargetOrFeedbackMatches() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                character(1, "Leader"),
                List.of(),
                "hello",
                AgentReplyChannel.MAP,
                hooks(new AgentTargetedCommandMatch<>(null, null, null), false, false, calls));

        assertFalse(handled);
        assertEquals(List.of("resolve:hello"), calls);
    }

    @Test
    void sendsFeedbackWhenResolverReturnsFeedbackMessage() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                character(1, "Leader"),
                List.of(),
                "zz hello",
                AgentReplyChannel.MAP,
                hooks(new AgentTargetedCommandMatch<>(null, null, "not found"), false, false, calls));

        assertTrue(handled);
        assertEquals(List.of("resolve:zz hello", "leader:not found"), calls);
    }

    @Test
    void targetedFollowCommandUsesFollowTargetPathOnly() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        BotEntry entry = entry(character(2, "Agent"), leader);

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                leader,
                List.of(entry),
                "Agent follow Bob",
                AgentReplyChannel.PARTY,
                hooks(new AgentTargetedCommandMatch<>(entry, "follow Bob", null), false, false, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:Agent follow Bob",
                "follow:follow Bob",
                "applyFollow:Bob:1"), calls);
    }

    @Test
    void typoSuggestionQueuesLegacyReplyAndStops() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        BotEntry entry = entry(character(2, "Agent"), leader);

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                leader,
                List.of(entry),
                "Agent potsz",
                AgentReplyChannel.PARTY,
                hooks(new AgentTargetedCommandMatch<>(entry, "potsz", null), true, false, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:Agent potsz",
                "follow:potsz",
                "channel:PARTY",
                "typo:potsz",
                "reply:did you mean 'pots'?"), calls);
    }

    @Test
    void recordsMatchedOwnerCommandAndSkipsLlm() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        BotEntry entry = entry(character(2, "Agent"), leader);

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                leader,
                List.of(entry),
                "Agent pots",
                AgentReplyChannel.PARTY,
                hooks(new AgentTargetedCommandMatch<>(entry, "pots", null), false, true, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:Agent pots",
                "follow:pots",
                "channel:PARTY",
                "chat:pots",
                "record:pots:123"), calls);
    }

    @Test
    void unmatchedCommandFallsThroughToLlmWhenEnabled() {
        List<String> calls = new ArrayList<>();
        Character leader = character(1, "Leader");
        BotEntry entry = entry(character(2, "Agent"), leader);

        boolean handled = AgentTargetedChatRouteService.handleTargetedChat(
                leader,
                List.of(entry),
                "Agent hi",
                AgentReplyChannel.PARTY,
                hooks(new AgentTargetedCommandMatch<>(entry, "hi", null), false, false, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:Agent hi",
                "follow:hi",
                "channel:PARTY",
                "chat:hi",
                "llm:hi"), calls);
    }

    private static AgentTargetedChatRouteService.Hooks<BotEntry> hooks(AgentTargetedCommandMatch<BotEntry> match,
                                                             boolean typoEnabled,
                                                             boolean matched,
                                                             List<String> calls) {
        AtomicBoolean chatMatched = new AtomicBoolean(matched);
        return new AgentTargetedChatRouteService.Hooks<BotEntry>(
                (entries, message) -> {
                    calls.add("resolve:" + message);
                    return match;
                },
                commandText -> {
                    calls.add("follow:" + commandText);
                    return commandText.startsWith("follow ") ? commandText.substring("follow ".length()) : null;
                },
                (leader, entries, targetToken) -> calls.add("applyFollow:" + targetToken + ":" + entries.size()),
                (entry, channel) -> calls.add("channel:" + channel),
                () -> typoEnabled,
                commandText -> {
                    calls.add("typo:" + commandText);
                    return commandText.equals("potsz") ? "pots" : null;
                },
                (entry, reply) -> calls.add("reply:" + reply),
                (entry, commandText) -> calls.add("chat:" + commandText),
                chatMatched::get,
                () -> 123L,
                BotEntry::owner,
                (entry, commandText, commandAtMs) -> calls.add("record:" + commandText + ":" + commandAtMs),
                () -> true,
                (entry, sender, commandText) -> calls.add("llm:" + commandText),
                (leader, message) -> calls.add("leader:" + message));
    }

    private static BotEntry entry(Character agent, Character leader) {
        return new BotEntry(agent, leader, null);
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
