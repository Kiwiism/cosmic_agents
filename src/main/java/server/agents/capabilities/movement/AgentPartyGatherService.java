package server.agents.capabilities.movement;

import client.Character;
import client.Skill;
import server.StatEffect;
import server.agents.capabilities.combat.AgentCombatBuffStateRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;
import server.agents.capabilities.combat.AgentCombatSupportPolicy;
import server.agents.capabilities.dialogue.AgentDialogueCatalog;
import server.agents.integration.AgentDialogueTransportRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.AgentSkillGatewayRuntime;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentPartyGatherRegistry;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.AgentSchedulerRuntime;
import server.maps.Foothold;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

/**
 * Brings the party together so buffs reach everyone. A party buff lands only on members inside its
 * box around the caster, so a buffer rebuffing a spread-out party leaves people out. Two ways in:
 *
 * <ul>
 *   <li>"gather up" / "everyone come here" (owner, untargeted): the owner's Agents on the map walk
 *       to spots around the owner and stay there. With "for buffs" the buffers also rebuff once
 *       everyone has arrived.</li>
 *   <li>"buff me again" to a buffer while the party is spread: the owner's Agents walk over, the
 *       buffers cast once the whole party stands in their boxes (or the wait runs out), and every
 *       Agent the gathering moved goes back to what it was doing.</li>
 * </ul>
 *
 * Spots spread out from the owner (buffers innermost) so nobody stands on anyone, and stay on the
 * owner's platform.
 */
public final class AgentPartyGatherService {
    private static final int SLOT_SPACING_PX = config.AgentTuning.intValue(
            "server.agents.capabilities.movement.AgentPartyGatherService.SLOT_SPACING_PX");
    private static final long WAIT_FOR_PARTY_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentPartyGatherService.WAIT_FOR_PARTY_MS");
    private static final long CLOSE_AFTER_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentPartyGatherService.CLOSE_AFTER_MS");
    private static final long POLL_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentPartyGatherService.POLL_MS");
    private static final long ASSEMBLE_DEDUPE_MS = config.AgentTuning.longValue(
            "server.agents.capabilities.movement.AgentPartyGatherService.ASSEMBLE_DEDUPE_MS");
    /** When each owner last called the party together; several Agents resolving one message act once. */
    private static final Map<Integer, Long> LAST_ASSEMBLE_MS = new ConcurrentHashMap<>();

    private static final String GROUP_WORD = "(?:every(?:one|body)|all(?:\\s+of\\s+(?:you|u))?|guys|team|party|y'?all)";
    private static final String TAIL = "(?:\\s*,?\\s*" + GROUP_WORD + ")?(?:\\s+(?:for\\s+buffs?|pls|please|plz|now|quick))*";
    private static final Pattern GATHER = Pattern.compile(
            "^(?:(?:ok|okay|alright|hey|yo)\\s*,?\\s+)?(?:" + GROUP_WORD + "\\s*,?\\s+)?"
                    + "(?:gather|huddle|regroup|assemble|rally|group\\s+up)"
                    + "(?:\\s+(?:up|here|together|around|round|on\\s+me|over\\s+here|to\\s+me))*" + TAIL + "$");
    private static final Pattern COME_HERE = Pattern.compile(
            "^(?:(?:ok|okay|alright|hey|yo)\\s*,?\\s+)?(?:" + GROUP_WORD + "\\s*,?\\s+)?"
                    + "(?:(?:come|get)\\s+(?:over\\s+)?here|(?:on|to)\\s+me)" + TAIL + "$");
    private static final Pattern NAMES_THE_GROUP = Pattern.compile("\\b" + GROUP_WORD + "\\b");
    private static final Pattern MENTIONS_BUFFS = Pattern.compile("\\bbuffs?\\b");


    private AgentPartyGatherService() {
    }

    /**
     * "gather up", "everyone come here", "group up for buffs"... A bare "come here" stays a follow
     * command; the group word is what makes it the whole party.
     */
    public static boolean matchesAssemble(String message) {
        if (message == null) {
            return false;
        }
        String text = normalize(message);
        return GATHER.matcher(text).matches()
                || (COME_HERE.matcher(text).matches() && NAMES_THE_GROUP.matcher(text).find());
    }

    /** "...for buffs": the buffers rebuff once the party has gathered. */
    public static boolean mentionsBuffs(String message) {
        return message != null && MENTIONS_BUFFS.matcher(normalize(message)).find();
    }

