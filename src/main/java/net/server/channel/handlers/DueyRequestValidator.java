package net.server.channel.handlers;

import client.processor.npc.DueyProcessor;

final class DueyRequestValidator {
    private DueyRequestValidator() {
    }

    static boolean hasValidEnvelope(byte operation, int remainingBytes) {
        if (operation == DueyProcessor.Actions.TOSERVER_RECV_ITEM.getCode()
                || operation == DueyProcessor.Actions.TOSERVER_CLOSE_DUEY.getCode()) {
            return remainingBytes == 0;
        }
        if (operation == DueyProcessor.Actions.TOSERVER_REMOVE_PACKAGE.getCode()
                || operation == DueyProcessor.Actions.TOSERVER_CLAIM_PACKAGE.getCode()) {
            return remainingBytes == Integer.BYTES;
        }
        if (operation == DueyProcessor.Actions.TOSERVER_SEND_ITEM.getCode()) {
            return remainingBytes >= 12;
        }
        return false;
    }
}
