package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.bots.BotEntry;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowTargetCommandServiceTest {
    @Test
    void returnsHandledWhenTargetCannotResolve() {
        List<String> calls = new ArrayList<>();

        boolean handled = AgentFollowTargetCommandService.applyFollowTargetCommand(
                character(1, "Leader"),
                List.of(entry(character(2, "Agent"), character(1, "Leader"))),
                "missing",
                hooks(null, calls));

        assertTrue(handled);
        assertEquals(List.of("resolve:missing"), calls);
    }

    @Test
    void skipsNullMissingBotAndSelfTargetEntries() {
        Character leader = character(1, "Leader");
        Character target = character(2, "Target");
        Character agent = character(3, "Agent");
        BotEntry selfTarget = entry(target, leader);
        BotEntry valid = entry(agent, leader);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentFollowTargetCommandService.applyFollowTargetCommand(
                leader,
                new ArrayList<>(java.util.Arrays.asList(null, selfTarget, valid)),
                "target",
                hooks(target, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:target",
                "reply:Agent:ok",
                "delay:500",
                "autoEquip:Agent",
                "potions:Agent",
                "follow:Agent:Target"), calls);
    }

    @Test
    void appliesLegacyOrderToEachValidEntry() {
        Character leader = character(1, "Leader");
        Character target = character(2, "Target");
        BotEntry first = entry(character(3, "Alpha"), leader);
        BotEntry second = entry(character(4, "Beta"), leader);
        List<String> calls = new ArrayList<>();

        boolean handled = AgentFollowTargetCommandService.applyFollowTargetCommand(
                leader,
                List.of(first, second),
                "target",
                hooks(target, calls));

        assertTrue(handled);
        assertEquals(List.of(
                "resolve:target",
                "reply:Alpha:ok",
                "delay:500",
                "autoEquip:Alpha",
                "potions:Alpha",
                "follow:Alpha:Target",
                "reply:Beta:ok",
                "delay:500",
                "autoEquip:Beta",
                "potions:Beta",
                "follow:Beta:Target"), calls);
    }

    private static AgentFollowTargetCommandService.Hooks hooks(Character target, List<String> calls) {
        return new AgentFollowTargetCommandService.Hooks(
                (leader, token) -> {
                    calls.add("resolve:" + token);
                    return target;
                },
                followTarget -> "ok",
                (entry, reply) -> calls.add("reply:" + entry.bot().getName() + ":" + reply),
                () -> 500L,
                (delayMs, action) -> {
                    calls.add("delay:" + delayMs);
                    action.run();
                },
                entry -> calls.add("autoEquip:" + entry.bot().getName()),
                entry -> calls.add("potions:" + entry.bot().getName()),
                (entry, followTarget) -> calls.add("follow:" + entry.bot().getName() + ":" + followTarget.getName()));
    }

    private static BotEntry entry(Character bot, Character leader) {
        return new BotEntry(bot, leader, null);
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
