package server.agents.integration.live;

import net.server.Server;
import server.agents.capabilities.movement.AgentMovementProfile;
import server.agents.capabilities.navigation.AgentNavigationGraphService;
import server.agents.integration.AgentMapGatewayRuntime;
import server.maps.MapleMap;

/** Starts shutdown while a deliberately uncached heavy navigation warmup is active. */
public final class AgentNavigationShutdownSmokeMain {
    private static final int TEST_MAP_ID = 104_040_000;
    private static final AgentMovementProfile UNCACHED_PROFILE = new AgentMovementProfile(137, 119);

    private AgentNavigationShutdownSmokeMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        Server.getInstance().init();
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(0, 1, TEST_MAP_ID);
        AgentNavigationGraphService.warmGraphAsync(map, UNCACHED_PROFILE);

        long deadline = System.nanoTime() + 5_000_000_000L;
        while (AgentNavigationGraphService.pendingWarmupCount() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        if (AgentNavigationGraphService.pendingWarmupCount() == 0) {
            throw new IllegalStateException("navigation warmup did not start");
        }
        System.out.println("[AGENT-NAV-SHUTDOWN] active warmup observed; shutting down now");
        Server.getInstance().shutdown(false).run();
    }
}
