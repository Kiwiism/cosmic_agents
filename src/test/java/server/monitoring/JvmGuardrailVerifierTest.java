package server.monitoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmGuardrailVerifierTest {
    @Test
    void recognizesCompleteBoundedProductionArguments() {
        JvmGuardrailVerifier.Status status = JvmGuardrailVerifier.inspect(List.of(
                "-XX:+HeapDumpOnOutOfMemoryError",
                "-XX:+ExitOnOutOfMemoryError",
                "-Xlog:gc*,safepoint:file=logs/gc/gc.log:time:filecount=10,filesize=20M"));

        assertTrue(status.complete());
    }

    @Test
    void rejectsUnboundedOrMissingDiagnostics() {
        assertFalse(JvmGuardrailVerifier.inspect(List.of("-Xlog:gc")).complete());
    }
}
