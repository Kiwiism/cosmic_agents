package server.monitoring;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HeapDumpService {
    private static final Logger log = LoggerFactory.getLogger(HeapDumpService.class);
    private static final String HOTSPOT_BEAN_NAME = "com.sun.management:type=HotSpotDiagnostic";

    private HeapDumpService() {
    }

    public static boolean dumpHeap(Path path, boolean liveOnly) {
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long estimatedDumpBytes = Runtime.getRuntime().maxMemory();
            if (!DiskSpaceMonitor.hasRoomFor(path, estimatedDumpBytes)) {
                log.error("Refusing heap dump to {}: insufficient free space for estimated {} MB dump plus reserve",
                        path.toAbsolutePath(), estimatedDumpBytes / 1024 / 1024);
                return false;
            }

            HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
                    ManagementFactory.getPlatformMBeanServer(),
                    HOTSPOT_BEAN_NAME,
                    HotSpotDiagnosticMXBean.class);
            mxBean.dumpHeap(path.toAbsolutePath().toString(), liveOnly);
            log.warn("Heap dump written to {}", path.toAbsolutePath());
            return true;
        } catch (Exception e) {
            log.error("Failed to write heap dump to {}", path.toAbsolutePath(), e);
            return false;
        }
    }
}
