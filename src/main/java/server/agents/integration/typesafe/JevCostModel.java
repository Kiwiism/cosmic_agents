package server.agents.integration.typesafe;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns token counts into money and daily volumes into projections. Only input tokens are
 * billed; the published price lives in {@code agent-engine.yaml} so a price change is a config
 * edit, not a code change.
 */
public final class JevCostModel {
    private static final double PRICE_USD_PER_MILLION_INPUT_TOKENS = config.AgentTuning.doubleValue(
            "server.agents.integration.typesafe.JevCostModel.PRICE_USD_PER_MILLION_INPUT_TOKENS");

    private JevCostModel() {
    }

    public static double priceUsdPerMillionInputTokens() {
        return PRICE_USD_PER_MILLION_INPUT_TOKENS;
    }

    public static double costUsd(long inputTokens) {
        return inputTokens / 1_000_000.0d * PRICE_USD_PER_MILLION_INPUT_TOKENS;
    }

    /** One line of a projection: how many requests of a kind per day and what they cost. */
    public record Line(String kind, double requestsPerDay, long inputTokensPerRequest) {
        public double tokensPerDay() {
            return requestsPerDay * inputTokensPerRequest;
        }

        public double usdPerDay() {
            return costUsd(Math.round(tokensPerDay()));
        }
    }

    public record Projection(Map<String, Line> lines) {
        public Projection {
            lines = Collections.unmodifiableMap(new LinkedHashMap<>(lines));
        }

        public double requestsPerDay() {
            return lines.values().stream().mapToDouble(Line::requestsPerDay).sum();
        }

        public double tokensPerDay() {
            return lines.values().stream().mapToDouble(Line::tokensPerDay).sum();
        }

        public double usdPerDay() {
            return lines.values().stream().mapToDouble(Line::usdPerDay).sum();
        }

        public double usdPerMonth() {
            return usdPerDay() * 30.0d;
        }
    }

    /** Price per million tokens keeps three decimals so $0.042 does not print as $0.04. */
    public static String price(double usdPerMillion) {
        return String.format(Locale.ROOT, "$%.3f", usdPerMillion);
    }

    public static String money(double usd) {
        if (usd < 0.01d) {
            return String.format(Locale.ROOT, "$%.4f", usd);
        }
        return String.format(Locale.ROOT, "$%.2f", usd);
    }
}
