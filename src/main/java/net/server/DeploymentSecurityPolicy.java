package net.server;

import java.util.Locale;
import java.util.Set;

final class DeploymentSecurityPolicy {
    private static final Set<String> INSECURE_PASSWORDS = Set.of(
            "", "root", "password", "changeme", "change-me", "change-me-local-only");

    private DeploymentSecurityPolicy() {
    }

    static void validate(String profile, boolean pinEnabled, boolean picEnabled,
                         boolean allowInsecureAuth, boolean automaticRegister,
                         String databaseUser, String databasePassword) {
        if ("local".equalsIgnoreCase(profile)) {
            return;
        }
        if (!"production".equalsIgnoreCase(profile)) {
            throw new IllegalStateException("DEPLOYMENT_PROFILE must be either local or production");
        }
        if ((!pinEnabled || !picEnabled) && !allowInsecureAuth) {
            throw new IllegalStateException(
                    "Production requires ENABLE_PIN and ENABLE_PIC, or an explicit ALLOW_INSECURE_PRODUCTION_AUTH override");
        }
        if (automaticRegister) {
            throw new IllegalStateException("Production must disable AUTOMATIC_REGISTER");
        }
        if (databaseUser == null || "root".equalsIgnoreCase(databaseUser.trim())) {
            throw new IllegalStateException("Production must use a dedicated non-root database user");
        }
        String normalizedPassword = databasePassword == null
                ? ""
                : databasePassword.trim().toLowerCase(Locale.ROOT);
        if (INSECURE_PASSWORDS.contains(normalizedPassword) || normalizedPassword.length() < 16) {
            throw new IllegalStateException("Production requires a non-default database password of at least 16 characters");
        }
    }
}
