package server.agents.capabilities.partyquest.epq;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.partyquest.AgentPartyQuestCatalog;
import server.agents.capabilities.partyquest.AgentPartyQuestRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentEpqDefinitionTest {
    @Test
    void publishesTheLocalEllinPqContract() {
        var definition = AgentPartyQuestCatalog.require("EPQ");
        assertEquals("EllinPQ", definition.eventManagerName());
        assertEquals(AgentEpqDefinition.RECRUIT_MAP, definition.recruitMapId());
        assertEquals(AgentEpqDefinition.ENTRANCE_MAP, definition.entryMapId());
        assertEquals(AgentEpqDefinition.REWARD_MAP, definition.clearMapId());
        assertTrue(definition.acceptsPartySize(4));
        assertTrue(definition.acceptsPartySize(6));
        assertFalse(definition.acceptsPartySize(3));
        assertTrue(definition.acceptsLevel(44));
        assertTrue(definition.acceptsLevel(55));
        assertEquals("epq", AgentPartyQuestRuntime.requireSystem("epq").definition().questKey());
    }

    @Test
    void recognizesOnlyAuthoredEpqMapsAndStages() {
        assertFalse(AgentEpqDefinition.isEventMap(AgentEpqDefinition.RECRUIT_MAP));
        assertTrue(AgentEpqDefinition.isEventMap(AgentEpqDefinition.STAGE_FIVE_MAP));
        assertEquals(4, AgentEpqDefinition.stageForMap(AgentEpqDefinition.STAGE_FOUR_MAP));
        assertEquals(7, AgentEpqDefinition.stageForMap(AgentEpqDefinition.REWARD_MAP));
        assertEquals(-1, AgentEpqDefinition.stageForMap(930_000_700));
        assertTrue(AgentEpqDefinition.BOSS_COMBAT_TARGETS.containsAll(AgentEpqDefinition.BOSS_MOBS));
        assertTrue(AgentEpqDefinition.BOSS_COMBAT_TARGETS.containsAll(AgentEpqDefinition.BOSS_ADDS));
        assertFalse(AgentEpqDefinition.BOSS_COMBAT_TARGETS.contains(AgentEpqDefinition.POST_DEATH_DUMMY));
    }
}
