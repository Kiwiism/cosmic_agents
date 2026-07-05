package server.agents.capabilities.dialogue.llm;

import client.Character;
import server.maps.MapleMap;

public record AgentLlmPromptContext(
        Character agent,
        String botName,
        MapleMap map,
        boolean grinding,
        boolean following,
        boolean farmAnchorInCurrentMap,
        String lastOwnerCommand,
        long lastOwnerCommandAtMs) {
}
