package server.life.autonomy;

/** Whether a connected session can run the native v83 monster controller. */
public enum BossClientSimulationCapability {
    NATIVE_MOB_SIMULATION,
    RENDER_ONLY,
    HEADLESS
}
