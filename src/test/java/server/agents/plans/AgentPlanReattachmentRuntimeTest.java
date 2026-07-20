package server.agents.plans;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.objectives.AgentObjectiveDefinition;
import server.agents.objectives.AgentObjectiveKernel;
import server.agents.objectives.AgentObjectiveSource;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerBuildBundleRepository;
import server.agents.progression.AgentCareerProgressionState;
import server.agents.progression.AgentFirstJobJourneyRuntime;
import server.agents.progression.AgentVictoriaTrainingObjectiveRuntime;
import server.agents.progression.AgentVictoriaTrainingState;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPlanReattachmentRuntimeTest {
    @Test
    void resolvesEveryDurableMapleIslandPlanIdentity() {
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.AMHERST,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-amherst-subphase"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.SOUTHPERRY,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-southperry-mvp"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.FULL_MAPLE_ISLAND,
                AgentPlanReattachmentRuntime.resumeKind("plan:maple-island-full-mvp"));
        assertEquals(AgentPlanReattachmentRuntime.ResumeKind.UNSUPPORTED,
                AgentPlanReattachmentRuntime.resumeKind("career:level15:5"));
    }

    @Test
    void registersMapleIslandCareerAndVictoriaTrainingHandlers() {
        assertTrue(AgentPlanReattachmentRuntime.handlers().handlerFor("maple-island-progression").isPresent());
        assertTrue(AgentPlanReattachmentRuntime.handlers()
                .handlerFor(AgentFirstJobJourneyRuntime.OBJECTIVE_TYPE).isPresent());
        assertTrue(AgentPlanReattachmentRuntime.handlers()
                .handlerFor(AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE).isPresent());
        assertTrue(AgentPlanReattachmentRuntime.handlers()
                .handlerFor("maintenance.resupply").isPresent());
    }

    @Test
    void rebuildsTransientVictoriaTrainingStateWithoutReplacingDurableIntent() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(71);
        when(agent.getName()).thenReturn("RelogTrainer");
        when(agent.getLevel()).thenReturn(20);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "victoria:training:71:25", AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE,
                80, Long.MAX_VALUE, 5, AgentObjectiveSource.PROGRESSION_POLICY,
                "victoria-v1", "71:level-25:mode-grind");
        AgentObjectiveKernel.start(entry, objective, 10L);

        assertTrue(AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, 20L));

        AgentVictoriaTrainingState state = entry.capabilityStates().require(
                AgentVictoriaTrainingState.STATE_KEY);
        assertTrue(state.active());
        assertEquals(25, state.targetLevel());
        assertFalse(state.questsEnabled());
        assertSame(objective, AgentObjectiveKernel.active(entry));
    }

    @Test
    void reconcilesTerminalCareerCheckpointInsteadOfRestartingIt() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(72);
        when(agent.getName()).thenReturn("CareerDone");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("warrior-standard-v1").orElseThrow();
        entry.capabilityStates().require(AgentCareerProgressionState.STATE_KEY).reset(
                bundle, AgentCareerProgressionState.RunMode.LEVEL15, "lv10",
                AgentCareerProgressionState.Stage.COMPLETE, 0L);
        AgentObjectiveDefinition objective = new AgentObjectiveDefinition(
                "career:level15:72", AgentFirstJobJourneyRuntime.OBJECTIVE_TYPE,
                100, Long.MAX_VALUE, 3, AgentObjectiveSource.PROGRESSION_POLICY,
                "victoria-level15-v1", "warrior-standard-v1:72");
        AgentObjectiveKernel.start(entry, objective, 10L);

        assertFalse(AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, 20L));
        assertNull(AgentObjectiveKernel.active(entry));
    }

    @Test
    void relogDuringShopInterruptionRestoresTheExactSuspendedObjective() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(73);
        when(agent.getName()).thenReturn("InterruptedShopper");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentObjectiveDefinition training = new AgentObjectiveDefinition(
                "victoria:training:73:30", AgentVictoriaTrainingObjectiveRuntime.OBJECTIVE_TYPE,
                80, Long.MAX_VALUE, 5, AgentObjectiveSource.PROGRESSION_POLICY,
                "victoria-v1", "73:level-30:mode-grind");
        AgentObjectiveDefinition maintenance = new AgentObjectiveDefinition(
                "maintenance:resupply:73", "maintenance.resupply", 1_000,
                10_000L, 2, AgentObjectiveSource.RECOVERY_POLICY,
                "supply-procurement-v2", training.objectiveId());
        AgentObjectiveKernel.start(entry, training, 10L);
        AgentObjectiveKernel.suspendFor(entry, maintenance, "HP potions empty", 20L);

        assertFalse(AgentPlanReattachmentRuntime.reattachIfNeeded(entry, agent, 30L));
        assertEquals(training, AgentObjectiveKernel.active(entry));
        assertTrue(entry.capabilityStates()
                .require(server.agents.objectives.AgentObjectiveState.STATE_KEY)
                .suspendedSnapshot().isEmpty());
    }
}
