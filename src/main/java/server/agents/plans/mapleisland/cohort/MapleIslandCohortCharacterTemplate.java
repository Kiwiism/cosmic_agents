package server.agents.plans.mapleisland.cohort;

/** Immutable v83-approved Beginner appearance and starting equipment. */
public record MapleIslandCohortCharacterTemplate(
        int ordinal,
        int gender,
        int skin,
        int face,
        int hair,
        int top,
        int bottom,
        int shoes,
        int weapon) {
}
