package server.life.simulation;

/** Most recent client-reported mob motion, converted to fixed-step physics units. */
public record MobMovementSnapshot(double x, double y, double velocityX, double velocityY,
                                  int footholdId, int stance, long capturedAtNanos) {
    public boolean isFresh(long nowNanos, long maximumAgeNanos) {
        long age = nowNanos - capturedAtNanos;
        return age >= 0L && age <= maximumAgeNanos;
    }
}
