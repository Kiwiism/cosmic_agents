package server.agents.economy.activity;

import client.Character;

import java.util.Objects;

/** Canonical economy/calibration job-family mapping for a real Cosmic character. */
public final class EconomyJobFamily {
    private EconomyJobFamily() { }

    public static String of(Character character) {
        Objects.requireNonNull(character, "character");
        return switch (character.getJob().getJobNiche()) {
            case 1 -> "warrior";
            case 2 -> "magician";
            case 3 -> "bowman";
            case 4 -> "thief";
            case 5 -> "pirate";
            default -> "beginner";
        };
    }
}
