package server.agents.capabilities.quest;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapleIslandSouthperryQuestCatalogTest {
    @Test
    void catalogMatchesVerifiedModernPostAmherstQuestSequence() {
        assertEquals(Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046),
                MapleIslandSouthperryQuestCatalog.requiredQuestIdSet());
        assertEquals(Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045),
                MapleIslandSouthperryQuestCatalog.completedQuestIdSet());
        assertEquals("Biggs's Story on Victoria Island.",
                MapleIslandSouthperryQuestCatalog.find(1046).orElseThrow().questName());
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
}
