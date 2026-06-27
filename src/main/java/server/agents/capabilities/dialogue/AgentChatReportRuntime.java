package server.agents.capabilities.dialogue;

public final class AgentChatReportRuntime {
    private AgentChatReportRuntime() {
    }

    public static AgentChatReportFlow.ReportCallbacks reportCallbacks(
            ReportScheduler scheduler,
            ReportActions actions) {
        return new AgentChatReportFlow.ReportCallbacks() {
            @Override
            public void help() {
                scheduleFast(scheduler, actions::help);
            }

            @Override
            public void requestUpgrade() {
                scheduleFast(scheduler, actions::requestUpgrade);
            }

            @Override
            public void recommendedGear() {
                scheduleFast(scheduler, actions::recommendedGear);
            }

            @Override
            public void skills() {
                scheduleStandard(scheduler, actions::skills);
            }

            @Override
            public void stats() {
                scheduleStandard(scheduler, actions::stats);
            }

            @Override
            public void movementStats() {
                scheduleStandard(scheduler, actions::movementStats);
            }

            @Override
            public void range() {
                scheduleStandard(scheduler, actions::range);
            }

            @Override
            public void build() {
                scheduleStandard(scheduler, actions::build);
            }

            @Override
            public void inventory() {
                scheduleStandard(scheduler, actions::inventory);
            }

            @Override
            public void mesos() {
                scheduleStandard(scheduler, actions::mesos);
            }

            @Override
            public void exp() {
                scheduleStandard(scheduler, actions::exp);
            }

            @Override
            public void inventorySlots() {
                scheduleStandard(scheduler, actions::inventorySlots);
            }

            @Override
            public void scrolls() {
                scheduleStandard(scheduler, actions::scrolls);
            }

            @Override
            public void potions() {
                scheduleStandard(scheduler, actions::potions);
            }

            @Override
            public void debugStats() {
                scheduleStandard(scheduler, actions::debugStats);
            }

            @Override
            public void critDebug() {
                scheduleStandard(scheduler, actions::critDebug);
            }

            @Override
            public void potDebug() {
                scheduleStandard(scheduler, actions::potDebug);
            }
        };
    }

    private static void scheduleFast(ReportScheduler scheduler, Runnable action) {
        scheduler.afterRandomDelay(500, 700, action);
    }

    private static void scheduleStandard(ReportScheduler scheduler, Runnable action) {
        scheduler.afterRandomDelay(900, 1100, action);
    }

    public static void reportRecommendedGear(
            RecommendedGearState state,
            RecommendedGearActions actions,
            long nowMs) {
        if (!actions.hasOwner()) {
            actions.queueGearCheckUnavailable();
            return;
        }

        if (!actions.offerBestRecommendedGear()) {
            actions.queueNoBetterGear();
        }
        state.setNextGearSuggestionAt(nowMs + 60_000L);
    }

    public static void reportHelp(HelpActions actions) {
        for (String line : AgentChatReportFlow.helpLines()) {
            actions.queueReply(line);
        }
    }

    public static void reportLine(String line, LineActions actions) {
        actions.queueReply(line);
    }

    public interface ReportScheduler {
        void afterRandomDelay(int minMs, int maxMs, Runnable action);
    }

    public interface ReportActions {
        void help();

        void requestUpgrade();

        void recommendedGear();

        void skills();

        void stats();

        void movementStats();

        void range();

        void build();

        void inventory();

        void mesos();

        void exp();

        void inventorySlots();

        void scrolls();

        void potions();

        void debugStats();

        void critDebug();

        void potDebug();
    }

    public interface RecommendedGearState {
        void setNextGearSuggestionAt(long nextGearSuggestionAt);
    }

    public interface RecommendedGearActions {
        boolean hasOwner();

        boolean offerBestRecommendedGear();

        void queueGearCheckUnavailable();

        void queueNoBetterGear();
    }

    public interface HelpActions {
        void queueReply(String line);
    }

    public interface LineActions {
        void queueReply(String line);
    }
}
