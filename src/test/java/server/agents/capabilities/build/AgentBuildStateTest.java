package server.agents.capabilities.build;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBuildStateTest {
    @Test
    void defaultsMatchLegacyBotEntryState() {
        AgentBuildState state = new AgentBuildState();

        assertNull(state.apBuild());
        assertFalse(state.hasApBuild());
        assertFalse(state.apPromptSent());
        assertNull(state.spVariant());
        assertFalse(state.hasSpVariant());
        assertFalse(state.spVariantPromptSent());
        assertEquals(0, state.jobPromptSent());
        assertEquals(-1, state.lastKnownLevel());
    }

    @Test
    void setApBuildStoresBuildAndClearsPromptFlag() {
        AgentBuildState state = new AgentBuildState();
        AgentBuildService.ApBuild build = new AgentBuildService.ApBuild(
                AgentBuildService.StatType.LUK,
                AgentBuildService.StatType.DEX,
                25);

        state.markApPromptSent();
        state.setApBuild(build);

        assertSame(build, state.apBuild());
        assertTrue(state.hasApBuild());
        assertFalse(state.apPromptSent());
    }

    @Test
    void clearApBuildPromptStateClearsBuildAndPromptFlag() {
        AgentBuildState state = new AgentBuildState();
        state.setApBuild(new AgentBuildService.ApBuild(
                AgentBuildService.StatType.INT,
                AgentBuildService.StatType.LUK,
                4));
        state.markApPromptSent();

        state.clearApBuildPromptState();

        assertNull(state.apBuild());
        assertFalse(state.hasApBuild());
        assertFalse(state.apPromptSent());
    }

    @Test
    void storesSpVariantPromptAndJobProgressionState() {
        AgentBuildState state = new AgentBuildState();

        state.setSpVariant("one-handed");
        state.markSpVariantPromptSent();
        state.setLastKnownLevel(70);
        state.setJobPromptSent(70);

        assertEquals("one-handed", state.spVariant());
        assertTrue(state.hasSpVariant());
        assertTrue(state.spVariantPromptSent());
        assertEquals(70, state.lastKnownLevel());
        assertEquals(70, state.jobPromptSent());
    }
}
