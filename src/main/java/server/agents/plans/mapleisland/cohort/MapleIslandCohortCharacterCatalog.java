package server.agents.plans.mapleisland.cohort;

import java.util.HashSet;
import java.util.Set;

/** Mixed-radix catalog of every v83 MakeCharInfo-approved Beginner combination. */
public final class MapleIslandCohortCharacterCatalog {
    private static final int[] MALE_FACES = {20000, 20001, 20002};
    private static final int[] FEMALE_FACES = {21000, 21001, 21002};
    private static final int[] MALE_HAIRS = {30030, 30020, 30000};
    private static final int[] FEMALE_HAIRS = {31000, 31040, 31050};
    private static final int[] HAIR_COLORS = {0, 7, 3, 2};
    private static final int[] SKINS = {0, 1, 2, 3};
    private static final int[] MALE_TOPS = {1040002, 1040006, 1040010};
    private static final int[] FEMALE_TOPS = {1041002, 1041006, 1041010, 1041011};
    private static final int[] MALE_BOTTOMS = {1060002, 1060006};
    private static final int[] FEMALE_BOTTOMS = {1061002, 1061008};
    private static final int[] SHOES = {1072001, 1072005, 1072037, 1072038};
    private static final int[] WEAPONS = {1302000, 1322005, 1312004};

    public static final int MALE_COMBINATION_COUNT = 10_368;
    public static final int FEMALE_COMBINATION_COUNT = 13_824;
    public static final int COMBINATION_COUNT = MALE_COMBINATION_COUNT + FEMALE_COMBINATION_COUNT;
    private static final int INTERLEAVED_COUNT = MALE_COMBINATION_COUNT * 2;

    private MapleIslandCohortCharacterCatalog() {
    }

    public static MapleIslandCohortCharacterTemplate template(int ordinal) {
        if (ordinal < 0 || ordinal >= COMBINATION_COUNT) {
            throw new IllegalArgumentException("Character combination ordinal must be between 0 and "
                    + (COMBINATION_COUNT - 1));
        }
        if (ordinal < INTERLEAVED_COUNT) {
            int gender = ordinal & 1;
            return decode(ordinal, gender, ordinal / 2);
        }
        return decode(ordinal, 1, MALE_COMBINATION_COUNT + ordinal - INTERLEAVED_COUNT);
    }

    public static int firstUnusedOrdinal(Iterable<Integer> assignedOrdinals) {
        Set<Integer> assigned = new HashSet<>();
        if (assignedOrdinals != null) {
            for (Integer ordinal : assignedOrdinals) {
                if (ordinal != null) {
                    assigned.add(ordinal);
                }
            }
        }
        for (int ordinal = 0; ordinal < COMBINATION_COUNT; ordinal++) {
            if (!assigned.contains(ordinal)) {
                return ordinal;
            }
        }
        throw new IllegalStateException("No unused Maple Island character combinations remain");
    }

    private static MapleIslandCohortCharacterTemplate decode(int ordinal, int gender, int localOrdinal) {
        int[] faces = gender == 0 ? MALE_FACES : FEMALE_FACES;
        int[] hairs = gender == 0 ? MALE_HAIRS : FEMALE_HAIRS;
        int[] tops = gender == 0 ? MALE_TOPS : FEMALE_TOPS;
        int[] bottoms = gender == 0 ? MALE_BOTTOMS : FEMALE_BOTTOMS;
        int value = localOrdinal;
        int weapon = WEAPONS[value % WEAPONS.length];
        value /= WEAPONS.length;
        int shoes = SHOES[value % SHOES.length];
        value /= SHOES.length;
        int bottom = bottoms[value % bottoms.length];
        value /= bottoms.length;
        int top = tops[value % tops.length];
        value /= tops.length;
        int skin = SKINS[value % SKINS.length];
        value /= SKINS.length;
        int hair = hairs[value % hairs.length];
        value /= hairs.length;
        hair += HAIR_COLORS[value % HAIR_COLORS.length];
        value /= HAIR_COLORS.length;
        int face = faces[value % faces.length];
        return new MapleIslandCohortCharacterTemplate(
                ordinal, gender, skin, face, hair, top, bottom, shoes, weapon);
    }
}
