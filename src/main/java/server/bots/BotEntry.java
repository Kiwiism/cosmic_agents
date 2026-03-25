package server.bots;

import client.Character;
import server.life.Monster;
import server.maps.Foothold;
import server.maps.Rope;

import java.awt.*;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

class BotEntry {
    final Character bot;
    volatile Character owner;
    volatile boolean following = false;
    final ScheduledFuture<?> task;

    // Physics
    float velY      = 0f;
    boolean inAir   = false;
    int jumpCooldown = 0;

    // Rope climbing
    boolean climbing  = false;
    Rope    climbRope = null;

    // Jitter prevention — only starts moving toward owner once distance exceeds FOLLOW_DIST
    boolean wasMovingX = false;

    // Committed horizontal step while airborne — set at jump time, not re-computed each tick
    int airVelX = 0;

    // Rope state
    boolean seekingRope     = false; // only grab a rope mid-air when we intentionally jumped for one
    int     ropeGrabCooldown = 0;    // ticks before rope-grab is re-enabled after leaving a rope

    // Down-jump: true when prone was shown last tick, jump fires this tick
    boolean downJumpPending = false;
    long downJumpGracePeriodMS = 0;

    // Stuck recovery
    int   stuckCheckTimer     = 0;
    Point lastStuckCheckPos   = null;
    int   rawChaseTicks       = 0;

    // Waypoint — navigate to a rope outside normal detection range
    Rope  waypointRope  = null;
    int   waypointTimer = 0;

    // Grind mode
    boolean grinding       = false;
    Monster grindTarget    = null;
    int     attackCooldown = 0;

    // Skill cache — rebuilt on job change
    int          cachedSkillJob = -1;
    int          attackSkillId  = 0;   // best single-target attack skill (0 = basic attack)
    int          aoeSkillId     = 0;   // best AoE attack skill (0 = none)
    int          aoeSkillMobs   = 1;   // mobCount of the chosen AoE skill
    List<Integer> buffSkillIds  = new ArrayList<>();
    // skillId → ms timestamp when buff should be re-applied (0 = apply immediately)
    final Map<Integer, Long> nextBuffAt = new HashMap<>();

    // Damage taken
    long deadUntil      = 0;  // ms timestamp when bot may respawn; 0 = alive
    int  mobHitCooldown = 0;  // ticks until next mob hit is allowed

    // Loot & potions
    int  potCheckTimer      = 0;
    int  invFullWarnCooldown = 0;

    // Job advancement: 0=none, 8=lv8 mage prompt, 10=lv10 1st job, 30=2nd, 70=3rd, 120=4th
    int jobPromptSent  = 0;
    int lastKnownLevel = -1;

    // AP/SP builds — set once on first job, can be changed via chat
    BotBuildManager.ApBuild apBuild       = null;   // null = not chosen yet
    boolean apPromptSent  = false;  // prevent re-prompting before owner responds

    // Pending two-step action: null, "logout", or "relog"
    String pendingAction = null;

    // Message queue — sends with ~5s spacing between messages
    final ArrayDeque<String> msgQueue = new ArrayDeque<>();
    boolean msgSending = false;

    // AFK detection
    Point ownerAfkPos     = null;
    long  ownerAfkSinceMs = 0;
    boolean ownerWasAfk   = false;

    // Foothold index, rebuilt on map change
    int lastMapId = -1;
    Map<Integer, Foothold> fhIndex = new HashMap<>();

    BotEntry(Character bot, Character owner, ScheduledFuture<?> task) {
        this.bot = bot;
        this.owner = owner;
        this.task = task;
    }
}
