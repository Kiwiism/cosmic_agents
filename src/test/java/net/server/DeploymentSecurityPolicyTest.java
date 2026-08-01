package net.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeploymentSecurityPolicyTest {
    @Test
    void localProfileAllowsDevelopmentDefaults() {
        assertDoesNotThrow(() -> DeploymentSecurityPolicy.validate(
                "local", false, false, false, true, "root", "root"));
    }

    @Test
    void productionRejectsRootAndWeakCredentials() {
        assertThrows(IllegalStateException.class, () -> DeploymentSecurityPolicy.validate(
                "production", true, true, false, false, "root", "a-strong-looking-password"));
        assertThrows(IllegalStateException.class, () -> DeploymentSecurityPolicy.validate(
                "production", true, true, false, false, "cosmic", "change-me-local-only"));
    }

    @Test
    void productionRejectsAutomaticRegistrationAndMissingSecondFactors() {
        assertThrows(IllegalStateException.class, () -> DeploymentSecurityPolicy.validate(
                "production", false, true, false, false, "cosmic", "a-strong-unique-database-password"));
        assertThrows(IllegalStateException.class, () -> DeploymentSecurityPolicy.validate(
                "production", true, true, false, true, "cosmic", "a-strong-unique-database-password"));
    }

    @Test
    void productionAcceptsDedicatedCredentialsAndAuthentication() {
        assertDoesNotThrow(() -> DeploymentSecurityPolicy.validate(
                "production", true, true, false, false, "cosmic", "a-strong-unique-database-password"));
    }
}
