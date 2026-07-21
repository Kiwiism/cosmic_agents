package server.agents.catalog.decision;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import server.agents.catalog.CatalogLookupException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileAgentDecisionCatalogRepositoryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsVersionedImmutableSnapshotAndServesSharedQueries() throws Exception {
        writeFixture(false, false, 1);

        AgentDecisionCatalogRepository repository = FileAgentDecisionCatalogRepository.load(tempDir);
        AgentDecisionCatalogSnapshot snapshot = repository.snapshot();
        AgentTopologyQueryService topology = new AgentTopologyQueryService(repository);
        AgentCombatPolicyQueryService combat = new AgentCombatPolicyQueryService(repository, topology);

        assertEquals(1, snapshot.version().schemaVersion());
        assertEquals("2026-07-21T00:00:00Z", snapshot.version().generatedAt());
        assertEquals(1, snapshot.navigationByMapId().size());
        assertEquals(1, snapshot.combatByMapId().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.navigationByMapId().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.navigation(100).orElseThrow().footholdsById().clear());

        AgentTopologyQueryService.NavigationRecommendation navigation = topology.recommend(
                100, 20, 100, 220, 50).orElseThrow();
        assertEquals(1, navigation.source().componentId());
        assertEquals(2, navigation.target().componentId());
        assertFalse(navigation.sameComponent());
        assertEquals(1, navigation.transitionCandidates().size());

        AgentCombatPolicyQueryService.TargetRecommendation target = combat.recommendTarget(
                100, 20, 100, 200, 220, 50).orElseThrow();
        assertTrue(target.mobRecognized());
        assertTrue(target.targetOnRecommendedAnchor());
        assertEquals("100:component:2", target.anchorId());
        assertEquals(2, combat.partition(100, 2).orElseThrow().groups().size());
    }

    @Test
    void rejectsPartialCatalogsAtRuntimeBoundary() throws Exception {
        writeFixture(true, false, 1);

        CatalogLookupException failure = assertThrows(CatalogLookupException.class,
                () -> FileAgentDecisionCatalogRepository.load(tempDir));

        assertTrue(failure.getMessage().contains("Partial decision catalogs"));
    }

    @Test
    void rejectsCatalogTransitionThatClaimsExecutionAuthority() throws Exception {
        writeFixture(false, true, 1);

        CatalogLookupException failure = assertThrows(CatalogLookupException.class,
                () -> FileAgentDecisionCatalogRepository.load(tempDir));

        assertTrue(failure.getMessage().contains("authoritative transition hint"));
    }

    @Test
    void rejectsManifestCountMismatch() throws Exception {
        writeFixture(false, false, 2);

        CatalogLookupException failure = assertThrows(CatalogLookupException.class,
                () -> FileAgentDecisionCatalogRepository.load(tempDir));

        assertTrue(failure.getMessage().contains("count mismatch"));
    }

    @Test
    void shadowSamplingIsBoundedPerCapability() {
        AgentDecisionShadowSamplingState state = new AgentDecisionShadowSamplingState();

        assertTrue(state.allowNavigation(1_000L, 2_000L));
        assertFalse(state.allowNavigation(2_999L, 2_000L));
        assertTrue(state.allowNavigation(3_000L, 2_000L));
        assertTrue(state.allowCombat(1_500L, 2_000L));
        assertFalse(state.allowCombat(3_000L, 2_000L));
    }

    @Test
    void loadsCurrentGeneratedFullCatalogWhenAvailable() {
        Path generated = Path.of("tmp", "agent-llm-catalog");
        Assumptions.assumeTrue(Files.isRegularFile(
                generated.resolve("generated_agent_decision_catalog_manifest.json")));

        AgentDecisionCatalogSnapshot snapshot = FileAgentDecisionCatalogRepository
                .load(generated)
                .snapshot();

        assertFalse(snapshot.navigationByMapId().isEmpty());
        assertFalse(snapshot.combatByMapId().isEmpty());
        assertEquals(snapshot.version().counts().get("navigationMaps"),
                snapshot.navigationByMapId().size());
        assertEquals(snapshot.version().counts().get("combatMaps"),
                snapshot.combatByMapId().size());
    }

    private void writeFixture(boolean partial, boolean authoritativeTransition, int navigationCount)
            throws Exception {
        Files.writeString(tempDir.resolve("generated_agent_decision_catalog_manifest.json"), """
                {
                  "schemaVersion": 1,
                  "generatedAt": "2026-07-21T00:00:00Z",
                  "partialExport": %s,
                  "allRegions": false,
                  "regions": ["victoria"],
                  "counts": {"navigationMaps": %d, "combatMaps": 1}
                }
                """.formatted(partial, navigationCount));
        Files.writeString(tempDir.resolve("generated_navigation_topology_catalog.json"), """
                [{
                  "schemaVersion": 1,
                  "mapId": 100,
                  "mapName": "Fixture Map",
                  "footholds": [
                    {"footholdId": 1, "componentId": 1, "x1": 0, "y1": 100,
                     "x2": 100, "y2": 100, "wall": false},
                    {"footholdId": 2, "componentId": 2, "x1": 200, "y1": 50,
                     "x2": 300, "y2": 50, "wall": false}
                  ],
                  "components": [
                    {"componentId": 1, "bounds": {"minX": 0, "maxX": 100, "minY": 100, "maxY": 100},
                     "center": {"x": 50, "y": 100}, "safePoint": {"x": 50, "y": 100},
                     "footholdIds": [1]},
                    {"componentId": 2, "bounds": {"minX": 200, "maxX": 300, "minY": 50, "maxY": 50},
                     "center": {"x": 250, "y": 50}, "safePoint": null, "footholdIds": [2]}
                  ],
                  "climbables": [],
                  "transitionCandidates": [
                    {"type": "jump-candidate", "componentA": 1, "componentB": 2,
                     "executable": %s, "requiresRuntimePhysicsValidation": true}
                  ],
                  "policy": {"runtimeMustValidateMovement": true}
                }]
                """.formatted(authoritativeTransition));
        Files.writeString(tempDir.resolve("generated_combat_map_policy_catalog.json"), """
                [{
                  "schemaVersion": 1,
                  "mapId": 100,
                  "recommendedAgents": 1,
                  "maximumAgents": 2,
                  "anchors": [
                    {"anchorId": "100:component:1", "componentId": 1,
                     "center": {"x": 50, "y": 100}, "spawnCount": 1, "mobIds": [100]},
                    {"anchorId": "100:component:2", "componentId": 2,
                     "center": {"x": 250, "y": 50}, "spawnCount": 2, "mobIds": [200]}
                  ],
                  "partyPartitions": [
                    {"partySize": 1, "groups": [
                      {"slot": 1, "anchorIds": ["100:component:1", "100:component:2"]}
                    ]},
                    {"partySize": 2, "groups": [
                      {"slot": 1, "anchorIds": ["100:component:1"]},
                      {"slot": 2, "anchorIds": ["100:component:2"]}
                    ]}
                  ],
                  "policy": {"anchorReachabilityRequiresRuntimeValidation": true}
                }]
                """);
    }
}
