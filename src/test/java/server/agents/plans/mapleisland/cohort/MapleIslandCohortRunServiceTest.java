package server.agents.plans.mapleisland.cohort;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapleIslandCohortRunServiceTest {
    @Test
    void releasesExactBatchesAndPreservesRequestedSeed() {
        FakeHooks hooks = new FakeHooks();
        MapleIslandCohortRunService service = new MapleIslandCohortRunService(hooks);

        MapleIslandCohortRunService.Status accepted = service.start(
                new MapleIslandCohortRunService.StartRequest(99, 0, 1, 5, 2, 10, 44L));
        assertEquals(44L, accepted.runSeed());
        assertEquals(MapleIslandCohortRealismMode.LIGHT, accepted.realismMode());

        hooks.runNextWave();
        assertEquals(2, service.status(0, 1).launched());
        hooks.runNextWave();
        assertEquals(4, service.status(0, 1).launched());
        hooks.runNextWave();

        MapleIslandCohortRunService.Status status = service.status(0, 1);
        assertEquals(MapleIslandCohortRunService.RunState.RUNNING, status.state());
        assertEquals(5, status.launched());
        assertEquals(List.of(1, 2, 3, 4, 5), hooks.startedOrdinals);
        assertEquals(List.of(
                MapleIslandCohortRealismMode.LIGHT,
                MapleIslandCohortRealismMode.LIGHT,
                MapleIslandCohortRealismMode.LIGHT,
                MapleIslandCohortRealismMode.LIGHT,
                MapleIslandCohortRealismMode.LIGHT), hooks.startedModes);
        assertEquals(List.of(0L, 10_000L, 10_000L), hooks.scheduledDelays);
    }

    @Test
    void cancelLeavesLaunchedAgentsRunningAndStopDisconnectsThenReleases() {
        FakeHooks hooks = new FakeHooks();
        MapleIslandCohortRunService service = new MapleIslandCohortRunService(hooks);
        service.start(new MapleIslandCohortRunService.StartRequest(99, 0, 1, 5, 2, 10, 55L));
        hooks.runNextWave();

        assertEquals(MapleIslandCohortRunService.RunState.CANCELLED,
                service.cancel(0, 1).state());
        assertEquals(2, service.status(0, 1).running());
        assertEquals(MapleIslandCohortRunService.RunState.STOPPING,
                service.stop(0, 1).state());
        hooks.runWorkers();

        assertEquals(MapleIslandCohortRunService.RunState.STOPPED,
                service.status(0, 1).state());
        assertEquals(Set.of(100, 101), Set.copyOf(hooks.stopped));
        assertEquals(1, hooks.releaseCalls);
    }

    @Test
    void startRequestEnforcesOperationalBounds() {
        assertThrows(IllegalArgumentException.class,
                () -> new MapleIslandCohortRunService.StartRequest(99, 0, 1, 101, 5, 10, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MapleIslandCohortRunService.StartRequest(99, 0, 1, 10, 11, 10, null));
        assertThrows(IllegalArgumentException.class,
                () -> new MapleIslandCohortRunService.StartRequest(99, 0, 1, 10, 5, 4, null));
    }

    @Test
    void explicitRealismModeIsCarriedToEveryAgentContext() {
        FakeHooks hooks = new FakeHooks();
        MapleIslandCohortRunService service = new MapleIslandCohortRunService(hooks);
        service.start(new MapleIslandCohortRunService.StartRequest(
                99, 0, 1, 2, 2, 10, 77L, MapleIslandCohortRealismMode.OFF));

        hooks.runNextWave();

        assertEquals(List.of(MapleIslandCohortRealismMode.OFF,
                MapleIslandCohortRealismMode.OFF), hooks.startedModes);
        assertEquals(MapleIslandCohortRealismMode.OFF, service.status(0, 1).realismMode());
    }

    @Test
    void rejectedFirstWaveDoesNotLeaveAStuckSession() {
        FakeHooks hooks = new FakeHooks();
        hooks.rejectSchedule = true;
        MapleIslandCohortRunService service = new MapleIslandCohortRunService(hooks);

        assertThrows(RejectedExecutionException.class, () -> service.start(
                new MapleIslandCohortRunService.StartRequest(99, 0, 1, 1, 1, 10, 88L)));
        assertNull(service.status(0, 1));
    }

    @Test
    void failedStopRequestKeepsLeasesAndCanBeRetried() {
        FakeHooks hooks = new FakeHooks();
        MapleIslandCohortRunService service = new MapleIslandCohortRunService(hooks);
        service.start(new MapleIslandCohortRunService.StartRequest(99, 0, 1, 1, 1, 10, 89L));
        hooks.runNextWave();
        hooks.rejectStop = true;

        service.stop(0, 1);
        hooks.runWorkers();

        assertEquals(MapleIslandCohortRunService.RunState.FAILED, service.status(0, 1).state());
        assertEquals(0, hooks.releaseCalls);

        hooks.rejectStop = false;
        service.stop(0, 1);
        hooks.runWorkers();
        assertEquals(MapleIslandCohortRunService.RunState.STOPPED, service.status(0, 1).state());
        assertEquals(1, hooks.releaseCalls);
    }

    private static final class FakeHooks implements MapleIslandCohortRunService.Hooks {
        private final Deque<TestFuture> scheduled = new ArrayDeque<>();
        private final Deque<Runnable> workers = new ArrayDeque<>();
        private final List<Long> scheduledDelays = new ArrayList<>();
        private final List<Integer> startedOrdinals = new ArrayList<>();
        private final List<MapleIslandCohortRealismMode> startedModes = new ArrayList<>();
        private final List<Integer> stopped = new ArrayList<>();
        private final Map<Integer, MapleIslandCohortRunService.AgentState> states = new HashMap<>();
        private int nextCharacterId = 100;
        private int releaseCalls;
        private boolean rejectSchedule;
        private boolean rejectStop;

        @Override
        public List<MapleIslandCohortPoolSnapshot.Agent> acquire(
                int count,
                String sessionId,
                int ownerCharacterId,
                int world,
                int channel,
                Set<Integer> excludedCharacterIds) {
            MapleIslandCohortPoolSnapshot.Account account = new MapleIslandCohortPoolSnapshot.Account(
                    10, "MIQuest0001", ownerCharacterId, 15, 1_000L);
            List<MapleIslandCohortPoolSnapshot.Agent> result = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int characterId = nextCharacterId++;
                result.add(MapleIslandCohortPoolSnapshot.Agent.available(
                        characterId, "Agent" + characterId, account, ownerCharacterId, world)
                        .leased(sessionId, ownerCharacterId, 2_000L));
            }
            return result;
        }

        @Override
        public void startAgent(MapleIslandCohortPoolSnapshot.Agent agent,
                               MapleIslandCohortRunService.AgentContext context) {
            startedOrdinals.add(context.ordinal());
            startedModes.add(context.realismMode());
            states.put(agent.characterId(), MapleIslandCohortRunService.AgentState.RUNNING);
        }

        @Override
        public void markBroken(MapleIslandCohortPoolSnapshot.Agent agent, String sessionId, String error) {
        }

        @Override
        public MapleIslandCohortRunService.AgentState agentState(int characterId) {
            return states.getOrDefault(characterId, MapleIslandCohortRunService.AgentState.MISSING);
        }

        @Override
        public void stopAgent(int characterId) {
            if (rejectStop) {
                throw new RejectedExecutionException("stop rejected");
            }
            stopped.add(characterId);
            states.put(characterId, MapleIslandCohortRunService.AgentState.MISSING);
        }

        @Override
        public void releaseSession(String sessionId) {
            releaseCalls++;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable action, long delayMs) {
            if (rejectSchedule) {
                throw new RejectedExecutionException("schedule rejected");
            }
            scheduledDelays.add(delayMs);
            TestFuture future = new TestFuture(action, delayMs);
            scheduled.addLast(future);
            return future;
        }

        @Override
        public void dispatch(Runnable action) {
            workers.addLast(action);
        }

        private void runNextWave() {
            TestFuture future;
            do {
                future = scheduled.removeFirst();
            } while (future.cancelled);
            future.run();
            runWorkers();
        }

        private void runWorkers() {
            while (!workers.isEmpty()) {
                workers.removeFirst().run();
            }
        }
    }

    private static final class TestFuture implements ScheduledFuture<Object> {
        private final Runnable action;
        private final long delayMs;
        private boolean cancelled;
        private boolean done;

        private TestFuture(Runnable action, long delayMs) {
            this.action = action;
            this.delayMs = delayMs;
        }

        private void run() {
            if (!cancelled) {
                action.run();
                done = true;
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(getDelay(TimeUnit.MILLISECONDS), other.getDelay(TimeUnit.MILLISECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done || cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
