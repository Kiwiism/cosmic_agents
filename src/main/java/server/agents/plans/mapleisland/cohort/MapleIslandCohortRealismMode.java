package server.agents.plans.mapleisland.cohort;

import java.util.Locale;

/** Operator-selectable presentation preset for one Maple Island cohort run. */
public enum MapleIslandCohortRealismMode {
    OFF,
    LIGHT,
    FULL;

    public static MapleIslandCohortRealismMode parse(String value) {
        if (value == null || value.isBlank()) {
            return LIGHT;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("realism mode must be off, light, or full", failure);
        }
    }
}
