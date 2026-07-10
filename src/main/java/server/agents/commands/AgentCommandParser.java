package server.agents.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentCommandParser {
    private static final Pattern TRANSFER_PATTERN = Pattern.compile(
            "\\btransfer\\s+(\\S+)(?:\\s+to)?\\s+(\\S+)\\b", Pattern.CASE_INSENSITIVE);
    private static final int MIN_PREFIX_TARGET_LENGTH = 2;
    private static final int MAX_NUMERIC_TARGET_SLOT = 5;

    private AgentCommandParser() {
    }

    public record AgentTransferCommand(String agentName, String targetName) {
    }

    private record TargetedAgentCommand(String targetToken, String commandText) {
    }

    public record TargetedAgentMatch<T extends AgentCommandTarget>(
            T target,
            String commandText,
            String feedbackMessage) {
    }

    public static AgentTransferCommand matchTransferCommand(String message) {
        Matcher matcher = TRANSFER_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        return new AgentTransferCommand(matcher.group(1), matcher.group(2));
    }

    public static <T extends AgentCommandTarget> TargetedAgentMatch<T> resolveTargetedAgent(
            List<T> targets,
            String message) {
        if (targets == null || targets.isEmpty()) {
            return new TargetedAgentMatch<>(null, null, null);
        }

        TargetedAgentCommand targetedCommand = parseTargetedAgentCommand(message);
        if (targetedCommand == null) {
            return new TargetedAgentMatch<>(null, null, null);
        }

        String targetToken = targetedCommand.targetToken();
        for (T target : targets) {
            if (target.name().equalsIgnoreCase(targetToken)) {
                return new TargetedAgentMatch<>(target, targetedCommand.commandText(), null);
            }
        }

        if (isNumericTarget(targetToken)) {
            Integer slot = AgentCommandNumberParser.parseIntInRange(targetToken, 1, MAX_NUMERIC_TARGET_SLOT);
            if (slot == null) {
                return new TargetedAgentMatch<>(null, null, null);
            }
            if (slot > targets.size()) {
                return new TargetedAgentMatch<>(null, null, "No bot in slot " + slot + ".");
            }
            return new TargetedAgentMatch<>(targets.get(slot - 1), targetedCommand.commandText(), null);
        }

        if (targetToken.length() < MIN_PREFIX_TARGET_LENGTH) {
            return new TargetedAgentMatch<>(null, null, null);
        }

        List<T> prefixMatches = new ArrayList<>();
        for (T target : targets) {
            if (target.name().regionMatches(true, 0, targetToken, 0, targetToken.length())) {
                prefixMatches.add(target);
            }
        }

        if (prefixMatches.size() == 1) {
            return new TargetedAgentMatch<>(prefixMatches.get(0), targetedCommand.commandText(), null);
        }
        if (prefixMatches.size() > 1) {
            return new TargetedAgentMatch<>(null, null, buildAmbiguousPrefixMessage(targetToken, prefixMatches));
        }

        return new TargetedAgentMatch<>(null, null, null);
    }

    private static TargetedAgentCommand parseTargetedAgentCommand(String message) {
        if (message == null) {
            return null;
        }

        String trimmed = message.stripLeading();
        if (trimmed.isEmpty()) {
            return null;
        }

        int separatorIndex = 0;
        while (separatorIndex < trimmed.length() && !isTargetSeparator(trimmed.charAt(separatorIndex))) {
            separatorIndex++;
        }
        if (separatorIndex == 0 || separatorIndex >= trimmed.length()) {
            return null;
        }

        String commandText = trimmed.substring(separatorIndex).replaceFirst("^[,!?\\s]+", "").trim();
        if (commandText.isEmpty()) {
            return null;
        }

        return new TargetedAgentCommand(trimmed.substring(0, separatorIndex), commandText);
    }

    private static boolean isTargetSeparator(char ch) {
        return java.lang.Character.isWhitespace(ch) || ch == ',' || ch == '!' || ch == '?';
    }

    private static boolean isNumericTarget(String targetToken) {
        return !targetToken.isEmpty() && targetToken.chars().allMatch(java.lang.Character::isDigit);
    }

    private static <T extends AgentCommandTarget> String buildAmbiguousPrefixMessage(String prefix, List<T> matches) {
        StringBuilder message = new StringBuilder("Ambiguous bot prefix '")
                .append(prefix)
                .append("': ");
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) {
                message.append(", ");
            }
            message.append(i + 1).append(": ").append(matches.get(i).name());
        }
        message.append(". Use the full name or a slot number.");
        return message.toString();
    }
}
