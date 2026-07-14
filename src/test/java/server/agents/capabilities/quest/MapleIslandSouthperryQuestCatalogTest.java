package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.quest.Quest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandSouthperryQuestCatalogTest {
    @Test
    void catalogMatchesVerifiedModernPostAmherstQuestSequence() {
        assertEquals(Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046,
                        8020, 8021, 8022, 8023, 8024, 8025),
                MapleIslandSouthperryQuestCatalog.requiredQuestIdSet());
        assertEquals(Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045,
                        8020, 8021, 8022, 8023, 8024, 8025),
                MapleIslandSouthperryQuestCatalog.completedQuestIdSet());
        assertEquals("Yoona's Quiz on Shopping : Start",
                MapleIslandSouthperryQuestCatalog.find(8020).orElseThrow().questName());
        assertEquals("Biggs's Story on Victoria Island.",
                MapleIslandSouthperryQuestCatalog.find(1046).orElseThrow().questName());
        assertEquals("Yoona's Quiz on Shopping 5",
                MapleIslandSouthperryQuestCatalog.find(8025).orElseThrow().questName());
        assertEquals(20002,
                MapleIslandSouthperryQuestCatalog.find(1046).orElseThrow().startNpc().id());
    }

    @Test
    void southperryScopeAllowsQuestInteractionButBlocksShanksTransport() {
        AmherstScopePolicy policy = AmherstScopePolicy.southperry();

        assertTrue(policy.checkQuest(1046).allowed());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_QUEST,
                policy.checkQuest(1028).status());
        assertFalse(policy.checkQuest(1007).allowed());
        assertTrue(policy.checkNpcTravel(22000).allowed());
        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_NPC,
                policy.checkNpcTransport(22000).status());
    }

    @Test
    void routeUsesRealMapEdgesAndTheVerifiedMaiScriptPortal() {
        AmherstScopePolicy policy = AmherstScopePolicy.southperry();

        assertEquals(1010000, policy.nextHopMap(1000000, 2000000));
        assertEquals(1020000, policy.nextHopMap(1010000, 2000000));
        assertEquals(2000000, policy.nextHopMap(1020000, 2000000));
        assertEquals(1010100, policy.nextHopMap(1010000, 1010100));
        assertEquals(1, policy.scriptedPortalId(1010000, 1010100));
        assertEquals(1, policy.scriptedPortalId(1010000, 1010400));
        assertEquals(null, policy.scriptedPortalId(1000000, 1010000));
        assertFalse(policy.checkMap(104000000).allowed());
    }

    @Test
    void yoonaShoppingGuideAloneDoesNotMeetQuest8020StartRequirements() {
        Quest quest = Quest.getInstance(8020);

        assertEquals(1, quest.getStartItemAmountNeeded(1042003));
        assertEquals(1, quest.getCompleteItemAmountNeeded(4031180));
        assertTrue(MapleIslandSouthperryQuestCatalog.isRequiredQuest(8020));
    }

    @Test
    void everyFollowupQuizStillRequiresTheLegacyMaleStarterTop() {
        for (int questId = 8021; questId <= 8025; questId++) {
            assertEquals(1, Quest.getInstance(questId).getStartItemAmountNeeded(1042003),
                    "unexpected starter-top requirement for quest " + questId);
        }
    }
}
