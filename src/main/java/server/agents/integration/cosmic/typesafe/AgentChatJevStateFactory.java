package server.agents.integration.cosmic.typesafe;

import client.Character;
import constants.game.GameConstants;
import server.agents.capabilities.follow.AgentActivityStateRuntime;
import server.agents.commands.AgentReplyChannel;
import server.agents.integration.typesafe.state.JevState;
import server.agents.integration.typesafe.state.JevStateBands;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cosmic adapter that turns a companion chat moment into System One state blocks. Everything
 * numeric is banded into words, ids become names, and the untrusted player text sits in its own
 * clearly named {@code message} block so criteria can point at it explicitly.
 */
public final class AgentChatJevStateFactory {
    private AgentChatJevStateFactory() {
    }

    public static JevState forCommand(AgentRuntimeEntry entry, Character speaker, String message,
                                      AgentReplyChannel channel, long nowMs) {
        JevState state = JevState.create();
        state.put("message", message == null ? "" : message.trim(), 100);
        state.put("channel", channelName(channel), 70);

        Character bot = entry == null ? null : entry.bot();
        Character owner = entry == null ? null : entry.owner();
        if (speaker != null) {
            Map<String, Object> speakerBlock = new LinkedHashMap<>();
            speakerBlock.put("name", speaker.getName());
            speakerBlock.put("is_owner", owner != null && owner.getId() == speaker.getId());
            speakerBlock.put("level", speaker.getLevel());
            state.put("speaker", speakerBlock, 90);
        }
        if (bot != null) {
            Map<String, Object> botBlock = new LinkedHashMap<>();
            botBlock.put("name", bot.getName());
            botBlock.put("level", bot.getLevel());
            botBlock.put("job", bot.getJob() == null ? "unknown" : GameConstants.getJobName(bot.getJob().getId()));
            botBlock.put("hp", JevStateBands.percentBand(bot.getHp(), bot.getCurrentMaxHp()));
            botBlock.put("mp", JevStateBands.percentBand(bot.getMp(), bot.getCurrentMaxMp()));
            botBlock.put("activity", activity(entry));
            MapleMap map = bot.getMap();
            if (map != null) {
                botBlock.put("map", map.getMapName() + (map.isTown() ? " (a town)" : ""));
            }
            state.put("bot", botBlock, 80);
        }
        if (entry != null) {
            String lastCommand = AgentActivityStateRuntime.lastOwnerCommand(entry);
            if (lastCommand != null && !lastCommand.isBlank()) {
                Map<String, Object> last = new LinkedHashMap<>();
                last.put("text", lastCommand);
                last.put("when", JevStateBands.relativeAge(nowMs - AgentActivityStateRuntime.lastOwnerCommandAtMs(entry)));
                state.put("last_owner_command", last, 60);
            }
        }
        return state;
    }

    static String activity(AgentRuntimeEntry entry) {
        if (entry == null || entry.modeState() == null) {
            return "unknown";
        }
        if (entry.modeState().grinding()) {
            return "hunting monsters";
        }
        if (entry.modeState().following()) {
            return "following its leader";
        }
        return "idle";
    }

    static String channelName(AgentReplyChannel channel) {
        if (channel == null) {
            return "map chat";
        }
        return switch (channel) {
            case PARTY -> "party chat";
            case WHISPER -> "whisper";
            default -> "map chat";
        };
    }
}
