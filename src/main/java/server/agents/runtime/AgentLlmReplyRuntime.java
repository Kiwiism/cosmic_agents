package server.agents.runtime;

import client.Character;
import server.agents.capabilities.dialogue.llm.AgentLlmPromptContext;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyRequest;
import server.agents.capabilities.dialogue.llm.AgentLlmReplyService;
import server.agents.capabilities.dialogue.llm.AgentSenderRelation;
import server.agents.integration.AgentActivityStateRuntime;
import server.agents.integration.AgentFarmAnchorStateRuntime;
import server.agents.integration.AgentLlmRuntime;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentReplyChannelStateRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.maps.MapleMap;

public final class AgentLlmReplyRuntime {
    private AgentLlmReplyRuntime() {
    }

    public static void maybeRespond(AgentRuntimeEntry entry, Character sender, String message) {
        if (entry == null || !AgentRuntimeIdentityRuntime.hasBot(entry) || sender == null) {
            return;
        }
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        AgentLlmReplyService.maybeRespond(replyRequest(entry, agent, sender), sender, message, AgentLlmRuntime::replyNow);
    }

    private static AgentLlmReplyRequest<AgentRuntimeEntry> replyRequest(AgentRuntimeEntry entry, Character agent, Character sender) {
        MapleMap map = AgentRuntimeIdentityRuntime.botMap(entry);
        return new AgentLlmReplyRequest<>(
                entry,
                AgentRuntimeIdentityRuntime.botId(entry),
                AgentRuntimeIdentityRuntime.botName(entry),
                AgentReplyChannelStateRuntime.replyChannel(entry),
                AgentSenderRelation.resolve(agent, AgentRuntimeIdentityRuntime.owner(entry), sender),
                new AgentLlmPromptContext(
                        agent,
                        AgentRuntimeIdentityRuntime.hasBot(entry)
                                ? AgentRuntimeIdentityRuntime.botName(entry)
                                : "bot",
                        map,
                        AgentModeStateRuntime.grinding(entry),
                        AgentModeStateRuntime.following(entry),
                        map != null && AgentFarmAnchorStateRuntime.isFarmAnchorInMap(entry, map.getId()),
                        AgentActivityStateRuntime.lastOwnerCommand(entry),
                        AgentActivityStateRuntime.lastOwnerCommandAtMs(entry)));
    }
}
