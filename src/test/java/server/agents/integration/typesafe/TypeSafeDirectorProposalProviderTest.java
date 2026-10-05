package server.agents.integration.typesafe;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.control.AgentDirectorAction;
import server.agents.runtime.activity.control.AgentDirectorActionAvailability;
import server.agents.runtime.activity.control.AgentDirectorExecutiveView;
import server.agents.runtime.activity.control.chat.AgentDirectorDomainContext;
import server.agents.runtime.activity.control.chat.AgentDirectorModelAdvice;
import server.agents.runtime.activity.control.chat.AgentDirectorModelSelection;
import server.agents.runtime.activity.control.chat.AgentDirectorProposalProvider;
import server.agents.runtime.activity.control.chat.AgentDirectorRankedSelection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TypeSafeDirectorProposalProviderTest {
    private static final TypeSafeSettings SETTINGS = new TypeSafeSettings(
            "key", TypeSafeSettings.DEFAULT_ENDPOINT, "jev-latest", 1_000);

    @Test
    void selectsAConfidentExecutableActionAndExplainsIt() {
        AtomicReference<JevRequest> sent = new AtomicReference<>();
        JevClient client = new JevClient(SETTINGS, (request, settings) -> {
            sent.set(request);
            return response(Map.of(
                    TypeSafeDirectorProposalProvider.ACTION_QUESTION, choice("resupply", 0.88),
                    TypeSafeDirectorProposalProvider.ACTIONABLE_QUESTION, noul(0.95)));
        });
        AgentDirectorExecutiveView view = view(action("resupply", "Send Agents to resupply potions", false),
                action("train", "Move Agents to a training map", false),
                action("wipe", "Reset every Agent", true));
        FallbackProvider fallback = new FallbackProvider();

        AgentDirectorModelSelection selection = new TypeSafeDirectorProposalProvider(client, fallback, true, 0.6d)
                .select(view, "get the bots some pots please").orElseThrow();

        assertEquals("resupply", selection.actionId());
        assertTrue(selection.rationale().contains("resupply potions"));
        assertEquals("typesafe:jev-latest", selection.provider());
        assertEquals(0, fallback.selectCalls.get());
        @SuppressWarnings("unchecked")
        Map<String, Object> criteria = (Map<String, Object>) sent.get().questions()
                .get(TypeSafeDirectorProposalProvider.ACTION_QUESTION).toJson().get("criteria");
        assertTrue(criteria.containsKey("none_of_these"));
        assertTrue(String.valueOf(criteria.get("wipe")).contains("destructive"));
    }

    @Test
    void unconfidentOrNoneOfTheseDefersToFallback() {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> response(Map.of(
                TypeSafeDirectorProposalProvider.ACTION_QUESTION, choice("none_of_these", 0.9))));
        FallbackProvider fallback = new FallbackProvider();

        Optional<AgentDirectorModelSelection> selection = new TypeSafeDirectorProposalProvider(client, fallback, true, 0.6d)
                .select(view(action("a", "A", false), action("b", "B", false)), "hello there");

        assertTrue(selection.isEmpty());
        assertEquals(1, fallback.selectCalls.get());
    }

    @Test
    void ranksTrainingMapsByScoreThenCatalogRank() {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> response(Map.of(
                "fit_0", score(2.1, 0.6),
                "fit_1", score(3.7, 0.8),
                "fit_2", score(3.7, 0.7))));
        AgentDirectorDomainContext context = context(
                candidate("m0", "Henesys Hunting Ground I", 1),
                candidate("m1", "Ant Tunnel", 3),
                candidate("m2", "Kerning Subway", 2));

        AgentDirectorModelAdvice advice = new TypeSafeDirectorProposalProvider(client, new FallbackProvider(), true, 0.6d)
                .recommendTrainingMaps(mock(AgentDirectorExecutiveView.class), "somewhere good for level 25", context)
                .orElseThrow();

        List<String> order = advice.selections().stream().map(AgentDirectorRankedSelection::actionId).toList();
        assertEquals(List.of("m2", "m1"), order, "tie on score broken by catalog rank; requestedCount caps at 2");
        assertTrue(advice.selections().get(0).rationale().contains("Kerning Subway"));
        assertTrue(advice.selections().get(0).rationale().contains("ideal fit"));
    }

    @Test
    void confidentActionWithoutActionableRequestDefersToFallback() {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> response(Map.of(
                TypeSafeDirectorProposalProvider.ACTION_QUESTION, choice("a", 0.95),
                TypeSafeDirectorProposalProvider.ACTIONABLE_QUESTION, noul(0.1))));
        FallbackProvider fallback = new FallbackProvider();

        assertTrue(new TypeSafeDirectorProposalProvider(client, fallback, true, 0.6d)
                .select(view(action("a", "A", false), action("b", "B", false)), "what does A do?").isEmpty());
        assertEquals(1, fallback.selectCalls.get());
    }

    @Test
    void uncertainTrainingRankingDefersToFallback() {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> response(Map.of(
                "fit_0", score(3.0, 0.1))));

        assertTrue(new TypeSafeDirectorProposalProvider(client, new FallbackProvider(), true, 0.6d)
                .recommendTrainingMaps(mock(AgentDirectorExecutiveView.class), "a good training map",
                        context(candidate("m0", "Ant Tunnel", 1))).isEmpty());
    }

    @Test
    void disabledProviderIsTransparent() {
        JevClient client = new JevClient(SETTINGS, (request, settings) -> {
            throw new AssertionError("must not be called");
        });
        FallbackProvider fallback = new FallbackProvider();

        new TypeSafeDirectorProposalProvider(client, fallback, false, 0.6d)
                .select(view(action("a", "A", false), action("b", "B", false)), "do a");

        assertEquals(1, fallback.selectCalls.get());
    }

    private static AgentDirectorExecutiveView view(AgentDirectorAction... actions) {
        AgentDirectorExecutiveView view = mock(AgentDirectorExecutiveView.class);
        when(view.actions()).thenReturn(List.of(actions));
        return view;
    }

    private static AgentDirectorAction action(String id, String label, boolean destructive) {
        AgentDirectorAction action = mock(AgentDirectorAction.class);
        when(action.actionId()).thenReturn(id);
        when(action.label()).thenReturn(label);
        when(action.destructive()).thenReturn(destructive);
        when(action.availability()).thenReturn(AgentDirectorActionAvailability.values()[0]);
        return action;
    }

    private static AgentDirectorDomainContext context(AgentDirectorDomainContext.TrainingMapCandidate... candidates) {
        AgentDirectorDomainContext context = mock(AgentDirectorDomainContext.class);
        when(context.trainingMaps()).thenReturn(List.of(candidates));
        when(context.requestedLevel()).thenReturn(25);
        when(context.agentLevel()).thenReturn(24);
        when(context.requestedCount()).thenReturn(2);
        return context;
    }

    private static AgentDirectorDomainContext.TrainingMapCandidate candidate(String id, String name, int rank) {
        AgentDirectorDomainContext.TrainingMapCandidate candidate = mock(AgentDirectorDomainContext.TrainingMapCandidate.class);
        when(candidate.actionId()).thenReturn(id);
        when(candidate.mapName()).thenReturn(name);
        when(candidate.catalogRank()).thenReturn(rank);
        when(candidate.selectable()).thenReturn(true);
        when(candidate.recommendedMinLevel()).thenReturn(20);
        when(candidate.recommendedMaxLevel()).thenReturn(30);
        when(candidate.recommendedAgents()).thenReturn(3);
        when(candidate.maximumAgents()).thenReturn(6);
        when(candidate.terrain()).thenReturn("flat");
        when(candidate.tags()).thenReturn(List.of("beginner"));
        when(candidate.hazards()).thenReturn(List.of());
        when(candidate.catalogRationale()).thenReturn("dense spawns");
        return candidate;
    }

    private static JevResponse response(Map<String, JevAnswer> answers) {
        return new JevResponse("jev-1.13.0", new LinkedHashMap<>(answers), 1_200L, 0L, 110L);
    }

    private static JevAnswer choice(String option, double confidence) {
        return new JevAnswer("choice", option, Map.of(option, confidence), null, null, Map.of(), confidence);
    }

    private static JevAnswer noul(double probability) {
        return new JevAnswer("noul", null, Map.of(), probability, null, Map.of(), null);
    }

    private static JevAnswer score(double score, double confidence) {
        Map<String, String> legend = Map.of("1", "poor fit: ...", "2", "partial fit: ...", "3", "good fit: ...", "4", "ideal fit: ...");
        String top = String.valueOf((int) Math.round(score));
        return new JevAnswer("score", null, Map.of(top, confidence), null, score, legend, confidence);
    }

    private static final class FallbackProvider implements AgentDirectorProposalProvider {
        final AtomicInteger selectCalls = new AtomicInteger();

        @Override
        public Optional<AgentDirectorModelSelection> select(AgentDirectorExecutiveView view, String operatorPrompt) {
            selectCalls.incrementAndGet();
            return Optional.empty();
        }
    }
}
