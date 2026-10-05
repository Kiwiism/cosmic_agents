package server.agents.integration.typesafe;

import java.util.Locale;

/**
 * Rollout mode shared by every System One judgment: {@code OFF} never asks, {@code SHADOW} asks
 * and logs the decision next to the deterministic result without acting, {@code LIVE} acts.
 */
public enum JevJudgmentMode {
    OFF, SHADOW, LIVE;

    public static JevJudgmentMode parse(String value) {
        if (value == null) {
            return OFF;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            return OFF;
        }
    }

    public boolean asks() {
        return this != OFF;
    }

    public boolean acts() {
        return this == LIVE;
    }
}
