package server.agents.field;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldCapacityEstimatorTest {
    @Test
    void henesysGeometryProducesFourToSevenRecommendedAndNineMaximum() {
        List<AgentFieldCapacityEstimator.SpawnEvidence> spawns = new ArrayList<>();
        addPlatform(spawns, 1, 10, 178, 1_063, -100);
        addPlatform(spawns, 17, 11, -10, 1_434, 180);
        addPlatform(spawns, 5, 11, 196, 1_068, -380);
        addPlatform(spawns, 9, 7, 304, 1_009, -600);

        var capacity = AgentFieldCapacityEstimator.estimate(
                new AgentFieldCapacityEstimator.MapEvidence(
                        104040000, "Henesys Hunting Ground I", 39, 8, "high", spawns), null);

        assertEquals(4, capacity.recommendedMinimum());
        assertEquals(7, capacity.recommendedMaximum());
        assertEquals(9, capacity.maximumAgents());
        assertEquals(List.of(4, 7, 9, 7, 4), capacity.activeCounts());
        assertEquals(List.of(5, 4), capacity.partySizes());
        assertEquals(List.of(2, 2, 2, 3), capacity.platforms().stream()
                .map(AgentFieldCapacityCatalog.PlatformCapacity::effectiveCapacity).toList());
    }

    @Test
    void highComplexitySparseFragmentationReducesOtherwiseInflatedCapacity() {
        List<AgentFieldCapacityEstimator.SpawnEvidence> spawns = new ArrayList<>();
        for (int component = 1; component <= 12; component++) {
            spawns.add(new AgentFieldCapacityEstimator.SpawnEvidence(
                    component, component * 100, component * 120));
        }

        var capacity = AgentFieldCapacityEstimator.estimate(
                new AgentFieldCapacityEstimator.MapEvidence(
                        101020000, "Fragmented", 12, 8, "high", spawns), null);

        assertTrue(capacity.accessPenalty() > 0);
        assertTrue(capacity.maximumAgents() < capacity.spawnBudget());
        assertTrue(capacity.adjustments().stream().anyMatch(reason -> reason.contains("fragmented")));
    }

    @Test
    void largeSpawnFieldCanExceedTwelveWithLegalBalancedParties() {
        List<AgentFieldCapacityEstimator.SpawnEvidence> spawns = new ArrayList<>();
        addPlatform(spawns, 1, 64, 0, 6_400, 0);

        var capacity = AgentFieldCapacityEstimator.estimate(
                new AgentFieldCapacityEstimator.MapEvidence(
                        103000101, "Subway", 64, 0, "low", spawns), null);

        assertEquals(13, capacity.maximumAgents());
        assertTrue(capacity.activeCounts().stream().allMatch(count -> count >= 6));
        assertEquals(List.of(5, 4, 4), capacity.partySizes());
        assertTrue(capacity.partySizes().stream().allMatch(size -> size <= 6));
    }

    private static void addPlatform(
            List<AgentFieldCapacityEstimator.SpawnEvidence> target,
            int componentId,
            int count,
            int minX,
            int maxX,
            int y) {
        for (int index = 0; index < count; index++) {
            int x = count == 1 ? minX : minX + (maxX - minX) * index / (count - 1);
            target.add(new AgentFieldCapacityEstimator.SpawnEvidence(componentId, x, y));
        }
    }
}
