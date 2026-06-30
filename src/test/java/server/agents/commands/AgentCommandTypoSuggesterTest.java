package server.agents.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentCommandTypoSuggesterTest {

    @Test
    void exactVerbReturnsNullSuggestion() {
        assertNull(AgentCommandTypoSuggester.suggest("farm here"));
        assertNull(AgentCommandTypoSuggester.suggest("FOLLOW me"));
        assertNull(AgentCommandTypoSuggester.suggest("stop"));
    }

    @Test
    void nearMissProducesSuggestion() {
        assertEquals("farm", AgentCommandTypoSuggester.suggest("farn"));
        assertEquals("farm", AgentCommandTypoSuggester.suggest("farn here"));
        assertEquals("follow", AgentCommandTypoSuggester.suggest("folow me"));
        assertEquals("formation", AgentCommandTypoSuggester.suggest("formaton"));
    }

    @Test
    void shortTokensDoNotTriggerSuggestion() {
        assertNull(AgentCommandTypoSuggester.suggest("hi"));
        assertNull(AgentCommandTypoSuggester.suggest("yo"));
        assertNull(AgentCommandTypoSuggester.suggest("ok"));
    }

    @Test
    void unrelatedWordsDoNotTriggerSuggestion() {
        assertNull(AgentCommandTypoSuggester.suggest("hello there"));
        assertNull(AgentCommandTypoSuggester.suggest("zzzzz"));
    }

    @Test
    void boundedLevenshteinExitsEarly() {
        assertEquals(3, AgentCommandTypoSuggester.levenshtein("abc", "xyz", 5));
        assertEquals(6, AgentCommandTypoSuggester.levenshtein("abcdef", "uvwxyz", 5));
    }
}
