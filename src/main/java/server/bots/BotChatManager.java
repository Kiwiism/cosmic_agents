package server.bots;

import client.Character;
import client.Job;
import client.inventory.InventoryType;
import client.inventory.Item;
import server.TimerManager;

import java.awt.*;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class BotChatManager {

    // --- helper prefix used in several info patterns ---
    // optional preamble: "what's/tell me/check … your/ur" or nothing at all
    // \\b at end ensures word boundary when no prefix is present
    private static final String INFO_PFX =
            "(?:(?:(?:what.?s?|what\\s+is|tell\\s+me|show\\s+me|check|how.?s?)\\s+)?(?:your|ur)\\s+)?\\b";

    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow(\\s+(me|here|pls|please|now))?|come(\\s+(here|to\\s+me|with\\s+me|closer|on|back))?|"
            + "get\\s+over\\s+here|f\\s+me|(pls|please)\\s+follow)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop(\\s+(moving|it|now|pls|please))?|stay(\\s+(here|there|put))?|"
            + "wait(\\s+(here|up|for\\s+me|a\\s+(sec|moment|bit)))?|"
            + "hold(\\s+(on|up|still|it))?|halt|freeze|don.?t\\s+move|stand\\s+(still|by)|"
            + "chill(\\s+here)?|idle|park(\\s+here)?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> FOLLOW_REPLIES = List.of(
            "ok", "k", "sure", "omw", "got it", "coming",
            "roger", "yep", "alright",
            "aye", "lets go!", "as you wish", "ok boss");
    private static final List<String> STOP_REPLIES = List.of(
            "ok", "k", "sure", "alright", "got it", "stopping",
            "ok ill wait here", "ill be here", "np", "standing by",
            "understood", "ok boss");

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
            INFO_PFX + "(build|ap|sp|skill(s)?)\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+build\\b"
            + "|\\bhow\\s+(did|do)\\s+(you|u)\\s+(build|assign|spend)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INVENTORY_PATTERN = Pattern.compile(
            INFO_PFX + "(inv(entory)?|bag|items?|equips?|equipment)\\b"
            + "|\\bwhat.?s\\s+in\\s+(your|ur)\\s+(inv(entory)?|bag)\\b"
            + "|\\b(show|check)\\s+(your|ur)\\s+(inv(entory)?|items?)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SCROLLS_PATTERN = Pattern.compile(
            "\\b(any|do\\s+(you|u)\\s+have(\\s+any)?|got(\\s+any)?|"
            + "carrying(\\s+any)?|you\\s+got(\\s+any)?)\\s+scrolls?\\b"
            + "|\\bhow\\s+many\\s+scrolls?\\b"
            + "|\\bscrolls?\\s+on\\s+(you|u|ya)\\b"
            + "|\\b(your|ur)\\s+scrolls?\\b"
            + "|^\\s*scrolls?\\s*\\??\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POTIONS_PATTERN = Pattern.compile(
            INFO_PFX + "(pots?|potions?|hp\\s+pots?|mp\\s+pots?|supplies)\\b"
            + "|\\b(any|do\\s+(you|u)\\s+have(\\s+any)?|got(\\s+any)?|how\\s+many)"
            +   "\\s+(pots?|potions?|hp\\s+pots?|mp\\s+pots?)\\b"
            + "|\\b(pots?|potions?)\\s+left\\b",
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
    // Drop-choice responses (matched only when pendingAction = "item_choice")
    private static final Pattern DROP_CHOICE_DROP_PATTERN = Pattern.compile(
            "\\b(drop\\s*(it|them|to\\s+ground)?|floor|ground)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_CHOICE_TRADE_PATTERN = Pattern.compile(
            "\\b(trade|trade\\s*me|send|give|transfer|give\\s*me)\\b",
            Pattern.CASE_INSENSITIVE);

    // Shared verb prefix for all drop/give/trade category commands
    private static final String DROP_VERB =
            "(?:drop|toss|give(?:\\s+(?:me|us))?|trade(?:\\s+(?:me|us))?|pass(?:\\s+me)?)";

    // Drop category commands
    private static final Pattern DROP_SCROLLS_PATTERN = Pattern.compile(
            "\\b" + DROP_VERB + "\\s+(?:(?:your|ur|my|all)\\s+)?scrolls?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_POTS_PATTERN = Pattern.compile(
            "\\b" + DROP_VERB + "\\s+(?:(?:your|ur|my|all)\\s+)?(?:pots?|potions?|supplies)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_EQUIPS_PATTERN = Pattern.compile(
            "\\b" + DROP_VERB + "\\s+(?:(?:your|ur|my|all)\\s+)?(?:equips?|equipment|gear)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_ETC_PATTERN = Pattern.compile(
            "\\b" + DROP_VERB + "\\s+(?:(?:your|ur|my|all)\\s+)?(?:etc|junk|misc(?:ellaneous)?)\\b",
            Pattern.CASE_INSENSITIVE);
    // Generic drop by item name — captured group 1 is the name; processed only if category patterns don't match
    private static final Pattern DROP_ITEM_PATTERN = Pattern.compile(
            "\\b" + DROP_VERB + "\\s+(?:(?:your|ur|my)\\s+)?([\\w][\\w '\\-]{1,39})$",
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
    private static final Pattern LOGOUT_PATTERN = Pattern.compile(
            "\\b((save\\s+and\\s+)?log\\s*(off|out)|disconnect|(pls|please)\\s+log(\\s+me)?\\s+(off|out))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELOG_PATTERN = Pattern.compile(
            "\\b(relog|save\\s+and\\s+relog|reconnect|log\\s+back\\s+in)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final List<String> GREETING_REPLIES = List.of(
            "hey", "hi", "sup", "yo", "heya", "hii", "hey!!", "hi!!");
    private static final List<String> WB_REPLIES = List.of(
            "wb", "wb!", "welcome back", "oh ur back", "hey ur back", "welcome back!!");

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
        if (entry.pendingAction != null) {
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

            if (LOGOUT_CONFIRM_PATTERN.matcher(message).find()) {
                String action = entry.pendingAction;
                entry.pendingAction = null;
                if ("relog".equals(action)) {
                    TimerManager.getInstance().schedule(() -> {
                        BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(List.of("brb!", "relogging~", "one sec, relogging")));
                        int charId      = entry.bot.getId();
                        int ownerCharId = entry.owner.getId();
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

        if (FOLLOW_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.grinding = false;
                BotEquipManager.autoEquip(entry.bot);
                BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(FOLLOW_REPLIES));
                TimerManager.getInstance().schedule(() -> entry.following = true, 250);
            }, 1500);
        } else if (GRIND_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                BotEquipManager.autoEquip(entry.bot);
                BotManager.getInstance().setupAutopotForBot(entry.bot);
                BotManager.getInstance().botSay(entry.bot, BotManager.getInstance().grindStartMessage(entry.bot));
                TimerManager.getInstance().schedule(() -> {
                    entry.grinding = true;
                    checkBotStatus(entry, entry.bot);
                }, 250);
            }, 1500);
        } else if (STOP_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
                entry.following = false;
                entry.grinding  = false;
                BotEquipManager.autoEquip(entry.bot);
                TimerManager.getInstance().schedule(() -> BotManager.getInstance().botSay(entry.bot, BotManager.randomReply(STOP_REPLIES)), 1500);
            }, 1000);
        } else if (GREETING_PATTERN.matcher(message).find()) {
            TimerManager.getInstance().schedule(() -> {
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

        // Drop commands — prompt "drop or trade?" for all categories; name-match is fallback
        {
            String dropCategory = null;
            if      (DROP_SCROLLS_PATTERN.matcher(message).find()) dropCategory = "scrolls";
            else if (DROP_POTS_PATTERN   .matcher(message).find()) dropCategory = "pots";
            else if (DROP_EQUIPS_PATTERN .matcher(message).find()) dropCategory = "equips";
            else if (DROP_ETC_PATTERN    .matcher(message).find()) dropCategory = "etc";
            else {
                Matcher dm = DROP_ITEM_PATTERN.matcher(message);
                if (dm.find()) dropCategory = "name:" + dm.group(1).trim();
            }
            if (dropCategory != null) {
                final String cat = dropCategory;
                entry.pendingAction       = "item_choice";
                entry.pendingDropCategory = cat;
                TimerManager.getInstance().schedule(
                        () -> BotManager.getInstance().botSay(entry.bot, dropOrTradePrompt(cat)), 600);
            }
        }

        // Info commands
        if (STATS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportStats(entry, entry.bot), 1000);
        if (RANGE_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportRange(entry, entry.bot), 1000);
        if (BUILD_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportBuild(entry, entry.bot), 1000);
        if (INVENTORY_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportInventory(entry, entry.bot), 1000);
        if (INV_SLOTS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportInventorySlots(entry, entry.bot), 1000);
        if (SCROLLS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportScrolls(entry, entry.bot), 1000);
        if (POTIONS_PATTERN.matcher(message).find())
            TimerManager.getInstance().schedule(() -> reportPotions(entry, entry.bot), 1000);

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
                TimerManager.getInstance().schedule(() -> entry.bot.changeJob(advJob), 1000);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Message queue — 5-second spacing between consecutive bot messages
    // -------------------------------------------------------------------------

    static void queueBotSay(BotEntry entry, String message) {
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
        if (spPrompt != null) queueBotSay(entry, spPrompt);
        String apPrompt = BotBuildManager.buildApPrompt(entry, bot);
        if (apPrompt != null) queueBotSay(entry, apPrompt);
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
                        () -> BotManager.getInstance().botSay(bot, BotManager.randomReply(WB_REPLIES)), 2000);
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

    private static void reportInventory(BotEntry entry, Character bot) {
        queueBotSay(entry, BotDropManager.inventorySummary(bot));
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
        int hp = 0, mp = 0;
        for (Item item : bot.getInventory(InventoryType.USE).list()) {
            int id = item.getItemId();
            int qty = item.getQuantity();
            if (id >= 2000000 && id < 2001000) hp += qty;      // HP potions
            else if (id >= 2001000 && id < 2002000) mp += qty; // MP potions
        }
        String msg;
        if (hp == 0 && mp == 0) {
            msg = "no pots on me rn";
        } else if (mp == 0) {
            msg = "I have " + hp + " hp pot" + (hp != 1 ? "s" : "") + ", no mp pots";
        } else if (hp == 0) {
            msg = "no hp pots, " + mp + " mp pot" + (mp != 1 ? "s" : "");
        } else {
            msg = "I have " + hp + " hp pot" + (hp != 1 ? "s" : "")
                + " and " + mp + " mp pot" + (mp != 1 ? "s" : "");
        }
        queueBotSay(entry, msg);
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
            case CORSAIR     -> "corsair";
            default -> job.name().toLowerCase();
        };
    }
}
