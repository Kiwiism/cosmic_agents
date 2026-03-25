package server.bots;

import client.Character;
import client.Skill;
import client.SkillFactory;
import net.server.channel.handlers.AbstractDealDamageHandler;
import net.server.channel.handlers.CloseRangeDamageHandler;
import server.StatEffect;
import server.life.Monster;
import tools.PacketCreator;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

class BotCombatManager {

    static class Config {
        // Physics (combat use only)
        public float KNOCKBACK_RISE = 18f;

        // Attack range
        public int   ATTACK_RANGE_X  = 80;
        public int   ATTACK_RANGE_Y  = 50;
        public int   ATTACK_DOWN_MAX = 20;
        public int   ATTACK_JUMP_Y   = 130;
        public int   ATTACK_COOLDOWN = 10;

        // Grind / AoE
        public int   GRIND_SEEK_RANGE  = 800;
        public int   AOE_MOB_THRESHOLD = 2;
        public long  AOE_RANGE_SQ      = 90000L;

        // Mob damage
        public int   MOB_TOUCH_HALF_W = 40;
        public int   MOB_TOUCH_HALF_H = 30;
        public int   MOB_HIT_COOLDOWN = 15;
        public long  BOT_DEAD_MS      = 10_000L;
    }

    static Config cfg = new Config();

    private static final List<String> DEATH_REPLIES = List.of(
            "oops im dead", "gg", "rip me", "oww", "i died lol",
            "welp", "ouchh", "nooo", "ok i died", "i'll be right back");

    // ─── Damage taken ─────────────────────────────────────────────────────────

    /** Check every alive monster on the map; if bot is inside its bounding box, apply a hit. */
    static void tickMobDamage(BotEntry entry, Character bot) {
        if (entry.mobHitCooldown > 0) { entry.mobHitCooldown--; return; }
        if (bot.getHp() <= 0) return;

        Point botPos = bot.getPosition();
        Config cc = BotCombatManager.cfg;
        for (Monster mob : bot.getMap().getAllMonsters()) {
            if (!mob.isAlive()) continue;
            Point mp = mob.getPosition();
            if (Math.abs(botPos.x - mp.x) <= cc.MOB_TOUCH_HALF_W
                    && Math.abs(botPos.y - mp.y) <= cc.MOB_TOUCH_HALF_H) {
                applyMobHit(entry, bot, mob);
                return;
            }
        }
    }

    /**
     * Apply one physical hit from {@code mob} to the bot.
     * Damage = PADamage ± 15% (v83 mob attack approximation; no server-side WDEF available).
     */
    static void applyMobHit(BotEntry entry, Character bot, Monster mob) {
        Config cc = BotCombatManager.cfg;
        BotMovementManager.Config mc = BotMovementManager.cfg;
        int pa  = mob.getPADamage();
        int max = Math.max(1, pa * 115 / 100);
        int min = Math.max(1, pa *  85 / 100);
        int dmg = min < max ? ThreadLocalRandom.current().nextInt(min, max + 1) : max;

        bot.addMPHP(-dmg, 0);

        // direction: 0 = hit from left (knocked right), 1 = hit from right (knocked left)
        int dir = mob.getPosition().x < bot.getPosition().x ? 0 : 1;
        bot.getMap().broadcastMessage(bot,
                PacketCreator.damagePlayer(-1, mob.getId(), bot.getId(), dmg, 0,
                        dir, false, 0, false, 0, 0, 0), false);

        // Knock bot back — enter airborne with slight upward arc so physics land it correctly.
        Point bp  = bot.getPosition();
        int   kbX = bp.x + (dir == 0 ? 30 : -30);
        bot.setPosition(new Point(kbX, bp.y));
        BotMovementManager.resetEntryState(entry);
        entry.velY    = -cc.KNOCKBACK_RISE;
        entry.airVelX = (dir == 0 ? 1 : -1) * mc.STEP;
        int velXBcast = entry.airVelX * (1000 / mc.TICK_MS);
        int velYBcast = (int) (-entry.velY * (1000f / mc.TICK_MS));
        BotMovementManager.broadcastMovement(bot, velXBcast, velYBcast);

        entry.mobHitCooldown = cc.MOB_HIT_COOLDOWN;

        if (bot.getHp() <= 0) {
            bot.setStance(mc.DEAD_STANCE);
            BotMovementManager.broadcastMovement(bot, 0, 0);
            BotManager.getInstance().botSay(bot, BotManager.randomReply(DEATH_REPLIES));
            entry.deadUntil = System.currentTimeMillis() + cc.BOT_DEAD_MS;
            BotMovementManager.resetEntryState(entry);
        }
    }

