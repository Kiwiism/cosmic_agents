package server.agents.capabilities.combat;

public final class AgentFallDamageCalculator {
    public static final float FALL_DIST_THRESHOLD_PX = 890.0f;   // below: 0 dmg, no packet
    public static final float FALL_DMG_SAT = 28.0f;              // asymptote of the knee component
    public static final float FALL_KNEE_SHARPNESS = 0.013f;      // 1/px, larger = sharper knee
    public static final float FALL_DMG_PER_PX_TAIL = 0.0024f;    // linear tail slope (dmg/px)

    private AgentFallDamageCalculator() {
    }

    public static int fallDamageFromDistance(float distancePx) {
        if (distancePx <= FALL_DIST_THRESHOLD_PX) {
            return 0;
        }
        double u = distancePx - FALL_DIST_THRESHOLD_PX;
        double dmg = FALL_DMG_SAT * (1.0 - Math.exp(-FALL_KNEE_SHARPNESS * u))
                + FALL_DMG_PER_PX_TAIL * u;
        return (int) Math.max(1, Math.round(dmg));
    }
}
