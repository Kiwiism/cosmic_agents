package server.agents.capabilities.partyquest.kpq;

import client.Character;
import server.agents.capabilities.dialogue.AgentChatTextSanitizer;
import server.agents.integration.AgentPacketGatewayRuntime;

/** Required KPQ coordination chat; independent of optional ambient Agent dialogue. */
final class AgentKpqDialogue {
    private AgentKpqDialogue() {
    }

    static void sayMapNow(Character speaker, String message) {
        AgentPacketGatewayRuntime.packets().broadcastChatText(
                speaker, AgentChatTextSanitizer.sanitize(message), false, 0);
    }
}