    // ─── Skill cache ──────────────────────────────────────────────────────────

    static void rebuildSkillCacheIfNeeded(BotEntry entry, Character bot) {
        if (entry.cachedSkillJob   == bot.getJob().getId()
                && entry.cachedSkillLevel == bot.getLevel()) return;
        entry.cachedSkillJob   = bot.getJob().getId();
        entry.cachedSkillLevel = bot.getLevel();

        entry.attackSkillId = 0;
        entry.aoeSkillId    = 0;
        entry.aoeSkillMobs  = 1;
        entry.buffSkillIds.clear();

        int bestAtkHits  = 0;
        int bestAoeScore = 0;

        for (Skill skill : bot.getSkills().keySet()) {
            int lvl = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx  = skill.getEffect(lvl);
            int atk  = fx.getAttackCount();
            int mobs = fx.getMobCount();
            int dur  = fx.getDuration();

            if (atk > 0) {
                if (mobs >= 2) {
                    int score = mobs * atk;
                    if (score > bestAoeScore) {
                        bestAoeScore       = score;
                        entry.aoeSkillId   = skill.getId();
                        entry.aoeSkillMobs = mobs;
                    }
                } else {
                    if (atk > bestAtkHits) {
                        bestAtkHits         = atk;
                        entry.attackSkillId = skill.getId();
                    }
                }
            } else if (dur > 0) {
                // Timed buff with no attack component
                entry.buffSkillIds.add(skill.getId());
                entry.nextBuffAt.putIfAbsent(skill.getId(), 0L); // 0 = apply immediately
            }
            // passive (dur=0, atk=0) — skip
        }
    }

    // ─── Auto-buff ────────────────────────────────────────────────────────────

    static void tickBuffs(BotEntry entry, Character bot) {
        if (!entry.following && !entry.grinding) return;
        if (entry.buffSkillIds.isEmpty()) return;
        if (bot.getMap().getAllMonsters().stream().noneMatch(Monster::isAlive)) return;

        long now = System.currentTimeMillis();
        for (int skillId : entry.buffSkillIds) {
            if (now < entry.nextBuffAt.getOrDefault(skillId, 0L)) continue;
            if (bot.skillIsCooling(skillId)) continue;

            Skill skill = SkillFactory.getSkill(skillId);
            int   lvl   = bot.getSkillLevel(skill);
            if (lvl <= 0) continue;

            StatEffect fx = skill.getEffect(lvl);
            fx.applyTo(bot, null);

            long dur = fx.getDuration();
            entry.nextBuffAt.put(skillId, now + (long)(dur * 0.9));
            if (fx.getCooldown() > 0) {
                bot.addCooldown(skillId, now, fx.getCooldown() * 1000L);
            }
        }
    }

    // ─── Grind helpers ────────────────────────────────────────────────────────

    /** Returns a random monster from the nearest 3 within seek range, so multiple bots spread across targets. */
    static Monster findGrindTarget(Character bot) {
        Point botPos = bot.getPosition();
        double rangeSq = (double) BotCombatManager.cfg.GRIND_SEEK_RANGE * BotCombatManager.cfg.GRIND_SEEK_RANGE;
        List<Monster> candidates = new ArrayList<>();
        for (Monster m : bot.getMap().getAllMonsters()) {
            if (m.isAlive() && m.getPosition().distanceSq(botPos) <= rangeSq) candidates.add(m);
        }
        if (candidates.isEmpty()) return null;
        candidates.sort((a, b) -> Double.compare(a.getPosition().distanceSq(botPos), b.getPosition().distanceSq(botPos)));
        return candidates.get(ThreadLocalRandom.current().nextInt(Math.min(3, candidates.size())));
    }

