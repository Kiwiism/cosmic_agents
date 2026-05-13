package server.bots.llm;

import client.Character;
import server.bots.BotEntry;

import java.util.List;

public final class PromptBuilder {
    private PromptBuilder() {}

    public static String buildSystem(BotEntry entry, SenderRelation relation, String senderName) {
        Character bot = entry.getBot();
        String botName = bot != null ? bot.getName() : "bot";
        String job = bot != null ? bot.getJob().toString().toLowerCase().replace('_', ' ') : "adventurer";
        int lvl = bot != null ? bot.getLevel() : 1;

        String relationLine = switch (relation) {
            case OWNER -> "You are talking to your close gaming friend and party leader " + senderName + ". Be friendly and helpful.";
            case PARTY -> "You are talking to " + senderName + ", your party member. Be casual.";
            case STRANGER -> "You are talking to " + senderName + ", a stranger you just met in the game.";
        };

        return "Your IGN is " + botName + ", roleplay as a player playing MapleStory. "
                + "You are a level " + lvl + " " + job + ". "
                + "Style: casual, lowercase, ONE short sentence, game chat style. "
                + "You are an extension if a bot that only does chatting, game actions like farming, "
                + "following, and looting are already done automatically. "
                + "Any thing you reply is just casual chatting and you can not act on tasks unless given in game command. "
                + relationLine;
    }

    public static String buildPrompt(BotEntry entry, String senderName, String newMessage,
                                     String summary, List<BotMemoryStore.Turn> recent) {
        StringBuilder sb = new StringBuilder(512);
        if (summary != null && !summary.isBlank()) {
            sb.append("What you remember: ").append(summary).append("\n\n");
        }
        if (recent != null && !recent.isEmpty()) {
            sb.append("Recent chat:\n");
            String botName = entry.getBot() != null ? entry.getBot().getName() : "bot";
            for (BotMemoryStore.Turn t : recent) {
                sb.append(t.sender()).append(": ").append(t.msg()).append("\n");
                sb.append(botName).append(": ").append(t.reply()).append("\n");
            }
            sb.append('\n');
        }
        sb.append(senderName).append(": ").append(newMessage).append("\n");
        sb.append(entry.getBot() != null ? entry.getBot().getName() : "bot").append(":");
        return sb.toString();
    }
}
