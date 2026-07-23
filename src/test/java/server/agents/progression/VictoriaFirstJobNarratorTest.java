package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VictoriaFirstJobNarratorTest {
    @Test
    void describesThiefJourneyMilestones() {
        AgentCareerBuildBundle bundle = AgentCareerBuildBundleRepository.defaultRepository()
                .find("thief-dagger-standard-v1").orElseThrow();

        assertEquals("I'm going to walk across Lith Harbor and complete Biggs' quest with Olaf.",
                VictoriaFirstJobNarrator.message(
                        AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF, bundle));
        assertEquals("I'm going to tell Olaf I want to become a thief.",
                VictoriaFirstJobNarrator.message(
                        AgentCareerProgressionState.Stage.START_CAREER_PATH, bundle));
        assertEquals("I'm going to ask Phil for a ride to Kerning City.",
                VictoriaFirstJobNarrator.message(
                        AgentCareerProgressionState.Stage.TAKE_TAXI, bundle));
    }

    @Test
    void eachStageCanOnlyBeAnnouncedOncePerRun() {
        AgentCareerProgressionState state = new AgentCareerProgressionState();

        assertEquals(true, state.markStageAnnounced(
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF));
        assertEquals(false, state.markStageAnnounced(
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF));
        assertEquals(true, state.markStageAnnounced(
                AgentCareerProgressionState.Stage.COMPLETE_OLAF_LESSON));
        assertEquals(false, state.markStageAnnounced(
                AgentCareerProgressionState.Stage.COMPLETE_BIGGS_AT_OLAF));
    }
}
