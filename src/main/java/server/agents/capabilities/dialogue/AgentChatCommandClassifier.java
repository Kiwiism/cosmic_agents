package server.agents.capabilities.dialogue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AgentChatCommandClassifier {
    private static final String INFO_PFX =
            "(?:(?:(?:what.?s?|what\\s+is|tell\\s+me|show\\s+me|check|how.?s?)\\s+)?(?:your|ur)\\s+)?\\b";
    private static final Pattern FOLLOW_PATTERN = Pattern.compile(
            "\\b(follow(\\s+(me|here|pls|please|now))?|come(\\s+(here|to\\s+me|with\\s+me|closer|on|back))?|"
            + "get\\s+over\\s+here|f\\s+me|(pls|please)\\s+follow)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FOLLOW_TARGET_PATTERN = Pattern.compile(
            "^\\s*follow\\s+(\\S+?)(?:\\s+(?:pls|please|now))?\\s*[?!.,]*\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE_HERE_PATTERN = Pattern.compile(
            "(?:move\\s+(?:here|there)|go\\s+(?:here|there)|here|move)(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FARM_HERE_PATTERN = Pattern.compile(
            "(?:(?:farm|grind|hunt|train)\\s+here"
            + "|(?:go\\s+)?(?:sentry|camp|guard|defend|post\\s+up|anchor)(?:\\s+(?:here|mode))?)"
            + "(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATROL_PATTERN = Pattern.compile(
            "(?:patrol|roam|wander)(?:\\s+(?:here|the\\s+area|around))?(?:\\s+(?:now|pls|please))?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern STOP_PATTERN = Pattern.compile(
            "\\b(stop(\\s+(moving|it|now|pls|please))?|stay(\\s+(here|there|put))?|"
            + "wait(\\s+(here|up|for\\s+me|a\\s+(sec|moment|bit)))?|"
            + "hold(\\s+(on|up|still|it))?|halt|freeze|don.?t\\s+move|stand\\s+(still|by)|"
            + "chill(\\s+here)?|idle|park(\\s+here)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GRIND_PATTERN = Pattern.compile(
            "\\b(go\\s+|start\\s+|begin\\s+|let.?s\\s+)?(farm(ing)?|grind(ing)?|hunt(ing)?|train(ing)?)\\b"
            + "|\\b(kill|fight)\\s+(mobs?|monsters?|stuff)\\b"
            + "|\\btime\\s+to\\s+(farm|grind|hunt)\\b"
            + "|\\bgo\\s+get\\s+(exp|xp)\\b"
            + "|\\b(auto|attack)\\s*(on|mode)?\\b"
            + "|\\bstart\\s+(killing|attacking)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FIDGET_PATTERN = Pattern.compile(
            "^\\s*fidget\\s*[?!.,]*\\s*$",
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
    private static final Pattern MOVEMENT_STATS_PATTERN = Pattern.compile(
            INFO_PFX + "(?:move\\s*speed|movespeed|speed|jump|movement|mobility)(?:\\s+stats?)?\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+(?:move\\s*speed|movespeed|speed|jump)\\b"
            + "|\\bhow\\s+fast\\s+(are|r)\\s+(you|u)\\b",
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
    private static final Pattern CRIT_DEBUG_PATTERN = Pattern.compile(
            "\\bcrit\\s*(debug|stats?|rate|chance|info)?\\s*\\??\\s*$"
            + "|\\bdo\\s+you\\s+(crit|get\\s+crits?)\\b"
            + "|\\bwhat.?s\\s+(your|ur)\\s+crit\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern POT_DEBUG_PATTERN = Pattern.compile(
            "\\b(pot|potion|autopot)\\s*(debug|info|select(ion)?|status)\\b"
            + "|\\bdebug\\s+(pot|potion|autopot)s?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HELP_PATTERN = Pattern.compile(
            "\\b(help|commands?|what\\s+can\\s+you\\s+do|how\\s+do\\s+i\\s+use\\s+you)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RECOMMENDED_GEAR_PATTERN = Pattern.compile(
            "\\b(any\\s+upgrades?|better\\s+gear|recommended\\s+gear|gear\\s+recommendations?|"
            + "any\\s+(better|recommended)\\s+(gear|equips?|equipment))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final String POTION_WORDS = "(?:pots?|potions?|hp\\s+pots?|mp\\s+pots?|supplies)";
    private static final String SCROLL_WORDS = "scrolls?";
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
    private static final Pattern EXP_PATTERN = Pattern.compile(
            "^\\s*(?:exp|xp|experience)\\s*[?!.,]*\\s*$"
            + "|\\bhow\\s+much\\s+(?:exp|xp|experience)(?:\\s+do\\s+(?:you|u)\\s+have)?\\b"
            + "|\\bwhat.?s\\s+(?:your|ur)\\s+(?:exp|xp|experience)\\b"
            + "|\\b(?:your|ur)\\s+(?:exp|xp|experience)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INV_SLOTS_PATTERN = Pattern.compile(
            "\\bslots?\\s*(?:left|free|remaining)?\\b"
            + "|\\binv(?:entory)?\\s+(?:full|space|slots?)\\b"
            + "|\\bhow\\s+full\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MESOS_PATTERN = Pattern.compile(
            "^\\s*(?:meso|mesos|cash)\\s*[?!.,]*\\s*$"
            + "|\\bhow\\s+much\\s+(?:meso|mesos|cash)(?:\\s+do\\s+(?:you|u)\\s+have)?\\b"
            + "|\\bwhat.?s\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\bshow\\s+me\\s+(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:your|ur)\\s+(?:meso|mesos|cash)\\b"
            + "|\\b(?:meso|mesos|cash)\\s+(?:left|on\\s+(?:you|u|ya))\\b"
            + "|\\b(?:do\\s+(you|u)\\s+have|got(?:\\s+any)?|(you|u)\\s+got)\\s+(?:any\\s+)?(?:meso|mesos|cash)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROACTIVE_OFFERS_ON_PATTERN = Pattern.compile(
            "\\b(?:(?:proactive|future)\\s+(?:offers?|upgrades?)\\s+on|offers?\\s+(?:proactive|future)\\s+on)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PROACTIVE_OFFERS_OFF_PATTERN = Pattern.compile(
            "\\b(?:(?:proactive|future)\\s+(?:offers?|upgrades?)\\s+off|offers?\\s+(?:proactive|future)\\s+off)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern REQUEST_UPGRADE_PATTERN = Pattern.compile(
            "\\brequest\\s*\\?|\\bneed\\s+anything\\b|\\bdo\\s+you\\s+need\\s+(anything|something)\\b"
            + "|\\bdo\\s+you\\s+need\\s+(?:any\\s+)?(?:gear|equips?|equipment)\\s*(?:from\\s+me)?\\b"
            + "|\\bneed\\s+(?:any\\s+)?(?:gear|equips?|equipment)\\s+from\\s+me\\b"
            + "|\\bwhat\\s+do\\s+you\\s+need\\b|\\bwhat.?s\\s+(on\\s+your\\s+)?wish\\s*list\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESPEC_PATTERN = Pattern.compile(
            "\\b(respec|reset\\s+(skills?|sp)|rebuild\\s+(skills?|sp)|fix\\s+(skills?|sp|build))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AP_RESPEC_PATTERN = Pattern.compile(
            "\\b(respec\\s+ap|reset\\s+ap|rebuild\\s+ap|fix\\s+ap(?:\\s+build)?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_LIST_PATTERN = Pattern.compile(
            "\\bbuff\\s+(pots?\\s+)?list\\b|\\bbuffs?\\s*\\?|\\bwhat\\s+buffs?\\b|\\bwhich\\s+buffs?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_DEBUG_PATTERN = Pattern.compile(
            "\\bbuffs?\\s*(?:debug|\\?)?\\b|\\bdebug\\s+buffs?\\b|\\bactive\\s+buffs?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SKILL_BUFF_DEBUG_PATTERN = Pattern.compile(
            "\\bskill\\s+buffs?\\s*(?:debug|\\?)?\\b|\\bdebug\\s+skill\\s+buffs?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_ON_PATTERN = Pattern.compile(
            "\\b(support\\s+(me|us|party)|support\\s+on|auto\\s+support|skill\\s+buffs?\\s+on)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_OFF_PATTERN = Pattern.compile(
            "\\b(support\\s+off|stop\\s+support(ing)?|no\\s+support|skill\\s+buffs?\\s+off|no\\s+skill\\s+buffs?|stop\\s+(skill\\s+)?buffing)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALS_ON_PATTERN = Pattern.compile(
            "\\b(heals?\\s+(me|us|party)|heals?\\s+on|auto\\s+heals?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEALS_OFF_PATTERN = Pattern.compile(
            "\\b(heals?\\s+off|stop\\s+heal(ing)?|no\\s+heals?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_ON_PATTERN = Pattern.compile(
            "\\bbuff\\s+(pots?\\s+)?on\\b|\\bauto\\s+buff\\s+pots?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_OFF_PATTERN = Pattern.compile(
            "\\bbuff\\s+(pots?\\s+)?off\\b|\\bno\\s+buff\\s+pots?\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_CHEAP_PATTERN = Pattern.compile(
            "\\bbuff\\s+(pots?\\s+)?cheap\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUFF_MAX_PATTERN = Pattern.compile(
            "\\bbuff\\s+(pots?\\s+)?(max|best|good)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_HP_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:hp|health)\\s+(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:hp|health)\\s+(?:pots?|potions?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_MP_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:mp|mana)\\s+(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:mp|mana)\\s+(?:pots?|potions?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_POT_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:pots?|potions?|supplies)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:pots?|potions?|supplies)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEED_AMMO_PATTERN = Pattern.compile(
            "\\b(?:need|nned|low\\s+on|out\\s+of|running\\s+low\\s+on)\\s+(?:some\\s+)?(?:ammo|arrows?|bolts?)\\b"
            + "|\\b(?:any(?:body|one)?|someone|somebody|u|you)\\s+(?:got|have|has)\\s+(?:any\\s+|some\\s+)?(?:ammo|arrows?|bolts?)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_PATTERN = Pattern.compile(
            "(?:(?:i\\s+)?(?:(?:have|got|need)\\s+to|gotta)\\s+)?"
            + "(?:(?:save\\s+and\\s+)?log\\s*(?:off|out)|disconnect|log\\s+me\\s+(?:off|out))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RELOG_PATTERN = Pattern.compile(
            "(?:(?:i\\s+)?(?:(?:have|got|need)\\s+to|gotta)\\s+)?"
            + "(?:relog|save\\s+and\\s+relog|reconnect|log\\s+back\\s+in)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY_PATTERN = Pattern.compile(
            "(?:(?:gtg|g2g)"
            + "|(?:i\\s+)?(?:(?:have|got|need)\\s+to|gotta)\\s+go"
            + "|(?:i\\s+)?(?:(?:have|got|gotta|need)\\s+to\\s+)?(?:leave|bounce)"
            + "|(?:(?:i\\s+am|i['’]?m|im)\\s+)?(?:brb|afk)"
            + "|(?:be\\s+right\\s+back|back\\s+in\\s+(?:a\\s+)?(?:bit|sec|minute|min))"
            + "|(?:(?:i\\s+am|i['’]?m|im)\\s+)?(?:off|logging\\s+out\\s+soon)"
            + "|(?:i\\s+)?(?:have|got|gotta)\\s+to\\s+(?:head\\s+out|run))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOGOUT_CONFIRM_PATTERN = Pattern.compile(
            "\\b(yes|yep|yeah|yea|y|ok|sure|confirm|do\\s+it|go\\s+(ahead|for\\s+it))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY_TOWN_CONFIRM_PATTERN = Pattern.compile(
            "^(?:yes|yep|yeah|yea|y|ok|sure|confirm|town|nearest\\s+town|go\\s+town|go\\s+to\\s+town)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY_STAY_CONFIRM_PATTERN = Pattern.compile(
            "^(?:stay|stay\\s+here|here|idle|wait\\s+here)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AWAY_LOGOUT_CONFIRM_PATTERN = Pattern.compile(
            "^(?:logout|log\\s*out|log\\s*off|disconnect|save\\s+and\\s+log\\s*(?:out|off))$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_CONFIRM_PATTERN = Pattern.compile(
            "\\b(no|nope|nah|nvm|never\\s*mind|dont|don't|not\\s+now|skip)\\b",
            Pattern.CASE_INSENSITIVE);

    private AgentChatCommandClassifier() {
    }

    public static String matchFollowTarget(String message) {
        Matcher matcher = FOLLOW_TARGET_PATTERN.matcher(message);
        if (!matcher.find()) {
            return null;
        }

        String target = matcher.group(1);
        if (target == null) {
            return null;
        }

        String normalized = target.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "me", "here", "pls", "please", "now" -> null;
            default -> target.trim();
        };
    }

    public static boolean isFarmHereCommand(String message) {
        return matchesWholeCommand(FARM_HERE_PATTERN, message);
    }

    public static boolean isPatrolCommand(String message) {
        return matchesWholeCommand(PATROL_PATTERN, message);
    }

    public static boolean isMoveHereCommand(String message) {
        return matchesWholeCommand(MOVE_HERE_PATTERN, message);
    }

    public static boolean isFollowCommand(String message) {
        return matchesWholeCommand(FOLLOW_PATTERN, message);
    }

    public static boolean isGrindCommand(String message) {
        return matchesWholeCommand(GRIND_PATTERN, message);
    }

    public static boolean isStopCommand(String message) {
        return matchesWholeCommand(STOP_PATTERN, message);
    }

    public static boolean isFidgetCommand(String message) {
        return message != null && FIDGET_PATTERN.matcher(message).find();
    }

    public static boolean isMesoQuery(String message) {
        return matchesWholeCommand(MESOS_PATTERN, message);
    }

    public static boolean isHelpCommand(String message) {
        return matchesWholeCommand(HELP_PATTERN, message);
    }

    public static boolean isRecommendedGearQuery(String message) {
        return matchesWholeCommand(RECOMMENDED_GEAR_PATTERN, message);
    }

    public static boolean isSkillsQuery(String message) {
        return matchesWholeCommand(SKILLS_PATTERN, message);
    }

    public static boolean isStatsQuery(String message) {
        return matchesWholeCommand(STATS_PATTERN, message);
    }

    public static boolean isRangeQuery(String message) {
        return matchesWholeCommand(RANGE_PATTERN, message);
    }

    public static boolean isMovementStatsQuery(String message) {
        return matchesWholeCommand(MOVEMENT_STATS_PATTERN, message);
    }

    public static boolean isBuildQuery(String message) {
        return matchesWholeCommand(BUILD_PATTERN, message);
    }

    public static boolean isInventoryQuery(String message) {
        return matchesWholeCommand(INVENTORY_PATTERN, message);
    }

    public static boolean isExpQuery(String message) {
        return matchesWholeCommand(EXP_PATTERN, message);
    }

    public static boolean isInventorySlotsQuery(String message) {
        return matchesWholeCommand(INV_SLOTS_PATTERN, message);
    }

    public static boolean isScrollsQuery(String message) {
        return matchesWholeCommand(SCROLLS_PATTERN, message);
    }

    public static boolean isPotionsQuery(String message) {
        return matchesWholeCommand(POTIONS_PATTERN, message);
    }

    public static boolean isDebugStatsQuery(String message) {
        return matchesWholeCommand(DEBUG_STATS_PATTERN, message);
    }

    public static boolean isCritDebugQuery(String message) {
        return matchesWholeCommand(CRIT_DEBUG_PATTERN, message);
    }

    public static boolean isPotDebugQuery(String message) {
        return matchesWholeCommand(POT_DEBUG_PATTERN, message);
    }

    public static boolean isBuffListQuery(String message) {
        return matchesWholeCommand(BUFF_LIST_PATTERN, message);
    }

    public static boolean isBuffDebugQuery(String message) {
        return matchesWholeCommand(BUFF_DEBUG_PATTERN, message);
    }

    public static boolean isSkillBuffDebugQuery(String message) {
        return matchesWholeCommand(SKILL_BUFF_DEBUG_PATTERN, message);
    }

    public static boolean isSupportOnCommand(String message) {
        return SUPPORT_ON_PATTERN.matcher(message).find();
    }

    public static boolean isSupportOffCommand(String message) {
        return SUPPORT_OFF_PATTERN.matcher(message).find();
    }

    public static boolean isHealsOnCommand(String message) {
        return HEALS_ON_PATTERN.matcher(message).find();
    }

    public static boolean isHealsOffCommand(String message) {
        return HEALS_OFF_PATTERN.matcher(message).find();
    }

    public static boolean isBuffConsumablesOnCommand(String message) {
        return BUFF_ON_PATTERN.matcher(message).find();
    }

    public static boolean isBuffConsumablesOffCommand(String message) {
        return BUFF_OFF_PATTERN.matcher(message).find();
    }

    public static boolean isBuffConsumablesCheapCommand(String message) {
        return BUFF_CHEAP_PATTERN.matcher(message).find();
    }

    public static boolean isBuffConsumablesMaxCommand(String message) {
        return BUFF_MAX_PATTERN.matcher(message).find();
    }

    public static boolean isProactiveOffersOnCommand(String message) {
        return PROACTIVE_OFFERS_ON_PATTERN.matcher(message).find();
    }

    public static boolean isProactiveOffersOffCommand(String message) {
        return PROACTIVE_OFFERS_OFF_PATTERN.matcher(message).find();
    }

    public static boolean isRequestUpgradeCommand(String message) {
        return matchesWholeCommand(REQUEST_UPGRADE_PATTERN, message);
    }

    public static boolean isRespecCommand(String message) {
        return RESPEC_PATTERN.matcher(message).find();
    }

    public static boolean isApRespecCommand(String message) {
        return AP_RESPEC_PATTERN.matcher(message).find();
    }

    public static boolean isNeedHpPotCommand(String message) {
        return NEED_HP_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedMpPotCommand(String message) {
        return NEED_MP_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedPotCommand(String message) {
        return NEED_POT_PATTERN.matcher(message).find();
    }

    public static boolean isNeedAmmoCommand(String message) {
        return NEED_AMMO_PATTERN.matcher(message).find();
    }

    public static boolean isGroupSupplyRequest(String message) {
        return isNeedHpPotCommand(message)
                || isNeedMpPotCommand(message)
                || isNeedPotCommand(message)
                || isNeedAmmoCommand(message);
    }

    public static boolean isRelogRequest(String message) {
        return matchesWholeCommand(RELOG_PATTERN, message);
    }

    public static boolean isLogoutRequest(String message) {
        return matchesWholeCommand(LOGOUT_PATTERN, message);
    }

    public static boolean isAwayRequest(String message) {
        return matchesWholeCommand(AWAY_PATTERN, message);
    }

    public static boolean isLogoutConfirm(String message) {
        return LOGOUT_CONFIRM_PATTERN.matcher(message).find();
    }

    public static boolean isAwayTownConfirm(String message) {
        return AWAY_TOWN_CONFIRM_PATTERN.matcher(message).matches();
    }

    public static boolean isAwayStayConfirm(String message) {
        return AWAY_STAY_CONFIRM_PATTERN.matcher(message).matches();
    }

    public static boolean isAwayLogoutConfirm(String message) {
        return AWAY_LOGOUT_CONFIRM_PATTERN.matcher(message).matches();
    }

    public static boolean isNegativeConfirm(String message) {
        return NEGATIVE_CONFIRM_PATTERN.matcher(message).find();
    }

    private static boolean matchesWholeCommand(Pattern pattern, String message) {
        String normalized = normalizeCommandText(message);
        return !normalized.isEmpty() && pattern.matcher(normalized).matches();
    }

    public static String normalizeCommandText(String message) {
        if (message == null) {
            return "";
        }

        return message.strip()
                .replaceAll("^[\\p{Punct}\\s]+", "")
                .replaceAll("[\\p{Punct}\\s]+$", "")
                .replaceFirst("^(?:(?:please|pls|hey|yo)\\s+)+", "")
                .replaceFirst("^(?:(?:can|could|will|would)\\s+you\\s+)", "")
                .replaceFirst("^(?:(?:please|pls)\\s+)+", "")
                .replaceFirst("\\s+(?:please|pls)$", "")
                .replaceAll("\\s+", " ");
    }
}
