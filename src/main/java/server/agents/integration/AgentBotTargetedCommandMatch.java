package server.agents.integration;

import server.bots.BotEntry;

public record AgentBotTargetedCommandMatch(BotEntry entry, String commandText, String feedbackMessage) {
}
