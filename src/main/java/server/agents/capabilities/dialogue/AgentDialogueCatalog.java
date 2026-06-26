package server.agents.capabilities.dialogue;

import java.util.List;

public final class AgentDialogueCatalog {
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
    private static final List<String> AMMO_NOT_NEEDED_REPLIES = List.of(
            "i don't use shareable arrow ammo rn",
            "i don't need arrows or bolts rn",
            "ammo sharing is only for arrows and bolts",
            "not using bow ammo rn");
    private static final List<String> FAME_OK_REPLIES = List.of(
            "k", "kk", "kkk", "ok", "sure", "done",
            "famed %s", "my turn tomorrow?", "that would be 1m pls",
            "trade me 500k first", "S> fame 1m", "famed!", "got u",
            "ok famed", "yw", "done, fame me back?",
            "np", "ok but u owe me", "famed %s, now we're even",
            "ok done, 1m in the mail pls", "fame4fame?",
            "consider this a gift", "ok fine, famed",
            "you're welcome, now buy me dinner", "done. u got a good one");
    private static final List<String> FAME_COOLDOWN_REPLIES = List.of(
            "already famed someone today, try tmrw",
            "can only fame once a day, already used it",
            "famed earlier today, comeback tomorrow",
            "daily limit hit, tomorrow ok?",
            "i'm tapped out on fame for today");
    private static final List<String> FAME_SAME_PERSON_REPLIES = List.of(
            "already famed %s this month",
            "famed %s too recently, next month",
            "can't fame %s again yet, monthly limit",
            "monthly limit for %s, try again next month",
            "famed %s already this month, gotta wait");
    private static final List<String> OWNER_POT_SHORTAGE_REPLIES = List.of(
            "almost out of %s pots too, i thought u were our shopper?",
            "i checked, nobody has spare %s pots. that's kinda your department lol",
            "we're low on %s pots too, boss",
            "no spare %s pots in the squad rn",
            "everyone's light on %s pots, might need a shop run",
            "i'd help, but we're all thin on %s pots",
            "no one has enough %s pots to share rn",
            "we're not holding extra %s pots, thought you packed supplies",
            "can't find spare %s pots. maybe time to restock?",
            "almost dry on %s pots too, don't look at me");
    private static final List<String> OWNER_AMMO_SHORTAGE_REPLIES = List.of(
            "almost out of ammo too, i thought u were our shopper?",
            "i checked, nobody has spare ammo. that's kinda your department lol",
            "we're low on ammo too, boss",
            "no spare ammo in the squad rn",
            "everyone's light on ammo, might need a shop run",
            "i'd help, but we're all thin on ammo",
            "no one has enough ammo to share rn",
            "we're not holding extra ammo, thought you packed supplies",
            "can't find spare ammo. maybe time to restock?",
            "almost dry on ammo too, don't look at me");
    private static final List<String> TRADE_INVITE_REPLIES = List.of(
            "ok", "sure", "k", "one sec", "coming to trade", "np", "k opening trade");
    private static final List<String> GREETING_REPLIES = List.of(
            "hey", "hi", "sup", "yo", "heya", "hii", "hey!!", "hi!!", "hai", "haii",
            "heyo", "ello", "o/", "hai", "eyy", "henlo", "o hey", "yo dude", "hey there", "hi there", "hi guys", "what's up", "howdy", "how's it going");
    private static final List<String> WELCOME_BACK_REPLIES = List.of(
            "wb", "wb!", "welcome back", "oh ur back", "hey ur back", "welcome back!!",
            "wb~", "there you are", "oh hey", "finally lol", "took ya a bit", "wb lol", "where were you lol", "ready to roll?", "lets continue!",
            "hey you're back", "oh wb!", "been waiting for you", "waiting on you", "ready to go?", "ready?", "back already?", "back?", "u back?");
    private static final List<String> WELCOME_BACK_OFFLINE_PARTY_TEMPLATES = List.of(
            "wb! we've been waiting at %s since u went offline",
            "yoo wb, chillin at %s for a while now",
            "back online? we parked at %s",
            "wb, took a break in %s when u dropped",
            "hey wb! waiting in %s",
            "wb!! we're at %s",
            "yo wb, headed to %s when u afk'd",
            "oh wb, been camping at %s",
            "wb~ we're in %s, come grab us",
            "hey ur back!! we're at %s");
    private static final List<String> MESO_REPLIES = List.of(
            "I have %s",
            "got %s on me",
            "im at %s rn",
            "sitting on %s");
    private static final List<String> RELOG_CONFIRM_PROMPTS = List.of(
            "relog? say yes to confirm",
            "save and relog? type yes",
            "relogging? say yes to go ahead");
    private static final List<String> LOGOUT_CONFIRM_PROMPTS = List.of(
            "log off? you sure? say yes to confirm",
            "save and log off? say yes if you're sure",
            "logging off? type yes to confirm");
    private static final List<String> RELOG_CONFIRMED_REPLIES = List.of(
            "brb!", "relogging~", "one sec, relogging");
    private static final List<String> LOGOUT_CONFIRMED_REPLIES = List.of(
            "ok! saving and logging off~", "cya!!", "ok bye!!");
    private static final String AWAY_TOWN_OR_LOGOUT_PROMPT =
            "ok, want us to wait at nearest town or logout? say yes/town or logout";
    private static final String AWAY_STAY_OR_LOGOUT_PROMPT =
            "ok, want us to stay safe here or logout? say yes/stay or logout";
    private static final String AWAY_LOGOUT_CONFIRM_REPLY = "ok, logging us out";
    private static final String AWAY_TOWN_CONFIRM_REPLY = "ok, heading to town and waiting";
    private static final String AWAY_STAY_CONFIRM_REPLY = "ok, staying safe here";
    private static final String AWAY_CANCEL_REPLY = "ok nvm, staying with you";
    private static final String SUPPORT_OFF_REPLY = "ok, skill buffs off";
    private static final String SUPPORT_ON_REPLY = "ok, skill buffs on";
    private static final String HEALS_OFF_REPLY = "ok, no heals";
    private static final String HEALS_ON_REPLY = "ok, ill heal when needed";
    private static final String BUFF_CONSUMABLES_OFF_REPLY = "ok, no buff pots";
    private static final String BUFF_CONSUMABLES_ON_REPLY_TEMPLATE = "ok, using buff pots (%s)";
    private static final String BUFF_CONSUMABLES_CHEAP_REPLY = "ok, using cheapest buff pots";
    private static final String BUFF_CONSUMABLES_MAX_REPLY = "ok, using best buff pots";
    private static final String PROACTIVE_OFFERS_OFF_REPLY = "ok, only offering immediate upgrades";
    private static final String PROACTIVE_OFFERS_ON_REPLY = "ok, proactive upgrade offers on";
    private static final String ONE_HANDED_SP_VARIANT_REPLY = "ok! going 1h sword build, Brandish first";
    private static final String TWO_HANDED_SP_VARIANT_REPLY =
            "ok! going 2h build, interleaving AC early for faster charges";
    private static final List<String> HELP_LINES = List.of(
            "commands: follow, stop, move here, fidget, grind, stats, speed, skills, inventory, mesos, exp, slots, scrolls, pots, debug stats, crit, respec, respec ap",
            "support: skill buffs on/off (= support on/off), heals on/off, buff on/off, buff cheap/max, proactive offers on/off, buff debug, skill buff debug",
            "gear: ask 'any upgrades?' or say 'trade recommended gear'",
            "supplies: need hp pot, need mp pot, need pot, need ammo",
            "trade: mesos, scrolls, pots, equips, etc, or named items");
    private static final List<String> JOB_CHANGE_REPLY_TEMPLATES = List.of(
            "ok, ill change to %s!",
            "alright becoming a %s then",
            "ok %s it is!",
            "sure, going %s",
            "ok changing to %s...");

