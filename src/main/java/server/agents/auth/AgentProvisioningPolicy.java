package server.agents.auth;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Restricts creation of new Agent backing accounts without affecting existing Agent spawn. */
public final class AgentProvisioningPolicy {
    private static final int DEFAULT_MINIMUM_GM_LEVEL = 6;
    private static final int DEFAULT_MAX_PER_WINDOW = 3;
    private static final long DEFAULT_WINDOW_MS = 10 * 60 * 1000L;
    private static final int DEFAULT_MAX_PER_CONTROLLER = 25;

    private final Clock clock;
    private final int minimumGmLevel;
    private final int maxPerWindow;
    private final long windowMs;
    private final int maxPerController;
    private final Map<Integer, ArrayDeque<Long>> attemptsByController = new ConcurrentHashMap<>();

    public AgentProvisioningPolicy() {
        this(
                Clock.systemUTC(),
                integerProperty("agents.provisioning.minimumGmLevel", DEFAULT_MINIMUM_GM_LEVEL),
                integerProperty("agents.provisioning.maxPerWindow", DEFAULT_MAX_PER_WINDOW),
                longProperty("agents.provisioning.windowMs", DEFAULT_WINDOW_MS),
                integerProperty("agents.provisioning.maxPerController", DEFAULT_MAX_PER_CONTROLLER));
    }

    AgentProvisioningPolicy(
            Clock clock,
            int minimumGmLevel,
            int maxPerWindow,
            long windowMs,
            int maxPerController) {
        this.clock = clock;
        this.minimumGmLevel = Math.max(0, minimumGmLevel);
        this.maxPerWindow = Math.max(1, maxPerWindow);
        this.windowMs = Math.max(1L, windowMs);
        this.maxPerController = Math.max(1, maxPerController);
    }

    public String validate(int controllerCharacterId, int gmLevel, int registeredAgentCount) {
        if (gmLevel < minimumGmLevel) {
            return "Creating Agent backing accounts requires GM level " + minimumGmLevel + ".";
        }
        if (registeredAgentCount >= maxPerController) {
            return "Agent backing-account quota reached (" + maxPerController + ").";
        }

        long now = clock.millis();
        ArrayDeque<Long> attempts = attemptsByController.computeIfAbsent(
                controllerCharacterId, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            while (!attempts.isEmpty() && now - attempts.peekFirst() >= windowMs) {
                attempts.removeFirst();
            }
            if (attempts.size() >= maxPerWindow) {
                return "Agent backing-account creation is rate limited. Try again later.";
            }
        }
        return null;
    }

    public void recordProvisioned(int controllerCharacterId) {
        ArrayDeque<Long> attempts = attemptsByController.computeIfAbsent(
                controllerCharacterId, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            attempts.addLast(clock.millis());
        }
    }

    private static int integerProperty(String name, int defaultValue) {
        return Integer.getInteger(name, defaultValue);
    }

    private static long longProperty(String name, long defaultValue) {
        return Long.getLong(name, defaultValue);
    }
}
