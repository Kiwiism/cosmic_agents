package server.bots.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTypoSuggesterTest {

    @Test
    void exactVerbReturnsNullSuggestion() {
        assertNull(CommandTypoSuggester.suggest("farm here"));
        assertNull(CommandTypoSuggester.suggest("FOLLOW me"));
        assertNull(CommandTypoSuggester.suggest("stop"));
    }

    @Test
    void nearMissProducesSuggestion() {
        assertEquals("farm", CommandTypoSuggester.suggest("farn"));
        assertEquals("farm", CommandTypoSuggester.suggest("farn here"));
        assertEquals("follow", CommandTypoSuggester.suggest("folow me"));
        assertEquals("formation", CommandTypoSuggester.suggest("formaton")); // len 8: 2-edit budget
    }

    @Test
    void shortTokensDoNotTriggerSuggestion() {
        assertNull(CommandTypoSuggester.suggest("hi"));
        assertNull(CommandTypoSuggester.suggest("yo"));
        assertNull(CommandTypoSuggester.suggest("ok"));
    }

    @Test
    void unrelatedWordsDoNotTriggerSuggestion() {
        assertNull(CommandTypoSuggester.suggest("hello there"));
        assertNull(CommandTypoSuggester.suggest("zzzzz"));
    }

    @Test
    void firstTokenLooksLikeCommandRecognizesVerbs() {
        assertTrue(CommandTypoSuggester.firstTokenLooksLikeCommand("farm here"));
        assertTrue(CommandTypoSuggester.firstTokenLooksLikeCommand("follow"));
        assertTrue(CommandTypoSuggester.firstTokenLooksLikeCommand("farn here")); // typo close to farm
    }

    @Test
    void firstTokenLooksLikeCommandRejectsChat() {
        assertFalse(CommandTypoSuggester.firstTokenLooksLikeCommand("hello"));
        assertFalse(CommandTypoSuggester.firstTokenLooksLikeCommand("how are you"));
        assertFalse(CommandTypoSuggester.firstTokenLooksLikeCommand("what is your favorite map"));
    }

    @Test
    void boundedLevenshteinExitsEarly() {
        assertEquals(3, CommandTypoSuggester.levenshtein("abc", "xyz", 5));
        assertEquals(6, CommandTypoSuggester.levenshtein("abcdef", "uvwxyz", 5));
    }
}
