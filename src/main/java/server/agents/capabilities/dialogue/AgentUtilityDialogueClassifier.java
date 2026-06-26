package server.agents.capabilities.dialogue;

import java.util.regex.Pattern;

public final class AgentUtilityDialogueClassifier {
    private static final Pattern TRADE_INVITE_PATTERN = Pattern.compile(
            "^\\s*trade(\\s+(me|pls|please))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SELL_TRASH_COMMAND_PATTERN = Pattern.compile(
            "^\\s*(?:sell|vendor)\\s+(?:(?:my|ur|your)\\s+)?(?:trash|junk)\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MAKE_CRYSTALS_COMMAND_PATTERN = Pattern.compile(
            "^\\s*(?:make|craft|create)\\s+(?:some\\s+)?(?:mob|mon|monster|monsters|mobs)\\s+crystals?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DISASSEMBLE_TRASH_COMMAND_PATTERN = Pattern.compile(
            "^\\s*(?:disassemble|dismantle|scrap|break\\s*down)\\s+(?:(?:my|ur|your)\\s+)?(?:trash|junk)(?:\\s+(?:equips?|gear))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private AgentUtilityDialogueClassifier() {
    }

    public static boolean isTradeInviteCommand(String message) {
        return TRADE_INVITE_PATTERN.matcher(message).find();
    }

    public static boolean isSellTrashCommand(String message) {
        return SELL_TRASH_COMMAND_PATTERN.matcher(message).matches();
    }

    public static boolean isMakeCrystalsCommand(String message) {
        return MAKE_CRYSTALS_COMMAND_PATTERN.matcher(message).matches();
    }

    public static boolean isDisassembleTrashCommand(String message) {
        return DISASSEMBLE_TRASH_COMMAND_PATTERN.matcher(message).matches();
    }
}