    private AgentDialogueCatalog() {
    }

    public static List<String> followReplies() {
        return FOLLOW_REPLIES;
    }

    public static List<String> moveHereReplies() {
        return MOVE_HERE_REPLIES;
    }

    public static List<String> stopReplies() {
        return STOP_REPLIES;
    }

    public static List<String> ammoNotNeededReplies() {
        return AMMO_NOT_NEEDED_REPLIES;
    }

    public static List<String> fameOkReplies() {
        return FAME_OK_REPLIES;
    }

    public static List<String> fameCooldownReplies() {
        return FAME_COOLDOWN_REPLIES;
    }

    public static List<String> fameSamePersonReplies() {
        return FAME_SAME_PERSON_REPLIES;
    }

    public static List<String> ownerPotShortageReplies() {
        return OWNER_POT_SHORTAGE_REPLIES;
    }

    public static List<String> ownerAmmoShortageReplies() {
        return OWNER_AMMO_SHORTAGE_REPLIES;
    }

    public static List<String> tradeInviteReplies() {
        return TRADE_INVITE_REPLIES;
    }

    public static List<String> greetingReplies() {
        return GREETING_REPLIES;
    }

    public static List<String> welcomeBackReplies() {
        return WELCOME_BACK_REPLIES;
    }

    public static List<String> welcomeBackOfflinePartyTemplates() {
        return WELCOME_BACK_OFFLINE_PARTY_TEMPLATES;
    }

