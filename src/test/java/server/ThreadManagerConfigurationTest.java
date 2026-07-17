package server;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.concurrent.TimeUnit.SECONDS;

class ThreadManagerConfigurationTest {
    @Test
    void usesValidSystemPropertyAndFallsBackFromInvalidValue() {
        String key = "cosmic.threads.test.core";
        String previous = System.getProperty(key);
        try {
            System.setProperty(key, "7");
            assertEquals(7, ThreadManager.configuredInt("test.core", 3));
            System.setProperty(key, "invalid");
            assertEquals(3, ThreadManager.configuredInt("test.core", 3));
        } finally {
            if (previous == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, previous);
            }
        }
    }

    @Test
    void rejectsSaturatedWorkWithoutRunningItOnTheSubmittingThread() throws InterruptedException {
        ThreadManager manager = ThreadManager.getInstance();
        Map<String, String> previous = configureSingleWorkerPools();
        CountDownLatch runningTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseRunningTask = new CountDownLatch(1);
        CountDownLatch queuedTaskRan = new CountDownLatch(1);
        AtomicBoolean rejectedTaskRan = new AtomicBoolean();

        manager.stop();
        try {
            manager.start();
            manager.newTask(() -> {
                runningTaskStarted.countDown();
                try {
                    releaseRunningTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(runningTaskStarted.await(2, SECONDS));

            manager.newTask(queuedTaskRan::countDown);
            manager.newTask(() -> rejectedTaskRan.set(true));

            assertFalse(rejectedTaskRan.get());
            assertTrue(manager.diagnostics().contains("general[active=1,queued=1,pool=1,completed=0,submitted=3,rejected=1]"));

            releaseRunningTask.countDown();
            assertTrue(queuedTaskRan.await(2, SECONDS));
            assertFalse(rejectedTaskRan.get());
        } finally {
            releaseRunningTask.countDown();
            manager.stop();
            restoreProperties(previous);
        }
    }

    @Test
    void autosaveLaneReportsBackpressureWithoutUsingAnotherExecutor() throws InterruptedException {
        ThreadManager manager = ThreadManager.getInstance();
        Map<String, String> previous = configureSingleWorkerPools();
        CountDownLatch runningTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseRunningTask = new CountDownLatch(1);

        manager.stop();
        try {
            manager.start();
            assertTrue(manager.newAutosaveTask(() -> {
                runningTaskStarted.countDown();
                try {
                    releaseRunningTask.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(runningTaskStarted.await(2, SECONDS));
            assertTrue(manager.newAutosaveTask(() -> {
            }));
            assertFalse(manager.newAutosaveTask(() -> {
            }));
            assertTrue(manager.diagnostics().contains(
                    "autosave[active=1,queued=1,pool=1,completed=0,submitted=3,rejected=1]"));
        } finally {
            releaseRunningTask.countDown();
            manager.stop();
            restoreProperties(previous);
        }
    }

    private static Map<String, String> configureSingleWorkerPools() {
        Map<String, String> previous = new LinkedHashMap<>();
        for (String workload : new String[]{"general", "blocking", "database", "autosave"}) {
            for (String setting : new String[]{"core", "max", "queue"}) {
                String key = "cosmic.threads." + workload + "." + setting;
                previous.put(key, System.getProperty(key));
                System.setProperty(key, "1");
            }
        }
        return previous;
    }

    private static void restoreProperties(Map<String, String> previous) {
        previous.forEach((key, value) -> {
            if (value == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, value);
            }
        });
    }
}
