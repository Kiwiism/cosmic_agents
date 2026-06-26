package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBuildDialogueClassifierTest {
    @Test
    void shouldClassifyJobSelectionCandidates() {
        assertTrue(AgentBuildDialogueClassifier.isJobSelectionCandidate("chief bandit"));
        assertTrue(AgentBuildDialogueClassifier.isJobSelectionCandidate("paladin"));
        assertTrue(AgentBuildDialogueClassifier.isJobSelectionCandidate("fp arch"));
        assertFalse(AgentBuildDialogueClassifier.isJobSelectionCandidate("lets go grind"));
    }

    @Test
    void shouldClassifySpVariantChoices() {
        assertTrue(AgentBuildDialogueClassifier.isOneHandedSpVariant("1h"));
        assertTrue(AgentBuildDialogueClassifier.isTwoHandedSpVariant("2h"));
        assertFalse(AgentBuildDialogueClassifier.isOneHandedSpVariant("one handed"));
        assertFalse(AgentBuildDialogueClassifier.isTwoHandedSpVariant("two handed"));
    }

    @Test
    void shouldClassifyApBuildRequestsAndStatProfiles() {
        assertTrue(AgentBuildDialogueClassifier.isApChangeBuildCommand("change build"));
        assertTrue(AgentBuildDialogueClassifier.isApChangeBuildCommand("switch ur build"));

        assertTrue(AgentBuildDialogueClassifier.isPureStrBuildCommand("pure str"));
        assertTrue(AgentBuildDialogueClassifier.isPureStrBuildCommand("dexless"));
        assertTrue(AgentBuildDialogueClassifier.isPureStrBuildCommand("pure"));

        assertTrue(AgentBuildDialogueClassifier.isDexlessBuildCommand("pure luk"));
        assertTrue(AgentBuildDialogueClassifier.isDexlessBuildCommand("dexless"));
        assertTrue(AgentBuildDialogueClassifier.isDexlessBuildCommand("pure"));

        assertTrue(AgentBuildDialogueClassifier.isLuklessBuildCommand("pure int"));
        assertTrue(AgentBuildDialogueClassifier.isLuklessBuildCommand("lukless"));
        assertTrue(AgentBuildDialogueClassifier.isLuklessBuildCommand("pure"));

        assertTrue(AgentBuildDialogueClassifier.isStrlessBuildCommand("pure dex"));
        assertTrue(AgentBuildDialogueClassifier.isStrlessBuildCommand("strless"));
        assertTrue(AgentBuildDialogueClassifier.isStrlessBuildCommand("pure"));
    }

    @Test
    void shouldParseFixedStatTargets() {
        assertEquals(40, AgentBuildDialogueClassifier.matchFixedDexTarget("40 dex"));
        assertEquals(23, AgentBuildDialogueClassifier.matchFixedLukTarget("23 luk"));
        assertEquals(12, AgentBuildDialogueClassifier.matchFixedStrTarget("12 str"));

        assertNull(AgentBuildDialogueClassifier.matchFixedDexTarget("dexless"));
        assertNull(AgentBuildDialogueClassifier.matchFixedLukTarget("lukless"));
        assertNull(AgentBuildDialogueClassifier.matchFixedStrTarget("strless"));
    }

    @Test
    void shouldReturnSkillTreeChoiceIdsInMessageOrder() {
        assertEquals(List.of(110, 112, 2110), AgentBuildDialogueClassifier.skillTreeChoiceIds("pick 110 then 112 or 2110"));
        assertEquals(List.of(), AgentBuildDialogueClassifier.skillTreeChoiceIds("pick warrior"));
    }
}
