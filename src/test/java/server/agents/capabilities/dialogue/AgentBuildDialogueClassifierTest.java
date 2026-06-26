package server.agents.capabilities.dialogue;

import client.Job;
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
    void shouldResolveFirstJobAdvancementChoicesWithLevelGates() {
        assertEquals(Job.MAGICIAN, AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 8, "mage"));
        assertNull(AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 9, "warrior"));
        assertEquals(Job.WARRIOR, AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 10, "warrior"));
        assertEquals(Job.BOWMAN, AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 10, "crossbow"));
        assertEquals(Job.THIEF, AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 10, "sin"));
        assertEquals(Job.PIRATE, AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 10, "gunslinger"));
        assertNull(AgentBuildDialogueClassifier.resolveJobChange(Job.BEGINNER, 10, "lets go grind"));
    }

    @Test
    void shouldResolveSecondJobAdvancementChoicesWithLevelGates() {
        assertNull(AgentBuildDialogueClassifier.resolveJobChange(Job.WARRIOR, 29, "fighter"));
        assertEquals(Job.FIGHTER, AgentBuildDialogueClassifier.resolveJobChange(Job.WARRIOR, 30, "fighter"));
        assertEquals(Job.PAGE, AgentBuildDialogueClassifier.resolveJobChange(Job.WARRIOR, 30, "page"));
        assertEquals(Job.SPEARMAN, AgentBuildDialogueClassifier.resolveJobChange(Job.WARRIOR, 30, "spear"));

        assertEquals(Job.FP_WIZARD, AgentBuildDialogueClassifier.resolveJobChange(Job.MAGICIAN, 30, "fire"));
        assertEquals(Job.IL_WIZARD, AgentBuildDialogueClassifier.resolveJobChange(Job.MAGICIAN, 30, "ice"));
        assertEquals(Job.CLERIC, AgentBuildDialogueClassifier.resolveJobChange(Job.MAGICIAN, 30, "healer"));
        assertEquals(Job.HUNTER, AgentBuildDialogueClassifier.resolveJobChange(Job.BOWMAN, 30, "bow"));
        assertEquals(Job.CROSSBOWMAN, AgentBuildDialogueClassifier.resolveJobChange(Job.BOWMAN, 30, "xbow"));
        assertEquals(Job.ASSASSIN, AgentBuildDialogueClassifier.resolveJobChange(Job.THIEF, 30, "assassin"));
        assertEquals(Job.BANDIT, AgentBuildDialogueClassifier.resolveJobChange(Job.THIEF, 30, "dit"));
        assertEquals(Job.BRAWLER, AgentBuildDialogueClassifier.resolveJobChange(Job.PIRATE, 30, "knuckle"));
        assertEquals(Job.GUNSLINGER, AgentBuildDialogueClassifier.resolveJobChange(Job.PIRATE, 30, "gun"));
    }

    @Test
    void shouldResolveThirdAndFourthJobAdvancementChoicesWithLevelGates() {
        assertNull(AgentBuildDialogueClassifier.resolveJobChange(Job.FIGHTER, 69, "crusader"));
        assertEquals(Job.CRUSADER, AgentBuildDialogueClassifier.resolveJobChange(Job.FIGHTER, 70, "crusader"));
        assertEquals(Job.WHITEKNIGHT, AgentBuildDialogueClassifier.resolveJobChange(Job.PAGE, 70, "white knight"));
        assertEquals(Job.DRAGONKNIGHT, AgentBuildDialogueClassifier.resolveJobChange(Job.SPEARMAN, 70, "dk"));
        assertEquals(Job.FP_MAGE, AgentBuildDialogueClassifier.resolveJobChange(Job.FP_WIZARD, 70, "fp mage"));
        assertEquals(Job.IL_MAGE, AgentBuildDialogueClassifier.resolveJobChange(Job.IL_WIZARD, 70, "il"));
        assertEquals(Job.PRIEST, AgentBuildDialogueClassifier.resolveJobChange(Job.CLERIC, 70, "priest"));
        assertEquals(Job.RANGER, AgentBuildDialogueClassifier.resolveJobChange(Job.HUNTER, 70, "ranger"));
        assertEquals(Job.SNIPER, AgentBuildDialogueClassifier.resolveJobChange(Job.CROSSBOWMAN, 70, "sniper"));
        assertEquals(Job.HERMIT, AgentBuildDialogueClassifier.resolveJobChange(Job.ASSASSIN, 70, "hermit"));
        assertEquals(Job.CHIEFBANDIT, AgentBuildDialogueClassifier.resolveJobChange(Job.BANDIT, 70, "chief bandit"));
        assertEquals(Job.MARAUDER, AgentBuildDialogueClassifier.resolveJobChange(Job.BRAWLER, 70, "marauder"));
        assertEquals(Job.OUTLAW, AgentBuildDialogueClassifier.resolveJobChange(Job.GUNSLINGER, 70, "outlaw"));

        assertNull(AgentBuildDialogueClassifier.resolveJobChange(Job.CRUSADER, 119, "hero"));
        assertEquals(Job.HERO, AgentBuildDialogueClassifier.resolveJobChange(Job.CRUSADER, 120, "hero"));
        assertEquals(Job.PALADIN, AgentBuildDialogueClassifier.resolveJobChange(Job.WHITEKNIGHT, 120, "paladin"));
        assertEquals(Job.DARKKNIGHT, AgentBuildDialogueClassifier.resolveJobChange(Job.DRAGONKNIGHT, 120, "dark knight"));
        assertEquals(Job.FP_ARCHMAGE, AgentBuildDialogueClassifier.resolveJobChange(Job.FP_MAGE, 120, "fp arch"));
        assertEquals(Job.IL_ARCHMAGE, AgentBuildDialogueClassifier.resolveJobChange(Job.IL_MAGE, 120, "il arch"));
        assertEquals(Job.BISHOP, AgentBuildDialogueClassifier.resolveJobChange(Job.PRIEST, 120, "bishop"));
        assertEquals(Job.BOWMASTER, AgentBuildDialogueClassifier.resolveJobChange(Job.RANGER, 120, "bm"));
        assertEquals(Job.MARKSMAN, AgentBuildDialogueClassifier.resolveJobChange(Job.SNIPER, 120, "mm"));
        assertEquals(Job.NIGHTLORD, AgentBuildDialogueClassifier.resolveJobChange(Job.HERMIT, 120, "nl"));
        assertEquals(Job.SHADOWER, AgentBuildDialogueClassifier.resolveJobChange(Job.CHIEFBANDIT, 120, "shadower"));
        assertEquals(Job.BUCCANEER, AgentBuildDialogueClassifier.resolveJobChange(Job.MARAUDER, 120, "buccaneer"));
        assertEquals(Job.CORSAIR, AgentBuildDialogueClassifier.resolveJobChange(Job.OUTLAW, 120, "corsair"));
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

    @Test
    void shouldResolveSkillTreeChoiceFromIdsAndLabels() {
        List<Integer> skillTrees = List.of(110, 111, 112);

        assertEquals(111, AgentBuildDialogueClassifier.resolveSkillTreeChoice("show me 111", skillTrees));
        assertEquals(110, AgentBuildDialogueClassifier.resolveSkillTreeChoice("fighter tree", skillTrees));
        assertEquals(111, AgentBuildDialogueClassifier.resolveSkillTreeChoice("crusader (111)", skillTrees));
        assertNull(AgentBuildDialogueClassifier.resolveSkillTreeChoice("pirate", skillTrees));
        assertNull(AgentBuildDialogueClassifier.resolveSkillTreeChoice("tree", skillTrees));
    }
}
