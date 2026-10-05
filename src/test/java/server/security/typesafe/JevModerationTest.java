package server.security.typesafe;

import org.junit.jupiter.api.Test;
import server.agents.integration.typesafe.JevAnswer;
import server.agents.integration.typesafe.JevClient;
import server.agents.integration.typesafe.JevRequest;
import server.agents.integration.typesafe.JevResponse;
import server.agents.integration.typesafe.TypeSafeSettings;
import server.security.SecuritySeverity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevModerationTest {
    private static final TypeSafeSettings SETTINGS = new TypeSafeSettings(
            "key", TypeSafeSettings.DEFAULT_ENDPOINT, TypeSafeSettings.DEFAULT_MODEL, 1_000);

    @Test
    void nameScreenRejectsOnlyConfidentStaffImpersonationOrSlurs() {
        assertFalse(JevNameScreen.allows("Adm1nJohn", client(0.92d, 0.05d)));
        assertFalse(JevNameScreen.allows("xXslurXx", client(0.05d, 0.88d)));
        assertTrue(JevNameScreen.allows("Gaylord", client(0.02d, 0.31d)), "innocent name with a blocked substring passes");
    }

    @Test
    void nameScreenAllowsWhenUnavailable() {
        JevClient unconfigured = new JevClient(TypeSafeSettings.disabled(), (request, settings) -> {
            throw new AssertionError("must not be called");
        });
        assertTrue(JevNameScreen.allows("Anything", unconfigured));
        assertTrue(JevNameScreen.allows("Anything", null));
    }

    @Test
    void nameScreenRequestAsksTwoNoulsAboutTheName() {
        JevRequest request = JevNameScreen.request("GMHelper");
        assertEquals(2, request.questions().size());
        assertTrue(request.questions().containsKey(JevNameScreen.IMPERSONATES_STAFF));
        assertTrue(request.questions().containsKey(JevNameScreen.OFFENSIVE));
        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) request.state();
        assertEquals("GMHelper", state.get("proposed_character_name"));
    }

    @Test
    void reportTriageReadsSeverityLabelFromLegendAndMapsSecuritySeverity() {
        JevResponse response = new JevResponse("jev", Map.of(
                JevReportTriage.SEVERITY, new JevAnswer("score", null, Map.of("4", 0.7d, "3", 0.3d), null, 3.7d,
                        Map.of("1", "nothing actionable: ...", "2", "minor: ...", "3", "serious: ...", "4", "urgent: ..."), 0.7d),
                JevReportTriage.CATEGORY, new JevAnswer("choice", "rmt_or_advertising",
                        Map.of("rmt_or_advertising", 0.8d), null, null, Map.of(), 0.8d)),
                600L, 0L, 120L);

        Optional<JevReportTriage.Triage> triage = JevReportTriage.triage(response);

        assertTrue(triage.isPresent());
        assertEquals("urgent", triage.get().severityLabel());
        assertEquals("rmt_or_advertising", triage.get().category());
        assertEquals(SecuritySeverity.CRITICAL, JevReportTriage.severityFor(triage.get()));
        assertTrue(triage.get().noticeSuffix().contains("urgent / rmt_or_advertising"));
    }

    @Test
    void reportTriageRequestIncludesChatLogOnlyWhenPresent() {
        JevRequest with = JevReportTriage.request("ganre", "Bob", "he keeps spamming", "Bob: buy mesos at ...");
        JevRequest without = JevReportTriage.request("ganre", "Bob", "hacking", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> withState = (Map<String, Object>) with.state();
        @SuppressWarnings("unchecked")
        Map<String, Object> withoutState = (Map<String, Object>) without.state();
        assertTrue(withState.containsKey("chat_log"));
        assertFalse(withoutState.containsKey("chat_log"));
        assertEquals("score", with.questions().get(JevReportTriage.SEVERITY).type());
        assertEquals("choice", with.questions().get(JevReportTriage.CATEGORY).type());
    }

    private static JevClient client(double staff, double offensive) {
        return new JevClient(SETTINGS, (request, settings) -> new JevResponse("jev", Map.of(
                JevNameScreen.IMPERSONATES_STAFF, new JevAnswer("noul", null, Map.of(), staff, null, Map.of(), null),
                JevNameScreen.OFFENSIVE, new JevAnswer("noul", null, Map.of(), offensive, null, Map.of(), null)),
                200L, 0L, 60L));
    }
}
