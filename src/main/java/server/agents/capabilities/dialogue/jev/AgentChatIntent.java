package server.agents.capabilities.dialogue.jev;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The closed set of companion intents a System One Choice may return. Every option carries the
 * contrastive description the TypeSafe docs recommend ({@code what}, {@code not_for},
 * {@code examples}) and, where the intent is complete by itself, the canonical chat phrase the
 * deterministic classifiers already understand. Intents with an {@link #argument()} need one
 * extra Choice answer before {@link AgentChatIntentCatalog} can build the phrase.
 */
public enum AgentChatIntent {
    FOLLOW("follow the speaker and stay near them",
            "moving to a spot and stopping there, following another named person, or calling the whole party together",
            List.of("follow me", "come", "come here", "stick with me"), "follow me", null),
    STOP("stop moving and stay idle where it is",
            "pausing briefly while still following, or leaving the party",
            List.of("stop", "stay", "wait", "hold on", "idle", "park here"), "stop", null),
    MOVE_HERE("walk to the speaker's exact position and stop there",
            "continuing to follow afterwards, or calling the whole party together",
            List.of("move here", "go here", "here", "move"), "move here", null),
    ASSEMBLE("call the whole party together: every companion walks over to the speaker and waits beside them, for example so buffs reach everyone",
            "one bot following or coming over on its own",
            List.of("gather up", "everyone come here", "group up for buffs", "regroup", "everybody over here"),
            null, AgentChatIntentArgument.GATHER_PURPOSE),
    GRIND("hunt monsters anywhere on the current map",
            "staying in one place or only nearby platforms",
            List.of("grind", "farm", "hunt", "kill mobs", "auto on", "go get exp"), "grind", null),
    PATROL("hunt only on the current platform and the platforms next to it",
            "roaming the whole map or standing still",
            List.of("patrol", "roam", "wander"), "patrol", null),
    SENTRY("stand exactly where it is and attack only what comes into range",
            "moving to chase monsters",
            List.of("sentry", "camp", "guard mode", "post up", "anchor here", "farm here", "grind here"), "farm here", null),
    FIDGET("perform a small idle or social animation",
            "any movement or combat", List.of("fidget"), "fidget", null),
    INFO_QUERY("report information about itself, for example stats, inventory, skills or mesos",
            "changing anything or requesting items",
            List.of("stats", "how much exp", "what skills do you have", "show inventory", "how many pots"),
            null, AgentChatIntentArgument.INFO_TOPIC),
    SUPPORT_TOGGLE("turn a support behaviour on or off: skill buffs, heals, or buff potions",
            "asking what buffs are active",
            List.of("support on", "heals off", "buff cheap", "buff max", "stop buffing"),
            null, AgentChatIntentArgument.SUPPORT_TARGET),
    SUPPLY_REQUEST("the speaker needs potions or ammunition from the bot",
            "asking how many potions the bot has",
            List.of("need hp pot", "running low on pots", "need ammo", "low on arrows", "give me mana pots"),
            null, AgentChatIntentArgument.SUPPLY_KIND),
    FAME("fame the speaker",
            "faming someone else by name", List.of("fame me", "fame pls"), "fame me", null),
    GEAR_FOR_SPEAKER("ask whether the bot holds equipment that would upgrade the speaker",
            "asking what the bot itself needs",
            List.of("any upgrades?", "better gear", "recommended gear"), "any upgrades?", null),
    GEAR_FOR_BOT("ask what equipment the bot itself wants from the speaker",
            "asking for gear for the speaker",
            List.of("request?", "need anything?", "what do you need"), "need anything?", null),
    TRADE_WINDOW("open a trade window with the speaker without naming items",
            "handing over a specific category of items",
            List.of("trade", "open trade", "trade with me"), "trade", null),
    TRADE_ITEMS("hand a category of items to the speaker through a trade",
            "dropping items on the ground or opening an empty trade window",
            List.of("trade me scrolls", "give me your pots", "pass me the equips", "trade trash"),
            null, AgentChatIntentArgument.ITEM_CATEGORY),
    DROP_ITEMS("drop a category of items on the ground",
            "trading items to the speaker",
            List.of("drop scrolls", "toss the etc items", "drop your junk"),
            null, AgentChatIntentArgument.ITEM_CATEGORY),
    SELL_TRASH("sell its unreserved junk equipment to a shop",
            "trading junk to the speaker",
            List.of("sell trash", "sell junk", "vendor the junk"), "sell trash", null),
    AUTOEQUIP("re-run gear optimisation and equip its best items",
            "unequipping",
            List.of("autoequip", "optimize gear", "equip your best stuff"), "autoequip", null),
    UNEQUIP_SLOT("unequip one specific equipment slot",
            "unequipping everything",
            List.of("unequip hat", "take off your weapon", "unequip gloves"),
            null, AgentChatIntentArgument.EQUIP_SLOT),
    UNEQUIP_ALL("unequip every non-cash item",
            "one slot only", List.of("unequip everything", "take it all off"), "unequip everything", null),
    RESPEC("refund and re-assign skill points or ability points",
            "changing the build choice itself",
            List.of("respec sp", "reset skills", "reset ap", "respec ap"),
            null, AgentChatIntentArgument.RESPEC_KIND),
    CHANGE_BUILD("re-prompt the ability point build selection",
            "refunding points", List.of("change build", "pick a different build"), "change build", null),
    HELP("list the commands it understands",
            "any other question", List.of("help", "commands", "what can you do"), "help", null),
    RELOG("disconnect and reconnect itself to refresh its session",
            "logging out entirely", List.of("relog", "reconnect", "log back in", "you need to relog"), "relog", null),
    LOGOUT("despawn and log out",
            "waiting in town", List.of("logout", "log off", "go away for now"), "logout", null),
    CHAT_NOT_A_COMMAND("small talk, questions to other players, or anything that is not an instruction to a companion bot",
            "any recognised command above",
            List.of("lol", "how was your day", "anyone selling steelies?", "gg"), null, null);

    private final String what;
    private final String notFor;
    private final List<String> examples;
    private final String canonicalCommand;
    private final AgentChatIntentArgument argument;

    AgentChatIntent(String what, String notFor, List<String> examples,
                    String canonicalCommand, AgentChatIntentArgument argument) {
        this.what = what;
        this.notFor = notFor;
        this.examples = examples;
        this.canonicalCommand = canonicalCommand;
        this.argument = argument;
    }

    /** Option id sent to and returned by the API. */
    public String optionId() {
        return name().toLowerCase();
    }

    /** Canonical phrase for argument-free intents, {@code null} when an argument is needed. */
    public String canonicalCommand() {
        return canonicalCommand;
    }

    /** The one argument Choice this intent needs, or {@code null}. */
    public AgentChatIntentArgument argument() {
        return argument;
    }

    public boolean actionable() {
        return this != CHAT_NOT_A_COMMAND;
    }

    public Map<String, Object> criteria() {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("what", what);
        description.put("not_for", notFor);
        description.put("examples", examples);
        return description;
    }

    public static AgentChatIntent fromOptionId(String optionId) {
        if (optionId == null) {
            return null;
        }
        for (AgentChatIntent intent : values()) {
            if (intent.optionId().equals(optionId.trim().toLowerCase())) {
                return intent;
            }
        }
        return null;
    }
}