    static void attackMonster(BotEntry entry, Character bot, Monster target) {
        if (entry.attackCooldown > 0) { entry.attackCooldown--; return; }

        int watk   = bot.getTotalWatk();
        int maxDmg = Math.max(1, bot.calculateMaxBaseDamage(watk));
        int minDmg = Math.max(1, bot.calculateMinBaseDamage(watk));

        // --- skill selection ---
        // TODO: have a dynamic sized list of available skills in case of cooldown-heavy classes
        int chosenSkill = 0;
        int chosenLevel = 0;
        int numDmg      = 1;
        List<Monster> aoeTargets = null;

        // Try AoE skill first if enough nearby monsters
        if (entry.aoeSkillId != 0 && !bot.skillIsCooling(entry.aoeSkillId)) {
            List<Monster> nearby = new ArrayList<>();
            for (Monster m : bot.getMap().getAllMonsters()) {
                if (m.isAlive() && distSq(m.getPosition(), target.getPosition()) <= BotCombatManager.cfg.AOE_RANGE_SQ) {
                    nearby.add(m);
                    if (nearby.size() >= entry.aoeSkillMobs) break;
                }
            }
            if (nearby.size() >= BotCombatManager.cfg.AOE_MOB_THRESHOLD) {
                chosenSkill = entry.aoeSkillId;
                chosenLevel = bot.getSkillLevel(chosenSkill);
                numDmg      = Math.max(1, SkillFactory.getSkill(chosenSkill).getEffect(chosenLevel).getAttackCount());
                aoeTargets  = nearby;
            }
        }
        // Fall back to single-target attack skill
        if (chosenSkill == 0 && entry.attackSkillId != 0 && !bot.skillIsCooling(entry.attackSkillId)) {
            chosenSkill = entry.attackSkillId;
            chosenLevel = bot.getSkillLevel(chosenSkill);
            numDmg      = Math.max(1, SkillFactory.getSkill(chosenSkill).getEffect(chosenLevel).getAttackCount());
        }
        // chosenSkill == 0 → basic attack

        // --- build attack ---
        int numAttacked = aoeTargets != null ? aoeTargets.size() : 1;
        AbstractDealDamageHandler.AttackInfo attack = new AbstractDealDamageHandler.AttackInfo();
        attack.skill                = chosenSkill;
        attack.skilllevel           = chosenLevel;
        attack.numDamage            = numDmg;
        attack.numAttacked          = numAttacked;
        attack.numAttackedAndDamage = (numAttacked << 4) | numDmg;
        attack.speed                = 4;
        attack.stance    = bot.getPosition().x > target.getPosition().x ? -128 : 0;
        attack.direction = bot.getPosition().x > target.getPosition().x ? 17 : 6;
        attack.targets   = new HashMap<>();

        if (aoeTargets != null) {
            for (Monster m : aoeTargets) {
                attack.targets.put(m.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
            }
            // Ensure primary target is always included
            if (!attack.targets.containsKey(target.getObjectId())) {
                attack.targets.put(target.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
            }
            attack.numAttacked          = attack.targets.size();
            attack.numAttackedAndDamage = (attack.numAttacked << 4) | numDmg;
        } else {
            attack.targets.put(target.getObjectId(), makeTarget(numDmg, minDmg, maxDmg));
        }

        CloseRangeDamageHandler.applyCloseRangeEffects(attack, bot, bot.getClient());
        entry.attackCooldown = BotCombatManager.cfg.ATTACK_COOLDOWN;
    }

    private static AbstractDealDamageHandler.AttackTarget makeTarget(int hits, int minDmg, int maxDmg) {
        List<Integer> lines = new ArrayList<>(hits);
        for (int i = 0; i < hits; i++) {
            lines.add(minDmg < maxDmg
                    ? ThreadLocalRandom.current().nextInt(minDmg, maxDmg + 1)
                    : maxDmg);
        }
        return new AbstractDealDamageHandler.AttackTarget((short) 305, lines);
    }

    static long distSq(Point a, Point b) {
        long dx = a.x - b.x, dy = a.y - b.y;
        return dx * dx + dy * dy;
    }
}
