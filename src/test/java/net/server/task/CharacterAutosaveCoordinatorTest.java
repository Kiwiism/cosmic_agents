package net.server.task;

import client.Character;
import net.server.PlayerStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CharacterAutosaveCoordinatorTest {
    @Test
    void spreadsCharactersAcrossTheWholeWindow() {
        AtomicLong nowMs = new AtomicLong();
        PlayerStorage storage = storageWithCharacters(1, 2, 3, 4);
        List<Integer> submitted = new ArrayList<>();
        CharacterAutosaveCoordinator coordinator = coordinator(
                nowMs,
                1_000L,
                8,
                character -> {
                    submitted.add(character.getId());
                    return CharacterAutosaveCoordinator.SubmissionResult.ACCEPTED;
                });

        coordinator.run(storage);
        assertEquals(List.of(), submitted);

        nowMs.set(249L);
        coordinator.run(storage);
        assertEquals(List.of(), submitted);

        nowMs.set(250L);
        coordinator.run(storage);
        nowMs.set(500L);
        coordinator.run(storage);
        nowMs.set(750L);
        coordinator.run(storage);
        assertEquals(List.of(1, 2, 3), submitted);
        assertEquals(1, coordinator.remainingInCycle());

        nowMs.set(1_000L);
        coordinator.run(storage);
        assertEquals(List.of(1, 2, 3, 4), submitted);
        assertEquals(0, coordinator.remainingInCycle());
    }

    @Test
    void boundsCatchUpWorkPerDispatcherRun() {
        AtomicLong nowMs = new AtomicLong();
        PlayerStorage storage = storageWithCharacters(1, 2, 3, 4, 5);
        List<Integer> submitted = new ArrayList<>();
        CharacterAutosaveCoordinator coordinator = coordinator(
                nowMs,
                1_000L,
                2,
                character -> {
                    submitted.add(character.getId());
                    return CharacterAutosaveCoordinator.SubmissionResult.ACCEPTED;
                });

        coordinator.run(storage);
        nowMs.set(1_000L);
        coordinator.run(storage);
        assertEquals(List.of(1, 2), submitted);
        assertEquals(3, coordinator.remainingInCycle());

        coordinator.run(storage);
        assertEquals(List.of(1, 2, 3, 4), submitted);
        assertEquals(1, coordinator.remainingInCycle());
    }

    @Test
    void retriesTheSameCharacterAfterQueueBackpressure() {
        AtomicLong nowMs = new AtomicLong();
        PlayerStorage storage = storageWithCharacters(7);
        List<Integer> submitted = new ArrayList<>();
        AtomicLong attempts = new AtomicLong();
        CharacterAutosaveCoordinator coordinator = coordinator(
                nowMs,
                100L,
                4,
                character -> {
                    if (attempts.getAndIncrement() == 0L) {
                        return CharacterAutosaveCoordinator.SubmissionResult.FULL;
                    }
                    submitted.add(character.getId());
                    return CharacterAutosaveCoordinator.SubmissionResult.ACCEPTED;
                });

        coordinator.run(storage);
        nowMs.set(100L);
        coordinator.run(storage);
        assertEquals(1, coordinator.remainingInCycle());
        assertEquals(List.of(), submitted);

        coordinator.run(storage);
        assertEquals(List.of(7), submitted);
        assertEquals(0, coordinator.remainingInCycle());
    }

    @Test
    void resolvesCharactersAtDispatchTimeInsteadOfRetainingLoggedOutInstances() {
        AtomicLong nowMs = new AtomicLong();
        PlayerStorage storage = storageWithCharacters(1, 2);
        List<Integer> submitted = new ArrayList<>();
        CharacterAutosaveCoordinator coordinator = coordinator(
                nowMs,
                100L,
                4,
                character -> {
                    submitted.add(character.getId());
                    return CharacterAutosaveCoordinator.SubmissionResult.ACCEPTED;
                });

        coordinator.run(storage);
        storage.removePlayer(1);
        nowMs.set(100L);
        coordinator.run(storage);

        assertEquals(List.of(2), submitted);
        assertEquals(0, coordinator.remainingInCycle());
    }

    private static CharacterAutosaveCoordinator coordinator(
            AtomicLong nowMs,
            long windowMs,
            int maxDispatchPerRun,
            CharacterAutosaveCoordinator.AutosaveSubmitter submitter) {
        return new CharacterAutosaveCoordinator(
                windowMs,
                maxDispatchPerRun,
                nowMs::get,
                ignored -> {
                },
                submitter);
    }

    private static PlayerStorage storageWithCharacters(int... ids) {
        PlayerStorage storage = new PlayerStorage();
        for (int id : ids) {
            Character character = mock(Character.class);
            when(character.getId()).thenReturn(id);
            when(character.getName()).thenReturn("Character" + id);
            when(character.isLoggedin()).thenReturn(true);
            storage.addPlayer(character);
        }
        return storage;
    }
}