    /** Owner-level chat route for {@link #matchesAssemble}; false when no Agent of the owner is on its map. */
    public static boolean assembleOnCommand(Character owner, String message,
                                            List<? extends AgentRuntimeEntry> ownersAgents) {
        if (owner == null || !matchesAssemble(message)) {
            return false;
        }
        List<AgentRuntimeEntry> here = onOwnersMap(owner, ownersAgents);
        if (here.isEmpty()) {
            return false;
        }
        // Untargeted chat reaches every Agent, and each may map the message to this command (the
        // intent judge runs per Agent): the first one gathers the party, the rest are already handled.
        if (!claimAssemble(owner.getId(), System.currentTimeMillis(), ASSEMBLE_DEDUPE_MS)) {
            return true;
        }
        boolean forBuffs = mentionsBuffs(message);
        AgentPartyGatherRegistry.Gathering gathering = forBuffs ? open(owner) : null;
        moveToSpots(owner, here, gathering, false);
        if (gathering != null) {
            gathering.markGathered();
            for (AgentRuntimeEntry entry : here) {
                if (isBuffer(entry)) {
                    AgentMailboxRuntime.dispatch(entry, ignored -> {
                        AgentCombatBuffStateRuntime.requestRebuff(entry);
                        return null;
                    });
                    gathering.addBuffer(AgentRuntimeIdentityRuntime.botId(entry));
                }
            }
            watch(here.get(0), gathering);
        }
        reply(here.get(0), forBuffs ? AgentDialogueCatalog.callForBuffsReplies() : AgentDialogueCatalog.assembleReplies());
        return true;
    }

    /**
     * "buff me again" reached {@code buffer}: if it has party buffs and someone in the party stands
     * outside their boxes, open a gathering and call the owner's Agents over. Its buffs are already
     * due ({@link AgentCombatBuffStateRuntime#requestRebuff}); the gathering makes it wait for the
     * party and lets it buff while it stands still.
     *
     * @return true when the party was called over, so the buffer says so instead of the usual reply
     */
    public static boolean onRebuffRequested(AgentRuntimeEntry buffer) {
        Character owner = buffer == null ? null : buffer.owner();
        Character bot = buffer == null ? null : AgentRuntimeIdentityRuntime.bot(buffer);
        if (owner == null || bot == null || !isBuffer(buffer) || owner.getMapId() != bot.getMapId()) {
            return false;
        }
        AgentPartyGatherRegistry.Gathering gathering = open(owner);
        gathering.addBuffer(bot.getId());
        watch(buffer, gathering);
        if (gathering.gathered() || !partySpread(buffer, bot)) {
            return false;
        }
        gathering.markGathered();
        moveToSpots(owner, onOwnersMap(owner, AgentRuntimeRegistry.entriesForLeader(owner.getId())), gathering, true);
        return true;
    }

    /** True when no assemble for this owner ran in the last {@code windowMs}; records this one. */
    static boolean claimAssemble(int ownerId, long nowMs, long windowMs) {
        boolean[] claimed = {false};
        LAST_ASSEMBLE_MS.compute(ownerId, (id, last) -> {
            if (last != null && nowMs - last < windowMs) {
                return last;
            }
            claimed[0] = true;
            return nowMs;
        });
        return claimed[0];
    }

