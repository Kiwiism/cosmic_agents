package server.agents.capabilities.dialogue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentTradeDialogueClassifier {
    private static final String SCROLL_WORDS = "scrolls?";
    private static final String POTION_WORDS = "(?:pots?|potions?|hp\\s+pots?|mp\\s+pots?|supplies)";
    private static final String BUFF_WORDS = "(?:buff\\s+pots?|buff\\s+potions?|buffs?\\s+items?)";
    private static final String AMMO_WORDS = "(?:ammo|arrows?|bolts?|bullets?|stars?|throwing\\s+stars?)";
    private static final String USE_WORDS = "(?:use|use\\s+items?|consumables?)";
    private static final String EQUIP_WORDS = "(?:equips?|equipment|gear)";
    private static final String ETC_WORDS = "(?:etc|misc(?:ellaneous)?)";
    private static final String TRASH_WORDS = "(?:trash|junk)";
    private static final String MESO_WORDS = "mesos?";
    private static final String EQUIP_SLOT_WORDS =
            "(?:weapon|wep|shield|offhand|cape|hat|helm(?:et)?|top|shirt|overall|bottom|pants|shoes|boots|"
            + "gloves?|face(?:\\s*acc(?:essory)?)?|eye(?:\\s*(?:acc(?:essory)?|piece))?|"
            + "earrings?|rings?\\s*[1-4]?|pendant|medal|belt)";
    private static final String TRADE_CMD_VERB = "(?:trade(?:\\s+(?:me|us))?)";
    private static final String DROP_CMD_VERB = "(?:drop|toss)";
    private static final String ASK_CMD_VERB = "(?:give(?:\\s+(?:me|us))?|pass(?:\\s+me)?)";
    private static final String MESO_CMD_VERB = "(?:trade(?:\\s+(?:me|us))?|give(?:\\s+(?:me|us))?|gimme|pass(?:\\s+me)?)";
    private static final String TRANSFER_OWNER = "(?:(?:your|ur|my|all)\\s+)?";
    private static final String TRANSFER_RECIPIENT = "(?:(?:me|us)\\s+)?";
    private static final String MESO_AMOUNT_TOKEN = "\\d[\\d,]*(?:\\.\\d+)?\\s*[kmb]?";

    private static final Pattern TRADE_VIEW_SLOT_COMMAND_PATTERN = Pattern.compile(
            "\\b(?:can\\s+i\\s+(?:c|see)|let\\s+me\\s+(?:c|see)|show(?:\\s+me)?)\\s+"
            + "(?:(?:u|ur|yo|your)\\s+)?(" + EQUIP_SLOT_WORDS + ")\\b[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_MESOS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + MESO_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(all)\\s+)?"
            + "(?:(?:your|ur|my)\\s+)?"
            + "(?:(" + MESO_AMOUNT_TOKEN + ")\\s+)?"
            + MESO_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_MESOS_AFTER_WORD_COMMAND_PATTERN = Pattern.compile(
            "\\b" + MESO_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(?:your|ur|my)\\s+)?"
            + MESO_WORDS + "\\s+(" + MESO_AMOUNT_TOKEN + ")[?!.,]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_MESOS_AMOUNT_ONLY_COMMAND_PATTERN = Pattern.compile(
            "\\b" + MESO_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(all)|(" + MESO_AMOUNT_TOKEN + "))[?!.,]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_SCROLLS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + SCROLL_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_POTS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + POTION_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_USE_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + USE_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_EQUIPS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + EQUIP_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_RESERVE_COMMAND_PATTERN = Pattern.compile(
            "^\\s*" + TRADE_CMD_VERB
            + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(?:your|ur|my)\\s+)?reserve(?:d)?(?:\\s+(\\d+))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_TRASH_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(?:your|ur|my)\\s+)?" + TRASH_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_JUNK_COMMAND_PATTERN = Pattern.compile(
            "^\\s*show(?:\\s+me)?\\s+(?:(?:your|ur)\\s+)?junk[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_ETC_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + ETC_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_BUFF_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + BUFF_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_AMMO_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + AMMO_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_RECOMMENDED_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT
            + "(?:(?:your|ur|my)\\s+)?"
            + "(?:(?:recommended|better)\\s+(?:gear|equips?|equipment)|upgrades?|recommended\\s+items?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRADE_ITEM_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + "(?:(?:your|ur|my)\\s+)?([\\w][\\w '\\-]{1,39})[?!.,]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_SCROLLS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + SCROLL_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_POTS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + POTION_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_USE_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + USE_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_EQUIPS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + EQUIP_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TRASH_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + "(?:(?:your|ur|my)\\s+)?" + TRASH_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_ETC_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + ETC_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_ITEM_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + "(?:(?:your|ur|my)\\s+)?([\\w][\\w '\\-]{1,39})[?!.,]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_BUFF_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + BUFF_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_BUFF_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + BUFF_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_SCROLLS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + SCROLL_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_POTS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + POTION_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_USE_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + USE_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_EQUIPS_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + EQUIP_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_ETC_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + ETC_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASK_ITEM_COMMAND_PATTERN = Pattern.compile(
            "\\b" + ASK_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + "(?:(?:your|ur|my)\\s+)?([\\w][\\w '\\-]{1,39})[?!.,]?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEM_QUERY_PATTERN = Pattern.compile(
            "^\\s*(?:(?:do\\s+(?:you|u)\\s+have)|(?:(?:any(?:body|one)?|someone|somebody|you|u)\\s+(?:got|have|has))|got|have)\\s+"
            + "(?:any\\s+|some\\s+)?(?:(?:your|ur)\\s+)?([\\w][\\w '\\-]{1,39})[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);

    private AgentTradeDialogueClassifier() {
    }

    public static String matchItemQuery(String message) {
        String itemName = matchNormalizedItemQuery(message);
        if (itemName == null) return null;
        String generic = itemName.toLowerCase(Locale.ROOT);
        if (generic.equals("pot") || generic.equals("potion")) {
            return null;
        }
        if (Pattern.matches(TRASH_WORDS, generic)) {
            return null;
        }
        return itemName;
    }

    public static String matchTradeCategory(String message) {
        String mesoCategory = matchTradeMesoCategory(message);
        if (mesoCategory != null) return mesoCategory;
        if (message != null && SHOW_JUNK_COMMAND_PATTERN.matcher(message).matches()) return "trash";
        if ("trash".equals(matchItemQueryCategory(message))) return "trash";

        if (TRADE_RECOMMENDED_COMMAND_PATTERN.matcher(message).find()) return "recommended";
        if (TRADE_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (TRADE_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (TRADE_BUFF_COMMAND_PATTERN.matcher(message).find()) return "buff";
        if (TRADE_AMMO_COMMAND_PATTERN.matcher(message).find()) return "ammo";
        Matcher reserveMatcher = TRADE_RESERVE_COMMAND_PATTERN.matcher(message);
        if (reserveMatcher.matches()) {
            return reservedEquipsCategory(parseTradeReservePage(reserveMatcher.group(1)));
        }
        if (TRADE_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (TRADE_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (TRADE_TRASH_COMMAND_PATTERN.matcher(message).find()) return "trash";
        if (TRADE_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";
        Matcher viewSlotMatcher = TRADE_VIEW_SLOT_COMMAND_PATTERN.matcher(message);
        if (viewSlotMatcher.find()) return "name:" + AgentItemQueryNormalizer.normalize(viewSlotMatcher.group(1));

        Matcher matcher = TRADE_ITEM_COMMAND_PATTERN.matcher(message);
        return matcher.find() ? "name:" + AgentItemQueryNormalizer.normalize(matcher.group(1)) : null;
    }

    public static String matchChoiceCategory(String message) {
        if (DROP_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (DROP_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (DROP_BUFF_COMMAND_PATTERN.matcher(message).find()) return "buff";
        if (DROP_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (DROP_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (DROP_TRASH_COMMAND_PATTERN.matcher(message).find()) return "trash";
        if (DROP_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";
        Matcher dropMatcher = DROP_ITEM_COMMAND_PATTERN.matcher(message);
        if (dropMatcher.find()) return "name:" + AgentItemQueryNormalizer.normalize(dropMatcher.group(1));

        if (ASK_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (ASK_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (ASK_BUFF_COMMAND_PATTERN.matcher(message).find()) return "buff";
        if (ASK_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (ASK_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (ASK_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";

        Matcher matcher = ASK_ITEM_COMMAND_PATTERN.matcher(message);
        return matcher.find() ? "name:" + AgentItemQueryNormalizer.normalize(matcher.group(1)) : null;
    }

    public static boolean isShowJunkCommand(String message) {
        return message != null && SHOW_JUNK_COMMAND_PATTERN.matcher(message).matches();
    }

    private static String matchTradeMesoCategory(String message) {
        Matcher matcher = TRADE_MESOS_AFTER_WORD_COMMAND_PATTERN.matcher(message);
        if (matcher.find()) {
            return "mesos:" + parseMesoAmount(matcher.group(1));
        }

        matcher = TRADE_MESOS_COMMAND_PATTERN.matcher(message);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return "mesos";
            }

            String amountToken = matcher.group(2);
            if (amountToken == null || amountToken.isBlank()) {
                return "mesos";
            }

            return "mesos:" + parseMesoAmount(amountToken);
        }

        matcher = TRADE_MESOS_AMOUNT_ONLY_COMMAND_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        if (matcher.group(1) != null) {
            return "mesos";
        }

        return "mesos:" + parseMesoAmount(matcher.group(2));
    }

    private static int parseMesoAmount(String amountToken) {
        String normalized = amountToken.toLowerCase().replace(",", "").replaceAll("\\s+", "");
        long multiplier = 1L;
        if (!normalized.isEmpty()) {
            char suffix = normalized.charAt(normalized.length() - 1);
            if (suffix == 'k' || suffix == 'm' || suffix == 'b') {
                multiplier = switch (suffix) {
                    case 'k' -> 1_000L;
                    case 'm' -> 1_000_000L;
                    default -> 1_000_000_000L;
                };
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }

        if (normalized.isEmpty()) {
            return 0;
        }

        try {
            long amount = Math.round(Double.parseDouble(normalized) * multiplier);
            if (amount < 0) {
                return 0;
            }
            return (int) Math.min(amount, Integer.MAX_VALUE);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static int parseTradeReservePage(String pageToken) {
        if (pageToken == null || pageToken.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(pageToken);
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private static String reservedEquipsCategory(int requestedPage) {
        return "equips:reserved:" + requestedPage;
    }

    private static String matchNormalizedItemQuery(String message) {
        Matcher matcher = ITEM_QUERY_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String itemName = AgentItemQueryNormalizer.normalize(matcher.group(1));
        return itemName.isBlank() ? null : itemName;
    }

    private static String matchItemQueryCategory(String message) {
        String itemName = matchNormalizedItemQuery(message);
        if (itemName == null) return null;
        String generic = itemName.toLowerCase(Locale.ROOT);
        return Pattern.matches(TRASH_WORDS, generic) ? "trash" : null;
    }
}
