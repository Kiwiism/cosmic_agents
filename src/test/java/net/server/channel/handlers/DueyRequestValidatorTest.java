package net.server.channel.handlers;

import client.processor.npc.DueyProcessor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DueyRequestValidatorTest {
    @Test
    void acceptsExactOneShotMutationEnvelope() {
        assertTrue(DueyRequestValidator.hasValidEnvelope(
                DueyProcessor.Actions.TOSERVER_CLAIM_PACKAGE.getCode(), Integer.BYTES));
        assertTrue(DueyRequestValidator.hasValidEnvelope(
                DueyProcessor.Actions.TOSERVER_REMOVE_PACKAGE.getCode(), Integer.BYTES));
    }

    @Test
    void rejectsTruncatedTrailingAndUnknownOperations() {
        assertFalse(DueyRequestValidator.hasValidEnvelope(
                DueyProcessor.Actions.TOSERVER_CLAIM_PACKAGE.getCode(), 3));
        assertFalse(DueyRequestValidator.hasValidEnvelope(
                DueyProcessor.Actions.TOSERVER_CLAIM_PACKAGE.getCode(), 5));
        assertFalse(DueyRequestValidator.hasValidEnvelope((byte) 0x7f, 0));
    }
}
