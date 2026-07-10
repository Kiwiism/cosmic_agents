package server.agents.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentCommandNumberParserTest {
    @Test
    void parsesValuesInsideInclusiveRange() {
        assertEquals(1, AgentCommandNumberParser.parseIntInRange("1", 1, 5));
        assertEquals(5, AgentCommandNumberParser.parseIntInRange("5", 1, 5));
    }

    @Test
    void rejectsMalformedOverflowingAndOutOfRangeValues() {
        assertNull(AgentCommandNumberParser.parseIntInRange(null, 1, 5));
        assertNull(AgentCommandNumberParser.parseIntInRange("one", 1, 5));
        assertNull(AgentCommandNumberParser.parseIntInRange("999999999999999999999", 1, 5));
        assertNull(AgentCommandNumberParser.parseIntInRange("-1", 0, 5));
        assertNull(AgentCommandNumberParser.parseIntInRange("6", 1, 5));
    }
}
