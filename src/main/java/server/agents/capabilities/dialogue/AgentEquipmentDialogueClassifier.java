package server.agents.capabilities.dialogue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentEquipmentDialogueClassifier {
    private static final String EQUIP_SLOT_WORDS =
            "(?:weapon|wep|shield|offhand|cape|hat|helm(?:et)?|top|shirt|overall|bottom|pants|shoes|boots|"
            + "gloves?|face(?:\\s*acc(?:essory)?)?|eye(?:\\s*(?:acc(?:essory)?|piece))?|"
            + "earrings?|rings?\\s*[1-4]?|pendant|medal|belt)";
    private static final Pattern UNEQUIP_PATTERN = Pattern.compile(
            "\\b(unequip|take\\s+off|remove)\\s+(?:everything|all|all\\s+(?:your|ur|my)\\s+gear|gear|equipment|equips?)\\b"
            + "|\\bstrip\\s+(?:down|everything|all)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNEQUIP_SLOT_PATTERN = Pattern.compile(
            "\\b(unequip|take\\s+off|remove)\\s+(" + EQUIP_SLOT_WORDS + ")\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTOEQUIP_DEBUG_PATTERN = Pattern.compile(
            "\\b(?:auto[\\-\\s]?equip|optimi[sz]e\\s+(?:gear|equip(?:s|ment)?))\\s+(?:debug|verbose|why|explain)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTOEQUIP_PATTERN = Pattern.compile(
            "\\b(?:auto[\\-\\s]?equip|optimi[sz]e\\s+(?:gear|equip(?:s|ment)?))\\b",
            Pattern.CASE_INSENSITIVE);

    private AgentEquipmentDialogueClassifier() {
    }

    public static String matchUnequipSlotName(String message) {
        Matcher matcher = UNEQUIP_SLOT_PATTERN.matcher(message);
        return matcher.find() ? matcher.group(2) : null;
    }

    public static boolean isUnequipAllCommand(String message) {
        return UNEQUIP_PATTERN.matcher(message).find();
    }

    public static boolean isAutoEquipDebugCommand(String message) {
        return AUTOEQUIP_DEBUG_PATTERN.matcher(message).find();
    }

    public static boolean isAutoEquipCommand(String message) {
        return AUTOEQUIP_PATTERN.matcher(message).find();
    }
}
