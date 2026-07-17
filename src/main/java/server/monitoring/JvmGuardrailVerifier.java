package server.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.List;

public final class JvmGuardrailVerifier {
    private static final Logger log = LoggerFactory.getLogger(JvmGuardrailVerifier.class);

    private JvmGuardrailVerifier() {
    }

    public static Status currentStatus() {
        return inspect(ManagementFactory.getRuntimeMXBean().getInputArguments());
    }

    public static void warnIfIncomplete() {
        Status status = currentStatus();
        if (!status.complete()) {
            log.warn("JVM production guardrails are incomplete: {}. See docs/PRODUCTION_JVM_GUARDRAILS.md",
                    status.compact());
        }
    }

    static Status inspect(List<String> arguments) {
        boolean heapDumpOnOom = arguments.contains("-XX:+HeapDumpOnOutOfMemoryError");
        boolean exitOnOom = arguments.contains("-XX:+ExitOnOutOfMemoryError");
        boolean boundedGcLog = arguments.stream().anyMatch(argument -> argument.startsWith("-Xlog:")
                && argument.contains("gc")
                && argument.contains("filecount=")
                && argument.contains("filesize="));
        return new Status(heapDumpOnOom, exitOnOom, boundedGcLog);
    }

    public record Status(boolean heapDumpOnOom, boolean exitOnOom, boolean boundedGcLog) {
        public boolean complete() {
            return heapDumpOnOom && exitOnOom && boundedGcLog;
        }

        public String compact() {
            return "heapDumpOnOom=" + heapDumpOnOom
                    + " exitOnOom=" + exitOnOom
                    + " boundedGcLog=" + boundedGcLog;
        }
    }
}
