package server.agents.progression;

import client.Character;
import client.Job;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.inventory.demand.AgentQuestItemDemandIndex;
import server.agents.capabilities.inventory.demand.AgentQuestItemDemandIndexRepository;
import server.agents.runtime.AgentRuntimeEntry;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentVictoriaAdaptiveQuestCatalogTest {
    @Test
    void loadsVersionedIndexWithScoreEvidence() {
        AgentVictoriaQuestHuntIndex index =
                AgentVictoriaQuestHuntIndexRepository.defaultRepository().index();

        assertEquals(1, index.schemaVersion());
        assertFalse(index.revision().isBlank());
        assertEquals(93, index.entries().size());
        assertTrue(index.entries().stream()
                .flatMap(entry -> entry.objectives().stream())
                .flatMap(objective -> objective.candidates().stream())
                .allMatch(candidate -> candidate.mapId() > 0
                        && candidate.maximumAgents() >= candidate.recommendedAgents()
                        && !candidate.targetMobIds().isEmpty()
                        && candidate.targetSpawnEntries() > 0
                        && candidate.totalSpawnEntries() >= candidate.targetSpawnEntries()
                        && candidate.targetComponentCount() > 0
                        && candidate.scoreEvidence() != null));
    }

    @Test
    void policyKeepsMvpAndNonMvpOnPreferredAdaptiveDuringShadowRollout() {
        AgentVictoriaQuestHuntPolicy policy =
                AgentVictoriaQuestHuntPolicyRepository.defaultRepository().policy();

        assertTrue(policy.shadowModeEnabled());
        assertTrue(policy.adaptiveFallbackEnabled());
        assertEquals(AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                policy.modeFor(2000, true));
        assertEquals(AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                policy.modeFor(2000, false));
        assertNotNull(policy.questPolicies());
    }

    @Test
    void generatedFactsAndIndexShareOneRevision() throws IOException {
        String[] resources = {
                "/agents/catalogs/adaptive/victoria-quest-facts.json",
                "/agents/catalogs/adaptive/victoria-mob-drop-facts.json",
                "/agents/catalogs/adaptive/victoria-map-facts.json",
                "/agents/catalogs/adaptive/victoria-quest-hunt-index.json",
                "/agents/catalogs/adaptive/victoria-quest-item-demand-index.json"
        };
        ObjectMapper mapper = new ObjectMapper();
        String revision = null;
        for (String resource : resources) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, resource);
                JsonNode root = mapper.readTree(input);
                assertEquals(1, root.path("schemaVersion").asInt(), resource);
                assertTrue(root.path("entries").size() > 0, resource);
                if (revision == null) {
                    revision = root.path("revision").asText();
                } else {
                    assertEquals(revision, root.path("revision").asText(), resource);
                }
            }
        }
        assertNotNull(revision);
        assertEquals(64, revision.length());
    }

    @Test
    void questOverridesCanPromoteOrPinIndividualQuests() {
        AgentVictoriaQuestHuntPolicy policy = new AgentVictoriaQuestHuntPolicy(
                1, "test", true, true,
                AgentQuestHuntSelectionMode.FIXED,
                AgentQuestHuntSelectionMode.ADAPTIVE,
                List.of(new AgentVictoriaQuestHuntPolicy.QuestPolicy(
                        28270, AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE)));

        assertEquals(AgentQuestHuntSelectionMode.FIXED, policy.modeFor(1, true));
        assertEquals(AgentQuestHuntSelectionMode.ADAPTIVE, policy.modeFor(1, false));
        assertEquals(AgentQuestHuntSelectionMode.PREFERRED_ADAPTIVE,
                policy.modeFor(28270, true));
    }

    @Test
    void generatedDemandMatchesFixedLevel15QuestPackRequirements() {
        Map<String, Integer> expected = Map.ofEntries(
                Map.entry("28281:4000005", 20),
                Map.entry("28273:4000004", 20),
                Map.entry("2089:4000003", 30),
                Map.entry("2089:4000004", 30),
                Map.entry("2089:4000010", 10),
                Map.entry("2088:4000001", 40),
                Map.entry("2088:4000011", 10),
                Map.entry("28267:4000012", 20),
                Map.entry("2091:4000003", 40),
                Map.entry("2091:4000004", 40),
                Map.entry("28279:4000002", 20));
        Map<String, Integer> generated = new HashMap<>();
        for (AgentQuestItemDemandIndex.Entry item
                : AgentQuestItemDemandIndexRepository.defaultRepository().index().entries()) {
            for (AgentQuestItemDemandIndex.QuestDemand quest : item.quests()) {
                String key = quest.questId() + ":" + item.itemId();
                if (expected.containsKey(key)) {
                    generated.put(key, quest.requiredCount());
                }
            }
        }

        assertEquals(expected, generated);
    }

    @Test
    void combinedFallbackKeepsAllKerningMaterialObjectivesOnOneMap() {
        AgentVictoriaQuestHuntIndexRepository index =
                AgentVictoriaQuestHuntIndexRepository.defaultRepository();
        List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> objectives =
                new ArrayList<>();
        objectives.addAll(index.findObjectivesForTarget(Set.of(2091), 4000003));
        objectives.addAll(index.findObjectivesForTarget(Set.of(2091), 4000004));

        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(93);
        when(agent.getName()).thenReturn("AdaptiveKerning");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(14);
        when(agent.getMapId()).thenReturn(103000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentAdaptiveQuestHuntSelector.Selection selection =
                AgentAdaptiveQuestHuntSelector.defaultSelector()
                        .selectCombined(entry, agent, "kerning-material-test",
                                999999999, List.of(130100, 210100), objectives, 100L)
                        .orElseThrow();

        assertEquals(100050000, selection.map().mapId());
        assertEquals(Set.of(130100, 210100),
                Set.copyOf(selection.map().targetMobIds()));
        assertEquals(AgentAdaptiveQuestHuntSelector.Source.ADAPTIVE_FALLBACK,
                selection.source());
    }

    @Test
    void combinedFallbackDecomposesOnlyWhenNoMapCoversEveryObjective() {
        AgentVictoriaQuestHuntIndexRepository index =
                AgentVictoriaQuestHuntIndexRepository.defaultRepository();
        List<AgentVictoriaQuestHuntIndexRepository.ObjectiveReference> objectives =
                new ArrayList<>();
        objectives.addAll(index.findObjectivesForTarget(Set.of(28272), 1120100));
        objectives.addAll(index.findObjectivesForTarget(Set.of(28281), 4000005));

        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(94);
        when(agent.getName()).thenReturn("AdaptiveSplit");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(14);
        when(agent.getMapId()).thenReturn(103000000);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentAdaptiveQuestHuntSelector.Selection selection =
                AgentAdaptiveQuestHuntSelector.defaultSelector()
                        .selectCombined(entry, agent, "decomposed-test",
                                999999999, List.of(1120100), objectives, 100L)
                        .orElseThrow();

        assertEquals(AgentAdaptiveQuestHuntSelector.Source.ADAPTIVE_DECOMPOSED_FALLBACK,
                selection.source());
    }
}
