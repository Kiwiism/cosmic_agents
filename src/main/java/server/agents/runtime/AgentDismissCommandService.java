package server.agents.runtime;

import client.Character;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentDismissCommandService {
    private static final Pattern DISMISS_PATTERN = Pattern.compile(
            "\\b(dismiss|disown|release)\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);

    private AgentDismissCommandService() {
    }

    public record Hooks(DismissAction dismissAction,
                        LeaderMessage leaderMessage) {
    }

    @FunctionalInterface
    public interface DismissAction {
        boolean dismiss(int leaderCharId, String agentName);
    }

    @FunctionalInterface
    public interface LeaderMessage {
        void send(Character leader, String message);
    }

    public static boolean handleDismissCommand(Character leader, String message, Hooks hooks) {
        Matcher matcher = DISMISS_PATTERN.matcher(message);
        if (!matcher.find()) {
            return false;
        }
        String name = matcher.group(2);
        if (hooks.dismissAction().dismiss(leader.getId(), name)) {
            hooks.leaderMessage().send(leader, "Bot '" + name + "' disowned - now idle.");
        } else {
            hooks.leaderMessage().send(leader, "No bot named '" + name + "' in your group.");
        }
        return true;
    }
}
