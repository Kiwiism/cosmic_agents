package server.agents.capabilities.dialogue.jev;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Argument Choices asked speculatively alongside the intent Choice. Each option maps directly
 * to the canonical phrase fragment the deterministic classifiers understand, so a confident
 * answer becomes a normal command without new dispatch code.
 */
public enum AgentChatIntentArgument {
    INFO_TOPIC("info_topic", "Which information does the speaker want from the bot?",
            options(
                    "stats", "level and base stats (STR/DEX/INT/LUK)", "stats",
                    "inventory", "what items it carries", "inventory",
                    "slots", "how many free inventory slots it has", "slots",
                    "range", "damage range and hit chance", "range",
                    "build", "how AP and SP are assigned", "build",
                    "skills", "its skill levels", "skills",
                    "mesos", "how many mesos it has", "mesos",
                    "speed", "movement speed and jump", "speed",
                    "exp", "experience progress", "exp",
                    "scrolls", "scrolls it carries", "scrolls",
                    "pots", "HP and MP potion counts", "pots",
                    "buffs", "which buffs are active or available", "buff list")),
    SUPPORT_TARGET("support_target", "Which support behaviour and which setting does the speaker want?",
            options(
                    "support_on", "start casting skill buffs on the party", "support on",
                    "support_off", "stop casting skill buffs", "support off",
                    "heals_on", "start healing party members", "heals on",
                    "heals_off", "stop healing party members", "heals off",
                    "buff_pots_on", "start using buff potions", "buff on",
                    "buff_pots_off", "stop using buff potions", "buff off",
                    "buff_pots_cheap", "use the cheapest buff potions", "buff cheap",
                    "buff_pots_max", "use the best buff potions", "buff max")),
    SUPPLY_KIND("supply_kind", "Which supply does the speaker need?",
            options(
                    "hp", "HP or health potions", "need hp pot",
                    "mp", "MP or mana potions", "need mp pot",
                    "ammo", "arrows, bolts, stars or bullets", "need ammo",
                    "any", "potions in general, type unspecified", "need pot")),
    ITEM_CATEGORY("item_category", "Which category of items does the speaker mean?",
            options(
                    "scrolls", "upgrade scrolls", "scrolls",
                    "pots", "HP and MP potions", "pots",
                    "buff", "buff potions and buff consumables", "buff",
                    "ammo", "arrows, bolts, stars or bullets", "ammo",
                    "equips", "all equipment", "equips",
                    "trash", "unreserved junk equipment only", "trash",
                    "use", "everything in the USE tab", "use",
                    "etc", "everything in the ETC tab", "etc")),
    EQUIP_SLOT("equip_slot", "Which equipment slot does the speaker mean?",
            options(
                    "hat", "hat, cap or helmet", "hat",
                    "top", "top or shirt", "top",
                    "bottom", "bottom or pants", "bottom",
                    "overall", "overall", "overall",
                    "shoes", "shoes or boots", "shoes",
                    "gloves", "gloves", "gloves",
                    "cape", "cape", "cape",
                    "shield", "shield or off-hand", "shield",
                    "weapon", "weapon", "weapon",
                    "earrings", "earrings", "earrings",
                    "ring", "a ring", "ring",
                    "pendant", "pendant", "pendant",
                    "face", "face accessory", "face",
                    "eye", "eye accessory", "eye")),
    RESPEC_KIND("respec_kind", "Which points does the speaker want refunded and re-assigned?",
            options(
                    "sp", "skill points", "respec sp",
                    "ap", "ability points", "respec ap")),
    GATHER_PURPOSE("gather_purpose", "Is the speaker calling the party together so everyone gets buffed?",
            options(
                    "gather", "just to have everyone come over and wait beside them", "gather up",
                    "buffs", "to get buffed: the buffers rebuff the whole party once everyone has arrived",
                    "gather up for buffs"));

    private final String questionId;
    private final String question;
    private final Map<String, Option> options;

    public record Option(String description, String phrase) {
    }

    AgentChatIntentArgument(String questionId, String question, Map<String, Option> options) {
        this.questionId = questionId;
        this.question = question;
        this.options = options;
    }

    public String questionId() {
        return questionId;
    }

    public String question() {
        return question;
    }

    public Map<String, Object> criteria() {
        Map<String, Object> criteria = new LinkedHashMap<>();
        options.forEach((id, option) -> criteria.put(id, option.description()));
        return criteria;
    }

    /** Canonical phrase fragment for {@code optionId}, or {@code null} when unknown. */
    public String phraseFor(String optionId) {
        Option option = optionId == null ? null : options.get(optionId.trim().toLowerCase());
        return option == null ? null : option.phrase();
    }

    private static Map<String, Option> options(String... triples) {
        Map<String, Option> options = new LinkedHashMap<>();
        for (int index = 0; index + 2 < triples.length; index += 3) {
            options.put(triples[index], new Option(triples[index + 1], triples[index + 2]));
        }
        return options;
    }
}
