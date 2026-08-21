package server.agents.capabilities.expedition.balrog;

import org.junit.jupiter.api.Test;
import server.agents.field.AgentBalrogTestFixtureService;
import server.expeditions.ExpeditionType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBalrogDefinitionTest {
    @Test
    void easyRunSelectsSixLevel60Members() {
        assertEquals(6, AgentBalrogTestFixtureService.ROSTER_SIZE);
        assertEquals(6, new AgentEasyBalrogScenario(1234L).roster().size());
        assertEquals(60, AgentBalrogDefinition.LEVEL);
    }

    @Test
    void combatTargetsExcludeScriptKilledReleaseSealAndDisabledCorpse() {
        assertEquals(java.util.Set.of(8830007, 8830008, 8830009),
                AgentBalrogDefinition.COMBAT_MOBS);
        assertEquals(java.util.Set.of(8830008, 8830009), AgentBalrogDefinition.CLAW_MOBS);
        assertEquals(8830007, AgentBalrogDefinition.BODY_MOB);
    }

    @Test
    void definitionMatchesEasyBalrogScriptMapsAndNpc() {
        assertEquals(105100100, AgentBalrogDefinition.RECRUIT_MAP);
        assertEquals(105100400, AgentBalrogDefinition.BATTLE_MAP);
        assertEquals(105100401, AgentBalrogDefinition.CLEAR_MAP);
        assertEquals(1061014, AgentBalrogDefinition.ENTRY_NPC);
    }

    @Test
    void scenarioUsesTheEasyNpcRegistrationAndStartSequence() {
        var spec = new AgentEasyBalrogScenario(1234L).spec();

        assertEquals(ExpeditionType.BALROG_EASY, spec.expeditionType());
        assertEquals(List.of(1, 1), spec.createSelections());
        assertEquals(List.of(1), spec.joinSelections());
        assertEquals(List.of(1, 2, 0), spec.startSelections());
    }

    @Test
    void normalEventExitReturnsClearedMembersToTheEntrance() throws Exception {
        String script = Files.readString(Path.of("scripts/event/BalrogBattle_Easy.js"));

        assertTrue(script.contains("var exitMap = 105100100;"));
        assertTrue(script.contains("player.changeMap(exitMap, 0);"));
    }
}
