package server.agents.capabilities.dialogue.llm;

import client.Character;

import java.util.List;

public final class AgentPromptBuilder {
    private AgentPromptBuilder() {}

    public static String buildSystem(Character agent, AgentSenderRelation relation, String senderName) {
        String botName = agent != null ? agent.getName() : "bot";
        String job = agent != null ? agent.getJob().toString().toLowerCase().replace('_', ' ') : "adventurer";
        int lvl = agent != null ? agent.getLevel() : 1;

        String relationLine = switch (relation) {
            case OWNER -> "You are talking to your close gaming friend and party leader " + senderName + ". Be friendly and helpful.";
            case PARTY -> "You are talking to " + senderName + ", your party member. Be casual.";
            case STRANGER -> "You are talking to " + senderName + ", a stranger you just met in the game.";
        };

        return "Your IGN is " + botName + ". "
                + "You are a real human MapleStory player, level " + lvl + " " + job + ". "
                + "Talk like mmo chatter: lowercase, 1 short sentence, abbreviate whenever possible, text only, use internet slang when applicable, no emoji, no speaker label. "
                + "Keep replies simple. Prefer fragments over full grammar. "
                + "Answer only the latest message directly. If unsure, say so briefly instead of guessing. "
                + "Never say you are an ai, assistant, chatbot, or bot. "
                + relationLine;
    }

    public static String buildPrompt(String botName,
                                     String situation,
                                     String senderName,
                                     String newMessage,
                                     String summary,
                                     List<AgentMemoryStore.Turn> recent) {
        StringBuilder sb = new StringBuilder(512);
        if (summary != null && !summary.isBlank()) {
            sb.append("What you remember: ").append(summary).append("\n\n");
        }
        if (!situation.isEmpty()) {
            sb.append(situation).append('\n');
        }
        if (recent != null && !recent.isEmpty()) {
            sb.append("Recent chat (older lines matter less):\n");
            long now = System.currentTimeMillis();
            for (AgentMemoryStore.Turn t : recent) {
                String age = AgentSituationBuilder.ago(now - t.ts());
                sb.append('[').append(age).append(" ago] ")
                        .append(t.sender()).append(": ").append(t.msg()).append('\n');
                sb.append(botName).append(": ").append(t.reply()).append('\n');
            }
            sb.append('\n');
        }
        sb.append("Reply to the newest message only. Treat older chat as background, not the topic.\n");
        sb.append(senderName).append(": ").append(newMessage).append('\n');
        sb.append(botName).append(':');
        return sb.toString();
    }
}
