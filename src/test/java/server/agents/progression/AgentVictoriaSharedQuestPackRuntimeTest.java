package server.agents.progression;

import client.Character;
import client.Job;
import org.junit.jupiter.api.Test;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentVictoriaSharedQuestPackRuntimeTest {
    @Test
    void completedCombinedHuntSpeciesBecomeSpawnPressureCandidates() {
        AgentVictoriaSharedQuestPackCatalog.Step hunt =
                AgentVictoriaSharedQuestPackCatalog.require("nautilus-pre15").steps().stream()
                        .filter(step -> step.mapId() == 100030000
                                && "HUNT".equals(step.type()))
                        .findFirst()
                        .orElseThrow();

        assertEquals(Set.of(1210102, 1210100, 210100),
                AgentVictoriaSharedQuestPackRuntime.spawnPressureCandidates(
                        hunt, Set.of(1210101)));
    }

    @Test
    void fixedHuntMapsCoverGreenCapsAndKerningMaterials() {
        AgentVictoriaSharedQuestPackCatalog.Pack henesys =
                AgentVictoriaSharedQuestPackCatalog.require("henesys-pre15");
        AgentVictoriaSharedQuestPackCatalog.Step greenCaps = henesys.steps().stream()
                .filter(step -> step.conditions().stream()
                        .anyMatch(condition -> condition.targetId() == 4000012))
                .findFirst()
                .orElseThrow();
        assertEquals(100000002, greenCaps.mapId());
        assertEquals(List.of(1110100), greenCaps.preferredMobIds());
        assertEquals(List.of(1210102), greenCaps.incidentalMobIds());

        AgentVictoriaSharedQuestPackCatalog.Pack kerning =
                AgentVictoriaSharedQuestPackCatalog.require("kerning-pre15");
        AgentVictoriaSharedQuestPackCatalog.Step materials = kerning.steps().stream()
                .filter(step -> step.conditions().stream()
                        .anyMatch(condition -> condition.targetId() == 4000003))
                .findFirst()
                .orElseThrow();
        assertEquals(100050000, materials.mapId());
        assertTrue(materials.conditions().stream()
                .anyMatch(condition -> condition.targetId() == 4000004));
        assertEquals(List.of(130100, 210100), materials.preferredMobIds());
    }

    @Test
    void kerningCabReceivesHenesysSelectionAtItsDestinationPrompt() {
        AgentVictoriaSharedQuestPackCatalog.Pack pack =
                AgentVictoriaSharedQuestPackCatalog.require("kerning-pre15");
        int taxiStep = 0;
        while (taxiStep < pack.steps().size()
                && !("TAXI".equals(pack.steps().get(taxiStep).type())
                && pack.steps().get(taxiStep).destinationMapId() == 100000000)) {
            taxiStep++;
        }

        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(77);
        when(agent.getName()).thenReturn("KerningTaxi");
        when(agent.getJob()).thenReturn(Job.THIEF);
        when(agent.getLevel()).thenReturn(15);
        when(agent.getMapId()).thenReturn(103000000);
        when(agent.getPosition()).thenReturn(new Point(10, 0));

        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerProgressionState state = entry.capabilityStates()
                .require(AgentCareerProgressionState.STATE_KEY);
        state.questPackIndex(taxiStep);

        PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        when(gateway.grounded(agent)).thenReturn(true);
        when(gateway.npcPosition(agent, 1052016)).thenReturn(new Point(10, 0));

        assertEquals(AgentVictoriaSharedQuestPackRuntime.Result.RUNNING,
                AgentVictoriaSharedQuestPackRuntime.tick(
                        entry, agent, state, "kerning-pre15", 100L, gateway));

        verify(gateway).runNpcScript(agent, 1052016, 0, 2, 0);
    }
}
