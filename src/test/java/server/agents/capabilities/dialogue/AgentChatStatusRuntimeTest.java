package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatStatusRuntimeTest {
    @Test
    void markActiveClearsAfkAndCopiesOwnerPosition() {
        TestState state = new TestState();
        state.wasAfk = true;
        Point ownerPosition = new Point(10, 20);

        AgentChatStatusRuntime.markActive(state, ownerPosition, 1234L);

        assertFalse(state.wasAfk);
        assertEquals(1234L, state.sinceMs);
        assertEquals(ownerPosition, state.position);
        assertNotSame(ownerPosition, state.position);
    }

    @Test
    void markActiveAllowsMissingOwnerPosition() {
        TestState state = new TestState();

        AgentChatStatusRuntime.markActive(state, null, 500L);

        assertNull(state.position);
        assertEquals(500L, state.sinceMs);
    }

    @Test
    void ownerIdleMirrorsAfkFlag() {
        TestState state = new TestState();
        assertFalse(AgentChatStatusRuntime.isOwnerIdle(state));
        state.wasAfk = true;
        assertTrue(AgentChatStatusRuntime.isOwnerIdle(state));
    }

    @Test
    void randomFidgetExpressionUsesLegacyExpressionSet() {
        Set<Integer> allowed = Set.of(2, 3, 5, 6, 7);
        for (int i = 0; i < 100; i++) {
            assertTrue(allowed.contains(AgentChatStatusRuntime.randomFidgetExpression()));
        }
    }

    @Test
    void checkStatusQueuesPromptsAndKeepsLegacyHookOrder() {
        TestStatusCheckState state = new TestStatusCheckState();
        TestStatusCheckActions actions = new TestStatusCheckActions();
        actions.jobPrompt = "job?";
        actions.spPrompt = "sp?";
        actions.apPrompt = "ap?";
        actions.canOfferSpawnUpgrade = true;

        AgentChatStatusRuntime.checkStatus(state, actions);

        assertTrue(state.spawnDone);
        assertEquals(List.of(
                "buildJobPrompt",
                "queue:job?",
                "buildSpVariantPrompt",
                "queue:sp?",
                "buildApPrompt",
                "queue:ap?",
                "maybeSuggestRecommendedGear",
                "maybeSuggestGearToSiblings",
                "canOfferSpawnUpgrade",
                "offerSpawnUpgradeIfAvailable"), actions.events);
    }

    @Test
    void checkStatusAutoAssignsWhenPromptsAreMissing() {
        TestStatusCheckState state = new TestStatusCheckState();
        TestStatusCheckActions actions = new TestStatusCheckActions();

        AgentChatStatusRuntime.checkStatus(state, actions);

        assertEquals(List.of(
                "buildJobPrompt",
                "buildSpVariantPrompt",
                "autoAssignSp",
                "buildApPrompt",
                "autoAssignAp",
                "maybeSuggestRecommendedGear",
                "maybeSuggestGearToSiblings",
                "canOfferSpawnUpgrade"), actions.events);
    }

    @Test
    void checkStatusSkipsOneTimeSpawnUpgradeWhenAlreadyDone() {
        TestStatusCheckState state = new TestStatusCheckState();
        state.spawnDone = true;
        TestStatusCheckActions actions = new TestStatusCheckActions();

        AgentChatStatusRuntime.checkStatus(state, actions);

        assertEquals(List.of(
                "buildJobPrompt",
                "buildSpVariantPrompt",
                "autoAssignSp",
                "buildApPrompt",
                "autoAssignAp",
                "maybeSuggestRecommendedGear",
                "maybeSuggestGearToSiblings"), actions.events);
    }

    @Test
    void prepareActiveModeUsesLegacyPreparationOrder() {
        TestActiveModeActions actions = new TestActiveModeActions();

        AgentChatStatusRuntime.prepareActiveMode(actions);

        assertEquals(List.of(
                "autoEquip",
                "resetGearSuggestionCooldown",
                "maybeSuggestGearToSiblings",
                "setupAutopot",
                "checkPotShareOnModeStart"), actions.events);
    }

    @Test
    void maybeSuggestGearSkipsWhenRecipientMissing() {
        TestGearSuggestionState state = new TestGearSuggestionState();
        TestGearSuggestionActions actions = new TestGearSuggestionActions();
        actions.hasRecipient = false;
        actions.offerResult = true;

        AgentChatStatusRuntime.maybeSuggestGear(state, actions, 1000L);

        assertFalse(actions.offerCalled);
        assertEquals(0L, state.nextAt);
    }

    @Test
    void maybeSuggestGearSkipsDuringCooldown() {
        TestGearSuggestionState state = new TestGearSuggestionState();
        state.nextAt = 2000L;
        TestGearSuggestionActions actions = new TestGearSuggestionActions();
        actions.hasRecipient = true;
        actions.offerResult = true;

        AgentChatStatusRuntime.maybeSuggestGear(state, actions, 1000L);

        assertFalse(actions.offerCalled);
        assertEquals(2000L, state.nextAt);
    }

    @Test
    void maybeSuggestGearSetsLegacyCooldownWhenOfferSucceeds() {
        TestGearSuggestionState state = new TestGearSuggestionState();
        TestGearSuggestionActions actions = new TestGearSuggestionActions();
        actions.hasRecipient = true;
        actions.offerResult = true;

        AgentChatStatusRuntime.maybeSuggestGear(state, actions, 1000L);

        assertTrue(actions.offerCalled);
        assertEquals(61_000L, state.nextAt);
    }

    @Test
    void maybeSuggestGearKeepsCooldownWhenOfferFails() {
        TestGearSuggestionState state = new TestGearSuggestionState();
        TestGearSuggestionActions actions = new TestGearSuggestionActions();
        actions.hasRecipient = true;
        actions.offerResult = false;

        AgentChatStatusRuntime.maybeSuggestGear(state, actions, 1000L);

        assertTrue(actions.offerCalled);
        assertEquals(0L, state.nextAt);
    }

    @Test
    void gearSuggestionActionsWrapRecipientAndOfferSupplier() {
        AgentChatStatusRuntime.GearSuggestionActions actions =
                AgentChatStatusRuntime.gearSuggestionActions(true, () -> true);

        assertTrue(actions.hasRecipient());
        assertTrue(actions.offerGear());
    }

    @Test
    void announceOfflineReturnSkipsWhenAgentMissing() {
        TestOfflineReturnActions actions = new TestOfflineReturnActions();

        AgentChatStatusRuntime.announceOfflineReturn(actions);

        assertEquals(List.of(), actions.events);
    }

    @Test
    void announceOfflineReturnSchedulesLegacyPartyReply() {
        TestOfflineReturnActions actions = new TestOfflineReturnActions();
        actions.hasAgent = true;
        actions.mapName = "Henesys";

        AgentChatStatusRuntime.announceOfflineReturn(actions);

        assertEquals("1500-2500", actions.events.get(0));
        assertTrue(actions.events.get(1).startsWith("face:"));
        assertTrue(Set.of("face:2", "face:3").contains(actions.events.get(1)));
        assertTrue(actions.events.get(2).startsWith("say:"));
        assertTrue(actions.events.get(2).contains("Henesys"));
    }

    @Test
    void announceAfkReturnSkipsWhenAgentMissing() {
        TestAfkReturnActions actions = new TestAfkReturnActions();

        AgentChatStatusRuntime.announceAfkReturn(actions);

        assertEquals(List.of(), actions.events);
    }

    @Test
    void announceAfkReturnSchedulesLegacyOwnerReply() {
        TestAfkReturnActions actions = new TestAfkReturnActions();
        actions.hasAgent = true;

        AgentChatStatusRuntime.announceAfkReturn(actions);

        assertEquals("1800-2200", actions.events.get(0));
        assertTrue(Set.of("face:2", "face:3").contains(actions.events.get(1)));
        assertTrue(actions.events.get(2).startsWith("reply:"));
    }

    @Test
    void tickAfkCheckSchedulesWelcomeBackWhenAfkOwnerMoves() {
        TestAfkState state = new TestAfkState();
        state.position = new Point(1, 1);
        state.sinceMs = 1000L;
        state.wasAfk = true;
        TestAfkReturnActions actions = new TestAfkReturnActions();
        actions.hasAgent = true;

        AgentChatStatusRuntime.tickAfkCheck(state, new Point(2, 2), 2000L, actions);

        assertFalse(state.wasAfk);
        assertEquals(new Point(2, 2), state.position);
        assertEquals(2000L, state.sinceMs);
        assertEquals("1800-2200", actions.events.get(0));
        assertTrue(Set.of("face:2", "face:3").contains(actions.events.get(1)));
        assertTrue(actions.events.get(2).startsWith("reply:"));
    }

    private static final class TestState implements AgentChatStatusRuntime.StatusState {
        private Point position;
        private long sinceMs;
        private boolean wasAfk;

        @Override
        public void setOwnerAfkPosition(Point position) {
            this.position = position;
        }

        @Override
        public void setOwnerAfkSinceMs(long sinceMs) {
            this.sinceMs = sinceMs;
        }

        @Override
        public boolean ownerWasAfk() {
            return wasAfk;
        }

        @Override
        public void setOwnerWasAfk(boolean wasAfk) {
            this.wasAfk = wasAfk;
        }
    }

    private static final class TestStatusCheckState implements AgentChatStatusRuntime.StatusCheckState {
        private boolean spawnDone;

        @Override
        public boolean spawnUpgradeCheckDone() {
            return spawnDone;
        }

        @Override
        public void setSpawnUpgradeCheckDone(boolean done) {
            spawnDone = done;
        }
    }

    private static final class TestStatusCheckActions implements AgentChatStatusRuntime.StatusCheckActions {
        private final List<String> events = new ArrayList<>();
        private String jobPrompt;
        private String spPrompt;
        private String apPrompt;
        private boolean canOfferSpawnUpgrade;

        @Override
        public String buildJobPrompt() {
            events.add("buildJobPrompt");
            return jobPrompt;
        }

        @Override
        public String buildSpVariantPrompt() {
            events.add("buildSpVariantPrompt");
            return spPrompt;
        }

        @Override
        public String buildApPrompt() {
            events.add("buildApPrompt");
            return apPrompt;
        }

        @Override
        public void queueReply(String message) {
            events.add("queue:" + message);
        }

        @Override
        public void autoAssignSp() {
            events.add("autoAssignSp");
        }

        @Override
        public void autoAssignAp() {
            events.add("autoAssignAp");
        }

        @Override
        public void maybeSuggestRecommendedGear() {
            events.add("maybeSuggestRecommendedGear");
        }

        @Override
        public void maybeSuggestGearToSiblings() {
            events.add("maybeSuggestGearToSiblings");
        }

        @Override
        public boolean canOfferSpawnUpgrade() {
            events.add("canOfferSpawnUpgrade");
            return canOfferSpawnUpgrade;
        }

        @Override
        public void offerSpawnUpgradeIfAvailable() {
            events.add("offerSpawnUpgradeIfAvailable");
        }
    }

    private static final class TestActiveModeActions implements AgentChatStatusRuntime.ActiveModeActions {
        private final List<String> events = new ArrayList<>();

        @Override
        public void autoEquip() {
            events.add("autoEquip");
        }

        @Override
        public void resetGearSuggestionCooldown() {
            events.add("resetGearSuggestionCooldown");
        }

        @Override
        public void maybeSuggestGearToSiblings() {
            events.add("maybeSuggestGearToSiblings");
        }

        @Override
        public void setupAutopot() {
            events.add("setupAutopot");
        }

        @Override
        public void checkPotShareOnModeStart() {
            events.add("checkPotShareOnModeStart");
        }
    }

    private static final class TestGearSuggestionState implements AgentChatStatusRuntime.GearSuggestionState {
        private long nextAt;

        @Override
        public long nextGearSuggestionAt() {
            return nextAt;
        }

        @Override
        public void setNextGearSuggestionAt(long nextGearSuggestionAt) {
            nextAt = nextGearSuggestionAt;
        }
    }

    private static final class TestGearSuggestionActions implements AgentChatStatusRuntime.GearSuggestionActions {
        private boolean hasRecipient;
        private boolean offerResult;
        private boolean offerCalled;

        @Override
        public boolean hasRecipient() {
            return hasRecipient;
        }

        @Override
        public boolean offerGear() {
            offerCalled = true;
            return offerResult;
        }
    }

    private static final class TestOfflineReturnActions implements AgentChatStatusRuntime.OfflineReturnActions {
        private final List<String> events = new ArrayList<>();
        private boolean hasAgent;
        private String mapName;

        @Override
        public boolean hasAgent() {
            return hasAgent;
        }

        @Override
        public String mapName() {
            return mapName;
        }

        @Override
        public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
            events.add(minMs + "-" + maxMs);
            action.run();
        }

        @Override
        public void changeFaceExpression(int expression) {
            events.add("face:" + expression);
        }

        @Override
        public void sayParty(String text) {
            events.add("say:" + text);
        }
    }

    private static final class TestAfkReturnActions implements AgentChatStatusRuntime.AfkReturnActions {
        private final List<String> events = new ArrayList<>();
        private boolean hasAgent;

        @Override
        public boolean hasAgent() {
            return hasAgent;
        }

        @Override
        public void afterRandomDelay(int minMs, int maxMs, Runnable action) {
            events.add(minMs + "-" + maxMs);
            action.run();
        }

        @Override
        public void changeFaceExpression(int expression) {
            events.add("face:" + expression);
        }

        @Override
        public void reply(String text) {
            events.add("reply:" + text);
        }
    }

    private static final class TestAfkState implements AgentChatWelcomeBackFlow.AfkState {
        private Point position;
        private long sinceMs;
        private boolean wasAfk;

        @Override
        public Point ownerAfkPosition() {
            return position;
        }

        @Override
        public void setOwnerAfkPosition(Point position) {
            this.position = position;
        }

        @Override
        public long ownerAfkSinceMs() {
            return sinceMs;
        }

        @Override
        public void setOwnerAfkSinceMs(long sinceMs) {
            this.sinceMs = sinceMs;
        }

        @Override
        public boolean ownerWasAfk() {
            return wasAfk;
        }

        @Override
        public void setOwnerWasAfk(boolean wasAfk) {
            this.wasAfk = wasAfk;
        }
    }
}
