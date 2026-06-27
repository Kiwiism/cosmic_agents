package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

        assertEquals(List.of("queueGearCheckUnavailable"), actions.events);
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
        assertEquals(61_000L, state.nextGearSuggestionAt);
    }

    @Test
    void recommendedGearReportQueuesNoBetterGearAndSetsCooldownWhenOfferFails() {
        TestRecommendedGearState state = new TestRecommendedGearState();
        TestRecommendedGearActions actions = new TestRecommendedGearActions();
        actions.hasOwner = true;
        actions.offerResult = false;

        AgentChatReportRuntime.reportRecommendedGear(state, actions, 1000L);

        assertEquals(List.of("offerBestRecommendedGear", "queueNoBetterGear"), actions.events);
        assertEquals(61_000L, state.nextGearSuggestionAt);
    }

    @Test
    void helpReportQueuesEveryHelpLineInOrder() {
        List<String> replies = new ArrayList<>();

        AgentChatReportRuntime.reportHelp(replies::add);

        assertEquals(AgentChatReportFlow.helpLines(), replies);
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

    private static final class TestRecommendedGearState implements AgentChatReportRuntime.RecommendedGearState {
        private long nextGearSuggestionAt;

        @Override
        public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
            this.nextGearSuggestionAt = nextGearSuggestionAt;
        }
    }

    private static final class TestRecommendedGearActions implements AgentChatReportRuntime.RecommendedGearActions {
        private final List<String> events = new ArrayList<>();
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
        public void queueGearCheckUnavailable() {
            events.add("queueGearCheckUnavailable");
        }

        @Override
        public void queueNoBetterGear() {
            events.add("queueNoBetterGear");
        }
    }
}
