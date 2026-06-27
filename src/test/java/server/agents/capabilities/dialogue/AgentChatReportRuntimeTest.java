package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentChatReportRuntimeTest {
    @Test
    void fastReportsUseLegacyFastDelayWindow() {
        TestScheduler scheduler = new TestScheduler();
        TestActions actions = new TestActions();
        AgentChatReportFlow.ReportCallbacks callbacks =
                AgentChatReportRuntime.reportCallbacks(scheduler, actions);

        callbacks.help();
        callbacks.requestUpgrade();
        callbacks.recommendedGear();

        assertEquals(List.of("500-700", "500-700", "500-700"), scheduler.delays);
        assertEquals(List.of("help", "requestUpgrade", "recommendedGear"), actions.events);
    }

    @Test
    void reportActionsDelegateToOperationsByName() {
        TestOperations operations = new TestOperations();
        AgentChatReportRuntime.ReportActions actions = AgentChatReportRuntime.reportActions(operations);

        actions.help();
        actions.requestUpgrade();
        actions.recommendedGear();
        actions.skills();
        actions.stats();
        actions.movementStats();
        actions.range();
        actions.build();
        actions.inventory();
        actions.mesos();
        actions.exp();
        actions.inventorySlots();
        actions.scrolls();
        actions.potions();
        actions.debugStats();
        actions.critDebug();
        actions.potDebug();

        assertEquals(List.of(
                "help", "requestUpgrade", "recommendedGear", "skills", "stats",
                "movementStats", "range", "build", "inventory", "mesos", "exp",
                "inventorySlots", "scrolls", "potions", "debugStats", "critDebug",
                "potDebug"), operations.events);
    }

    @Test
    void reportCallbacksCanUseOperationsDirectly() {
        TestScheduler scheduler = new TestScheduler();
        TestOperations operations = new TestOperations();
        AgentChatReportFlow.ReportCallbacks callbacks =
                AgentChatReportRuntime.reportCallbacks(scheduler, operations);

        callbacks.help();
        callbacks.stats();

        assertEquals(List.of("500-700", "900-1100"), scheduler.delays);
        assertEquals(List.of("help", "stats"), operations.events);
    }

    @Test
    void standardReportsUseLegacyStandardDelayWindow() {
        TestScheduler scheduler = new TestScheduler();
        TestActions actions = new TestActions();
        AgentChatReportFlow.ReportCallbacks callbacks =
                AgentChatReportRuntime.reportCallbacks(scheduler, actions);

        callbacks.skills();
        callbacks.stats();
        callbacks.movementStats();
        callbacks.range();
        callbacks.build();
        callbacks.inventory();
        callbacks.mesos();
        callbacks.exp();
        callbacks.inventorySlots();
        callbacks.scrolls();
        callbacks.potions();
        callbacks.debugStats();
        callbacks.critDebug();
        callbacks.potDebug();

        assertEquals(List.of(
                "900-1100", "900-1100", "900-1100", "900-1100", "900-1100",
                "900-1100", "900-1100", "900-1100", "900-1100", "900-1100",
                "900-1100", "900-1100", "900-1100", "900-1100"), scheduler.delays);
        assertEquals(List.of(
                "skills", "stats", "movementStats", "range", "build",
                "inventory", "mesos", "exp", "inventorySlots", "scrolls",
                "potions", "debugStats", "critDebug", "potDebug"), actions.events);
    }

    @Test
    void recommendedGearReportQueuesUnavailableWhenOwnerMissing() {
        TestRecommendedGearState state = new TestRecommendedGearState();
        TestRecommendedGearActions actions = new TestRecommendedGearActions();

        AgentChatReportRuntime.reportRecommendedGear(state, actions, 1000L);

        assertEquals(List.of(AgentChatEquipmentFlow.gearCheckUnavailableReply()), actions.replies);
        assertEquals(List.of(), actions.events);
        assertEquals(0L, state.nextGearSuggestionAt);
    }

    @Test
    void recommendedGearReportSetsCooldownWhenOfferSucceeds() {
        TestRecommendedGearState state = new TestRecommendedGearState();
        TestRecommendedGearActions actions = new TestRecommendedGearActions();
        actions.hasOwner = true;
        actions.offerResult = true;

        AgentChatReportRuntime.reportRecommendedGear(state, actions, 1000L);

        assertEquals(List.of("offerBestRecommendedGear"), actions.events);
        assertEquals(List.of(), actions.replies);
        assertEquals(61_000L, state.nextGearSuggestionAt);
    }

    @Test
    void recommendedGearReportQueuesNoBetterGearAndSetsCooldownWhenOfferFails() {
        TestRecommendedGearState state = new TestRecommendedGearState();
        TestRecommendedGearActions actions = new TestRecommendedGearActions();
        actions.hasOwner = true;
        actions.offerResult = false;

        AgentChatReportRuntime.reportRecommendedGear(state, actions, 1000L);

        assertEquals(List.of("offerBestRecommendedGear"), actions.events);
        assertEquals(List.of(AgentChatEquipmentFlow.noBetterGearReply()), actions.replies);
        assertEquals(61_000L, state.nextGearSuggestionAt);
    }

    @Test
    void helpReportQueuesEveryHelpLineInOrder() {
        List<String> replies = new ArrayList<>();

        AgentChatReportRuntime.reportHelp(replies::add);

        assertEquals(AgentChatReportFlow.helpLines(), replies);
    }

    @Test
    void lineReportQueuesProvidedLine() {
        List<String> replies = new ArrayList<>();

        AgentChatReportRuntime.reportLine("range: 1~2", replies::add);

        assertEquals(List.of("range: 1~2"), replies);
    }

    @Test
    void linesReportQueuesProvidedLinesInOrder() {
        List<String> replies = new ArrayList<>();

        AgentChatReportRuntime.reportLines(List.of("one", "two", "three"), replies::add);

        assertEquals(List.of("one", "two", "three"), replies);
    }

    @Test
    void skillReportAppliesBeginnerDecision() {
        TestSkillReportActions actions = new TestSkillReportActions();

        AgentChatReportRuntime.reportSkills(
                true,
                0,
                List.of(new AgentSkillReportFlow.SkillLine(1000, "Three Snails", 1)),
                2,
                Map.of(),
                actions);

        assertEquals(1, actions.decisions.size());
        assertEquals(false, actions.decisions.get(0).requestSkillTreeChoice());
        assertEquals(false, actions.decisions.get(0).clearPendingAction());
        assertEquals(1, actions.decisions.get(0).replies().size());
    }

    @Test
    void skillReportAppliesNoJobSkillDecision() {
        TestSkillReportActions actions = new TestSkillReportActions();

        AgentChatReportRuntime.reportSkills(false, 3, List.of(), 0, Map.of(), actions);

        assertEquals(1, actions.decisions.size());
        assertEquals(false, actions.decisions.get(0).requestSkillTreeChoice());
        assertEquals(false, actions.decisions.get(0).clearPendingAction());
        assertEquals(1, actions.decisions.get(0).replies().size());
    }

    @Test
    void skillReportAppliesPendingChoiceDecisionForMultipleSkillTrees() {
        TestSkillReportActions actions = new TestSkillReportActions();

        AgentChatReportRuntime.reportSkills(
                false,
                0,
                List.of(),
                0,
                Map.of(
                        100, List.of(new AgentSkillReportFlow.SkillLine(1001004, "Power Strike", 1)),
                        110, List.of(new AgentSkillReportFlow.SkillLine(1101006, "Sword Booster", 1))),
                actions);

        assertEquals(1, actions.decisions.size());
        assertEquals(true, actions.decisions.get(0).requestSkillTreeChoice());
        assertEquals(false, actions.decisions.get(0).clearPendingAction());
        assertEquals(1, actions.decisions.get(0).replies().size());
    }

    private static final class TestScheduler implements AgentChatReportRuntime.ReportScheduler {
        private final List<String> delays = new ArrayList<>();

        @Override
        public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
            delays.add(minMs + "-" + maxMs);
            action.run();
        }
    }

    private static final class TestActions implements AgentChatReportRuntime.ReportActions {
        private final List<String> events = new ArrayList<>();

        @Override
        public void help() {
            events.add("help");
        }

        @Override
        public void requestUpgrade() {
            events.add("requestUpgrade");
        }

        @Override
        public void recommendedGear() {
            events.add("recommendedGear");
        }

        @Override
        public void skills() {
            events.add("skills");
        }

        @Override
        public void stats() {
            events.add("stats");
        }

        @Override
        public void movementStats() {
            events.add("movementStats");
        }

        @Override
        public void range() {
            events.add("range");
        }

        @Override
        public void build() {
            events.add("build");
        }

        @Override
        public void inventory() {
            events.add("inventory");
        }

        @Override
        public void mesos() {
            events.add("mesos");
        }

        @Override
        public void exp() {
            events.add("exp");
        }

        @Override
        public void inventorySlots() {
            events.add("inventorySlots");
        }

        @Override
        public void scrolls() {
            events.add("scrolls");
        }

        @Override
        public void potions() {
            events.add("potions");
        }

        @Override
        public void debugStats() {
            events.add("debugStats");
        }

        @Override
        public void critDebug() {
            events.add("critDebug");
        }

        @Override
        public void potDebug() {
            events.add("potDebug");
        }
    }

    private static final class TestOperations implements AgentChatReportRuntime.ReportOperations {
        private final List<String> events = new ArrayList<>();

        @Override
        public void help() {
            events.add("help");
        }

        @Override
        public void requestUpgrade() {
            events.add("requestUpgrade");
        }

        @Override
        public void recommendedGear() {
            events.add("recommendedGear");
        }

        @Override
        public void skills() {
            events.add("skills");
        }

        @Override
        public void stats() {
            events.add("stats");
        }

        @Override
        public void movementStats() {
            events.add("movementStats");
        }

        @Override
        public void range() {
            events.add("range");
        }

        @Override
        public void build() {
            events.add("build");
        }

        @Override
        public void inventory() {
            events.add("inventory");
        }

        @Override
        public void mesos() {
            events.add("mesos");
        }

        @Override
        public void exp() {
            events.add("exp");
        }

        @Override
        public void inventorySlots() {
            events.add("inventorySlots");
        }

        @Override
        public void scrolls() {
            events.add("scrolls");
        }

        @Override
        public void potions() {
            events.add("potions");
        }

        @Override
        public void debugStats() {
            events.add("debugStats");
        }

        @Override
        public void critDebug() {
            events.add("critDebug");
        }

        @Override
        public void potDebug() {
            events.add("potDebug");
        }
    }

    private static final class TestRecommendedGearState implements AgentChatReportRuntime.RecommendedGearState {
        private long nextGearSuggestionAt;

        @Override
        public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
            this.nextGearSuggestionAt = nextGearSuggestionAt;
        }
    }

    private static final class TestRecommendedGearActions implements AgentChatReportRuntime.RecommendedGearActions {
        private final List<String> events = new ArrayList<>();
        private final List<String> replies = new ArrayList<>();
        private boolean hasOwner;
        private boolean offerResult;

        @Override
        public boolean hasOwner() {
            return hasOwner;
        }

        @Override
        public boolean offerBestRecommendedGear() {
            events.add("offerBestRecommendedGear");
            return offerResult;
        }

        @Override
        public void queueReply(String line) {
            replies.add(line);
        }
    }

    private static final class TestSkillReportActions implements AgentChatReportRuntime.SkillReportActions {
        private final List<AgentSkillReportFlow.SkillReportDecision> decisions = new ArrayList<>();

        @Override
        public void applySkillReportDecision(AgentSkillReportFlow.SkillReportDecision decision) {
            decisions.add(decision);
        }
    }
}
