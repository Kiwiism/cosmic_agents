/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package server;

import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class TimerManager implements TimerManagerMBean {
    private static final Logger log = LoggerFactory.getLogger(TimerManager.class);
    private static final TimerManager instance = new TimerManager();

    public enum SchedulerLane {
        CORE,
        SAVE,
        MAP,
        EVENT,
        LOW_PRIORITY
    }

    public static TimerManager getInstance() {
        return instance;
    }

    private ScheduledThreadPoolExecutor coreExecutor;
    private ScheduledThreadPoolExecutor saveExecutor;
    private ScheduledThreadPoolExecutor mapExecutor;
    private ScheduledThreadPoolExecutor eventExecutor;
    private ScheduledThreadPoolExecutor lowPriorityExecutor;

    private TimerManager() {
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        try {
            mBeanServer.registerMBean(this, new ObjectName("server:type=TimerManager"));
        } catch (Exception e) {
            log.warn("Failed to register TimerManager MBean", e);
        }
    }

    public void start() {
        if (coreExecutor != null && !coreExecutor.isShutdown() && !coreExecutor.isTerminated()) {
            return;
        }

        coreExecutor = newExecutor("TimerManager-Core", 4);
        saveExecutor = newExecutor("TimerManager-Save", 2);
        mapExecutor = newExecutor("TimerManager-Map", 2);
        eventExecutor = newExecutor("TimerManager-Event", 2);
        lowPriorityExecutor = newExecutor("TimerManager-Low", 1);
    }

    private ScheduledThreadPoolExecutor newExecutor(String name, int threads) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(threads, new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(name + "-" + threadNumber.getAndIncrement());
                return t;
            }
        });
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setRemoveOnCancelPolicy(true);
        executor.setKeepAliveTime(5, MINUTES);
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    public void stop() {
        shutdownNow(coreExecutor);
        shutdownNow(saveExecutor);
        shutdownNow(mapExecutor);
        shutdownNow(eventExecutor);
        shutdownNow(lowPriorityExecutor);
    }

    private void shutdownNow(ScheduledThreadPoolExecutor executor) {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public Runnable purge() {
        return () -> {
            Server.getInstance().forceUpdateCurrentTime();
            purge(coreExecutor);
            purge(saveExecutor);
            purge(mapExecutor);
            purge(eventExecutor);
            purge(lowPriorityExecutor);
        };
    }

    private void purge(ScheduledThreadPoolExecutor executor) {
        if (executor != null) {
            executor.purge();
        }
    }

    public ScheduledFuture<?> register(Runnable r, long repeatTime, long delay) {
        return register(SchedulerLane.CORE, r, repeatTime, delay);
    }

    public ScheduledFuture<?> register(Runnable r, long repeatTime) {
        return register(SchedulerLane.CORE, r, repeatTime, 0);
    }

    public ScheduledFuture<?> register(SchedulerLane lane, Runnable r, long repeatTime, long delay) {
        return executor(lane).scheduleAtFixedRate(new LoggingSaveRunnable(lane, r), delay, repeatTime, MILLISECONDS);
    }

    public ScheduledFuture<?> schedule(Runnable r, long delay) {
        return schedule(SchedulerLane.CORE, r, delay);
    }

    public ScheduledFuture<?> schedule(SchedulerLane lane, Runnable r, long delay) {
        return executor(lane).schedule(new LoggingSaveRunnable(lane, r), delay, MILLISECONDS);
    }

    public ScheduledFuture<?> scheduleAtTimestamp(Runnable r, long timestamp) {
        return schedule(r, timestamp - System.currentTimeMillis());
    }

    @Override
    public long getActiveCount() {
        return activeCount(coreExecutor) + activeCount(saveExecutor) + activeCount(mapExecutor)
                + activeCount(eventExecutor) + activeCount(lowPriorityExecutor);
    }

    @Override
    public long getCompletedTaskCount() {
        return completedTaskCount(coreExecutor) + completedTaskCount(saveExecutor) + completedTaskCount(mapExecutor)
                + completedTaskCount(eventExecutor) + completedTaskCount(lowPriorityExecutor);
    }

    @Override
    public int getQueuedTasks() {
        return queuedTasks(coreExecutor) + queuedTasks(saveExecutor) + queuedTasks(mapExecutor)
                + queuedTasks(eventExecutor) + queuedTasks(lowPriorityExecutor);
    }

    @Override
    public long getTaskCount() {
        return taskCount(coreExecutor) + taskCount(saveExecutor) + taskCount(mapExecutor)
                + taskCount(eventExecutor) + taskCount(lowPriorityExecutor);
    }

    @Override
    public boolean isShutdown() {
        return isShutdown(coreExecutor) && isShutdown(saveExecutor) && isShutdown(mapExecutor)
                && isShutdown(eventExecutor) && isShutdown(lowPriorityExecutor);
    }

    @Override
    public boolean isTerminated() {
        return isTerminated(coreExecutor) && isTerminated(saveExecutor) && isTerminated(mapExecutor)
                && isTerminated(eventExecutor) && isTerminated(lowPriorityExecutor);
    }

    public String diagnostics() {
        return "timers core=" + executorStats(coreExecutor)
                + " save=" + executorStats(saveExecutor)
                + " map=" + executorStats(mapExecutor)
                + " event=" + executorStats(eventExecutor)
                + " low=" + executorStats(lowPriorityExecutor);
    }

    private ScheduledThreadPoolExecutor executor(SchedulerLane lane) {
        return switch (lane) {
            case CORE -> coreExecutor;
            case SAVE -> saveExecutor;
            case MAP -> mapExecutor;
            case EVENT -> eventExecutor;
            case LOW_PRIORITY -> lowPriorityExecutor;
        };
    }

    private static long activeCount(ScheduledThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getActiveCount();
    }

    private static long completedTaskCount(ScheduledThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getCompletedTaskCount();
    }

    private static int queuedTasks(ScheduledThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getQueue().size();
    }

    private static long taskCount(ScheduledThreadPoolExecutor executor) {
        return executor == null ? 0 : executor.getTaskCount();
    }

    private static boolean isShutdown(ScheduledThreadPoolExecutor executor) {
        return executor == null || executor.isShutdown();
    }

    private static boolean isTerminated(ScheduledThreadPoolExecutor executor) {
        return executor == null || executor.isTerminated();
    }

    private static String executorStats(ScheduledThreadPoolExecutor executor) {
        if (executor == null) {
            return "stopped";
        }
        return "active:" + executor.getActiveCount()
                + ",queued:" + executor.getQueue().size()
                + ",completed:" + executor.getCompletedTaskCount();
    }

    private static class LoggingSaveRunnable implements Runnable {
        private final SchedulerLane lane;
        private final Runnable r;

        public LoggingSaveRunnable(SchedulerLane lane, Runnable r) {
            this.lane = lane;
            this.r = r;
        }

        @Override
        public void run() {
            try {
                r.run();
            } catch (Throwable t) {
                log.error("Error in scheduled task lane={}", lane, t);
            }
        }
    }
}
