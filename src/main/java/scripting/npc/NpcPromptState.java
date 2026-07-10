package scripting.npc;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NpcPromptState {
    private static final Pattern MENU_SELECTION = Pattern.compile("#L(-?\\d+)#");

    private final byte messageType;
    private final Integer minimum;
    private final Integer maximum;
    private final Set<Integer> selections;

    private NpcPromptState(byte messageType, Integer minimum, Integer maximum, Set<Integer> selections) {
        this.messageType = messageType;
        this.minimum = minimum;
        this.maximum = maximum;
        this.selections = selections;
    }

    public static NpcPromptState plain(byte messageType) {
        return new NpcPromptState(messageType, null, null, null);
    }

    public static NpcPromptState number(int minimum, int maximum) {
        return new NpcPromptState((byte) 3, minimum, maximum, null);
    }

    public static NpcPromptState menu(String text) {
        Set<Integer> selections = new LinkedHashSet<>();
        Matcher matcher = MENU_SELECTION.matcher(text);
        while (matcher.find()) {
            selections.add(Integer.parseInt(matcher.group(1)));
        }
        return new NpcPromptState((byte) 4, null, null, Collections.unmodifiableSet(selections));
    }

    public static NpcPromptState style(int optionCount) {
        return new NpcPromptState((byte) 7, 0, optionCount - 1, null);
    }

    public boolean accepts(byte responseType, byte action, int selection) {
        if (responseType != messageType || action < 0 || action > 1) {
            return false;
        }
        if (action == 0) {
            return true;
        }
        if (selections != null) {
            return selections.contains(selection);
        }
        if (minimum != null) {
            return selection >= minimum && selection <= maximum;
        }
        return true;
    }
}
