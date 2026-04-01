package server.bots;

import client.BotClient;
import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import client.inventory.InventoryType;
import client.inventory.Item;
import constants.game.GameConstants;
import server.ItemInformationProvider;
import server.TimerManager;
import server.Trade;

import java.awt.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BotChatManager {
    private static final String SKILL_TREE_CHOICE_ACTION = "skill_tree_choice";
    private static final String RECOMMENDED_TRADE_ACTION = "recommended_trade_offer";

    private record LearnedSkill(int id, String name, int level) {}

    // --- helper prefix used in several info patterns ---
    // optional preamble: "what's/tell me/check … your/ur" or nothing at all
    // \\b at end ensures word boundary when no prefix is present
    private static final String INFO_PFX =
            "(?:(?:(?:what.?s?|what\\s+is|tell\\s+me|show\\s+me|check|how.?s?)\\s+)?(?:your|ur)\\s+)?\\b";

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow(\\s+(me|here|pls|please|now))?|come(\\s+(here|to\\s+me|with\\s+me|closer|on|back))?|"
            + "get\\s+over\\s+here|f\\s+me|(pls|please)\\s+follow)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOVE_HERE_PATTERN = Pattern.compile(
            "\\b(move\\s+(here|there)|go\\s+(here|there))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop(\\s+(moving|it|now|pls|please))?|stay(\\s+(here|there|put))?|"
            + "wait(\\s+(here|up|for\\s+me|a\\s+(sec|moment|bit)))?|"
            + "hold(\\s+(on|up|still|it))?|halt|freeze|don.?t\\s+move|stand\\s+(still|by)|"
            + "chill(\\s+here)?|idle|park(\\s+here)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> FOLLOW_REPLIES = List.of(
            "ok", "k", "sure", "omw", "got it", "coming",
            "roger", "yep", "alright", "aye", "lets go!", "as you wish", "ok boss",
            "on my way", "right behind you", "np", "kk omw", "w8 up",
            "gotchu", "moving now", "aye aye");
    private static final List<String> MOVE_HERE_REPLIES = List.of(
            "k, coming", "omw", "ok heading over", "on my way", "k",
            "got it, moving there", "coming, then staying put", "k moving there", "sure omw");
    private static final List<String> STOP_REPLIES = List.of(
            "ok", "k", "sure", "alright", "got it", "stopping",
            "ok ill wait here", "ill be here", "np", "standing by",
            "understood", "ok boss", "staying put", "chilling here",
            "resting", "aye aye", "on it", "noted");

    private static final Pattern GRIND_PATTERN = Pattern.compile(
            "\\b(go\\s+|start\\s+|begin\\s+|let.?s\\s+)?(farm(ing)?|grind(ing)?|hunt(ing)?|train(ing)?)\\b"
            + "|\\b(kill|fight)\\s+(mobs?|monsters?|stuff)\\b"
            + "|\\btime\\s+to\\s+(farm|grind|hunt)\\b"
            + "|\\bgo\\s+get\\s+(exp|xp)\\b"
            + "|\\b(auto|attack)\\s*(on|mode)?\\b"
            + "|\\bstart\\s+(killing|attacking)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern JOB_SELECT_PATTERN = Pattern.compile(
            "\\b(warrior|fighter|page|spearman|sader|crusader|hero|dk|drk|dark knight|paladin|" +
            "mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage|fp arch|il arch|priest|bishop|" +
            "bowman|bowmen|archer|hunter|crossbow|xbow|sniper|ranger|bowmaster|bm|marksman|mm|" +
            "thief|assassin|sin|bandit|dit|hermit|chief bandit|cb|shadower|shad|night lord|nl|" +
            "pirate|brawler|gunslinger|gun|marauder|outlaw|bucc|buccaneer|corsair|" +
            "white knight|wk|dragon knight)\\b", Pattern.CASE_INSENSITIVE);


    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "\\b(hi+|hey+|hello+|sup|yo+|howdy|hiya|heya|hai|ello|"
            + "whats?\\s*up|waz+up|wassup|hows?\\s+it\\s+going|"
            + "(good\\s+)?(morning|evening|afternoon)|"
            + "how\\s+(are|r)\\s+(you|u|ya)(\\s+doing)?|"
            + "what.?s\\s+(good|up|new|poppin.?))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STATS_PATTERN = Pattern.compile(
            INFO_PFX + "(stats?|str(ength)?|dex(terity)?|int(elligence)?|luk|level|lv)\\b"
            + "|\\bwhat\\s+(are|r)\\s+(your|ur)\\s+stats\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern RANGE_PATTERN = Pattern.compile(
            INFO_PFX + "(range|damage|dmg|dps|watk|atk)\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+(range|damage|dmg)\\b"
            + "|\\bhow\\s+(strong|powerful)\\s+(are|r)\\s+(you|u)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BUILD_PATTERN = Pattern.compile(
            INFO_PFX + "(build|ap|sp)\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+build\\b"
            + "|\\bhow\\s+(did|do)\\s+(you|u)\\s+(build|assign|spend)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILLS_PATTERN = Pattern.compile(
            INFO_PFX + "(skills?|skill\\s+trees?|skill\\s+tabs?)\\b"
            + "|\\bwhat\\s+skills?\\s+do\\s+(you|u)\\s+have\\b"
            + "|\\bshow\\s+me\\s+(your|ur)\\s+skills?\\b"
            + "|^\\s*skills\\s*\\??\\s*$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVENTORY_PATTERN = Pattern.compile(
            INFO_PFX + "(inv(entory)?|bag|items?|equips?|equipment)\\b"
            + "|\\bwhat.?s\\s+in\\s+(your|ur)\\s+(inv(entory)?|bag)\\b"
            + "|\\b(show|check)\\s+(your|ur)\\s+(inv(entory)?|items?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBUG_STATS_PATTERN = Pattern.compile(
            INFO_PFX + "(debug\\s+stats?|attack\\s+cooldown|atk\\s+cooldown)\\b"
            + "|\\bshow\\s+(me\\s+)?debug\\s+stats\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+attack\\s+cooldown\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HELP_PATTERN = Pattern.compile(
            "\\b(help|commands?|what\\s+can\\s+you\\s+do|how\\s+do\\s+i\\s+use\\s+you)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RECOMMENDED_GEAR_PATTERN = Pattern.compile(
            "\\b(any\\s+upgrades?|better\\s+gear|recommended\\s+gear|gear\\s+recommendations?|"
            + "any\\s+(better|recommended)\\s+(gear|equips?|equipment))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_ON_PATTERN = Pattern.compile(
            "\\b(support\\s+(me|us|party)|support\\s+on|auto\\s+support)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_OFF_PATTERN = Pattern.compile(
            "\\b(support\\s+off|stop\\s+support(ing)?|no\\s+support)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFFS_ON_PATTERN = Pattern.compile(
            "\\b(buffs?\\s+(me|us|party)|buffs?\\s+on|auto\\s+buffs?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFFS_OFF_PATTERN = Pattern.compile(
            "\\b(buffs?\\s+off|stop\\s+buff(ing)?|no\\s+buffs?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALS_ON_PATTERN = Pattern.compile(
            "\\b(heals?\\s+(me|us|party)|heals?\\s+on|auto\\s+heals?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALS_OFF_PATTERN = Pattern.compile(
            "\\b(heals?\\s+off|stop\\s+heal(ing)?|no\\s+heals?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final String SCROLL_WORDS = "scrolls?";
    private static final String POTION_WORDS = "(?:pots?|potions?|hp\\s+pots?|mp\\s+pots?|supplies)";
    private static final String USE_WORDS = "(?:use|use\\s+items?|consumables?)";
    private static final String EQUIP_WORDS = "(?:equips?|equipment|gear)";
    private static final String ETC_WORDS = "(?:etc|junk|misc(?:ellaneous)?)";
    private static final String MESO_WORDS = "mesos?";

    private static final Pattern SCROLLS_PATTERN = Pattern.compile(
            "\\b(any|do\\s+(you|u)\\s+have(\\s+any)?|got(\\s+any)?|"
            + "carrying(\\s+any)?|you\\s+got(\\s+any)?)\\s+" + SCROLL_WORDS + "\\b"
            + "|\\bhow\\s+many\\s+scrolls?\\b"
            + "|\\bscrolls?\\s+on\\s+(you|u|ya)\\b"
            + "|\\b(your|ur)\\s+scrolls?\\b"
            + "|^\\s*scrolls?\\s*\\??\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POTIONS_PATTERN = Pattern.compile(
            INFO_PFX + POTION_WORDS + "\\b"
            + "|\\b(any|do\\s+(you|u)\\s+have(\\s+any)?|got(\\s+any)?|how\\s+many)"
            +   "\\s+" + POTION_WORDS + "\\b"
            + "|\\b(pots?|potions?)\\s+left\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MESOS_PATTERN = Pattern.compile(
            "^\\s*(?:meso|mesos|cash)\\s*[?!.,]*\\s*$"
            + "|\\bhow\\s+much\\s+(?:meso|mesos|cash)\\b"
            + "|\\bwhat.?s\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\bshow\\s+me\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:meso|mesos|cash)\\s+(?:left|on\\s+(?:you|u|ya))\\b"
            + "|\\b(?:do\\s+(you|u)\\s+have|got(?:\\s+any)?|(you|u)\\s+got)\\s+(?:any\\s+)?(?:meso|mesos|cash)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNEQUIP_PATTERN = Pattern.compile(
            "\\b(unequip|take\\s+off|remove)\\s+(?:everything|all|all\\s+(?:your|ur|my)\\s+gear|gear|equipment|equips?)\\b"
            + "|\\bstrip\\s+(?:down|everything|all)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNEQUIP_SLOT_PATTERN = Pattern.compile(
            "\\b(unequip|take\\s+off|remove)\\s+(weapon|wep|shield|offhand|cape|hat|helm(?:et)?|top|shirt|bottom|pants|shoes|boots|gloves?|face(?:\\s*acc(?:essory)?)?|eye(?:\\s*(?:acc(?:essory)?|piece))?|rings?\\s*[1-4]?|pendant|medal|belt)\\b",
            Pattern.CASE_INSENSITIVE);

    // SP variant selection — only matched when spVariantPromptSent=true and spVariant=null
    private static final Pattern SP_1H_PATTERN = Pattern.compile(
            "\\b1h\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SP_2H_PATTERN = Pattern.compile(
            "\\b2h\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern AP_PURE_STR_PATTERN = Pattern.compile(
            "\\bpure\\s+str\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_FIXED_DEX_PATTERN = Pattern.compile(
            "\\b(\\d+)\\s*dex\\b", Pattern.CASE_INSENSITIVE);
    // Bare trade invite — whole-message match so "trade me" isn't swallowed by TRADE_ITEM_COMMAND_PATTERN
    private static final Pattern TRADE_INVITE_PATTERN = Pattern.compile(
            "^\\s*trade(\\s+(me|pls|please))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final List<String> TRADE_INVITE_REPLIES = List.of(
            "ok", "sure", "k", "one sec", "coming to trade", "np", "k opening trade");

    // Drop-choice responses (matched only when pendingAction = "item_choice")
    private static final Pattern DROP_CHOICE_DROP_PATTERN = Pattern.compile(
            "\\b(drop\\s*(it|them|to\\s+ground)?|floor|ground)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_CHOICE_TRADE_PATTERN = Pattern.compile(
            "\\b(trade|trade\\s*me|send|give|transfer|give\\s*me)\\b",
            Pattern.CASE_INSENSITIVE);

    // Shared verb prefix for all drop/give/trade category commands

    // Drop category commands
    // Generic drop by item name — captured group 1 is the name; processed only if category patterns don't match
    private static final String TRADE_CMD_VERB = "(?:trade(?:\\s+(?:me|us))?)";
    private static final String DROP_CMD_VERB = "(?:drop|toss)";
    private static final String ASK_CMD_VERB = "(?:give(?:\\s+(?:me|us))?|pass(?:\\s+me)?)";
    private static final String MESO_CMD_VERB = "(?:trade(?:\\s+(?:me|us))?|give(?:\\s+(?:me|us))?|gimme|pass(?:\\s+me)?)";
    private static final String TRANSFER_OWNER = "(?:(?:your|ur|my|all)\\s+)?";
    private static final String TRANSFER_RECIPIENT = "(?:(?:me|us)\\s+)?";
    private static final String MESO_AMOUNT_TOKEN = "\\d[\\d,]*(?:\\.\\d+)?\\s*[kmb]?";
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
    private static final Pattern TRADE_ETC_COMMAND_PATTERN = Pattern.compile(
            "\\b" + TRADE_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + ETC_WORDS + "\\b",
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
    private static final Pattern DROP_ETC_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + ETC_WORDS + "\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_ITEM_COMMAND_PATTERN = Pattern.compile(
            "\\b" + DROP_CMD_VERB + "\\s+" + TRANSFER_RECIPIENT + TRANSFER_OWNER + "(?:(?:your|ur|my)\\s+)?([\\w][\\w '\\-]{1,39})[?!.,]?\\s*$",
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
    // Inventory slot query
    private static final Pattern INV_SLOTS_PATTERN = Pattern.compile(
            "\\bslots?\\s*(?:left|free|remaining)?\\b"
            + "|\\binv(?:entory)?\\s+(?:full|space|slots?)\\b"
            + "|\\bhow\\s+full\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AP_CHANGE_BUILD_PATTERN = Pattern.compile(
            "\\b(change|switch|update|reset|new)\\s+(your\\s+|ur\\s+)?build\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESPEC_PATTERN = Pattern.compile(
            "\\b(respec|reset\\s+(skills?|sp)|rebuild\\s+(skills?|sp)|fix\\s+(skills?|sp|build))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_PATTERN = Pattern.compile(
            "\\b((save\\s+and\\s+)?log\\s*(off|out)|disconnect|(pls|please)\\s+log(\\s+me)?\\s+(off|out))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELOG_PATTERN = Pattern.compile(
            "\\b(relog|save\\s+and\\s+relog|reconnect|log\\s+back\\s+in)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> GREETING_REPLIES = List.of(
            "hey", "hi", "sup", "yo", "heya", "hii", "hey!!", "hi!!",
            "heyo", "ello", "o/", "hai", "eyy", "henlo", "o hey");
    private static final List<String> WB_REPLIES = List.of(
            "wb", "wb!", "welcome back", "oh ur back", "hey ur back", "welcome back!!",
            "wb~", "there you are", "oh hey", "finally lol", "took ya a bit",
            "hey you're back", "oh wb!");
    private static final List<String> MESO_REPLIES = List.of(
            "I have %s",
            "got %s on me",
            "im at %s rn",
            "sitting on %s");

    private enum TransferMode {
        TRADE,
        CHOICE
    }

    private static final class TransferCommand {
        private final TransferMode mode;
        private final String category;

        private TransferCommand(TransferMode mode, String category) {
            this.mode = mode;
            this.category = category;
        }
    }

    static void handleChat(BotEntry entry, String message) {
        // Logout / relog — two-step confirmation
        if (RELOG_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.pendingAction = "relog";
                entry.following = false;
                entry.grinding  = false;
                List<String> prompts = List.of(
                        "relog? say yes to confirm",
                        "save and relog? type yes",
                        "relogging? say yes to go ahead");
                BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(prompts));
            }, 1000);
            return;
        }
        if (LOGOUT_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.pendingAction = "logout";
                entry.following = false;
                entry.grinding  = false;
                List<String> prompts = List.of(
                        "log off? you sure? say yes to confirm",
                        "save and log off? say yes if you're sure",
                        "logging off? type yes to confirm");
                BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(prompts));
            }, 1000);
            return;
        }
        if (entry.pendingAction != null && !RECOMMENDED_TRADE_ACTION.equals(entry.pendingAction)) {
            // Item-choice: three-way "drop / trade / cancel" — handled independently of yes/no
            if ("item_choice".equals(entry.pendingAction)) {
                String category = entry.pendingDropCategory;
                if (DROP_CHOICE_TRADE_PATTERN.matcher(message).find()) {
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    TimerManager.getInstance().schedule(
                            () -> BotDropManager.executeChoice(category, true, entry, entry.bot), 500);
                } else if (DROP_CHOICE_DROP_PATTERN.matcher(message).find()) {
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    TimerManager.getInstance().schedule(
                            () -> BotDropManager.executeChoice(category, false, entry, entry.bot), 500);
                } else {
                    // any other response = cancel
                    entry.pendingAction       = null;
                    entry.pendingDropCategory = null;
                    TimerManager.getInstance().schedule(
                            () -> BotManager.getInstance().botSay(entry.bot, "ok! keeping them"), 500);
                }
                return;
            }
            if (SKILL_TREE_CHOICE_ACTION.equals(entry.pendingAction)) {
                handleSkillTreeChoice(entry, entry.bot, message);
                return;
            }
            if (LOGOUT_CONFIRM_PATTERN.matcher(message).find()) {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                if ("relog".equals(action)) {
                    TimerManager.getInstance().schedule(() -> {
                        Character o = entry.owner;
                        if (o == null) return; // owner logged out before relog fired
                        BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(List.of("brb!", "relogging~", "one sec, relogging")));
                        int charId      = entry.bot.getId();
                        int ownerCharId = o.getId();
                        int world       = entry.bot.getClient().getWorld();
                        int channel     = entry.bot.getClient().getChannel();
                        TimerManager.getInstance().schedule(() -> {
                            entry.bot.saveCharToDB(true);
                            entry.bot.getClient().disconnect(false, false);
                            TimerManager.getInstance().schedule(
                                    () -> BotManager.getInstance().reloginBot(charId, ownerCharId, world, channel), 10_000);
                        }, 2000);
                    }, 1000);
                } else {
                    TimerManager.getInstance().schedule(() -> {
                        BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(List.of("ok! saving and logging off~", "cya!!", "ok bye!!")));
                        TimerManager.getInstance().schedule(() -> {
                            entry.bot.saveCharToDB(true);
                            entry.bot.getClient().disconnect(false, false);
                        }, 2000);
                    }, 1000);
                }
            } else {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                String cancelMsg = action != null && action.startsWith("drop") ? "ok! keeping them" : "ok nvm, staying!";
                TimerManager.getInstance().schedule(() ->
                        BotManager.getInstance().botSay(entry.bot, cancelMsg), 800);
            }
            return;
        }

        if (HELP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> reportHelp(entry), 600);
            return;
        }
        if (SUPPORT_OFF_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportBuffsEnabled = false;
                entry.supportHealsEnabled = false;
                BotManager.getInstance().botSay(entry.bot, "ok, support off");
            }, 600);
            return;
        }
        if (SUPPORT_ON_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportBuffsEnabled = true;
                entry.supportHealsEnabled = true;
                BotManager.getInstance().botSay(entry.bot, "ok, support on");
            }, 600);
            return;
        }
        if (BUFFS_OFF_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportBuffsEnabled = false;
                BotManager.getInstance().botSay(entry.bot, "ok, no party buffs");
            }, 600);
            return;
        }
        if (BUFFS_ON_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportBuffsEnabled = true;
                BotManager.getInstance().botSay(entry.bot, "ok, ill keep buffs up");
            }, 600);
            return;
        }
        if (HEALS_OFF_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportHealsEnabled = false;
                BotManager.getInstance().botSay(entry.bot, "ok, no heals");
            }, 600);
            return;
        }
        if (HEALS_ON_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.supportHealsEnabled = true;
                BotManager.getInstance().botSay(entry.bot, "ok, ill heal when needed");
            }, 600);
            return;
        }
        if (isRespecCommand(message)) {
            TimerManager.getInstance().schedule(() ->
                    BotManager.getInstance().botSay(entry.bot, BotBuildManager.respecSp(entry, entry.bot)), 600);
            return;
        }
        Matcher unequipSlotMatcher = UNEQUIP_SLOT_PATTERN.matcher(message);
        if (unequipSlotMatcher.find()) {
            String slotName = unequipSlotMatcher.group(2);
            short[] slots = BotEquipManager.slotsFromName(slotName);
            if (slots.length > 0) {
                TimerManager.getInstance().schedule(() ->
                        BotManager.getInstance().botSay(entry.bot, BotEquipManager.unequipSlot(entry.bot, slots)), 600);
                return;
            }
        }
        if (UNEQUIP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.grinding = false;
                BotManager.getInstance().botSay(entry.bot, BotEquipManager.unequipAll(entry.bot));
            }, 600);
            return;
        }

        if (MOVE_HERE_PATTERN.matcher(message).find()) {
            Point dest = entry.owner != null ? new Point(entry.owner.getPosition()) : null;
            if (dest != null) {
                TimerManager.getInstance().schedule(() -> {
                    entry.following = false;
                    entry.grinding = false;
                    entry.moveTarget = dest;
                    BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(MOVE_HERE_REPLIES));
                }, 1000 + ThreadLocalRandom.current().nextInt(0, 500));
            }
        } else if (FOLLOW_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.grinding = false;
                entry.moveTarget = null;
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(FOLLOW_REPLIES));
                TimerManager.getInstance().schedule(() -> entry.following = true, 250 + ThreadLocalRandom.current().nextInt(0, 500));
            }, 1500 + ThreadLocalRandom.current().nextInt(0, 500));
        } else if (GRIND_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.moveTarget = null;
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                BotManager.getInstance().setupAutopotForBot(entry.bot);
                BotManager.getInstance().botSay(entry.bot, BotManager.getInstance().grindStartMessage(entry.bot));
                TimerManager.getInstance().schedule(() -> {
                    entry.grinding = true;
                    checkBotStatus(entry, entry.bot);
                }, 250 + ThreadLocalRandom.current().nextInt(0, 500));
            }, 1500 + ThreadLocalRandom.current().nextInt(0, 500));
        } else if (STOP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.grinding  = false;
                entry.moveTarget = null;
                BotEquipManager.autoEquip(entry.bot, entry.owner, entry.pendingLootOfferItem);
                TimerManager.getInstance().schedule(() -> BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(STOP_REPLIES)), 1500);
            }, 1000);
        } else if (GREETING_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.bot.changeFaceExpression(Emote.HAPPY.getValue());
                queueBotSay(entry, BotManager.randomReply(GREETING_REPLIES));
                checkBotStatus(entry, entry.bot);
            }, 1000);
        }

        // SP build variant selection — only matched when waiting for an answer (Hero 1h vs 2h)
        if (entry.spVariantPromptSent && entry.spVariant == null) {
            if (SP_1H_PATTERN.matcher(message).find()) {
                entry.spVariant = "1h";
                BotManager.getInstance().botSay(entry.bot, "ok! going 1h sword build, Brandish first");
                BotBuildManager.autoAssignSp(entry, entry.bot);
            } else if (SP_2H_PATTERN.matcher(message).find()) {
                entry.spVariant = "2h";
                BotManager.getInstance().botSay(entry.bot, "ok! going 2h build, interleaving AC early for faster charges");
                BotBuildManager.autoAssignSp(entry, entry.bot);
            }
        }

        // AP build selection — "change build" always triggers a re-prompt;
        // "pure str" / "X dex" only apply when bot is actively waiting for the answer (apPromptSent=true)
        if (AP_CHANGE_BUILD_PATTERN.matcher(message).find()) {
            entry.apBuild      = null;
            entry.apPromptSent = false;
            String prompt = BotBuildManager.buildApPrompt(entry, entry.bot);
            if (prompt != null) BotManager.getInstance().botSay(entry.bot, prompt);
        } else if (entry.apPromptSent) {
            if (AP_PURE_STR_PATTERN.matcher(message).find()) {
                if (entry.apBuild != null && entry.apBuild.dexTarget == 0) {
                    BotManager.getInstance().botSay(entry.bot, "already doing pure str!");
                } else {
                    BotBuildManager.setApBuild(entry, new BotBuildManager.ApBuild(0), "pure str it is! dumping everything into str");
                }
            } else {
                Matcher m = AP_FIXED_DEX_PATTERN.matcher(message);
                if (m.find()) {
                    int dexTarget = Integer.parseInt(m.group(1));
                    if (entry.apBuild != null && entry.apBuild.dexTarget == dexTarget) {
                        BotManager.getInstance().botSay(entry.bot, "already doing " + dexTarget + " dex build!");
                    } else {
                        BotBuildManager.setApBuild(entry, new BotBuildManager.ApBuild(dexTarget),
                                "ok! keeping dex at " + dexTarget + ", rest into str");
                    }
                }
            }
        }

        if (TRADE_INVITE_PATTERN.matcher(message).find()) {
            Character bot = entry.bot;
            Character owner = entry.owner;
            if (owner != null && bot.getTrade() == null && owner.getTrade() == null
                    && entry.pendingTradeCategory == null) {
                TimerManager.getInstance().schedule(() -> {
                    BotManager.getInstance().botSay(bot, BotManager.randomReply(TRADE_INVITE_REPLIES));
                    TimerManager.getInstance().schedule(() -> {
                        Trade.startTrade(bot);
                        Trade.inviteTrade(bot, owner);
                    }, 800 + ThreadLocalRandom.current().nextInt(0, 400));
                }, 600 + ThreadLocalRandom.current().nextInt(0, 400));
            }
            return;
        }

        TransferCommand transferCommand = matchTransferCommand(message);
        if (transferCommand != null) {
            handleTransferCommand(entry, transferCommand);
            return;
        }

        // Info commands
        if (RECOMMENDED_GEAR_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> reportRecommendedGear(entry, entry.bot), 600);
            return;
        }
        if (SKILLS_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> reportSkills(entry, entry.bot), 1000);
            return;
        }
        if (STATS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportStats(entry, entry.bot), 1000);
        if (RANGE_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportRange(entry, entry.bot), 1000);
        if (BUILD_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportBuild(entry, entry.bot), 1000);
        if (INVENTORY_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportInventory(entry, entry.bot), 1000);
        if (isMesoQuery(message))
            TimerManager.getInstance().schedule(() -> reportMesos(entry, entry.bot), 1000);
        if (INV_SLOTS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportInventorySlots(entry, entry.bot), 1000);
        if (SCROLLS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportScrolls(entry, entry.bot), 1000);
        if (POTIONS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportPotions(entry, entry.bot), 1000);
        if (DEBUG_STATS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportDebugStats(entry, entry.bot), 1000);

        // Job advancement — check if message contains a valid job selection
        if (JOB_SELECT_PATTERN.matcher(message).find()) {
            Job advJob = resolveJobChange(entry.bot, message.toLowerCase());
            if (advJob != null) {
                String jobName = jobDisplayName(advJob);
                List<String> replies = List.of(
                        "ok, ill change to " + jobName + "!",
                        "alright becoming a " + jobName + " then",
                        "ok " + jobName + " it is!",
                        "sure, going " + jobName,
                        "ok changing to " + jobName + "...");
                BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(replies));
                TimerManager.getInstance().schedule(() -> BotStarterKitManager.advanceJob(entry.bot, entry.owner, advJob), 1000);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    public static void queueBotSay(BotEntry entry, String message) {
        synchronized (entry.msgQueue) {
            entry.msgQueue.add(message);
            if (!entry.msgSending) {
                entry.msgSending = true;
                drainMsgQueue(entry);
            }
        }
    }

    private static void drainMsgQueue(BotEntry entry) {
        String msg;
        synchronized (entry.msgQueue) {
            msg = entry.msgQueue.poll();
            if (msg == null) { entry.msgSending = false; return; }
        }
        BotManager.getInstance().botSay(entry.bot, msg);
        TimerManager.getInstance().schedule(() -> drainMsgQueue(entry), 5000);
    }

    // Status check — called on spawn, grind start, greeting, and level-up
    static void checkBotStatus(BotEntry entry, Character bot) {
        String jobPrompt = BotBuildManager.buildJobPrompt(entry, bot);
        if (jobPrompt != null) queueBotSay(entry, jobPrompt);
        String spPrompt = BotBuildManager.buildSpVariantPrompt(entry, bot);
        if (spPrompt != null) {
            queueBotSay(entry, spPrompt);
        } else {
            BotBuildManager.autoAssignSp(entry, bot);
        }
        String apPrompt = BotBuildManager.buildApPrompt(entry, bot);
        if (apPrompt != null) {
            queueBotSay(entry, apPrompt);
        } else {
            BotBuildManager.autoAssignAp(entry, bot);
        }
        if (!entry.debugPromptSent) {
            queueBotSay(entry, "ask me for debug stats if you want my attack cooldown");
            entry.debugPromptSent = true;
        }
        maybeSuggestRecommendedGear(entry, bot);
        if (!entry.spawnUpgradeCheckDone) {
            entry.spawnUpgradeCheckDone = true;
            Character owner = entry.owner;
            if (owner != null && !isOwnerIdle(entry)) {
                List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
                if (!recs.isEmpty()) {
                    Item candidate = recs.get(0).candidate();
                    entry.requestedUpgradeItemIds.add(candidate.getItemId());
                    requestUpgradeFromOwner(entry, bot, owner, candidate);
                }
            }
        }
    }

    /** Detects owner AFK (same position ≥5 min) and says "wb" when they return. */
    static void tickAfkCheck(BotEntry entry, Character owner) {
        Point pos = owner.getPosition();
        long now  = System.currentTimeMillis();

        if (entry.ownerAfkPos == null) {
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
            return;
        }

        if (!pos.equals(entry.ownerAfkPos)) {
            if (entry.ownerWasAfk) {
                entry.ownerWasAfk = false;
                final Character bot = entry.bot;
                TimerManager.getInstance().schedule(
                        () -> {
                    bot.changeFaceExpression(ThreadLocalRandom.current().nextBoolean() ? 1 : 7);
                    BotManager.getInstance().botSay(bot, BotManager.randomReply(WB_REPLIES));
                }, 2000);
            }
            entry.ownerAfkPos     = pos;
            entry.ownerAfkSinceMs = now;
        } else if (!entry.ownerWasAfk && (now - entry.ownerAfkSinceMs) >= 5 * 60_000L) {
            entry.ownerWasAfk = true;
        }
    }

    private static void reportStats(BotEntry entry, Character bot) {
        queueBotSay(entry, String.format("lv%d %s | str %d dex %d int %d luk %d | hp %d/%d mp %d/%d",
                bot.getLevel(), jobDisplayName(bot.getJob()),
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getHp(), bot.getCurrentMaxHp(),
                bot.getMp(), bot.getCurrentMaxMp()));
    }

    private static void reportRange(BotEntry entry, Character bot) {
        int watk   = bot.getTotalWatk();
        int maxDmg = Math.max(1, bot.calculateMaxBaseDamage(watk));
        int minDmg = Math.max(1, bot.calculateMinBaseDamage(watk));
        queueBotSay(entry, String.format("my dmg is %d-%d, watk %d", minDmg, maxDmg, watk));
    }

    private static void reportBuild(BotEntry entry, Character bot) {
        queueBotSay(entry, String.format("build: str %d / dex %d / int %d / luk %d, %d ap left",
                bot.getStr(), bot.getDex(), bot.getInt(), bot.getLuk(),
                bot.getRemainingAp()));
    }

    private static void reportSkills(BotEntry entry, Character bot) {
        if (bot.isBeginnerJob()) {
            reportBeginnerSkills(entry, bot);
            return;
        }

        Map<Integer, List<LearnedSkill>> skillTrees = collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            queueBotSay(entry, "no job skills yet " + bot.getRemainingSp() + " SP left");
            return;
        }

        if (skillTrees.size() == 1) {
            Map.Entry<Integer, List<LearnedSkill>> onlyTree = skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        entry.pendingAction = SKILL_TREE_CHOICE_ACTION;
        queueBotSay(entry, skillTreeChoicePrompt(skillTrees));
    }

    private static void reportBeginnerSkills(BotEntry entry, Character bot) {
        List<LearnedSkill> beginnerSkills = collectLearnedBeginnerSkills(bot);
        int beginnerSpLeft = getRemainingBeginnerSp(bot);

        if (beginnerSkills.isEmpty()) {
            queueBotSay(entry, "no learned beginner skills yet " + beginnerSpLeft + " beginner SP left");
            return;
        }

        StringBuilder line = new StringBuilder("beginner: ");
        for (int i = 0; i < beginnerSkills.size(); i++) {
            if (i > 0) {
                line.append(", ");
            }

            LearnedSkill skill = beginnerSkills.get(i);
            line.append(skill.name()).append(" lv").append(skill.level());
        }
        line.append(" | ").append(beginnerSpLeft).append(" beginner SP left");
        queueBotSay(entry, line.toString());
    }

    private static void reportInventory(BotEntry entry, Character bot) {
        queueBotSay(entry, BotDropManager.inventorySummary(bot));
    }

    private static void reportMesos(BotEntry entry, Character bot) {
        queueBotSay(entry, buildMesoReport(bot.getMeso()));
    }

    private static void reportInventorySlots(BotEntry entry, Character bot) {
        queueBotSay(entry, BotDropManager.slotsReport(bot));
    }

    private static void reportScrolls(BotEntry entry, Character bot) {
        int count = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            if (id >= 2040000 && id < 2050000) count += item.getQuantity();
        }
        queueBotSay(entry, count > 0
                ? "I have " + count + " scroll" + (count != 1 ? "s" : "") + " on me"
                : "no scrolls on me");
    }

    private static void reportPotions(BotEntry entry, Character bot) {
        int[] counts = BotManager.getInstance().countPotions(bot);
        queueBotSay(entry, buildPotionReport(counts[0], counts[1]));
    }

    static String buildPotionReport(int hp, int mp) {
        if (hp == 0 && mp == 0) {
            return "no pots on me rn";
        }
        if (mp == 0) {
            return "I have " + hp + " hp pot" + (hp != 1 ? "s" : "") + ", no mp pots";
        }
        if (hp == 0) {
            return "no hp pots, " + mp + " mp pot" + (mp != 1 ? "s" : "");
        }

        return "I have " + hp + " hp pot" + (hp != 1 ? "s" : "")
                + " and " + mp + " mp pot" + (mp != 1 ? "s" : "");
    }

    static boolean isMesoQuery(String message) {
        return MESOS_PATTERN.matcher(message).find();
    }

    static String buildMesoReport(int mesos) {
        String amount = formatCompactMesos(mesos);
        String pattern = MESO_REPLIES.get(ThreadLocalRandom.current().nextInt(MESO_REPLIES.size()));
        return String.format(pattern, amount);
    }

    static String formatCompactMesos(int mesos) {
        if (mesos < 1_000) {
            return String.valueOf(mesos);
        }

        double value = mesos;
        String[] suffixes = {"k", "m", "b"};
        int suffixIndex = -1;

        while (value >= 1_000d && suffixIndex < suffixes.length - 1) {
            value /= 1_000d;
            suffixIndex++;
        }

        double rounded = Math.round(value * 10d) / 10d;
        if (rounded >= 1_000d && suffixIndex < suffixes.length - 1) {
            rounded = Math.round((rounded / 1_000d) * 10d) / 10d;
            suffixIndex++;
        }

        if (Math.floor(rounded) == rounded) {
            return String.format(Locale.ROOT, "%.0f%s", rounded, suffixes[suffixIndex]);
        }

        return String.format(Locale.ROOT, "%.1f%s", rounded, suffixes[suffixIndex]);
    }

    private static void reportDebugStats(BotEntry entry, Character bot) {
        queueBotSay(entry, BotCombatManager.describeDebugStats(entry, bot));
    }

    private static void reportHelp(BotEntry entry) {
        queueBotSay(entry, "commands: follow, stop, move here, grind, stats, skills, inventory, mesos, slots, scrolls, pots, debug stats, respec");
        queueBotSay(entry, "support: support on/off, buffs on/off, heals on/off");
        queueBotSay(entry, "gear: ask 'any upgrades?' or say 'trade recommended gear'");
        queueBotSay(entry, "trade: mesos, scrolls, pots, equips, etc, or named items");
    }

    static boolean isRespecCommand(String message) {
        return RESPEC_PATTERN.matcher(message).find();
    }

    private static void reportRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        if (owner == null) {
            queueBotSay(entry, "can't check your gear rn");
            return;
        }

        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(owner, bot);
        if (recs.isEmpty()) {
            queueBotSay(entry, "no better gear for you rn");
            return;
        }

        offerGearItem(entry, bot, owner, recs.get(0).candidate());
        entry.nextGearSuggestionAt = System.currentTimeMillis() + 60_000L;
    }

    private static void maybeSuggestRecommendedGear(BotEntry entry, Character bot) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null || now < entry.nextGearSuggestionAt) {
            return;
        }

        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(owner, bot);
        if (recs.isEmpty()) {
            return;
        }

        offerGearItem(entry, bot, owner, recs.get(0).candidate());
        entry.nextGearSuggestionAt = now + 60_000L;
    }

    /** Returns true when the owner hasn't moved in ≥5 min (AFK). Skip chat interactions. */
    static boolean isOwnerIdle(BotEntry entry) {
        return entry.ownerWasAfk;
    }

    /**
     * Called when the owner gains an equip item (loot, buy, etc.).
     * If it is an upgrade for the bot and hasn't been requested before, ask for it once.
     */
    static void notifyOwnerGainedEquip(BotEntry entry, Character bot, Item item) {
        if (isOwnerIdle(entry)) return;
        if (entry.requestedUpgradeItemIds.contains(item.getItemId())) return;
        if (entry.pendingAction != null || entry.pendingTradeCategory != null) return;
        Character owner = entry.owner;
        if (owner == null) return;

        List<BotEquipManager.EquipRecommendation> recs = BotEquipManager.findRecommendedEquips(bot, owner);
        boolean isUpgrade = recs.stream().anyMatch(r -> r.candidate().getItemId() == item.getItemId());
        if (!isUpgrade) return;

        entry.requestedUpgradeItemIds.add(item.getItemId());
        requestUpgradeFromOwner(entry, bot, owner, item);
    }

    private static void requestUpgradeFromOwner(BotEntry entry, Character bot, Character owner, Item ownerItem) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        String itemName = ii.getName(ownerItem.getItemId());
        if (itemName == null || itemName.isBlank()) itemName = String.valueOf(ownerItem.getItemId());

        entry.pendingAction = RECOMMENDED_TRADE_ACTION;
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = ownerItem;
        entry.pendingLootOfferRecipientId = owner.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 45_000L;
        entry.pendingLootOfferBotRequesting = true;

        List<String> prompts = List.of(
                "hey, that " + itemName + " would be an upgrade for me, can i have it pls?",
                "Can I have your " + itemName + "?",
                "Your " + itemName + " would be better on me! trade it over?",
                "I could use that " + itemName + " of yours ;)",
                "that " + itemName + " is an upgrade for me, want to trade?");
        queueBotSay(entry, BotManager.randomReply(prompts));
    }

    private static void offerGearItem(BotEntry entry, Character bot, Character owner, Item item) {
        if (entry.pendingAction != null || entry.pendingTradeCategory != null
                || !BotDropManager.hasItem(bot, item)) {
            return;
        }
        entry.pendingAction = RECOMMENDED_TRADE_ACTION;
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = item;
        entry.pendingLootOfferRecipientId = owner.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 30_000L;
        queueBotSay(entry, buildLootOfferPrompt(owner, owner, item));
    }

    static void scheduleLootOfferPrompt(BotEntry entry, Character bot, Item item, long delayMs) {
        Character owner = entry.owner;
        long now = System.currentTimeMillis();
        if (owner == null || item == null || entry.pendingGearPromptAt > now || isOwnerIdle(entry)) {
            return;
        }

        long scheduledAt = now + Math.max(0L, delayMs);
        entry.pendingGearPromptAt = scheduledAt;
        TimerManager.getInstance().schedule(() -> promptLootOfferAfterLoot(entry, bot, item, scheduledAt), delayMs);
    }

    static boolean handlePendingLootOfferResponse(BotEntry entry, Character speaker, String message) {
        expirePendingLootOffer(entry);
        if (!RECOMMENDED_TRADE_ACTION.equals(entry.pendingAction)
                || speaker == null
                || speaker.getId() != entry.pendingLootOfferRecipientId) {
            return false;
        }

        if (LOGOUT_CONFIRM_PATTERN.matcher(message).find()) {
            if (entry.pendingLootOfferBotRequesting) {
                clearPendingLootOffer(entry);
                TimerManager.getInstance().schedule(
                        () -> BotManager.getInstance().botSay(entry.bot,
                                "ty! inv me?"), 500);
            } else {
                Item item = entry.pendingLootOfferItem;
                clearPendingLootOffer(entry);
                TimerManager.getInstance().schedule(
                        () -> BotDropManager.startTradeTransfer(item, speaker, entry, entry.bot), 500);
            }
            return true;
        }
        if (NEGATIVE_CONFIRM_PATTERN.matcher(message).find()) {
            clearPendingLootOffer(entry);
            TimerManager.getInstance().schedule(
                    () -> BotManager.getInstance().botSay(entry.bot, "ok, keeping it for now"), 500);
            return true;
        }

        return false;
    }

    static void expirePendingLootOffer(BotEntry entry) {
        if (RECOMMENDED_TRADE_ACTION.equals(entry.pendingAction)
                && entry.pendingLootOfferExpiresAt > 0L
                && System.currentTimeMillis() >= entry.pendingLootOfferExpiresAt) {
            clearPendingLootOffer(entry);
        }
    }

    private static void promptLootOfferAfterLoot(BotEntry entry, Character bot, Item item, long scheduledAt) {
        if (entry.pendingGearPromptAt != scheduledAt) {
            return;
        }
        entry.pendingGearPromptAt = 0L;

        Character owner = entry.owner;
        if (owner == null
                || entry.pendingAction != null
                || entry.pendingTradeCategory != null
                || !BotDropManager.hasItem(bot, item)) {
            return;
        }

        Character recipient = findLootOfferRecipient(entry, bot, item);
        if (recipient == null) {
            return;
        }

        entry.pendingAction = RECOMMENDED_TRADE_ACTION;
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = item;
        entry.pendingLootOfferRecipientId = recipient.getId();
        entry.pendingLootOfferExpiresAt = System.currentTimeMillis() + 30_000L;
        queueBotSay(entry, buildLootOfferPrompt(recipient, owner, item));
    }

    static String buildLootOfferPrompt(String recipientName, String itemName, boolean targetIsOwner) {
        List<String> prompts = targetIsOwner
                ? List.of(
                        "I have %s, you want?",
                        "picked up %s, want it?",
                        "I got %s for you, want?")
                : List.of(
                        "%s, I have %s, you want?",
                        "%s, picked up %s, want it?",
                        "%s, I got %s if you want it");
        String format = BotManager.randomReply(prompts);
        return targetIsOwner ? String.format(format, itemName) : String.format(format, recipientName, itemName);
    }

    private static String buildLootOfferPrompt(Character recipient, Character owner, Item item) {
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        String itemName = ii.getName(item.getItemId());
        if (itemName == null || itemName.isBlank()) {
            itemName = String.valueOf(item.getItemId());
        }
        boolean targetIsOwner = owner != null && recipient.getId() == owner.getId();
        return buildLootOfferPrompt(recipient.getName(), itemName, targetIsOwner);
    }

    private static Character findLootOfferRecipient(BotEntry entry, Character bot, Item item) {
        Character owner = entry.owner;
        if (owner == null) {
            return null;
        }
        if (BotEquipManager.findRecommendationForItem(owner, bot, item) != null) {
            return owner;
        }

        for (Character member : owner.getPartyMembersOnSameMap()) {
            if (member == null
                    || member.getId() == owner.getId()
                    || member.getId() == bot.getId()
                    || member.getClient() instanceof BotClient) {
                continue;
            }
            if (BotEquipManager.findRecommendationForItem(member, bot, item) != null) {
                return member;
            }
        }

        return null;
    }

    private static void clearPendingLootOffer(BotEntry entry) {
        if (RECOMMENDED_TRADE_ACTION.equals(entry.pendingAction)) {
            entry.pendingAction = null;
        }
        entry.pendingDropCategory = null;
        entry.pendingLootOfferItem = null;
        entry.pendingLootOfferRecipientId = 0;
        entry.pendingLootOfferExpiresAt = 0L;
        entry.pendingLootOfferBotRequesting = false;
    }

    private static void handleSkillTreeChoice(BotEntry entry, Character bot, String message) {
        Map<Integer, List<LearnedSkill>> skillTrees = collectLearnedSkillTrees(bot);
        if (skillTrees.isEmpty()) {
            entry.pendingAction = null;
            queueBotSay(entry, "no job skills yet");
            return;
        }

        if (skillTrees.size() == 1) {
            entry.pendingAction = null;
            Map.Entry<Integer, List<LearnedSkill>> onlyTree = skillTrees.entrySet().iterator().next();
            queueSkillTreeReport(entry, onlyTree.getKey(), onlyTree.getValue());
            return;
        }

        Integer treeId = resolveSkillTreeChoice(message, skillTrees);
        if (treeId == null) {
            queueBotSay(entry, skillTreeChoicePrompt(skillTrees));
            return;
        }

        entry.pendingAction = null;
        queueSkillTreeReport(entry, treeId, skillTrees.get(treeId));
    }

    private static Map<Integer, List<LearnedSkill>> collectLearnedSkillTrees(Character bot) {
        Map<Integer, List<LearnedSkill>> skillTrees = new TreeMap<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : bot.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            int treeId = skillId / 10000;
            skillTrees.computeIfAbsent(treeId, ignored -> new ArrayList<>())
                    .add(new LearnedSkill(skillId, skillName(skillId), skillEntry.skillevel));
        }

        for (List<LearnedSkill> skills : skillTrees.values()) {
            skills.sort(Comparator.comparingInt(LearnedSkill::id));
        }
        return skillTrees;
    }

    private static List<LearnedSkill> collectLearnedBeginnerSkills(Character bot) {
        List<LearnedSkill> beginnerSkills = new ArrayList<>();
        for (Map.Entry<Skill, Character.SkillEntry> entry : bot.getSkills().entrySet()) {
            Skill skill = entry.getKey();
            Character.SkillEntry skillEntry = entry.getValue();
            if (skill == null || skillEntry == null || skillEntry.skillevel <= 0) {
                continue;
            }

            int skillId = skill.getId();
            if (!skill.isBeginnerSkill() || GameConstants.isHiddenSkills(skillId)) {
                continue;
            }

            beginnerSkills.add(new LearnedSkill(skillId, skillName(skillId), skillEntry.skillevel));
        }

        beginnerSkills.sort(Comparator.comparingInt(LearnedSkill::id));
        return beginnerSkills;
    }

    private static int getRemainingBeginnerSp(Character bot) {
        int usedBeginnerSp = 0;
        int beginnerSkillBase = bot.getJobType() * 10000000 + 1000;
        for (int i = 0; i < 3; i++) {
            Skill skill = SkillFactory.getSkill(beginnerSkillBase + i);
            if (skill != null) {
                usedBeginnerSp += bot.getSkillLevel(skill);
            }
        }

        return Math.max(0, Math.min(bot.getLevel() - 1, 6) - usedBeginnerSp);
    }

    private static void queueSkillTreeReport(BotEntry entry, int treeId, List<LearnedSkill> skills) {
        if (skills == null || skills.isEmpty()) {
            queueBotSay(entry, "no learned skills in " + skillTreeLabel(treeId));
            return;
        }

        String label = skillTreeLabel(treeId);
        String prefix = label + ": ";
        String followupPrefix = "more " + label + ": ";
        StringBuilder line = new StringBuilder(prefix);
        int countOnLine = 0;

        for (LearnedSkill skill : skills) {
            String piece = skill.name() + " lv" + skill.level();
            boolean needsSeparator = countOnLine > 0;
            int extraChars = piece.length() + (needsSeparator ? 2 : 0);
            if ((line.length() + extraChars > 100 || countOnLine >= 3) && countOnLine > 0) {
                queueBotSay(entry, line.toString());
                line = new StringBuilder(followupPrefix);
                countOnLine = 0;
                needsSeparator = false;
            }

            if (needsSeparator) {
                line.append(", ");
            }
            line.append(piece);
            countOnLine++;
        }

        if (countOnLine > 0) {
            queueBotSay(entry, line.toString());
        }
    }

    private static Integer resolveSkillTreeChoice(String message, Map<Integer, List<LearnedSkill>> skillTrees) {
        Matcher matcher = Pattern.compile("\\b(\\d{3,4})\\b").matcher(message);
        while (matcher.find()) {
            int treeId = Integer.parseInt(matcher.group(1));
            if (skillTrees.containsKey(treeId)) {
                return treeId;
            }
        }

        String normalizedMessage = normalizeChoiceText(message);
        List<Integer> matches = new ArrayList<>();
        for (int treeId : skillTrees.keySet()) {
            if (matchesSkillTreeChoice(normalizedMessage, treeId)) {
                matches.add(treeId);
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean matchesSkillTreeChoice(String normalizedMessage, int treeId) {
        String fullLabel = normalizeChoiceText(skillTreeLabel(treeId));
        if (!fullLabel.isEmpty() && normalizedMessage.contains(fullLabel)) {
            return true;
        }

        Job job = Job.getById(treeId);
        if (job == null) {
            return false;
        }

        String baseLabel = normalizeChoiceText(jobDisplayName(job));
        return !baseLabel.isEmpty() && normalizedMessage.contains(baseLabel);
    }

    private static String skillTreeChoicePrompt(Map<Integer, List<LearnedSkill>> skillTrees) {
        List<String> labels = new ArrayList<>();
        for (int treeId : skillTrees.keySet()) {
            labels.add(skillTreeLabel(treeId));
        }
        return "which skill tree? " + String.join(", ", labels);
    }

    private static String skillTreeLabel(int treeId) {
        Job job = Job.getById(treeId);
        if (job == null) {
            return "tree " + treeId;
        }

        return switch (job) {
            case NOBLESSE -> "noblesse (" + treeId + ")";
            case DAWNWARRIOR1 -> "dawn warrior 1st job (" + treeId + ")";
            case DAWNWARRIOR2 -> "dawn warrior 2nd job (" + treeId + ")";
            case DAWNWARRIOR3 -> "dawn warrior 3rd job (" + treeId + ")";
            case DAWNWARRIOR4 -> "dawn warrior 4th job (" + treeId + ")";
            case BLAZEWIZARD1 -> "blaze wizard 1st job (" + treeId + ")";
            case BLAZEWIZARD2 -> "blaze wizard 2nd job (" + treeId + ")";
            case BLAZEWIZARD3 -> "blaze wizard 3rd job (" + treeId + ")";
            case BLAZEWIZARD4 -> "blaze wizard 4th job (" + treeId + ")";
            case WINDARCHER1 -> "wind archer 1st job (" + treeId + ")";
            case WINDARCHER2 -> "wind archer 2nd job (" + treeId + ")";
            case WINDARCHER3 -> "wind archer 3rd job (" + treeId + ")";
            case WINDARCHER4 -> "wind archer 4th job (" + treeId + ")";
            case NIGHTWALKER1 -> "night walker 1st job (" + treeId + ")";
            case NIGHTWALKER2 -> "night walker 2nd job (" + treeId + ")";
            case NIGHTWALKER3 -> "night walker 3rd job (" + treeId + ")";
            case NIGHTWALKER4 -> "night walker 4th job (" + treeId + ")";
            case THUNDERBREAKER1 -> "thunder breaker 1st job (" + treeId + ")";
            case THUNDERBREAKER2 -> "thunder breaker 2nd job (" + treeId + ")";
            case THUNDERBREAKER3 -> "thunder breaker 3rd job (" + treeId + ")";
            case THUNDERBREAKER4 -> "thunder breaker 4th job (" + treeId + ")";
            case LEGEND -> "legend (" + treeId + ")";
            case ARAN1 -> "aran 1st job (" + treeId + ")";
            case ARAN2 -> "aran 2nd job (" + treeId + ")";
            case ARAN3 -> "aran 3rd job (" + treeId + ")";
            case ARAN4 -> "aran 4th job (" + treeId + ")";
            case EVAN -> "evan (" + treeId + ")";
            case EVAN1 -> "evan 1st job (" + treeId + ")";
            case EVAN2 -> "evan 2nd job (" + treeId + ")";
            case EVAN3 -> "evan 3rd job (" + treeId + ")";
            case EVAN4 -> "evan 4th job (" + treeId + ")";
            case EVAN5 -> "evan 5th job (" + treeId + ")";
            case EVAN6 -> "evan 6th job (" + treeId + ")";
            case EVAN7 -> "evan 7th job (" + treeId + ")";
            case EVAN8 -> "evan 8th job (" + treeId + ")";
            case EVAN9 -> "evan 9th job (" + treeId + ")";
            case EVAN10 -> "evan 10th job (" + treeId + ")";
            default -> jobDisplayName(job) + " (" + treeId + ")";
        };
    }

    private static String normalizeChoiceText(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String skillName(int skillId) {
        String name = SkillFactory.getSkillName(skillId);
        return name != null && !name.isBlank() ? name : String.valueOf(skillId);
    }

    private static void handleTransferCommand(BotEntry entry, TransferCommand transferCommand) {
        String category = transferCommand.category;
        if (transferCommand.mode == TransferMode.TRADE && BotDropManager.isMesoCategory(category)) {
            TimerManager.getInstance().schedule(
                    () -> BotDropManager.startTradeTransfer(category, entry, entry.bot), 600);
            return;
        }

        if (!BotDropManager.hasTransferableItems(category, entry, entry.bot)) {
            TimerManager.getInstance().schedule(
                    () -> BotManager.getInstance().botSay(entry.bot, BotDropManager.noItemsReply(category)), 600);
            return;
        }

        switch (transferCommand.mode) {
            case TRADE -> TimerManager.getInstance().schedule(
                    () -> BotDropManager.startTradeTransfer(category, entry, entry.bot), 600);
            case CHOICE -> {
                entry.pendingAction = "item_choice";
                entry.pendingDropCategory = category;
                TimerManager.getInstance().schedule(
                        () -> BotManager.getInstance().botSay(entry.bot, dropOrTradePrompt(category)), 600);
            }
        }
    }

    private static TransferCommand matchTransferCommand(String message) {
        String tradeCategory = matchTradeCategory(message);
        if (tradeCategory != null) {
            return new TransferCommand(TransferMode.TRADE, tradeCategory);
        }

        String choiceCategory = matchChoiceCategory(message);
        if (choiceCategory != null) {
            return new TransferCommand(TransferMode.CHOICE, choiceCategory);
        }

        return null;
    }

    static String matchTradeCategory(String message) {
        String mesoCategory = matchTradeMesoCategory(message);
        if (mesoCategory != null) return mesoCategory;

        if (TRADE_RECOMMENDED_COMMAND_PATTERN.matcher(message).find()) return "recommended";
        if (TRADE_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (TRADE_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (TRADE_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (TRADE_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (TRADE_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";

        Matcher matcher = TRADE_ITEM_COMMAND_PATTERN.matcher(message);
        return matcher.find() ? "name:" + matcher.group(1).trim() : null;
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

    static String matchChoiceCategory(String message) {
        if (DROP_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (DROP_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (DROP_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (DROP_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (DROP_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";
        Matcher dropMatcher = DROP_ITEM_COMMAND_PATTERN.matcher(message);
        if (dropMatcher.find()) return "name:" + dropMatcher.group(1).trim();

        if (ASK_SCROLLS_COMMAND_PATTERN.matcher(message).find()) return "scrolls";
        if (ASK_POTS_COMMAND_PATTERN.matcher(message).find()) return "pots";
        if (ASK_USE_COMMAND_PATTERN.matcher(message).find()) return "use";
        if (ASK_EQUIPS_COMMAND_PATTERN.matcher(message).find()) return "equips";
        if (ASK_ETC_COMMAND_PATTERN.matcher(message).find()) return "etc";

        Matcher matcher = ASK_ITEM_COMMAND_PATTERN.matcher(message);
        return matcher.find() ? "name:" + matcher.group(1).trim() : null;
    }

    private static final String[] DROP_OR_TRADE_PROMPTS = {
        "ok, giving you my %s - drop or trade?",
        "sure! %s - drop or trade?",
        "got it, %s - drop or trade?",
        "just to confirm, drop or trade my %s?",
        "dropping or trading my %s?",
    };

    private static String dropOrTradePrompt(String category) {
        String what = switch (category) {
            case "scrolls" -> "scrolls";
            case "pots"    -> "pots";
            case "use"     -> "use items";
            case "equips"  -> "equips";
            case "etc"     -> "etc items";
            default        -> category.startsWith("name:") ? "'" + category.substring(5) + "'" : "those items";
        };
        String fmt = DROP_OR_TRADE_PROMPTS[ThreadLocalRandom.current().nextInt(DROP_OR_TRADE_PROMPTS.length)];
        return String.format(fmt, what);
    }

    /** Maps a chat keyword to the correct next Job given bot's current job and level. Returns null if not valid. */
    private static Job resolveJobChange(Character bot, String msg) {
        Job cur = bot.getJob();
        int lvl = bot.getLevel();

        return switch (cur) {
            case BEGINNER -> {
                if (lvl >= 8  && msg.matches(".*\\b(mage|magician|wizard|cleric|healer|fp|il|fp mage|il mage)\\b.*")) yield Job.MAGICIAN;
                if (lvl >= 10 && msg.matches(".*\\b(warrior|fighter|page|spearman|sader)\\b.*")) yield Job.WARRIOR;
                if (lvl >= 10 && msg.matches(".*\\b(bowman|bowmen|archer|hunter|crossbow|xbow)\\b.*")) yield Job.BOWMAN;
                if (lvl >= 10 && msg.matches(".*\\b(thief|assassin|sin|bandit|dit)\\b.*")) yield Job.THIEF;
                if (lvl >= 10 && msg.matches(".*\\b(pirate|brawler|gunslinger|gun|bucc)\\b.*")) yield Job.PIRATE;
                yield null;
            }
            // 2nd job
            case WARRIOR -> lvl < 30 ? null :
                    msg.matches(".*\\b(fighter|sader)\\b.*") ? Job.FIGHTER :
                    msg.matches(".*\\bpage\\b.*") ? Job.PAGE :
                    msg.matches(".*\\b(spearman|spear)\\b.*") ? Job.SPEARMAN : null;
            case MAGICIAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(fp|fp wizard|fp mage|fire|f\\.p)\\b.*") ? Job.FP_WIZARD :
                    msg.matches(".*\\b(il|il wizard|il mage|ice|i\\.l)\\b.*") ? Job.IL_WIZARD :
                    msg.matches(".*\\b(cleric|healer|priest|bishop)\\b.*") ? Job.CLERIC : null;
            case BOWMAN -> lvl < 30 ? null :
                    msg.matches(".*\\b(hunter|bow)\\b.*") ? Job.HUNTER :
                    msg.matches(".*\\b(crossbow|xbow|crossbowman)\\b.*") ? Job.CROSSBOWMAN : null;
            case THIEF -> lvl < 30 ? null :
                    msg.matches(".*\\b(assassin|sin)\\b.*") ? Job.ASSASSIN :
                    msg.matches(".*\\b(bandit|dit)\\b.*") ? Job.BANDIT : null;
            case PIRATE -> lvl < 30 ? null :
                    msg.matches(".*\\b(brawler|knuckle)\\b.*") ? Job.BRAWLER :
                    msg.matches(".*\\b(gunslinger|gun)\\b.*") ? Job.GUNSLINGER : null;
            // 3rd job
            case FIGHTER     -> lvl >= 70 && msg.matches(".*\\bcrusader\\b.*")                 ? Job.CRUSADER     : null;
            case PAGE        -> lvl >= 70 && msg.matches(".*\\b(white knight|wk)\\b.*")         ? Job.WHITEKNIGHT  : null;
            case SPEARMAN    -> lvl >= 70 && msg.matches(".*\\b(dragon knight|dk)\\b.*")        ? Job.DRAGONKNIGHT : null;
            case FP_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(fp mage|fp)\\b.*")              ? Job.FP_MAGE      : null;
            case IL_WIZARD   -> lvl >= 70 && msg.matches(".*\\b(il mage|il)\\b.*")              ? Job.IL_MAGE      : null;
            case CLERIC      -> lvl >= 70 && msg.matches(".*\\bpriest\\b.*")                    ? Job.PRIEST       : null;
            case HUNTER      -> lvl >= 70 && msg.matches(".*\\branger\\b.*")                    ? Job.RANGER       : null;
            case CROSSBOWMAN -> lvl >= 70 && msg.matches(".*\\bsniper\\b.*")                    ? Job.SNIPER       : null;
            case ASSASSIN    -> lvl >= 70 && msg.matches(".*\\bhermit\\b.*")                    ? Job.HERMIT       : null;
            case BANDIT      -> lvl >= 70 && msg.matches(".*\\b(chief bandit|cb|chief)\\b.*")   ? Job.CHIEFBANDIT  : null;
            case BRAWLER     -> lvl >= 70 && msg.matches(".*\\bmarauder\\b.*")                  ? Job.MARAUDER     : null;
            case GUNSLINGER  -> lvl >= 70 && msg.matches(".*\\boutlaw\\b.*")                    ? Job.OUTLAW       : null;
            // 4th job
            case CRUSADER    -> lvl >= 120 && msg.matches(".*\\bhero\\b.*")                         ? Job.HERO        : null;
            case WHITEKNIGHT -> lvl >= 120 && msg.matches(".*\\bpaladin\\b.*")                      ? Job.PALADIN     : null;
            case DRAGONKNIGHT -> lvl >= 120 && msg.matches(".*\\b(dark knight|drk)\\b.*")           ? Job.DARKKNIGHT  : null;
            case FP_MAGE     -> lvl >= 120 && msg.matches(".*\\b(fp archmage|fp arch)\\b.*")        ? Job.FP_ARCHMAGE : null;
            case IL_MAGE     -> lvl >= 120 && msg.matches(".*\\b(il archmage|il arch)\\b.*")        ? Job.IL_ARCHMAGE : null;
            case PRIEST      -> lvl >= 120 && msg.matches(".*\\bbishop\\b.*")                       ? Job.BISHOP      : null;
            case RANGER      -> lvl >= 120 && msg.matches(".*\\b(bowmaster|bm)\\b.*")               ? Job.BOWMASTER   : null;
            case SNIPER      -> lvl >= 120 && msg.matches(".*\\b(marksman|mm)\\b.*")                ? Job.MARKSMAN    : null;
            case HERMIT      -> lvl >= 120 && msg.matches(".*\\b(night lord|nl)\\b.*")              ? Job.NIGHTLORD   : null;
            case CHIEFBANDIT -> lvl >= 120 && msg.matches(".*\\b(shadower|shad)\\b.*")              ? Job.SHADOWER    : null;
            case MARAUDER    -> lvl >= 120 && msg.matches(".*\\b(buccaneer|bucc)\\b.*")             ? Job.BUCCANEER   : null;
            case OUTLAW      -> lvl >= 120 && msg.matches(".*\\bcorsair\\b.*")                      ? Job.CORSAIR     : null;
            default -> null;
        };
    }

    static String jobDisplayName(Job job) {
        return switch (job) {
            case WARRIOR     -> "warrior";      case MAGICIAN    -> "mage";
            case BOWMAN      -> "bowman";       case THIEF       -> "thief";
            case PIRATE      -> "pirate";       case FIGHTER     -> "fighter";
            case PAGE        -> "page";         case SPEARMAN    -> "spearman";
            case FP_WIZARD   -> "f/p wizard";   case IL_WIZARD   -> "i/l wizard";
            case CLERIC      -> "cleric";       case HUNTER      -> "hunter";
            case CROSSBOWMAN -> "crossbowman";  case ASSASSIN    -> "assassin";
            case BANDIT      -> "bandit";       case BRAWLER     -> "brawler";
            case GUNSLINGER  -> "gunslinger";   case CRUSADER    -> "crusader";
            case WHITEKNIGHT -> "white knight"; case DRAGONKNIGHT-> "dragon knight";
            case FP_MAGE     -> "f/p mage";     case IL_MAGE     -> "i/l mage";
            case PRIEST      -> "priest";       case RANGER      -> "ranger";
            case SNIPER      -> "sniper";       case HERMIT      -> "hermit";
            case CHIEFBANDIT -> "chief bandit"; case MARAUDER    -> "marauder";
            case OUTLAW      -> "outlaw";       case HERO        -> "hero";
            case PALADIN     -> "paladin";      case DARKKNIGHT  -> "dark knight";
            case FP_ARCHMAGE -> "f/p archmage"; case IL_ARCHMAGE -> "i/l archmage";
            case BISHOP      -> "bishop";       case BOWMASTER   -> "bowmaster";
            case MARKSMAN    -> "marksman";     case NIGHTLORD   -> "night lord";
            case SHADOWER    -> "shadower";     case BUCCANEER   -> "buccaneer";
            case NOBLESSE    -> "noblesse";
            case DAWNWARRIOR1 -> "dawn warrior";   case DAWNWARRIOR2 -> "dawn warrior";
            case DAWNWARRIOR3 -> "dawn warrior";   case DAWNWARRIOR4 -> "dawn warrior";
            case BLAZEWIZARD1 -> "blaze wizard";   case BLAZEWIZARD2 -> "blaze wizard";
            case BLAZEWIZARD3 -> "blaze wizard";   case BLAZEWIZARD4 -> "blaze wizard";
            case WINDARCHER1  -> "wind archer";    case WINDARCHER2  -> "wind archer";
            case WINDARCHER3  -> "wind archer";    case WINDARCHER4  -> "wind archer";
            case NIGHTWALKER1 -> "night walker";   case NIGHTWALKER2 -> "night walker";
            case NIGHTWALKER3 -> "night walker";   case NIGHTWALKER4 -> "night walker";
            case THUNDERBREAKER1 -> "thunder breaker"; case THUNDERBREAKER2 -> "thunder breaker";
            case THUNDERBREAKER3 -> "thunder breaker"; case THUNDERBREAKER4 -> "thunder breaker";
            case LEGEND       -> "legend";
            case ARAN1        -> "aran";         case ARAN2        -> "aran";
            case ARAN3        -> "aran";         case ARAN4        -> "aran";
            case CORSAIR     -> "corsair";
            default -> job.name().toLowerCase();
        };
    }
}
