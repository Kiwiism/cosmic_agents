package server.agents.capabilities.partyquest.kpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentKpqHumanDialoguePolicyTest {
    @Test
    void recognizesCouponHelpWithoutTryingToInferTheAnswer() {
        assertTrue(AgentKpqHumanDialoguePolicy.asksForCouponCount(
                "how many str warrior adv?"));
        assertTrue(AgentKpqHumanDialoguePolicy.asksForCouponCount(
                "How many coupons do I need?"));
        assertTrue(AgentKpqHumanDialoguePolicy.asksForCouponCount(
                "need ticket count"));
    }

    @Test
    void unrelatedCountsAndQuestionFragmentsDoNotTriggerHelp() {
        assertFalse(AgentKpqHumanDialoguePolicy.asksForCouponCount("how many slimes left?"));
        assertFalse(AgentKpqHumanDialoguePolicy.asksForCouponCount("25 str warrior"));
        assertFalse(AgentKpqHumanDialoguePolicy.asksForCouponCount("anyone doing kpq?"));
    }

    @Test
    void answerUsesTheAuthoritativeTargetPassedByTheRuntime() {
        assertTrue(AgentKpqHumanDialoguePolicy.couponAnswer("Kiwi", 25)
                .contains("25 coupons"));
    }

    @Test
    void firstDelayedPromptIsSupportiveBeforeLaterTeasing() {
        String first = AgentKpqHumanDialoguePolicy.delayedPrompt(
                "Kiwi", "take rope 2", 1L, 0L, false, true);

        assertFalse(first.contains("lol"));
        assertFalse(first.contains("noob"));
        assertTrue(first.contains("no rush"));
    }

    @Test
    void partyLeaderIsNotTeasedWhenTheLeaderTaskDoesNotAllowIt() {
        String prompt = AgentKpqHumanDialoguePolicy.delayedPrompt(
                "Kiwi", "talk to Cloto to begin Stage 3", 3L, 5L, true, false);

        assertFalse(prompt.toLowerCase().contains("noob"));
        assertFalse(prompt.toLowerCase().contains("lol"));
        assertTrue(prompt.contains("we're ready when you are"));
    }

    @Test
    void partyLeaderCanBeTeasedAfterIgnoringAClotoSolutionCheck() {
        String first = AgentKpqHumanDialoguePolicy.delayedPrompt(
                "Kiwi", "check this formation with Cloto", 3L, 0L, true, true);
        String later = AgentKpqHumanDialoguePolicy.delayedPrompt(
                "Kiwi", "check this formation with Cloto", 3L, 1L, true, true);

        assertTrue(first.contains("we're ready when you are"));
        assertTrue(later.contains("lol") || later.contains("noob")
                || later.contains("need a hand") || later.contains("take your time"));
    }
}
