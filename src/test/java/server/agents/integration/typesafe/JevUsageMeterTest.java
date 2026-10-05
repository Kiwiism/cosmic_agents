package server.agents.integration.typesafe;

import org.junit.jupiter.api.Test;
import server.agents.integration.typesafe.cost.JevCostProjector;
import server.agents.integration.typesafe.cost.JevRequestSamples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JevUsageMeterTest {
    private static final long START = 1_000_000L;

    @Test
    void attributesTokensAndCostPerKindAndInTotal() {
        JevUsageMeter meter = new JevUsageMeter(START);
        meter.recordSuccess("chat-intent", response(3_500L, 500L));
        meter.recordSuccess("chat-intent", response(3_300L, 300L));
        meter.recordSuccess("name-screen", response(470L, 200L));
        meter.recordFailure("name-screen");

        assertEquals(4L, meter.requests());
        assertEquals(3L, meter.successes());
        assertEquals(1L, meter.failures());
        assertEquals(7_270L, meter.inputTokens());
        JevUsageMeter.KindUsage chat = meter.kind("chat-intent");
        assertEquals(2L, chat.successes());
        assertEquals(3_400L, chat.averageInputTokens());
        assertEquals(400L, chat.averageLatencyMs());
        assertEquals(500L, chat.maxLatencyMs());
        JevUsageMeter.KindUsage names = meter.kind("name-screen");
        assertEquals(2L, names.requests());
        assertEquals(1L, names.failures());
        assertEquals(List.of("chat-intent", "name-screen"), meter.kinds().stream().map(JevUsageMeter.KindUsage::kind).toList());

        double expectedCost = 7_270L / 1_000_000.0d * JevCostModel.priceUsdPerMillionInputTokens();
        assertEquals(expectedCost, meter.costUsd(), 1e-12);
        assertTrue(meter.diagnostics().contains("cost=$"));
    }

    @Test
    void observedRateProjectsSpendPerDay() {
        JevUsageMeter meter = new JevUsageMeter(START);
        meter.recordSuccess("chat-intent", response(6_000L, 100L));
        long oneHourLater = START + 3_600_000L;

        assertEquals(6_000.0d, meter.observedInputTokensPerHour(oneHourLater), 1e-9);
        double perDay = JevCostModel.costUsd(6_000L * 24L);
        assertEquals(perDay, meter.projectedUsdPerDay(oneHourLater), 1e-12);
        List<String> report = meter.report(oneHourLater);
        assertTrue(report.get(0).contains("over 60 min"));
        assertTrue(report.get(1).contains("6000 tokens/hour"));
        assertEquals(3, report.size(), "header, rate line, one kind line");
        assertEquals(2, meter.csvRows(oneHourLater).size(), "one row per kind plus the total row");
        assertTrue(meter.csvRows(oneHourLater).get(1).startsWith("1970-01-01T01:16:40Z,total,1,1,0,6000"));
    }

    @Test
    void projectionMultipliesScenarioVolumesByTokensPerRequest() {
        JevCostProjector.Scenario scenario = new JevCostProjector.Scenario(
                10, 4.0d, 10.0d, 0.5d, 0.0d, 0.0d, 0.0d, 0.0d, 2.0d, 0.0d, 0.0d);
        Map<String, Long> tokens = Map.of("chat-intent", 3_500L, "name-screen", 470L);

        JevCostModel.Projection projection = JevCostProjector.project(scenario, tokens);

        JevCostModel.Line chat = projection.lines().get("chat-intent");
        assertEquals(200.0d, chat.requestsPerDay(), 1e-9, "10 bots x 4/h x 10h x 50% misses");
        assertEquals(700_000.0d, chat.tokensPerDay(), 1e-9);
        assertEquals(JevCostModel.costUsd(700_000L), chat.usdPerDay(), 1e-12);
        assertEquals(2.0d, projection.lines().get("name-screen").requestsPerDay(), 1e-9);
        assertEquals(0.0d, projection.lines().get("report-triage").usdPerDay(), 1e-12);
        assertEquals(projection.usdPerDay() * 30.0d, projection.usdPerMonth(), 1e-12);
        assertTrue(JevCostProjector.render(scenario, JevCostProjector.estimateOffline()).stream()
                .anyMatch(line -> line.startsWith("TOTAL")));
    }

    @Test
    void samplesCoverEveryJudgmentKindWithProductionShapes() {
        Map<String, JevRequest> samples = JevRequestSamples.all();
        assertEquals(7, samples.size());
        samples.forEach((kind, request) -> {
            assertEquals(kind, request.kind(), "sample tagged with its kind");
            assertTrue(request.questions().size() >= 1);
        });
        assertEquals(1 + 7 + 1, samples.get("chat-intent").questions().size(), "intent + 7 arguments + addressed");
        assertEquals(5, samples.get("director-rank").questions().size());
    }

    private static JevResponse response(long inputTokens, long latencyMs) {
        return new JevResponse("jev-1.13.0", Map.of(), inputTokens, 0L, latencyMs);
    }
}
