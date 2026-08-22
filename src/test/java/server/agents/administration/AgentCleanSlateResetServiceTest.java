package server.agents.administration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentCleanSlateResetServiceTest {
    @Test
    void eligibleOfflineAgentRequiresPreviewTokenAndExactPhrase() throws Exception {
        StubPort port = new StubPort(target("before"));
        StubHooks hooks = new StubHooks();
        AgentCleanSlateResetService service = new AgentCleanSlateResetService(port, hooks);

        AgentCleanSlatePreview preview = service.preview(
                7, "operator", "restart initial observation", 1_000L);

        assertTrue(preview.eligible());
        assertFalse(preview.confirmationToken().isBlank());
        assertEquals("RESET Alpha", preview.confirmationPhrase());
        assertFalse(port.recordedPreview.confirmationToken().isBlank());

        AgentCleanSlateResult result = service.execute(
                7, preview.resetId(), preview.confirmationToken(), preview.confirmationPhrase(), 2_000L);

        assertTrue(result.success());
        assertTrue(port.resetCalled.get());
        assertTrue(hooks.cleanupCalled.get());
        assertThrows(IllegalStateException.class, () -> service.execute(
                7, preview.resetId(), preview.confirmationToken(), preview.confirmationPhrase(), 2_100L));
    }

    @Test
    void onlineAgentGetsPreviewWithBlockerAndNoUsableConfirmation() throws Exception {
        StubPort port = new StubPort(target("before"));
        StubHooks hooks = new StubHooks();
        hooks.online = true;
        AgentCleanSlateResetService service = new AgentCleanSlateResetService(port, hooks);

        AgentCleanSlatePreview preview = service.preview(
                7, "operator", "restart initial observation", 1_000L);

        assertFalse(preview.eligible());
        assertTrue(preview.blockers().contains("Agent must be fully offline"));
        assertTrue(preview.confirmationToken().isBlank());
        assertThrows(IllegalStateException.class, () -> service.execute(
                7, preview.resetId(), "token", "RESET Alpha", 2_000L));
        assertFalse(port.resetCalled.get());
    }

    @Test
    void changedStateConsumesConfirmationAndRejectsReset() throws Exception {
        StubPort port = new StubPort(target("before"));
        StubHooks hooks = new StubHooks();
        AgentCleanSlateResetService service = new AgentCleanSlateResetService(port, hooks);
        AgentCleanSlatePreview preview = service.preview(
                7, "operator", "restart initial observation", 1_000L);
        port.target = target("after");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.execute(
                7, preview.resetId(), preview.confirmationToken(), preview.confirmationPhrase(), 2_000L));

        assertTrue(failure.getMessage().contains("state changed"));
        assertEquals("REJECTED", port.auditStatus);
        assertFalse(port.resetCalled.get());
    }

    private static AgentCleanSlateTarget target(String fingerprint) {
        return new AgentCleanSlateTarget(
                7, "Alpha", 70, 0, 16, 0, 100000000, 100, 500,
                12, 2, 8, 3, true, false, true, true, fingerprint);
    }

    private static final class StubPort implements AgentCleanSlateResetPort {
        AgentCleanSlateTarget target;
        AgentCleanSlatePreview recordedPreview;
        String auditStatus = "";
        final AtomicBoolean resetCalled = new AtomicBoolean();

        StubPort(AgentCleanSlateTarget target) { this.target = target; }

        @Override public AgentCleanSlateTarget inspect(int characterId) { return target; }
        @Override public void recordPreview(AgentCleanSlatePreview preview, String requestedBy,
                                            String reason, String confirmationHash, long previewedAtMs) {
            recordedPreview = preview;
            auditStatus = preview.eligible() ? "PREVIEWED" : "PREVIEW_BLOCKED";
        }
        @Override public AgentCleanSlateTarget resetGameplay(
                String resetId, int characterId, String expectedFingerprint, long executedAtMs) {
            resetCalled.set(true);
            auditStatus = "SUCCEEDED";
            return target("clean");
        }
        @Override public void markRejected(String resetId, String reason, long executedAtMs) {
            auditStatus = "REJECTED";
        }
        @Override public void markCleanupWarning(String resetId, String warning) {
            auditStatus = "SUCCEEDED_WITH_WARNINGS";
        }
    }

    private static final class StubHooks implements AgentCleanSlateResetService.Hooks {
        boolean online;
        boolean runtimeActive;
        final AtomicBoolean cleanupCalled = new AtomicBoolean();

        @Override public boolean online(int characterId) { return online; }
        @Override public boolean runtimeActive(int characterId) { return runtimeActive; }
        @Override public AgentCleanSlateResetService.MaintenanceLease acquire(int characterId) {
            return () -> { };
        }
        @Override public void clearAgentOsProgress(int characterId) { cleanupCalled.set(true); }
    }
}
