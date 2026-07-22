package server.agents.plans.mapleisland.cohort;

import client.Character;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Deterministic old-school Maple-style names filtered through the live character-name policy. */
public final class MapleIslandCohortNameCatalog {
    public static final int CANDIDATE_COUNT = 30_000;

    private static final String[] ROOTS = {
            "Aeri", "Aiko", "Akira", "Aluna", "Ami", "Anzu", "Aria", "Aster", "Auri", "Aya",
            "Bambi", "Bao", "Beni", "Bibi", "Boba", "Ceri", "Ciel", "Coco", "Dango", "Deni",
            "Dori", "Eira", "Elio", "Emi", "Faye", "Fina", "Hana", "Hani", "Haru", "Hoshi",
            "Jae", "Jiji", "Juno", "Kairi", "Kiki", "Kira", "Koko", "Kumi", "Lana", "Lani",
            "Leya", "Lili", "Lumi", "Luna", "Maki", "Melo", "Miki", "Mina", "Miro", "Momo",
            "Nami", "Nana", "Neko", "Neri", "Niko", "Nori", "Nova", "Pika", "Pino", "Piyo",
            "Reni", "Riku", "Rina", "Riri", "Rumi", "Saki", "Sena", "Shiro", "Sora", "Taki",
            "Taro", "Tiki", "Tori", "Umi", "Vivi", "Yami", "Yori", "Yuki", "Yumi", "Zeri",
            "Mushie", "Snaily", "Slimey", "Piggy", "Stumpy", "Ribbon", "Orange", "Sprout", "Leafy", "Cloudy",
            "Starry", "Moonlit", "Lucky", "Sleepy", "Tiny", "Swift", "BowKid", "WandKid", "ClawKid", "DaggerKid"
    };
    private static final String[] PREFIXES = {
            "Lil", "Its", "Hey", "Im", "The", "Neo", "Mini", "Big", "Baby", "Dark",
            "Blue", "Red", "Pink", "Sky", "Star", "Moon", "Sir", "Lady", "Mr", "Miss",
            "i", "x", "Oo", "Pro", "Noob"
    };
    private static final String[] SUFFIXES = {
            "Jr", "MS", "Maple", "X", "x", "Kun", "Chan", "San", "Star", "Moon",
            "Boy", "Girl", "Hero", "Pro", "Noob", "FTW", "Pls", "Go", "One", "Two",
            "Kid", "Cat", "Bun", "Puff", "Pop"
    };
    private static final int ROOT_SPREAD_COLUMNS = 10;
    private static final List<String> CANDIDATES = buildCandidates();

    private MapleIslandCohortNameCatalog() {
    }

    public static String candidate(int index) {
        if (index < 0 || index >= CANDIDATE_COUNT) {
            throw new IllegalArgumentException("Name index must be between 0 and " + (CANDIDATE_COUNT - 1));
        }
        return CANDIDATES.get(index);
    }

    public static List<String> candidates() {
        return CANDIDATES;
    }

    static String diversityKey(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            String normalizedPrefix = prefix.toLowerCase(Locale.ROOT);
            if (normalized.length() > normalizedPrefix.length()
                    && normalized.startsWith(normalizedPrefix)) {
                return normalizedPrefix;
            }
        }
        return normalized;
    }

    static int maximumSamePrefix(int requested) {
        return Math.max(1, (requested + 4) / 5);
    }

    public static String accountCandidate(int ordinal) {
        if (ordinal < 1 || ordinal > CANDIDATE_COUNT) {
            throw new IllegalArgumentException("Account ordinal must be between 1 and " + CANDIDATE_COUNT);
        }
        return "MIQuest%04d".formatted(ordinal);
    }

    private static List<String> buildCandidates() {
        Set<String> names = new LinkedHashSet<>(CANDIDATE_COUNT * 2);
        Set<String> normalizedNames = new java.util.HashSet<>(CANDIDATE_COUNT * 2);
        List<String> roots = breadthFirstRoots();

        // Use every root family before consuming deeper style variants. The root order samples
        // across the full catalog so even one small launch batch is not alphabetically clustered.
        for (String root : roots) {
            add(names, normalizedNames, root);
        }
        for (int index = 0; index < roots.size(); index++) {
            String root = roots.get(index);
            add(names, normalizedNames, PREFIXES[index % PREFIXES.length] + root);
        }
        for (int index = 0; index < roots.size(); index++) {
            String root = roots.get(index);
            add(names, normalizedNames, root + SUFFIXES[(index * 7) % SUFFIXES.length]);
        }
        for (String root : roots) {
            add(names, normalizedNames, "x" + root + "x");
        }
        for (int index = 0; index < roots.size(); index++) {
            String root = roots.get(index);
            add(names, normalizedNames, root + "%02d".formatted((index * 13) % 100));
        }

        for (String prefix : PREFIXES) {
            for (String root : roots) {
                add(names, normalizedNames, prefix + root);
            }
        }
        for (String suffix : SUFFIXES) {
            for (String root : roots) {
                add(names, normalizedNames, root + suffix);
            }
        }
        for (int number = 0; names.size() < CANDIDATE_COUNT && number < 1_000; number++) {
            for (String root : roots) {
                add(names, normalizedNames, root + number);
                if (names.size() >= CANDIDATE_COUNT) {
                    break;
                }
            }
        }

        if (names.size() < CANDIDATE_COUNT) {
            throw new IllegalStateException("Could generate only " + names.size() + " safe cohort names");
        }
        return List.copyOf(new ArrayList<>(names).subList(0, CANDIDATE_COUNT));
    }

    private static List<String> breadthFirstRoots() {
        List<String> roots = new ArrayList<>(ROOTS.length);
        int rows = (ROOTS.length + ROOT_SPREAD_COLUMNS - 1) / ROOT_SPREAD_COLUMNS;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < ROOT_SPREAD_COLUMNS; column++) {
                int index = column * rows + row;
                if (index < ROOTS.length) {
                    roots.add(ROOTS[index]);
                }
            }
        }
        return roots;
    }

    private static void add(Set<String> names, Set<String> normalizedNames, String candidate) {
        if (!Character.isValidCharacterNameSyntax(candidate)) {
            return;
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        if (normalizedNames.add(normalized)) {
            names.add(candidate);
        }
    }
}
