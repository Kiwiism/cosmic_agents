package server.life.simulation;

/** Immutable diagnostic view of an active monster simulation. */
public record MobPhysicsState(int monsterOid, int agentId, MobMotionState motion,
                              double x, double y, double velocityX, double velocityY,
                              int footholdId, long generation) {
}
