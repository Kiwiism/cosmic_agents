package server.life.simulation;

/** Explicit authority for monster movement and controller changes. */
public enum MobControlAuthority {
    NONE,
    CLIENT,
    AGENT_PHYSICS
}
