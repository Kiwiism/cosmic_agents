package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.llm.AgentLlmPromptContext;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyRequest;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyService;
import server.agents.capabilities.dialogue.llm.AgentSenderRelation;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotLlmRuntime;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotReplyChannelStateRuntime;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;
import server.maps.MapleMap;

public final class AgentLlmReplyRuntime {
    private AgentLlmReplyRuntime() {
    }

    public static void maybeRespond(AgentRuntimeEntry entry, Character sender, String message) {
        if (entry == null || !AgentBotRuntimeIdentityRuntime.hasBot(entry) || sender == null) {
            return;
        }
        Character agent = AgentBotRuntimeIdentityRuntime.bot(entry);
        AgentLlmReplyService.maybeRespond(replyRequest(entry, agent, sender), sender, message, AgentBotLlmRuntime::replyNow);
    }

    private static AgentLlmReplyRequest<AgentRuntimeEntry> replyRequest(AgentRuntimeEntry entry, Character agent, Character sender) {
        MapleMap map = AgentBotRuntimeIdentityRuntime.botMap(entry);
        return new AgentLlmReplyRequest<>(
                entry,
                AgentBotRuntimeIdentityRuntime.botId(entry),
                AgentBotRuntimeIdentityRuntime.botName(entry),
                AgentBotReplyChannelStateRuntime.replyChannel(entry),
                AgentSenderRelation.resolve(agent, AgentBotRuntimeIdentityRuntime.owner(entry), sender),
                new AgentLlmPromptContext(
                        agent,
                        AgentBotRuntimeIdentityRuntime.hasBot(entry)
                                ? AgentBotRuntimeIdentityRuntime.botName(entry)
                                : "bot",
                        map,
                        AgentBotModeStateRuntime.grinding(entry),
                        AgentBotModeStateRuntime.following(entry),
                        map != null && AgentBotFarmAnchorStateRuntime.isFarmAnchorInMap(entry, map.getId()),
                        AgentBotActivityStateRuntime.lastOwnerCommand(entry),
                        AgentBotActivityStateRuntime.lastOwnerCommandAtMs(entry)));
    }
}
