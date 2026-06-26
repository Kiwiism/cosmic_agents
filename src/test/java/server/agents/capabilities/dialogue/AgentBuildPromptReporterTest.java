package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import client.Job;
import org.junit.jupiter.api.Test;

class AgentBuildPromptReporterTest {
    @Test
    void returnsApPromptsForSupportedJobBranches() {
        assertEquals(
                "what AP build? type 'dexless'/'pure' or e.g. '25 dex' to set a dex target",
                AgentBuildPromptReporter.apPromptForJob(Job.WARRIOR));
        assertEquals(
                "what AP build? type 'lukless'/'pure' or e.g. '25 luk' to set a luk target",
                AgentBuildPromptReporter.apPromptForJob(Job.MAGICIAN));
        assertEquals(
                "what AP build? type 'strless'/'pure' or e.g. '25 str' to set a str target",
                AgentBuildPromptReporter.apPromptForJob(Job.BOWMAN));
        assertEquals(
                "what AP build? type 'dexless'/'pure' or e.g. '25 dex' to set a dex target",
                AgentBuildPromptReporter.apPromptForJob(Job.THIEF));
        assertNull(AgentBuildPromptReporter.apPromptForJob(Job.PIRATE));
        assertNull(AgentBuildPromptReporter.apPromptForJob(null));
    }

    @Test
    void returnsHeroSpVariantPrompt() {
        assertEquals(
                "hero build: '1h' (1h sword, Brandish first) or '2h' (interleave AC + Brandish for faster charges)?",
                AgentBuildPromptReporter.heroSpVariantPrompt());
    }

    @Test
    void returnsBeginnerJobPromptsByLevel() {
        assertNull(AgentBuildPromptReporter.beginnerJobPrompt(7));
        assertEquals(
                "i can become a mage already if u want, or wait til lv10 for other jobs",
                AgentBuildPromptReporter.beginnerJobPrompt(8));
        assertEquals(
                "hey i can change jobs now!! warrior, mage, bowman, thief, or pirate?",
                AgentBuildPromptReporter.beginnerJobPrompt(10));
    }

    @Test
    void returnsSecondJobPrompts() {
        assertEquals(
                "lv30! 2nd job time~ fighter, page, or spearman?",
                AgentBuildPromptReporter.secondJobPrompt(Job.WARRIOR));
        assertEquals(
                "lv30! pick 2nd job: f/p wizard, i/l wizard, or cleric?",
                AgentBuildPromptReporter.secondJobPrompt(Job.MAGICIAN));
        assertNull(AgentBuildPromptReporter.secondJobPrompt(Job.FIGHTER));
    }

    @Test
    void returnsThirdJobPrompts() {
        assertEquals(
                "lv70!! type 'chief bandit' or 'cb'",
                AgentBuildPromptReporter.thirdJobPrompt(Job.BANDIT));
        assertEquals(
                "lv70!! type 'dragon knight' or 'dk'",
                AgentBuildPromptReporter.thirdJobPrompt(Job.SPEARMAN));
        assertNull(AgentBuildPromptReporter.thirdJobPrompt(Job.CRUSADER));
    }

    @Test
    void returnsFourthJobPrompts() {
        assertEquals(
                "lv120!! type 'dark knight' or 'drk'",
                AgentBuildPromptReporter.fourthJobPrompt(Job.DRAGONKNIGHT));
        assertEquals(
                "lv120!! type 'shadower'",
                AgentBuildPromptReporter.fourthJobPrompt(Job.CHIEFBANDIT));
        assertNull(AgentBuildPromptReporter.fourthJobPrompt(Job.HERO));
    }
}
