package net.server.task;

import client.Character;
import net.server.PlayerStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Maintains one bounded, evenly spaced autosave cycle for a world. The cycle
 * stores character ids rather than Character references so logout can release
 * the live character while it is waiting for its turn.
 */
final class CharacterAutosaveCoordinator {
    enum SubmissionResult {
        ACCEPTED,
        COALESCED,
        FULL
    }

    @FunctionalInterface
    interface AutosaveSubmitter {
        SubmissionResult submit(Character character);
    }

    private final long windowMs;
    private final int maxDispatchPerRun;
    private final LongSupplier clock;
    private final Consumer<List<Integer>> orderer;
    private final AutosaveSubmitter submitter;

    private List<Integer> cycleCharacterIds = List.of();
    private int nextIndex;
    private long cycleStartedAtMs;
    private long cycleEndsAtMs;
    private boolean cycleStarted;

    CharacterAutosaveCoordinator(long windowMs,
                                 int maxDispatchPerRun,
                                 LongSupplier clock,
                                 Consumer<List<Integer>> orderer,
                                 AutosaveSubmitter submitter) {
        if (windowMs < 1L || maxDispatchPerRun < 1 || clock == null || orderer == null || submitter == null) {
            throw new IllegalArgumentException("Autosave cycle configuration is invalid");
        }
        this.windowMs = windowMs;
        this.maxDispatchPerRun = maxDispatchPerRun;
        this.clock = clock;
        this.orderer = orderer;
        this.submitter = submitter;
    }

    void run(PlayerStorage storage) {
        if (storage == null) {
            return;
        }

        long nowMs = clock.getAsLong();
        if (!cycleStarted || cycleComplete() && nowMs >= cycleEndsAtMs) {
            startCycle(storage, nowMs);
        }
        if (cycleComplete()) {
            return;
        }

        int attempts = 0;
        while (!cycleComplete() && attempts < maxDispatchPerRun && nowMs >= dueAt(nextIndex)) {
            attempts++;
            int characterId = cycleCharacterIds.get(nextIndex);
            Character character = storage.getCharacterById(characterId);
            if (character == null || !character.isLoggedin()) {
                nextIndex++;
                continue;
            }

            SubmissionResult result = submitter.submit(character);
            if (result == SubmissionResult.FULL) {
                return;
            }
            nextIndex++;
        }
    }

    int remainingInCycle() {
        return cycleCharacterIds.size() - nextIndex;
    }

    private void startCycle(PlayerStorage storage, long nowMs) {
        List<Integer> characterIds = new ArrayList<>();
        for (Character character : storage.getAllCharacters()) {
            if (character != null && character.isLoggedin()) {
                characterIds.add(character.getId());
            }
        }
        orderer.accept(characterIds);
        cycleCharacterIds = List.copyOf(characterIds);
        nextIndex = 0;
        cycleStartedAtMs = nowMs;
        cycleEndsAtMs = safeAdd(nowMs, windowMs);
        cycleStarted = true;
    }

    private long dueAt(int index) {
        if (cycleCharacterIds.isEmpty()) {
            return cycleEndsAtMs;
        }
        long offset = windowMs * (index + 1L) / cycleCharacterIds.size();
        return safeAdd(cycleStartedAtMs, Math.max(1L, offset));
    }

    private boolean cycleComplete() {
        return nextIndex >= cycleCharacterIds.size();
    }

    private static long safeAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    static Consumer<List<Integer>> randomizedOrder() {
        return Collections::shuffle;
    }
}
