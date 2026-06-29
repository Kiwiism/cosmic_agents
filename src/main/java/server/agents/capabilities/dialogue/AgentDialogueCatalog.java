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
    private static final List<String> GRIND_REPLIES = List.of(
            "ok", "on it", "lets get it", "farming time", "got it",
            "sure", "ok boss", "time to grind",
            "lets farm", "hunting time", "aye, killing stuff",
            "lezgo", "gonna get some kills", "on it boss",
            "time to work", "lets do this");
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
    private static final String FAME_TARGET_NOT_FOUND_TEMPLATE = "can't find %s on the map";
    private static final String FAME_SELF_REPLY = "lol can't fame myself";
    private static final String FAME_TOO_LOW_LEVEL_REPLY = "i'm too low level to fame";
    private static final String FAME_FAILED_REPLY = "fame failed, might be at max already";
    private static final String KEEP_DROP_CHOICE_REPLY = "ok! keeping them";
    private static final String PENDING_DROP_CANCEL_REPLY = "ok! keeping them";
    private static final String PENDING_ACTION_CANCEL_REPLY = "ok nvm, staying!";
    private static final String NO_JOB_SKILLS_REPLY = "no job skills yet";
    private static final String NO_JOB_SKILLS_WITH_SP_TEMPLATE = "no job skills yet %d SP left";
    private static final String NO_BEGINNER_SKILLS_TEMPLATE =
            "no learned beginner skills yet %d beginner SP left";
    private static final String NO_LEARNED_SKILLS_IN_TEMPLATE = "no learned skills in %s";
    private static final String NO_CRIT_PASSIVE_REPLY = "i can't crit (my job doesn't have a crit passive)";
    private static final String WEIRD_TRANSFER_REPLY = "that sounded weird but ok";
    private static final String MOVEMENT_STATS_UNAVAILABLE_REPLY = "cant read my movement stats rn";
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
    private static final List<String> POT_REQUEST_HP_REPLIES = List.of(
            "anyone have HP pots? running low",
            "low on HP pots, does anyone have some?",
            "need HP pots!! anyone?",
            "HP pots? who has some",
            "anyone got HP pots to spare?");
    private static final List<String> POT_REQUEST_MP_REPLIES = List.of(
            "anyone have MP pots? running low",
            "low on MP pots, does anyone have some?",
            "need MP pots!! anyone?",
            "MP pots? who has some",
            "anyone got MP pots to spare?");
    private static final List<String> POT_OFFER_HP_REPLIES = List.of(
            "got some HP pots, inv u",
            "yep i have HP pots, inv u",
            "sure, got spare HP pots",
            "coming, inv",
            "got you");
    private static final List<String> POT_OFFER_MP_REPLIES = List.of(
            "got some MP pots, inv u",
            "yep i have MP pots, inv u",
            "sure, got spare MP pots",
            "coming, inv",
            "got you");
    private static final List<String> ARROW_REQUEST_REPLIES = List.of(
            "low on arrows, anyone have spare?",
            "need arrows soon, anyone got extras?",
            "running low on arrows, can someone share?");
    private static final List<String> BOLT_REQUEST_REPLIES = List.of(
            "low on bolts, anyone have spare?",
            "need crossbow bolts soon, anyone got extras?",
            "running low on bolts, can someone share?");
    private static final List<String> AMMO_OFFER_REPLIES = List.of(
            "i have spare ammo, inv u",
            "got some ammo for you, trading",
            "i can spare ammo, one sec");
    private static final List<String> SHOP_RESUPPLY_REPLIES = List.of(
            "brb gotta resupply", "one sec, going to restock", "be right back, need supplies",
            "brb, refilling", "be right back~");
    private static final List<String> SHOPPING_REPLIES = List.of(
            "shopping...", "restocking now", "buying stuff", "ok let me buy", "getting supplies");
    private static final List<String> COMBAT_DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");
    private static final List<String> COMBAT_AMMO_LOW_REPLIES = List.of(
            "running low on ammo",
            "ammo getting low",
            "not much ammo left",
            "gonna need more ammo soon");
    private static final List<String> COMBAT_AMMO_OUT_REPLIES = List.of(
            "out of ammo! heading back",
            "no ammo left, coming to you",
            "need ammo!! walking back",
            "im out of ammo, heading to you");
    private static final List<String> COMBAT_MP_POTS_OUT_REPLIES = List.of(
            "out of MP pots! heading back",
            "no MP pots left, coming to you",
            "need MP pots!! walking back",
            "im out of MP pots, heading to you");
    private static final List<String> TRADE_INVITE_REPLIES = List.of(
            "ok", "sure", "k", "one sec", "coming to trade", "np", "k opening trade");
    private static final List<String> TRADE_INVITATION_REPLIES = List.of(
            "k", "ok", "kk", "sure", "k, I inv", "k i inv",
            "omw", "inv u", "one sec", "coming", "1sec", "1 sec",
            "kkk", "aight", "aight inv", "alright", "alright inv",
            "pull up", "slide trade", "ill trade u", "opening trade",
            "trade time", "sending trade", "im here", "ready when u are");
    private static final List<String> TRADE_THANKS_REPLIES = List.of(
            "ty!", "thanks!", "thank you!", "tyty", "appreciate it!", "tysm!",
            "nice ty", "ooh ty!", "thx!!", "much appreciated", "thx", "wow thx", "I owe you one",
            "sweet ty", "ay ty", "perfect ty", "huge ty", "sick ty", "legend");
    private static final List<String> TRADE_FREEBIE_REPLIES = List.of(
            "i better get paid for that eventually lol", "you really should be paying me for that :P",
            "free delivery, where's my tip", "don't say i never gave you anything",
            "i'm basically your personal shopper at this point", "doing this for free smh",
            "enjoy", "hope u like it", "enjoy the loot",
            ":)", ":D", "np", "npnp", "npnpnp", "np man enjoy",
            "there u go", "have fun", "that should help",
            "use it well", "all yours", "take good care of it",
            "delivered", "hope that helps", "treat it nicely", "tell me if you find anything for me too");
    private static final List<String> TRADE_ALL_DONE_REPLIES = List.of(
            "that's all!", "done adding stuff!", "all set!", "everything's in!",
            "that's everything!", "done!", "added it all", "check it out");
    private static final List<String> TRADE_RESERVED_FOR_OTHER_REPLIES = List.of(
            "these might be needed by others, maybe don't sell them",
            "careful with these, they could be for someone else",
            "heads up, I was saving those for someone - don't lose them",
            "these might go to someone else, hold onto them for now",
            "those are kinda spoken for, keep them safe ok?",
            "just so you know, I had plans for those");
    private static final List<String> TRADE_RESERVED_FOR_SELF_REPLIES = List.of(
            "I might need those later, don't lose them ok?",
            "those could be an upgrade for me eventually, don't toss them",
            "I was thinking I'd use those someday, keep them somewhere",
            "heads up, I kinda wanted those for myself",
            "those might fit me later, maybe hold onto them",
            "just so you know, I had my eye on those");
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
    private static final List<String> DROP_OR_TRADE_PROMPTS = List.of(
            "got %s, want me to trade or drop?",
            "i have %s, trade or drop?",
            "sure, %s - trade or drop?",
            "just to confirm, trade or drop my %s?",
            "want me to trade or drop %s?");
    private static final List<String> NO_ITEMS_REPLIES = List.of(
            "i don't have any %s",
            "no %s on me rn",
            "don't have any %s right now",
            "i'm out of %s",
            "none of that on me right now",
            "fresh out of %s",
            "wish i had %s but nope",
            "checked, no %s");
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
    private static final String GEAR_OPTIMIZED_REPLY = "ok, gear optimized";
    private static final String GEAR_CHECK_UNAVAILABLE_REPLY = "can't check your gear rn";
    private static final String NO_BETTER_GEAR_REPLY = "no better gear for you rn";
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

    public static List<String> grindReplies() {
        return GRIND_REPLIES;
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

    public static String fameTargetNotFoundReply(String targetName) {
        return String.format(FAME_TARGET_NOT_FOUND_TEMPLATE, targetName);
    }

    public static String fameSelfReply() {
        return FAME_SELF_REPLY;
    }

    public static String fameTooLowLevelReply() {
        return FAME_TOO_LOW_LEVEL_REPLY;
    }

    public static String fameFailedReply() {
        return FAME_FAILED_REPLY;
    }

    public static String keepDropChoiceReply() {
        return KEEP_DROP_CHOICE_REPLY;
    }

    public static String pendingActionCancelReply(boolean dropAction) {
        return dropAction ? PENDING_DROP_CANCEL_REPLY : PENDING_ACTION_CANCEL_REPLY;
    }

    public static String noJobSkillsReply() {
        return NO_JOB_SKILLS_REPLY;
    }

    public static String noJobSkillsWithSpReply(int remainingSp) {
        return String.format(NO_JOB_SKILLS_WITH_SP_TEMPLATE, remainingSp);
    }

    public static String noBeginnerSkillsReply(int beginnerSpLeft) {
        return String.format(NO_BEGINNER_SKILLS_TEMPLATE, beginnerSpLeft);
    }

    public static String noLearnedSkillsInReply(String skillTreeLabel) {
        return String.format(NO_LEARNED_SKILLS_IN_TEMPLATE, skillTreeLabel);
    }

    public static String noCritPassiveReply() {
        return NO_CRIT_PASSIVE_REPLY;
    }

    public static String weirdTransferReply() {
        return WEIRD_TRANSFER_REPLY;
    }

    public static String movementStatsUnavailableReply() {
        return MOVEMENT_STATS_UNAVAILABLE_REPLY;
    }

    public static List<String> ownerPotShortageReplies() {
        return OWNER_POT_SHORTAGE_REPLIES;
    }

    public static List<String> ownerAmmoShortageReplies() {
        return OWNER_AMMO_SHORTAGE_REPLIES;
    }

    public static List<String> potRequestHpReplies() {
        return POT_REQUEST_HP_REPLIES;
    }

    public static List<String> potRequestMpReplies() {
        return POT_REQUEST_MP_REPLIES;
    }

    public static List<String> potOfferHpReplies() {
        return POT_OFFER_HP_REPLIES;
    }

    public static List<String> potOfferMpReplies() {
        return POT_OFFER_MP_REPLIES;
    }

    public static List<String> arrowRequestReplies() {
        return ARROW_REQUEST_REPLIES;
    }

    public static List<String> boltRequestReplies() {
        return BOLT_REQUEST_REPLIES;
    }

    public static List<String> ammoOfferReplies() {
        return AMMO_OFFER_REPLIES;
    }

    public static List<String> shopResupplyReplies() {
        return SHOP_RESUPPLY_REPLIES;
    }

    public static List<String> shoppingReplies() {
        return SHOPPING_REPLIES;
    }

    public static List<String> combatDeathReplies() {
        return COMBAT_DEATH_REPLIES;
    }

    public static List<String> combatAmmoLowReplies() {
        return COMBAT_AMMO_LOW_REPLIES;
    }

    public static List<String> combatAmmoOutReplies() {
        return COMBAT_AMMO_OUT_REPLIES;
    }

    public static List<String> combatMpPotsOutReplies() {
        return COMBAT_MP_POTS_OUT_REPLIES;
    }

    public static List<String> tradeInviteReplies() {
        return TRADE_INVITE_REPLIES;
    }

    public static List<String> tradeInvitationReplies() {
        return TRADE_INVITATION_REPLIES;
    }

    public static List<String> tradeThanksReplies() {
        return TRADE_THANKS_REPLIES;
    }

    public static List<String> tradeFreebieReplies() {
        return TRADE_FREEBIE_REPLIES;
    }

    public static List<String> tradeAllDoneReplies() {
        return TRADE_ALL_DONE_REPLIES;
    }

    public static List<String> tradeReservedForOtherReplies() {
        return TRADE_RESERVED_FOR_OTHER_REPLIES;
    }

    public static List<String> tradeReservedForSelfReplies() {
        return TRADE_RESERVED_FOR_SELF_REPLIES;
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

    public static List<String> dropOrTradePrompts() {
        return DROP_OR_TRADE_PROMPTS;
    }

    public static List<String> noItemsReplies() {
        return NO_ITEMS_REPLIES;
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

    public static String buffConsumablesModeLabel(boolean cheapMode) {
        return cheapMode ? "cheap" : "max";
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

    public static String gearOptimizedReply() {
        return GEAR_OPTIMIZED_REPLY;
    }

    public static String gearCheckUnavailableReply() {
        return GEAR_CHECK_UNAVAILABLE_REPLY;
    }

    public static String noBetterGearReply() {
        return NO_BETTER_GEAR_REPLY;
    }

    public static List<String> helpLines() {
        return HELP_LINES;
    }

    public static List<String> jobChangeReplyTemplates() {
        return JOB_CHANGE_REPLY_TEMPLATES;
    }
}
