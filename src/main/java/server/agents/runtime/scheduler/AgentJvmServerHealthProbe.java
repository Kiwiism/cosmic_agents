package server.agents.runtime.scheduler;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.concurrent.atomic.AtomicLong;

public final class AgentJvmServerHealthProbe implements AgentServerHealthProbe {
    private final AtomicLong previousGcCollectionMs = new AtomicLong(-1L);

    @Override
    public AgentServerHealthSnapshot sample() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        double heapPercent = heap.getMax() <= 0L ? 0.0d : heap.getUsed() * 100.0d / heap.getMax();
        double cpuPercent = 0.0d;
        if (ManagementFactory.getOperatingSystemMXBean()
                instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double processCpuLoad = operatingSystem.getProcessCpuLoad();
            if (processCpuLoad >= 0.0d) {
                cpuPercent = processCpuLoad * 100.0d;
            }
        }

        long totalGcMs = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(AgentJvmServerHealthProbe::collectionTime)
                .sum();
        long previous = previousGcCollectionMs.getAndSet(totalGcMs);
        long gcDeltaMs = previous < 0L ? 0L : Math.max(0L, totalGcMs - previous);
        return new AgentServerHealthSnapshot(cpuPercent, heapPercent, gcDeltaMs, true);
    }

    private static long collectionTime(GarbageCollectorMXBean collector) {
        return Math.max(0L, collector.getCollectionTime());
    }
}