    public static List<String> mesoReplies() {
        return MESO_REPLIES;
    }

    public static List<String> relogConfirmPrompts() {
        return RELOG_CONFIRM_PROMPTS;
    }

    public static List<String> logoutConfirmPrompts() {
        return LOGOUT_CONFIRM_PROMPTS;
    }

    public static List<String> relogConfirmedReplies() {
        return RELOG_CONFIRMED_REPLIES;
    }

    public static List<String> logoutConfirmedReplies() {
        return LOGOUT_CONFIRMED_REPLIES;
    }

    public static String awayTownOrLogoutPrompt() {
        return AWAY_TOWN_OR_LOGOUT_PROMPT;
    }

    public static String awayStayOrLogoutPrompt() {
        return AWAY_STAY_OR_LOGOUT_PROMPT;
    }

    public static String awayLogoutConfirmReply() {
        return AWAY_LOGOUT_CONFIRM_REPLY;
    }

    public static String awayTownConfirmReply() {
        return AWAY_TOWN_CONFIRM_REPLY;
    }

    public static String awayStayConfirmReply() {
        return AWAY_STAY_CONFIRM_REPLY;
    }

    public static String awayCancelReply() {
        return AWAY_CANCEL_REPLY;
    }

    public static String supportOffReply() {
        return SUPPORT_OFF_REPLY;
    }

    public static String supportOnReply() {
        return SUPPORT_ON_REPLY;
    }

    public static String healsOffReply() {
        return HEALS_OFF_REPLY;
    }

    public static String healsOnReply() {
        return HEALS_ON_REPLY;
    }

    public static String buffConsumablesOffReply() {
        return BUFF_CONSUMABLES_OFF_REPLY;
    }

    public static String buffConsumablesOnReply(String mode) {
        return String.format(BUFF_CONSUMABLES_ON_REPLY_TEMPLATE, mode);
    }

    public static String buffConsumablesCheapReply() {
        return BUFF_CONSUMABLES_CHEAP_REPLY;
    }

    public static String buffConsumablesMaxReply() {
        return BUFF_CONSUMABLES_MAX_REPLY;
    }

    public static String proactiveOffersOffReply() {
        return PROACTIVE_OFFERS_OFF_REPLY;
    }

    public static String proactiveOffersOnReply() {
        return PROACTIVE_OFFERS_ON_REPLY;
    }

    public static String oneHandedSpVariantReply() {
        return ONE_HANDED_SP_VARIANT_REPLY;
    }

    public static String twoHandedSpVariantReply() {
        return TWO_HANDED_SP_VARIANT_REPLY;
    }

    public static List<String> helpLines() {
        return HELP_LINES;
    }

    public static List<String> jobChangeReplyTemplates() {
        return JOB_CHANGE_REPLY_TEMPLATES;
    }
}
