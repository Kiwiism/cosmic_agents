package server.agents.plans.mapleisland.cohort;

import java.util.ArrayList;
import java.util.List;

/** Deterministic 5,000-name catalog without storing 5,000 literal rows. */
public final class MapleIslandCohortNameCatalog {
    public static final int CANDIDATE_COUNT = 5_000;
    private static final int CHARACTER_NAME_MAX = 12;
    private static final String[] ADJECTIVES = {
            "Blue", "Red", "Green", "Gold", "Silver", "Tiny", "Mega", "Swift", "Brave", "Calm",
            "Wild", "Lucky", "Happy", "Sleepy", "Sunny", "Misty", "Frosty", "Fiery", "Jolly", "Quiet",
            "Rapid", "Nimble", "Shiny", "Cozy", "Gentle", "Bold", "New", "Old", "Cool", "Warm",
            "Fresh", "Bright", "Drowsy", "Clever", "Noble", "Kind", "Fair", "Merry", "Magic", "Mystic",
            "Pure", "Silky", "Tough", "Spicy", "Sweet", "Fancy", "Dusty", "Breezy", "Peachy", "Zippy"
    };
    private static final String[] NOUNS = {
            "Snail", "Slime", "Shroom", "Boar", "Ribbon", "Stump", "Sprout", "Leaf", "Seed", "Apple",
            "Orange", "Shell", "Horn", "Star", "Moon", "Sun", "Cloud", "Rain", "Snow", "River",
            "Stone", "Sword", "Bow", "Wand", "Claw", "Dagger", "Cape", "Boot", "Hat", "Potion",
            "Coin", "Quest", "Scout", "Mage", "Rogue", "Archer", "Knight", "Buddy", "Panda", "Tiger",
            "Bunny", "Puppy", "Kitty", "Bird", "Fish", "Drake", "Fairy", "Pixie", "Wisp", "Golem"
    };

    private MapleIslandCohortNameCatalog() {
    }

    public static String candidate(int index) {
        if (index < 0 || index >= CANDIDATE_COUNT) {
            throw new IllegalArgumentException("Name index must be between 0 and " + (CANDIDATE_COUNT - 1));
        }
        boolean reversed = index >= ADJECTIVES.length * NOUNS.length;
        int localIndex = reversed ? index - ADJECTIVES.length * NOUNS.length : index;
        String adjective = ADJECTIVES[localIndex / NOUNS.length];
        String noun = NOUNS[localIndex % NOUNS.length];
        String name = reversed ? noun + adjective : adjective + noun;
        if (name.length() > CHARACTER_NAME_MAX) {
            throw new IllegalStateException("Generated overlong Maple Island name: " + name);
        }
        return name;
    }

    public static List<String> candidates() {
        List<String> names = new ArrayList<>(CANDIDATE_COUNT);
        for (int index = 0; index < CANDIDATE_COUNT; index++) {
            names.add(candidate(index));
        }
        return List.copyOf(names);
    }

    public static String accountCandidate(int ordinal) {
        if (ordinal < 1 || ordinal > CANDIDATE_COUNT) {
            throw new IllegalArgumentException("Account ordinal must be between 1 and " + CANDIDATE_COUNT);
        }
        return "MIQuest%04d".formatted(ordinal);
    }
}
