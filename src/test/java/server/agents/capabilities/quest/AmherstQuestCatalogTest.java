package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmherstQuestCatalogTest {
    private static final Set<Integer> REQUIRED_QUEST_IDS = Set.of(
            1000, 1001, 1003, 1004, 1005, 1006, 1008, 1009,
            1010, 1011, 1012, 1013, 1014, 1015, 1020, 1021,
            1025, 1029, 1030, 1031, 1032, 1033, 1034, 1037,
            1038, 8031);

    @Test
    void catalogContainsCoveredAmherstQuests() {
        assertEquals(REQUIRED_QUEST_IDS, AmherstQuestCatalog.requiredQuestIdSet());
        assertEquals(REQUIRED_QUEST_IDS.size(), AmherstQuestCatalog.allRequiredQuests().size());
    }

    @Test
    void catalogPinsNpcNamesAndSpecialHandling() {
        assertNpc(1000, "Borrowing Sera's Mirror", "Heena", "Sera");
        assertNpc(1021, "Roger's Apple", "Roger", "Roger");
        assertNpc(1003, "What Sen wants to eat", "Nina", "Sen");
        assertNpc(1037, "Help Hunt the Snails", "Sam", "Maria");
        assertNpc(1038, "Maria's Letter", "Maria", "Lucas");
        assertNpc(1008, "Pio's Collecting Recycled Goods", "Pio", "Pio");
        assertNpc(8031, "Protect Lucas's Farm", "Lucas", "Lucas");

        AmherstQuestDefinition quiz = AmherstQuestCatalog.find(1009).orElseThrow();
        assertEquals("Rain", quiz.startNpc().name());
        assertTrue(quiz.flags().contains(AmherstQuestFlag.QUIZ));

        AmherstQuestDefinition autoComplete = AmherstQuestCatalog.find(1030).orElseThrow();
        assertEquals(AmherstQuestCompletionType.AUTO_COMPLETE, autoComplete.completionType());
        assertTrue(autoComplete.flags().contains(AmherstQuestFlag.AUTO_COMPLETE));
    }

    @Test
    void scopePolicyAllowsCoveredAndBlocksExcludedOrLaterQuests() {
        AmherstScopePolicy policy = new AmherstScopePolicy();

        assertTrue(policy.checkQuest(1000).allowed());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST, policy.checkQuest(1018).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST, policy.checkQuest(1035).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, policy.checkQuest(1019).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, policy.checkQuest(1028).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE, policy.checkQuest(1040).status());
    }

    @Test
    void scopePolicyBlocksOffScopeTravel() {
        AmherstScopePolicy policy = new AmherstScopePolicy();

        assertTrue(policy.checkMap(10000).allowed());
        assertTrue(policy.checkMap(1000000).allowed());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP, policy.checkMap(1010000).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP, policy.checkMap(2000000).status());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                policy.checkNpcTravel(AmherstQuestCatalog.SHANKS_NPC_ID).status());
    }

    @Test
    void guardedResetHarnessRefusesRealCharactersAndOnlyAcceptsCoveredQuestScenarios() {
        GuardedAmherstTestResetHarness disabled = new GuardedAmherstTestResetHarness(false, Set.of(1), Set.of());
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                disabled.validate(new AmherstTestResetRequest(1, "AmherstTestAgent",
                        AmherstTestResetMode.CLEAN_LV1_START, 0)).status());

        GuardedAmherstTestResetHarness harness = new GuardedAmherstTestResetHarness(true, Set.of(1),
                Set.of("AmherstTestAgent"));
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                harness.validate(new AmherstTestResetRequest(2, "RealCharacter",
                        AmherstTestResetMode.CLEAN_LV1_START, 0)).status());
        assertTrue(harness.validate(new AmherstTestResetRequest(1, "RealCharacter",
                AmherstTestResetMode.CLEAN_LV1_START, 0)).allowed());
        assertTrue(harness.validate(new AmherstTestResetRequest(2, "AmherstTestAgent",
                AmherstTestResetMode.QUEST_SCENARIO, 1037)).allowed());
        assertEquals(AgentCapabilityStatus.BLOCKED_BY_SCOPE,
                harness.validate(new AmherstTestResetRequest(2, "AmherstTestAgent",
                        AmherstTestResetMode.QUEST_SCENARIO, 1028)).status());
        assertEquals(AgentCapabilityStatus.NOT_READY,
                harness.reset(new AmherstTestResetRequest(1, "AmherstTestAgent",
                        AmherstTestResetMode.AMHERST_MVP_CLEAN, 0)).status());
    }

    @Test
    void amherstPlanCardValidatesAgainstCatalog() throws IOException {
        String plan = Files.readString(Path.of("docs/agents/plans/maple-island-amherst-subphase.plan.json"));

        assertTrue(plan.contains("\"planId\": \"maple-island-amherst-subphase\""));
        assertTrue(plan.contains("\"requiredStartMapId\": 10000"));
        assertTrue(plan.contains("\"finalMapId\": 1000000"));
        assertTrue(plan.contains("\"npcId\": 22000"));
        assertTrue(plan.contains("\"mapId\": 1010000"));

        Set<Integer> requiredIds = extractRequiredQuestIds(plan);
        assertEquals(REQUIRED_QUEST_IDS, requiredIds);

        for (Integer questId : extractAllQuestReferences(plan)) {
            if (REQUIRED_QUEST_IDS.contains(questId)) {
                assertTrue(AmherstQuestCatalog.isRequiredQuest(questId));
            }
        }

        assertTrue(plan.contains("\"quest-start\", \"questId\": 8031"));
        assertTrue(plan.contains("\"kill-mobs\", \"forQuestId\": 8031"));
        assertTrue(plan.contains("\"quest-complete\", \"questId\": 8031"));
        assertFalse(plan.contains("\"questId\": 1028"));
    }

    private static void assertNpc(int questId, String name, String startNpc, String completeNpc) {
        AmherstQuestDefinition definition = AmherstQuestCatalog.find(questId).orElse(null);
        assertNotNull(definition);
        assertEquals(name, definition.questName());
        assertEquals(startNpc, definition.startNpc().name());
        assertEquals(completeNpc, definition.completeNpc().name());
    }

    private static Set<Integer> extractRequiredQuestIds(String plan) {
        Matcher blockMatcher = Pattern.compile("\"requiredQuestIds\"\\s*:\\s*\\[(?<ids>[^]]*)]",
                Pattern.DOTALL).matcher(plan);
        assertTrue(blockMatcher.find());
        return extractNumbers(blockMatcher.group("ids"));
    }

    private static Set<Integer> extractAllQuestReferences(String plan) {
        Matcher matcher = Pattern.compile("\"(?:questId|forQuestId)\"\\s*:\\s*(\\d+)").matcher(plan);
        Set<Integer> ids = new HashSet<>();
        while (matcher.find()) {
            ids.add(Integer.parseInt(matcher.group(1)));
        }
        Matcher questIdsMatcher = Pattern.compile("\"questIds\"\\s*:\\s*\\[(?<ids>[^]]*)]",
                Pattern.DOTALL).matcher(plan);
        while (questIdsMatcher.find()) {
            ids.addAll(extractNumbers(questIdsMatcher.group("ids")));
        }
        return ids;
    }

    private static Set<Integer> extractNumbers(String text) {
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        Set<Integer> numbers = new HashSet<>();
        while (matcher.find()) {
            numbers.add(Integer.parseInt(matcher.group()));
        }
        return numbers;
    }
}
