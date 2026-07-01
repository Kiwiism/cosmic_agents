/*
    This file is part of the HeavenMS MapleStory Server
    Copyleft (L) 2016 - 2019 RonanLana

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author Ronan
 */
public class ThreadManager {
    private static final Logger log = LoggerFactory.getLogger(ThreadManager.class);
    private static final ThreadManager instance = new ThreadManager();

    public static ThreadManager getInstance() {
        return instance;
    }

    private ThreadPoolExecutor tpe;
    private final AtomicLong submittedTasks = new AtomicLong();
    private final AtomicLong rejectedTasks = new AtomicLong();

    private ThreadManager() {
    }

    private class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            rejectedTasks.incrementAndGet();
            log.warn("ThreadManager rejected task. active={} queued={} completed={} rejected={}",
                    executor.getActiveCount(), executor.getQueue().size(), executor.getCompletedTaskCount(), rejectedTasks.get());
            if (!executor.isShutdown()) {
                r.run();
            }
        }

    }

    public void newTask(Runnable r) {
        submittedTasks.incrementAndGet();
        tpe.execute(r);
    }

    public void start() {
        RejectedExecutionHandler reh = new RejectedExecutionHandlerImpl();
        ThreadFactory tf = Executors.defaultThreadFactory();

        tpe = new ThreadPoolExecutor(20, 1000, 77, SECONDS, new ArrayBlockingQueue<>(50), tf, reh);
    }

    public void stop() {
        if (tpe == null) {
            return;
        }
        tpe.shutdown();
        try {
            tpe.awaitTermination(5, MINUTES);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public String diagnostics() {
        if (tpe == null) {
            return "ThreadManager stopped";
        }
        return "ThreadManager active=" + tpe.getActiveCount()
                + " queued=" + tpe.getQueue().size()
                + " pool=" + tpe.getPoolSize()
                + " completed=" + tpe.getCompletedTaskCount()
                + " submitted=" + submittedTasks.get()
                + " rejected=" + rejectedTasks.get();
    }

}
