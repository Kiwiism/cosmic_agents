package server.agents.capabilities.dialogue;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.commands.AgentReplyChannel;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChatIngressServiceTest {
    @Test
    void routesEarlyCommandsBeforeEntryLookup() {
        assertEarlyRoute("pending");
        assertEarlyRoute("recruit");
        assertEarlyRoute("transfer");
        assertEarlyRoute("formation");
    }

    @Test
    void stopsWhenLeaderHasNoEntriesAfterEarlyRoutes() {
        List<String> calls = new ArrayList<>();

        AgentChatIngressService.handleChat(
                character(1),
                "hello",
                AgentReplyChannel.MAP,
                hooks("none", null, calls));

        assertEquals(List.of("pending", "recruit", "transfer", "formation", "entries:1"), calls);
    }

    @Test
    void dismissRunsBeforeTargetedAndUntargetedRoutes() {
        List<String> calls = new ArrayList<>();

        AgentChatIngressService.handleChat(
                character(1),
                "dismiss agent",
                AgentReplyChannel.MAP,
                hooks("dismiss", entries(), calls));

        assertEquals(List.of("pending", "recruit", "transfer", "formation", "entries:1", "dismiss"), calls);
    }

    @Test
    void targetedRouteRunsBeforeUntargetedRoute() {
        List<String> calls = new ArrayList<>();

        AgentChatIngressService.handleChat(
                character(1),
                "agent pots",
                AgentReplyChannel.PARTY,
                hooks("targeted", entries(), calls));

        assertEquals(List.of(
                "pending",
                "recruit",
                "transfer",
                "formation",
                "entries:1",
                "dismiss",
                "targeted:1:PARTY"), calls);
    }

    @Test
    void untargetedRouteRunsLast() {
        List<String> calls = new ArrayList<>();

        AgentChatIngressService.handleChat(
                character(1),
                "hello",
                AgentReplyChannel.PARTY,
                hooks("none", entries(), calls));

        assertEquals(List.of(
                "pending",
                "recruit",
                "transfer",
                "formation",
                "entries:1",
                "dismiss",
                "targeted:1:PARTY",
                "untargeted:1:PARTY"), calls);
    }

    private static void assertEarlyRoute(String route) {
        List<String> calls = new ArrayList<>();

        AgentChatIngressService.handleChat(
                character(1),
                route,
                AgentReplyChannel.MAP,
                hooks(route, entries(), calls));

        assertEquals(expectedEarlyCalls(route), calls);
    }

    private static List<String> expectedEarlyCalls(String route) {
        List<String> order = List.of("pending", "recruit", "transfer", "formation");
        return order.subList(0, order.indexOf(route) + 1);
    }

    private static AgentChatIngressService.Hooks hooks(String handledRoute,
                                                       List<BotEntry> entries,
                                                       List<String> calls) {
        return new AgentChatIngressService.Hooks(
                (leader, message) -> route("pending", handledRoute, calls),
                (leader, message) -> route("recruit", handledRoute, calls),
                (leader, message) -> route("transfer", handledRoute, calls),
                (leader, message) -> route("formation", handledRoute, calls),
                leaderCharId -> {
                    calls.add("entries:" + leaderCharId);
                    return entries;
                },
                (leader, message) -> route("dismiss", handledRoute, calls),
                (leader, routeEntries, message, channel) -> {
                    calls.add("targeted:" + routeEntries.size() + ":" + channel);
                    return "targeted".equals(handledRoute);
                },
                (leader, routeEntries, message, channel) ->
                        calls.add("untargeted:" + routeEntries.size() + ":" + channel));
    }

    private static boolean route(String route, String handledRoute, List<String> calls) {
        calls.add(route);
        return route.equals(handledRoute);
    }

    private static List<BotEntry> entries() {
        Character leader = character(1);
        return List.of(new BotEntry(character(2), leader, null));
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