    /** Spot offsets from the owner: -s, +s, -2s, +2s, ... (the owner keeps the middle). */
    static List<Integer> spotOffsets(int count, int spacingPx) {
        List<Integer> offsets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int ring = i / 2 + 1;
            offsets.add((i % 2 == 0 ? -1 : 1) * ring * spacingPx);
        }
        return offsets;
    }

    /**
     * The spot {@code dx} from the anchor, pulled toward the anchor until it stands on ground level
     * with the anchor (within half a spot), so nobody is sent off the edge or onto another platform.
     */
    static Point groundedSpot(Point anchor, int dx, int spacingPx, IntFunction<Integer> groundYAt) {
        int half = Math.max(1, spacingPx / 2);
        for (int offset = dx; Math.abs(offset) >= half; offset -= Integer.signum(dx) * half) {
            Integer groundY = groundYAt.apply(anchor.x + offset);
            if (groundY != null && Math.abs(groundY - anchor.y) <= half) {
                return new Point(anchor.x + offset, groundY);
            }
        }
        return new Point(anchor);
    }

    private static void moveToSpots(Character owner, List<AgentRuntimeEntry> agents,
                                    AgentPartyGatherRegistry.Gathering gathering, boolean restoreAfter) {
        Point anchor = new Point(owner.getPosition());
        MapleMap map = owner.getMap();
        int tolerance = Math.max(1, SLOT_SPACING_PX / 2);
        IntFunction<Integer> groundYAt = x -> groundY(map, x, anchor.y - tolerance);
        List<AgentRuntimeEntry> ordered = new ArrayList<>(agents);
        // Buffers take the inner spots, so the outermost Agent stays inside their boxes.
        ordered.sort(Comparator.comparing((AgentRuntimeEntry entry) -> !isBuffer(entry))
                .thenComparingInt(AgentRuntimeIdentityRuntime::botId));
        List<Integer> offsets = spotOffsets(ordered.size(), SLOT_SPACING_PX);
        for (int i = 0; i < ordered.size(); i++) {
            AgentRuntimeEntry entry = ordered.get(i);
            Point spot = groundedSpot(anchor, offsets.get(i), SLOT_SPACING_PX, groundYAt);
            if (gathering != null) {
                gathering.rememberSpot(AgentRuntimeIdentityRuntime.botId(entry), spot, SLOT_SPACING_PX);
            }
            AgentMailboxRuntime.dispatch(entry, ignored -> {
                if (gathering != null && restoreAfter) {
                    Runnable restore = restoreAction(entry);
                    if (restore != null) {
                        gathering.rememberRestore(AgentRuntimeIdentityRuntime.botId(entry), restore);
                    }
                }
                AgentMovementCommandRuntime.moveTo(entry, spot, true);
                return null;
            });
        }
    }

    /** How to resume the Agent's current activity; null when it was standing still. */
    private static Runnable restoreAction(AgentRuntimeEntry entry) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        if (bot == null) {
            return null;
        }
        if (AgentModeStateRuntime.following(entry)) {
            return () -> AgentMovementCommandRuntime.followConfiguredTarget(entry);
        }
        if (!AgentModeStateRuntime.grinding(entry)) {
            return null;
        }
        Point farmAnchor = AgentFarmAnchorStateRuntime.farmAnchorInMap(entry, bot.getMapId());
        if (farmAnchor != null) {
            Point anchor = new Point(farmAnchor);
            return () -> AgentMovementCommandRuntime.farmHere(entry, anchor);
        }
        if (AgentPatrolStateRuntime.hasPatrolRegion(entry)) {
            Point inRegion = new Point(bot.getPosition());
            return () -> AgentMovementCommandRuntime.patrol(entry, inRegion);
        }
        return () -> AgentMovementCommandRuntime.grind(entry);
    }

    /** Polls until every buffer has finished (or the gathering expires), then sends the moved Agents back. */
    private static void watch(AgentRuntimeEntry host, AgentPartyGatherRegistry.Gathering gathering) {
        if (gathering.startWatching()) {
            poll(host, gathering);
        }
    }

    private static void poll(AgentRuntimeEntry host, AgentPartyGatherRegistry.Gathering gathering) {
        AgentSchedulerRuntime.afterDelay(host, POLL_MS, () -> {
            long now = System.currentTimeMillis();
            if (!gathering.buffersFinished() && !gathering.expired(now)) {
                poll(host, gathering);
                return;
            }
            for (Runnable restore : AgentPartyGatherRegistry.close(gathering)) {
                restore.run();
            }
        });
    }

    private static AgentPartyGatherRegistry.Gathering open(Character owner) {
        return AgentPartyGatherRegistry.open(owner.getId(), owner.getMapId(), System.currentTimeMillis(),
                WAIT_FOR_PARTY_MS, CLOSE_AFTER_MS);
    }

    /** Whether any of the buffer's party buffs would miss a party member from where it stands. */
    private static boolean partySpread(AgentRuntimeEntry buffer, Character bot) {
        for (StatEffect effect : partyBuffs(buffer, bot)) {
            if (!AgentCombatSupportPolicy.wholePartyInBox(bot, effect)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBuffer(AgentRuntimeEntry entry) {
        Character bot = AgentRuntimeIdentityRuntime.bot(entry);
        return bot != null && !partyBuffs(entry, bot).isEmpty();
    }

    private static List<StatEffect> partyBuffs(AgentRuntimeEntry entry, Character bot) {
        List<StatEffect> effects = new ArrayList<>();
        for (int skillId : AgentCombatSkillCacheStateRuntime.buffSkillIds(entry)) {
            Skill skill = AgentSkillGatewayRuntime.skills().getSkill(skillId);
            int level = skill == null ? 0 : bot.getSkillLevel(skill);
            StatEffect effect = level > 0 ? skill.getEffect(level) : null;
            if (AgentCombatSupportPolicy.isPartyBuff(skillId, effect)) {
                effects.add(effect);
            }
        }
        return effects;
    }

    private static List<AgentRuntimeEntry> onOwnersMap(Character owner, List<? extends AgentRuntimeEntry> agents) {
        List<AgentRuntimeEntry> here = new ArrayList<>();
        if (agents == null) {
            return here;
        }
        for (AgentRuntimeEntry entry : agents) {
            Character bot = AgentRuntimeIdentityRuntime.bot(entry);
            if (bot != null && bot.isAlive() && bot.getMapId() == owner.getMapId()) {
                here.add(entry);
            }
        }
        return here;
    }

    private static Integer groundY(MapleMap map, int x, int fromY) {
        if (map == null || map.getFootholds() == null) {
            return null;
        }
        Foothold foothold = map.getFootholds().findBelow(new Point(x, fromY));
        if (foothold == null || foothold.isWall()) {
            return null;
        }
        if (foothold.getX1() == foothold.getX2()) {
            return foothold.getY1();
        }
        return foothold.getY1() + (foothold.getY2() - foothold.getY1()) * (x - foothold.getX1())
                / (foothold.getX2() - foothold.getX1());
    }

    private static void reply(AgentRuntimeEntry entry, List<String> lines) {
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()));
        AgentSchedulerRuntime.afterRandomDelay(entry, 500, 900, () -> AgentDialogueTransportRuntime.replyNow(entry, line));
    }

    private static String normalize(String message) {
        return message.trim().toLowerCase(Locale.ROOT).replaceAll("[!.?~]+$", "").trim();
    }
}
