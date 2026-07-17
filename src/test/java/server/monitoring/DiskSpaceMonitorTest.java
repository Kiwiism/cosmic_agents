package server.monitoring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiskSpaceMonitorTest {
    @Test
    void classifiesConfiguredFreeSpaceBoundaries() {
        assertEquals(DiskSpaceMonitor.Level.CRITICAL,
                DiskSpaceMonitor.classify(100, 1_000, 100));
        assertEquals(DiskSpaceMonitor.Level.WARN,
                DiskSpaceMonitor.classify(500, 1_000, 100));
        assertEquals(DiskSpaceMonitor.Level.OK,
                DiskSpaceMonitor.classify(1_001, 1_000, 100));
    }
}
