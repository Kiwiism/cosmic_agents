package server.agents.capabilities.build.profiles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentSpBuildDefaultCatalogTest {
    @Test
    void everyAdvancedExplorerJobUsesTheSourceNamedDefault() {
        int[] jobs = {
                110, 111, 112, 120, 121, 122, 130, 131, 132,
                210, 211, 212, 220, 221, 222, 230, 231, 232,
                310, 311, 312, 320, 321, 322,
                410, 411, 412, 420, 421, 422,
                510, 511, 512, 520, 521, 522
        };
        AgentSpBuildProfileRepository profiles = AgentSpBuildProfileRepository.defaultRepository();
        for (int job : jobs) {
            String profileId = AgentSpBuildDefaultCatalog.profileIdFor(job);
            assertEquals(true, profileId.startsWith("mapleroyals-optimal-2026-"));
            assertEquals(job, profiles.find(profileId).orElseThrow().exactJobId());
        }
        assertNull(AgentSpBuildDefaultCatalog.profileIdFor(100));
        assertEquals("mapleroyals-optimal-2026-dark-knight-spear",
                AgentSpBuildDefaultCatalog.nextProfileId(
                        "mapleroyals-optimal-2026-dragon-knight-spear", 132));
    }
}
