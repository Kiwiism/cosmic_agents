package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFollowTargetResolutionServiceTest {
    @Test
    void rejectsBlankTargetWithLegacyMessage() {
        List<String> messages = new ArrayList<>();

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                " ",
                hooks(List.of(), messages));

        assertNull(resolved);
        assertEquals(List.of("Can't follow that target."), messages);
    }

    @Test
    void resolvesExactNameCaseInsensitively() {
        Character target = character(2, "Target");

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                "target",
                hooks(List.of(target), new ArrayList<>()));

        assertSame(target, resolved);
    }

    @Test
    void rejectsOneLetterPrefixWithLegacyMessage() {
        List<String> messages = new ArrayList<>();

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                "t",
                hooks(List.of(character(2, "Target")), messages));

        assertNull(resolved);
        assertEquals(List.of("Follow target must use at least 2 letters."), messages);
    }

    @Test
    void resolvesUniquePrefix() {
        Character target = character(2, "Target");

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                "tar",
                hooks(List.of(target, character(3, "Other")), new ArrayList<>()));

        assertSame(target, resolved);
    }

    @Test
    void rejectsAmbiguousPrefixWithLegacyMessage() {
        List<String> messages = new ArrayList<>();

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                "ta",
                hooks(List.of(character(2, "Target"), character(3, "Tara")), messages));

        assertNull(resolved);
        assertEquals(List.of("Ambiguous follow target 'ta': Target, Tara"), messages);
    }

    @Test
    void rejectsMissingTargetWithLegacyMessage() {
        List<String> messages = new ArrayList<>();

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                character(1, "Leader"),
                "missing",
                hooks(List.of(character(2, "Target")), messages));

        assertNull(resolved);
        assertEquals(List.of("Can't follow 'missing'. Target must be a same-party character or one of your active bots."), messages);
    }

    @Test
    void nullLeaderDoesNotQueryCandidatesOrEmitMessage() {
        AtomicInteger candidateCalls = new AtomicInteger();
        List<String> messages = new ArrayList<>();

        Character resolved = AgentFollowTargetResolutionService.resolveFollowTarget(
                null,
                "target",
                new AgentFollowTargetResolutionService.Hooks(
                        leader -> {
                            candidateCalls.incrementAndGet();
                            return List.of(character(2, "Target"));
                        },
                        (leader, message) -> messages.add(message)));

        assertNull(resolved);
        assertEquals(0, candidateCalls.get());
        assertEquals(List.of(), messages);
    }

    private static AgentFollowTargetResolutionService.Hooks hooks(List<Character> candidates, List<String> messages) {
        return new AgentFollowTargetResolutionService.Hooks(
                leader -> candidates,
                (leader, message) -> messages.add(message));
    }

    private static Character character(int id, String name) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        return character;
    }
}
