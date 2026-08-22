package server.agents.runtime.activity.control.binding;

import client.Character;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import server.agents.integration.AgentEconomyRuntime;
import server.agents.economy.session.EconomySessionPort;
import server.agents.runtime.commerce.AgentCommerceSessionRegistryRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.activity.control.facade.AgentStandardLiveActivityFacades;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentStandardWorldActivityBindingResolverTest {
    @AfterEach
    void clearOptionalEconomyRuntime() {
        AgentCommerceSessionRegistryRuntime.abandonPrepared(27);
        AgentEconomyRuntime.clear();
    }

    @Test
    void bindsEligibleKpqVisitWithoutStartingTheAggregateDuringPreflight() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getLevel()).thenReturn(25);
        AgentWorldDirective directive = new AgentWorldDirective(
                1, "join-kpq", 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, AgentActivityKind.PARTY_QUEST,
                AgentWorldActivityRequestType.PARTY_QUEST_VISIT, "kpq",
                Map.of("scenarioId", "kpq", "partySize", "4", "maximumRuns", "1"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                10, 1_000L, 0L, "join lobby");
        AgentStandardWorldActivityBindingResolver resolver =
                new AgentStandardWorldActivityBindingResolver(
                        new AgentWorldDirectiveRequestCompiler(),
                        AgentStandardLiveActivityFacades.registry());

        AgentWorldActivityBinding binding = resolver.bind(
                directive, mock(AgentRuntimeEntry.class), agent, null, "");

        assertTrue(binding.targetPreflight().inspect(
                "27", AgentActivityKind.PARTY_QUEST, 1_001L).ready());
        assertTrue(AgentStandardWorldActivityBindingResolver.supportedTargets()
                .contains(AgentActivityKind.PARTY_QUEST));
    }

    @Test
    void ordinaryFieldVisitUsesNormalMapTransferWhenDestinationDiffers() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getMapId()).thenReturn(100000000);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        AgentWorldDirective directive = new AgentWorldDirective(
                1, "hunt-field", 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, AgentActivityKind.HUNTING,
                AgentWorldActivityRequestType.FIELD_VISIT, "field-visit",
                Map.of("mapId", "100040001", "intent", "FREE_GRIND",
                        "acceptingQuestVisitors", "true", "maximumParticipants", "6",
                        "restAllowed", "true", "narration", "SUMMARY"),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                10, 1_000L, 0L, "visit field");
        AgentStandardWorldActivityBindingResolver resolver =
                new AgentStandardWorldActivityBindingResolver(
                        new AgentWorldDirectiveRequestCompiler(),
                        AgentStandardLiveActivityFacades.registry(),
                        (liveEntry, liveAgent, destinationMapId, nowMs) -> {
                            assertEquals(entry, liveEntry);
                            assertEquals(agent, liveAgent);
                            assertEquals(100040001, destinationMapId);
                            return server.agents.runtime.activity.session.AgentActivityTransferPort.Result
                                    .pending("normal route", nowMs + 500L);
                        });

        AgentWorldActivityBinding binding = resolver.bind(directive, entry, agent, null, "");

        assertTrue(binding.targetPreflight().inspect(
                "27", AgentActivityKind.HUNTING, 1_001L).ready());
        assertEquals(server.agents.runtime.activity.session.AgentActivityTransferPort.Result.Status.PENDING,
                binding.transfer().advance(1_001L).status());
    }

    @Test
    void commerceVisitUsesInstalledPerAgentSessionOwnerWhenAlreadyInFreeMarket() {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(27);
        when(agent.getMapId()).thenReturn(constants.id.MapId.FM_ENTRANCE);
        AgentRuntimeEntry entry = mock(AgentRuntimeEntry.class);
        EconomySessionPort sessions = mock(EconomySessionPort.class);
        AgentEconomyRuntime.Gateway gateway = mock(AgentEconomyRuntime.Gateway.class);
        when(gateway.available()).thenReturn(true);
        when(gateway.sessionPort()).thenReturn(java.util.Optional.of(sessions));
        AgentEconomyRuntime.install(gateway);
        AgentWorldDirective directive = new AgentWorldDirective(
                1, "commerce-visit", 27, AgentWorldDirectiveType.START_ACTIVITY,
                AgentWorldDirectiveSource.OPERATOR, null, AgentActivityKind.COMMERCE,
                AgentWorldActivityRequestType.COMMERCE_VISIT, "periodic-market-visit",
                Map.ofEntries(
                        Map.entry("jobFamily", "warrior"),
                        Map.entry("dailyActivityFraction", "0.5"),
                        Map.entry("riskTolerance", "0.5"),
                        Map.entry("liquidityPreference", "0.5"),
                        Map.entry("upgradeAggressiveness", "0.5"),
                        Map.entry("shoppingPatience", "0.5"),
                        Map.entry("stallWillingness", "0.5"),
                        Map.entry("priceMemoryHours", "72"),
                        Map.entry("negotiationAggressiveness", "0.5"),
                        Map.entry("chairInterest", "0.5"),
                        Map.entry("purpose", "PERIODIC_MARKET_VISIT"),
                        Map.entry("maximumDurationMs", "300000"),
                        Map.entry("maximumIdleMs", "60000")),
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION,
                10, 1_000L, 0L, "visit market");
        AgentStandardWorldActivityBindingResolver resolver =
                new AgentStandardWorldActivityBindingResolver(
                        new AgentWorldDirectiveRequestCompiler(),
                        AgentStandardLiveActivityFacades.registry());

        AgentWorldActivityBinding binding = resolver.bind(directive, entry, agent, null, "");

        assertTrue(AgentStandardWorldActivityBindingResolver.supportedTargets()
                .contains(AgentActivityKind.COMMERCE));
        assertTrue(binding.targetPreflight().inspect(
                "27", AgentActivityKind.COMMERCE, 1_001L).ready());
        assertEquals(server.agents.runtime.activity.session.AgentActivityTransferPort.Result.Status.READY,
                binding.transfer().advance(1_001L).status());
    }
}
