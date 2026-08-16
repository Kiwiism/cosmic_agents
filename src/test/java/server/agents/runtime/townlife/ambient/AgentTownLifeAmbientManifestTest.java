package server.agents.runtime.townlife.ambient;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTownLifeAmbientManifestTest {
    @Test
    void manifestCoversSevenTownsAndBothBaselineChairs() {
        AgentTownLifeAmbientManifest manifest =
                AgentTownLifeAmbientManifestRepository.defaultManifest();

        assertEquals(7, manifest.towns().size());
        assertEquals(50, manifest.targetActivePercent());
        assertEquals(java.util.List.of(3010000, 3010001), manifest.chairItemIds());
        assertTrue(manifest.towns().stream().allMatch(town -> town.minActive() <= town.maxActive()));
    }

    @Test
    void allocatorHonorsGlobalTargetTownMinimumsAndHardBounds() {
        AgentTownLifeAmbientManifest manifest =
                AgentTownLifeAmbientManifestRepository.defaultManifest();

        Map<Integer, Integer> active = AgentTownLifeAmbientAllocator.allocate(
                14, manifest.towns());

        assertEquals(14, active.values().stream().mapToInt(Integer::intValue).sum());
        for (AgentTownLifeAmbientManifest.Town town : manifest.towns()) {
            assertTrue(active.get(town.mapId()) >= town.minActive());
            assertTrue(active.get(town.mapId()) <= town.maxActive());
        }
    }

    @Test
    void sevenTownSoakAllocationRemainsBoundedAcrossPopulationChanges() {
        AgentTownLifeAmbientManifest manifest =
                AgentTownLifeAmbientManifestRepository.defaultManifest();
        int capacity = manifest.towns().stream()
                .mapToInt(AgentTownLifeAmbientManifest.Town::maxActive).sum();

        for (int requested = 0; requested <= capacity + 10; requested++) {
            Map<Integer, Integer> allocation = AgentTownLifeAmbientAllocator.allocate(
                    requested, manifest.towns());
            assertEquals(Math.min(requested, capacity),
                    allocation.values().stream().mapToInt(Integer::intValue).sum());
            for (AgentTownLifeAmbientManifest.Town town : manifest.towns()) {
                assertTrue(allocation.get(town.mapId()) <= town.maxActive());
            }
        }
    }

    @Test
    void standbyRosterAllocationNeverDropsLeasedAgentsAtActiveCapacity() {
        AgentTownLifeAmbientManifest manifest =
                AgentTownLifeAmbientManifestRepository.defaultManifest();

        Map<Integer, Integer> roster = AgentTownLifeAmbientAllocator.allocateRoster(
                100, manifest.towns());

        assertEquals(100, roster.values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(roster.values().stream().allMatch(count -> count > 0));
    }
}
