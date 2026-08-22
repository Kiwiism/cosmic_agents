package server.agents.runtime.activity.control.binding;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;
import server.agents.runtime.activity.world.AgentWorldActivityRequestType;
import server.agents.runtime.activity.world.AgentWorldCompletionPolicy;
import server.agents.runtime.activity.world.AgentWorldDirective;
import server.agents.runtime.activity.world.AgentWorldDirectiveSource;
import server.agents.runtime.activity.world.AgentWorldDirectiveType;
import server.agents.runtime.activity.world.AgentWorldInterruptionPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentWorldDirectiveRequestCompilerTest {
    private final AgentWorldDirectiveRequestCompiler compiler =
            new AgentWorldDirectiveRequestCompiler();

    @Test
    void compilesPlanFieldTownCommerceAndPartyQuestContracts() {
        assertEquals(AgentActivityKind.QUESTING,
                compiler.compile(directive(AgentActivityKind.QUESTING,
                        AgentWorldActivityRequestType.AUTHORED_PLAN, Map.of())).kind());
        assertEquals(AgentActivityKind.HUNTING,
                compiler.compile(directive(AgentActivityKind.HUNTING,
                        AgentWorldActivityRequestType.FIELD_VISIT, Map.of(
                                "mapId", "100000001", "maximumParticipants", "6",
                                "acceptingQuestVisitors", "true", "restAllowed", "true"))).kind());
        assertEquals(AgentActivityKind.TOWN_LIFE,
                compiler.compile(directive(AgentActivityKind.TOWN_LIFE,
                        AgentWorldActivityRequestType.TOWN_LIFE_VISIT, Map.of(
                                "mapId", "100000000", "purpose", "leisure",
                                "freeTimeBudgetMs", "180000"))).kind());
        Map<String, String> commerce = new LinkedHashMap<>();
        commerce.put("jobFamily", "warrior");
        commerce.put("purpose", "buy_supplies");
        commerce.put("maximumDurationMs", "600000");
        commerce.put("maximumIdleMs", "120000");
        commerce.put("priceMemoryHours", "24");
        for (String key : new String[]{"dailyActivityFraction", "riskTolerance",
                "liquidityPreference", "upgradeAggressiveness", "shoppingPatience",
                "stallWillingness", "negotiationAggressiveness", "chairInterest"}) {
            commerce.put(key, "0.5");
        }
        assertEquals(AgentActivityKind.COMMERCE,
                compiler.compile(directive(AgentActivityKind.COMMERCE,
                        AgentWorldActivityRequestType.COMMERCE_VISIT, commerce)).kind());
        assertEquals(AgentActivityKind.PARTY_QUEST,
                compiler.compile(directive(AgentActivityKind.PARTY_QUEST,
                        AgentWorldActivityRequestType.PARTY_QUEST_VISIT, Map.of(
                                "scenarioId", "kpq", "partySize", "4", "maximumRuns", "1"))).kind());
    }

    @Test
    void rejectsMismatchedOrIncompleteContractsBeforeHandoff() {
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(directive(
                AgentActivityKind.HUNTING, AgentWorldActivityRequestType.TOWN_LIFE_VISIT,
                Map.of("mapId", "100000000", "purpose", "leisure",
                        "freeTimeBudgetMs", "1"))));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(directive(
                AgentActivityKind.HUNTING, AgentWorldActivityRequestType.FIELD_VISIT,
                Map.of("mapId", "100000001"))));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(directive(
                AgentActivityKind.PARTY_QUEST, AgentWorldActivityRequestType.PARTY_QUEST_VISIT,
                Map.of("scenarioId", "kpq", "partySize", "4", "maximumRuns", "2"))));
    }

    private AgentWorldDirective directive(
            AgentActivityKind kind,
            AgentWorldActivityRequestType requestType,
            Map<String, String> parameters) {
        return new AgentWorldDirective(1, "directive-" + kind, 27,
                AgentWorldDirectiveType.START_ACTIVITY, AgentWorldDirectiveSource.OPERATOR,
                null, kind, requestType, "request-" + kind, parameters,
                AgentWorldInterruptionPolicy.WAIT_FOR_SAFE_BOUNDARY,
                AgentWorldCompletionPolicy.REQUEST_NEXT_DECISION, 10, 1_000L, 0L, "test");
    }
}
