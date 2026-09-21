package server.agents.diagnostics.skillprobe;

import client.BuffStat;
import client.Character;
import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.game.GameConstants;
import constants.skills.ChiefBandit;
import constants.skills.Crusader;
import constants.skills.DawnWarrior;
import constants.skills.DarkKnight;
import constants.skills.DragonKnight;
import constants.skills.SuperGM;
import constants.skills.Warrior;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import server.agents.auth.AgentAuthorityService;
import server.agents.auth.AgentControlService;
import server.agents.capabilities.combat.AgentAttackExecutionProvider;
import server.agents.capabilities.combat.AgentAttackPlan;
import server.agents.capabilities.combat.AgentAttackRoute;
import server.agents.capabilities.combat.AgentAttackTransactionResult;
import server.agents.capabilities.combat.AgentComboFinisherPolicy;
import server.agents.capabilities.combat.AgentCombatAimPointPolicy;
import server.agents.capabilities.combat.AgentCombatAmmoCounter;
import server.agents.capabilities.combat.AgentCombatAttackRuntime;
import server.agents.capabilities.combat.AgentCombatBuffRuntime;
import server.agents.capabilities.combat.AgentCombatConfig;
import server.agents.capabilities.combat.AgentCombatCooldownStateRuntime;
import server.agents.capabilities.combat.AgentCombatDebuffRuntime;
import server.agents.capabilities.combat.AgentCombatHealRuntime;
import server.agents.capabilities.combat.AgentCombatHitCounter;
import server.agents.capabilities.combat.AgentCombatHitboxIntersection;
import server.agents.capabilities.combat.AgentCombatSkillCacheRuntime;
import server.agents.capabilities.combat.AgentCombatSkillCacheStateRuntime;
import server.agents.capabilities.combat.AgentCombatSkillClassifier;
import server.agents.capabilities.combat.AgentCombatSkillHitboxPolicy;
import server.agents.capabilities.combat.AgentCombatSkillUsePolicy;
import server.agents.capabilities.combat.AgentCombatSpecialMoveState;
import server.agents.capabilities.combat.AgentCombatSpecialMoveTickRuntime;
import server.agents.capabilities.combat.AgentCombatSummonRuntime;
import server.agents.capabilities.combat.AgentCombatWeaponPolicy;
import server.agents.capabilities.build.AgentCharacterShaper;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileService;
import server.agents.capabilities.combat.AgentSkillAttackPlanRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentShopGatewayRuntime;
import server.agents.capabilities.supplies.AgentSkillConsumablePolicy;
import constants.skills.Bishop;
import constants.skills.Brawler;
import constants.skills.Gunslinger;
import constants.skills.Pirate;
import server.agents.integration.AgentCombatGatewayRuntime;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.capabilities.navigation.AgentMovementSkillPolicy;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.capabilities.party.AgentPartyLifecycleService;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.registry.AgentResolvedCharacter;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.life.LifeFactory;
import server.life.Monster;
import server.maps.MapleMap;
import server.security.SecurityEvent;
import server.security.SecurityEventRuntime;
import server.security.SecurityEventType;
import tools.Pair;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GM-only skill readiness harness: shapes an Agent to a job/level, learns every skill in its
 * job tree, then tries each one against a dummy monster in a private map and records where the
 * attempt stopped (planner stage, execution reason, server autoban signal, or applied damage).
 *
 * <p>It drives the same planner and attack transaction the Agent uses in normal combat, so a
 * failure here is a failure the Agent would hit while grinding. Shaping is destructive to the
 * target Agent (job, level, SP); use a throwaway companion.</p>
 */
public final class AgentSkillProbeService {
    private static final Logger log = LoggerFactory.getLogger(AgentSkillProbeService.class);
    private static final Map<Integer, Thread> RUNNING = new ConcurrentHashMap<>();
    private static final Map<Integer, SkillProbeReport> LAST_REPORT = new ConcurrentHashMap<>();

    private static final int EXEC_RETRIES = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.EXEC_RETRIES");
    private static final long EXEC_RETRY_SLEEP_MS = config.AgentTuning.longValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.EXEC_RETRY_SLEEP_MS");
    private static final long SETTLE_MS = config.AgentTuning.longValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.SETTLE_MS");
    /**
     * Dummy offsets tried in order until a plan exists: melee reach first, then beyond the ranged
     * "degenerate" distance (the planner aims at the nearest hitbox edge, so a 60px dummy is too
     * close for a bow), mirrored to both sides for facing-sensitive hitboxes.
     */
    private static final int[] MOB_OFFSETS_X = {30, 60, -60, 100, 160, -160, 260};
    private static final int MOB_OFFSET_X = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.MOB_OFFSET_X");
    /** Highest skill index scanned under each job id (skill id = job * 10000 + index). */
    private static final int MAX_SKILL_INDEX = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.MAX_SKILL_INDEX");
    /** Three Snails, Recovery, Nimble Feet. */
    private static final int[] STANDARD_BEGINNER_SKILLS = {1000, 1001, 1002};
    /** Hits landed with Combo Attack up before retrying a finisher; the server grants one orb per hit. */
    private static final int COMBO_WARMUP_HITS = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.COMBO_WARMUP_HITS");
    private static final int MESO_DROP_COUNT = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.MESO_DROP_COUNT");
    private static final int ELIXIR_ITEM_ID = 2000004;
    /** Orange Mushroom: 55px tall, covers chest-height hit lines a Snail slips under. */
    private static final int TALL_MOB_ID = 1210102;
    /** Sword, spear, pole arm, wand, bow, crossbow, claw, dagger, knuckle, gun. */
    private static final int[] ALTERNATE_WEAPONS = {1302000, 1432000, 1442000, 1372005, 1452002, 1462000, 1472000, 1332007, 1482000, 1492000};
    private static final int MESO_DROP_AMOUNT = config.AgentTuning.intValue("server.agents.diagnostics.skillprobe.AgentSkillProbeService.MESO_DROP_AMOUNT");

    private AgentSkillProbeService() {
    }

    public static List<String> execute(Character operator, String[] params) {
        if (operator == null || !AgentAuthorityService.mayOperate(operator)) {
            return List.of("You are not configured as an Agent operator.");
        }
        if (params != null && params.length == 1 && "report".equalsIgnoreCase(params[0])) {
            SkillProbeReport last = LAST_REPORT.get(operator.getId());
            return last == null ? List.of("No skill probe has completed yet.") : last.chatSummary();
        }
        SkillProbeOptions options;
        try {
            options = SkillProbeOptions.parse(params);
        } catch (IllegalArgumentException invalid) {
            return List.of(invalid.getMessage());
        }
        if (RUNNING.containsKey(operator.getId())) {
            return List.of("A skill probe is already running for you; wait for it to finish.");
        }
        AgentRuntimeEntry entry = resolveEntry(operator, options.agentName());
        if (entry == null || entry.bot() == null) {
            return List.of("Agent " + options.agentName() + " is not an active Agent.");
        }
        Thread worker = new Thread(() -> {
            try {
                SkillProbeReport report = run(operator, entry, options);
                LAST_REPORT.put(operator.getId(), report);
                report.chatSummary().forEach(operator::yellowMessage);
            } catch (Exception failure) {
                log.warn("Skill probe for {} failed", options.agentName(), failure);
                operator.yellowMessage("Skill probe failed: " + failure);
            } finally {
                RUNNING.remove(operator.getId());
            }
        }, "skill-probe-" + operator.getId());
        worker.setDaemon(true);
        RUNNING.put(operator.getId(), worker);
        worker.start();
        return List.of("Skill probe started for " + entry.bot().getName()
                + "; results follow in chat and in " + SkillProbeReportWriter.REPORT_DIR + ".");
    }

    private static AgentRuntimeEntry resolveEntry(Character operator, String agentName) {
        AgentRuntimeEntry own = AgentRuntimeRegistry.findByName(operator.getId(), agentName);
        if (own != null) {
            return own;
        }
        AgentResolvedCharacter resolved = AgentControlService.getInstance().resolveCharacterByName(agentName);
        return resolved == null || !resolved.isOnline() ? null
                : AgentRuntimeRegistry.findByAgentCharacterId(resolved.id());
    }

    static SkillProbeReport run(Character operator, AgentRuntimeEntry entry, SkillProbeOptions options)
            throws InterruptedException {
        Character bot = entry.bot();
        List<String> notes = new ArrayList<>();
        long startedAt = System.currentTimeMillis();

        if (options.wantsShaping()) {
            Job target = options.job() == null ? bot.getJob() : options.job();
            int level = options.level() > 0 ? options.level() : bot.getLevel();
            if (level < bot.getLevel()) {
                notes.add("Agent is already level " + bot.getLevel() + "; shaping never lowers level.");
            }
            AgentCharacterShaper.shapeTo(bot, target, level);
            if (bot.getJob() != target) {
                notes.add("Job stopped at " + bot.getJob() + " (target " + target
                        + " is not legal at level " + bot.getLevel() + ").");
            }
        }
        if (options.profileBuild()) {
            int learned = AgentSpBuildProfileService.assignOffline(bot);
            notes.add("SP profile build: learned " + learned + " skill levels; remaining SP "
                    + bot.getRemainingSps()[GameConstants.getSkillBook(bot.getJob().getId())] + ".");
        } else {
            int learned = learnFullTree(bot);
            notes.add("Learned/maxed " + learned + " skills across the " + bot.getJob() + " tree.");
        }

        provisionWeapon(bot, options, notes);
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (weaponType == null) {
            notes.add("WARNING: no weapon equipped; every attack skill will report WEAPON_INCOMPATIBLE.");
        }

        MapleMap map = warpToProbeMap(operator, bot, options.mapId(), notes);
        Thread.sleep(SETTLE_MS);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);

        List<Skill> skills = new ArrayList<>(bot.getSkills().keySet());
        skills.sort(Comparator.comparingInt(Skill::getId));
        List<SkillProbeOutcome> outcomes = new ArrayList<>();
        Set<Integer> probeMobOids = new LinkedHashSet<>();
        // A following companion's automatic special-move tick would cast into the probe's dummies and
        // hold the attack cooldown; the loop smoke re-enables it on purpose.
        boolean specialMovesWere = AgentCombatSpecialMoveTickRuntime.enabled();
        AgentCombatSpecialMoveTickRuntime.setEnabled(false);
        // Hold the Agent's own AI idle: in follow mode it jump-attacks the dummies spawned beside it and
        // ends up airborne, which fails every later cast. Probes that need a mode set it themselves.
        boolean wasFollowing = AgentModeStateRuntime.following(entry);
        boolean wasGrindingMode = AgentModeStateRuntime.grinding(entry);
        AgentModeStateRuntime.setFollowing(entry, false);
        AgentModeStateRuntime.setGrinding(entry, false);
        try {
            for (Skill skill : skills) {
                int level = bot.getSkillLevel(skill);
                if (level <= 0) {
                    continue;
                }
                outcomes.add(probeSkill(operator, entry, bot, map, skill, level, options, probeMobOids));
            }
        } finally {
            AgentCombatSpecialMoveTickRuntime.setEnabled(specialMovesWere);
            AgentModeStateRuntime.setFollowing(entry, wasFollowing);
            AgentModeStateRuntime.setGrinding(entry, wasGrindingMode);
            if (!options.keepMobs()) {
                clearProbeMobs(map, bot, probeMobOids);
            }
            bot.healHpMp();
        }
        notes.addAll(skillConsumableSupplyNotes(bot));
        if (options.loopSeconds() > 0) {
            notes.addAll(combatLoopSmoke(entry, bot, map, options, probeMobOids));
            if (!options.keepMobs()) {
                clearProbeMobs(map, bot, probeMobOids);
            }
        }
        SkillProbeReport report = new SkillProbeReport(bot.getName(), bot.getJob(), bot.getLevel(),
                weaponType, options, notes, outcomes, startedAt, System.currentTimeMillis(), null);
        try {
            report = report.withPath(SkillProbeReportWriter.write(report));
        } catch (Exception failure) {
            log.warn("Could not write skill probe report", failure);
            notes.add("Report file not written: " + failure.getMessage());
        }
        return report;
    }

    /** Which town supplier shops stock the Agent's skill consumables (procurement buys them on any shop visit). */
    private static List<String> skillConsumableSupplyNotes(Character bot) {
        List<String> lines = new ArrayList<>();
        int[] supplierNpcs = {1011100, 1021100, 1031100, 1051002, 1091002};
        for (Map.Entry<Integer, Integer> target : AgentSkillConsumablePolicy.targets(bot).entrySet()) {
            List<Integer> stocking = new ArrayList<>();
            for (int npcId : supplierNpcs) {
                server.Shop shop = AgentShopGatewayRuntime.shop().findForNpc(npcId);
                if (shop != null && shop.getItems().stream().anyMatch(item -> item.getItemId() == target.getKey())) {
                    stocking.add(npcId);
                }
            }
            lines.add("Skill consumable " + target.getKey() + " (" + ItemInformationProvider.getInstance().getName(target.getKey())
                    + "): have " + AgentSkillConsumablePolicy.quantity(bot, target.getKey()) + "/" + target.getValue()
                    + ", stocked by supplier NPCs " + stocking);
        }
        return lines;
    }

    /**
     * Combat-loop smoke: grinding mode among tall dummies for a while, then report what the automatic
     * tick did on its own (summon out, statuses on the dummies, damage taken by them).
     */
    private static List<String> combatLoopSmoke(AgentRuntimeEntry entry, Character bot, MapleMap map,
                                                SkillProbeOptions options, Set<Integer> probeMobOids) throws InterruptedException {
        List<String> lines = new ArrayList<>();
        clearProbeMobs(map, bot, probeMobOids);
        bot.cancelEffectFromBuffStat(BuffStat.SUMMON);
        bot.cancelEffectFromBuffStat(BuffStat.PUPPET);
        List<Monster> dummies = new ArrayList<>();
        for (int offset : new int[]{60, -60, 120, -120}) {
            Monster mob = spawnProbeMobAt(map, bot, TALL_MOB_ID, probeMobOids, offset);
            if (mob != null) {
                dummies.add(mob);
            }
        }
        long hpBefore = dummies.stream().mapToLong(Monster::getHp).sum();
        AgentCombatSpecialMoveState counters = entry.capabilityStates().require(AgentCombatSpecialMoveState.STATE_KEY);
        long attacksBefore = counters.attacksCommitted();
        long specialsBefore = counters.specialMovesCast();
        // Potion resupply would otherwise suspend the foreground (the GM map has no route to a shop).
        if (bot.getInventory(InventoryType.USE).countById(ELIXIR_ITEM_ID) < 50) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, ELIXIR_ITEM_ID, (short) 100);
        }
        boolean wasGrinding = AgentModeStateRuntime.grinding(entry);
        boolean specialWas = AgentCombatSpecialMoveTickRuntime.enabled();
        AgentCombatSpecialMoveTickRuntime.setEnabled(options.loopSpecialMoves());
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentModeStateRuntime.setGrinding(entry, true);
        int respawned = 0;
        int samples = 0;
        int cooldownSamples = 0;
        int airSamples = 0;
        int moveWindowSamples = 0;
        Set<String> statuses = new java.util.TreeSet<>();
        try {
            long until = System.currentTimeMillis() + options.loopSeconds() * 1000L;
            while (System.currentTimeMillis() < until) {
                Thread.sleep(1000L);
                samples++;
                if (AgentCombatCooldownStateRuntime.hasAttackCooldown(entry)) cooldownSamples++;
                if (!AgentMovementStateRuntime.grounded(entry)) airSamples++;
                if (AgentCombatCooldownStateRuntime.hasMoveWindow(entry)) moveWindowSamples++;
                // Keep the fight going: a 4th-job Agent one-shots dummies, and a dead map ends the loop.
                for (int i = 0; i < dummies.size(); i++) {
                    Monster mob = dummies.get(i);
                    if (mob.isAlive()) {
                        mob.getStati().keySet().forEach(status -> statuses.add(status.name()));
                        continue;
                    }
                    Monster fresh = spawnProbeMobAt(map, bot, TALL_MOB_ID, probeMobOids, (i % 2 == 0 ? 1 : -1) * (60 + 60 * (i / 2)));
                    if (fresh != null) {
                        dummies.set(i, fresh);
                        respawned++;
                    }
                }
            }
        } finally {
            AgentModeStateRuntime.setGrinding(entry, wasGrinding);
            AgentCombatSpecialMoveTickRuntime.setEnabled(specialWas);
        }
        List<String> summons = new ArrayList<>();
        for (int skillId : AgentCombatSkillCacheStateRuntime.summonSkillIds(entry)) {
            if (bot.getSummonByKey(skillId) != null) {
                summons.add(SkillFactory.getSkillName(skillId));
            }
        }
        long hpAfter = 0;
        int alive = 0;
        for (Monster mob : dummies) {
            if (mob.isAlive()) {
                alive++;
                hpAfter += mob.getHp();
                mob.getStati().keySet().forEach(status -> statuses.add(status.name()));
            }
        }
        lines.add("Combat loop " + options.loopSeconds() + "s (special moves " + (options.loopSpecialMoves() ? "on" : "off") + "): own attacks " + (counters.attacksCommitted() - attacksBefore)
                + ", own special moves " + (counters.specialMovesCast() - specialsBefore) + "; summons out " + summons
                + "; statuses seen on dummies " + statuses + "; dummies killed " + respawned
                + ", alive now " + alive + "/" + dummies.size() + " (hp " + hpBefore + " -> " + hpAfter + ")"
                + "; debuff ids cached " + entry.capabilityStates().require(AgentCombatSpecialMoveState.STATE_KEY).debuffSkillIds()
                + "; samples " + samples + ": attack-cooldown " + cooldownSamples + ", not-grounded " + airSamples
                + ", move-window " + moveWindowSamples);
        return lines;
    }

    /**
     * Sets every skill of every job on the Agent's advancement chain to its maximum level. The
     * beginner tree is limited to the three standard skills; its event and mount skills would
     * otherwise pollute the report with entries no Agent ever has.
     */
    static int learnFullTree(Character bot) {
        List<Integer> jobIds = new ArrayList<>();
        for (Job job : AgentCharacterShaper.advancementChain(bot.getJob())) {
            jobIds.add(job.getId());
        }
        int changed = 0;
        for (Skill learned : new ArrayList<>(bot.getSkills().keySet())) {
            // Event and mount beginner skills left over from older shaping: no Agent should carry them.
            if (learned.getId() < 10000 && java.util.Arrays.stream(STANDARD_BEGINNER_SKILLS).noneMatch(id -> id == learned.getId())) {
                bot.changeSkillLevel(learned, (byte) -1, 0, -1);
            }
        }
        for (int skillId : STANDARD_BEGINNER_SKILLS) {
            changed += maxSkill(bot, SkillFactory.getSkill(skillId)) ? 1 : 0;
        }
        for (int jobId : jobIds) {
            for (int index = 0; index <= MAX_SKILL_INDEX; index++) {
                changed += maxSkill(bot, SkillFactory.getSkill(jobId * 10000 + index)) ? 1 : 0;
            }
        }
        // changeSkillLevel marks the SKILLS persistence section dirty; the regular save cycle flushes it.
        return changed;
    }

    private static boolean maxSkill(Character bot, Skill skill) {
        if (skill == null) {
            return false;
        }
        int max = skill.getMaxLevel();
        if (max <= 0 || bot.getSkillLevel(skill) >= max) {
            return false;
        }
        int master = skill.isFourthJob() ? max : bot.getMasterLevel(skill);
        if (bot.changeSkillLevel(skill, (byte) max, master, -1)) {
            return true;
        }
        log.debug("skill probe could not set {} to {}", skill.getId(), max);
        return false;
    }

    private static void provisionWeapon(Character bot, SkillProbeOptions options, List<String> notes) {
        int itemId = options.weaponItemId();
        boolean hasWeapon = AgentAttackExecutionProvider.getEquippedWeaponType(bot) != null;
        if (itemId <= 0) {
            if (hasWeapon && !options.wantsShaping()) {
                return;
            }
            // A shaped Agent keeps whatever it held before (a mage with a starter sword); give it the family weapon.
            itemId = defaultWeaponFor(bot.getJob());
            notes.add("Provisioning default weapon " + itemId + " for " + bot.getJob() + ".");
        }
        ItemInformationProvider ii = ItemInformationProvider.getInstance();
        if (ii.getName(itemId) == null) {
            notes.add("WARNING: weapon item " + itemId + " does not exist; skipping.");
            return;
        }
        if (bot.getInventory(InventoryType.EQUIP).countById(itemId) == 0
                && bot.getInventory(InventoryType.EQUIPPED).countById(itemId) == 0) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, itemId, (short) 1);
        }
        if (!AgentEquipmentService.equipPreferredWeapon(bot, itemId)) {
            notes.add("WARNING: could not equip weapon " + itemId + " (" + ii.getName(itemId) + ").");
            return;
        }
        int ammoId = defaultAmmoFor(AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        if (ammoId > 0 && bot.getInventory(InventoryType.USE).countById(ammoId) < 200) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, ammoId, (short) 500);
            notes.add("Provisioned ammo " + ammoId + " (" + ii.getName(ammoId) + ").");
        }
    }

    /** A plain shop weapon per branch so the tree's skills are weapon-compatible. */
    static int defaultWeaponFor(Job job) {
        int id = job == null ? 0 : job.getId();
        int family = id / 100;
        int branch = (id / 10) % 10;
        return switch (family) {
            case 1 -> branch == 3 ? 1432000 : 1302000;  // spear for the Spearman line, else sword
            case 2 -> 1372005;                           // wand
            case 3 -> branch == 2 ? 1462000 : 1452002;  // crossbow / bow
            case 4 -> branch == 2 ? 1332007 : 1472000;  // dagger for the Bandit line, else claw
            case 5 -> branch == 2 ? 1492000 : 1482000;  // gun for the Gunslinger line, else knuckle
            default -> 1302000;
        };
    }

    static int defaultAmmoFor(WeaponType weaponType) {
        if (weaponType == null) {
            return 0;
        }
        return switch (weaponType) {
            case BOW -> 2060000;
            case CROSSBOW -> 2061000;
            case CLAW -> 2070000;
            case GUN -> 2330000;
            default -> 0;
        };
    }

    private static MapleMap warpToProbeMap(Character operator, Character bot, int mapId, List<String> notes) {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(
                AgentClientGatewayRuntime.clients().world(operator),
                AgentClientGatewayRuntime.clients().channel(operator), mapId);
        if (map == null) {
            notes.add("Map " + mapId + " does not exist; probing in the Agent's current map "
                    + bot.getMapId() + ".");
            return bot.getMap();
        }
        Point spawn = map.getPortal(0) == null ? new Point(0, 0) : map.getPortal(0).getPosition();
        if (operator.getMapId() != mapId) {
            operator.changeMap(map, map.getPortal(0));
        }
        if (bot.getMapId() != mapId) {
            AgentMapGatewayRuntime.map().changeMap(bot, map, spawn);
        }
        return bot.getMap();
    }

    private static SkillProbeOutcome probeSkill(Character operator, AgentRuntimeEntry entry, Character bot, MapleMap map,
                                                Skill skill, int level, SkillProbeOptions options,
                                                Set<Integer> probeMobOids) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        StatEffect effect = skill.getEffect(level);
        AgentCombatSkillClassifier.SkillCacheBucket bucket =
                AgentCombatSkillClassifier.classifySkillCacheBucket(skill, effect);
        SkillProbeOutcome.Kind kind = switch (bucket) {
            case ACTIVE_ATTACK -> SkillProbeOutcome.Kind.ATTACK;
            case SUPPORT_BUFF -> SkillProbeOutcome.Kind.BUFF;
            case ACTIVE_HEAL -> SkillProbeOutcome.Kind.HEAL;
            case SUMMON -> SkillProbeOutcome.Kind.SUMMON;
            case DEBUFF -> SkillProbeOutcome.Kind.DEBUFF;
            case UTILITY -> SkillProbeOutcome.Kind.UTILITY;
            case IGNORE -> SkillProbeOutcome.Kind.PASSIVE_OR_OTHER;
        };
        // A previous run may have left Infinity/Concentrate-class cooldowns; the probe measures execution, not timing.
        bot.removeCooldown(skill.getId());
        try {
            SkillProbeOutcome outcome = probeOnce(operator, entry, bot, map, skill, level, effect, bucket, kind, options, probeMobOids);
            if (outcome.stage() == SkillProbeOutcome.Stage.PLAN_WEAPON_INCOMPATIBLE) {
                int alternate = alternateWeaponFor(skill.getId(), bot);
                if (alternate > 0) {
                    outcome = withWeapon(entry, bot, alternate,
                            () -> probeOnce(operator, entry, bot, map, skill, level, effect, bucket, kind, options, probeMobOids));
                }
            }
            if (outcome.stage() == SkillProbeOutcome.Stage.PLAN_TARGET_UNREACHABLE && options.mobId() != TALL_MOB_ID) {
                // A 26px Snail sits under some measured hit lines (Iron Arrow); most mobs are taller.
                SkillProbeOptions tall = new SkillProbeOptions(options.agentName(), options.job(), options.level(),
                        TALL_MOB_ID, options.mapId(), options.weaponItemId(), options.probeBuffs(), options.shape(),
                        options.keepMobs(), options.profileBuild(), options.loopSeconds(), options.loopSpecialMoves());
                SkillProbeOutcome retried = probeOnce(operator, entry, bot, map, skill, level, effect, bucket, kind, tall, probeMobOids);
                if (retried.ok()) {
                    outcome = withDetail(retried, "with dummy " + TALL_MOB_ID + " (taller); " + retried.detail());
                }
            }
            return outcome;
        } catch (RuntimeException failure) {
            log.warn("skill probe {} ({}) threw", skill.getId(), name, failure);
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket.name(), kind,
                    SkillProbeOutcome.Stage.ERROR, failure.toString(), 0, 0, 0L, List.of(), 0L);
        }
    }

    private static SkillProbeOutcome probeOnce(Character operator, AgentRuntimeEntry entry, Character bot, MapleMap map,
                                               Skill skill, int level, StatEffect effect,
                                               AgentCombatSkillClassifier.SkillCacheBucket bucket,
                                               SkillProbeOutcome.Kind kind, SkillProbeOptions options,
                                               Set<Integer> probeMobOids) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        return switch (kind) {
            case ATTACK -> probeAttack(operator, entry, bot, map, skill, level, effect, bucket.name(), options, probeMobOids);
            case BUFF -> options.probeBuffs()
                    ? probeBuff(entry, bot, skill, level, effect, bucket.name())
                    : SkillProbeOutcome.notProbed(skill.getId(), name, level, skill.getMaxLevel(),
                    bucket.name(), kind, "buff probing disabled");
            case HEAL -> probeHeal(entry, bot, skill, level, bucket.name());
            case SUMMON -> probeSummon(entry, bot, skill, level, bucket.name());
            case DEBUFF -> probeDebuff(entry, bot, map, skill, level, effect, bucket.name(), options, probeMobOids);
            case UTILITY -> probeUtility(entry, bot, skill, level, bucket.name());
            case PASSIVE_OR_OTHER -> probeOther(operator, entry, bot, skill, level, effect, bucket.name());
            case MOVEMENT, PASSIVE -> throw new IllegalStateException("classified only inside probeOther");
        };
    }

    private interface ProbeCall {
        SkillProbeOutcome call() throws InterruptedException;
    }

    /** Runs the probe with the given weapon equipped (provisioned if needed), then restores the previous one. */
    private static SkillProbeOutcome withWeapon(AgentRuntimeEntry entry, Character bot, int itemId, ProbeCall call)
            throws InterruptedException {
        Item previous = bot.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        int previousId = previous == null ? 0 : previous.getItemId();
        String weaponName = ItemInformationProvider.getInstance().getName(itemId);
        if (!provisionAndEquip(bot, itemId)) {
            throw new IllegalStateException("could not equip alternate weapon " + itemId);
        }
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
        try {
            SkillProbeOutcome outcome = call.call();
            return withDetail(outcome, "with " + weaponName + " (" + itemId + "); " + outcome.detail());
        } finally {
            if (previousId > 0) {
                AgentEquipmentService.equipPreferredWeapon(bot, previousId);
                AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
            }
        }
    }

    private static boolean provisionAndEquip(Character bot, int itemId) {
        if (bot.getInventory(InventoryType.EQUIP).countById(itemId) == 0
                && bot.getInventory(InventoryType.EQUIPPED).countById(itemId) == 0) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, itemId, (short) 1);
        }
        if (!AgentEquipmentService.equipPreferredWeapon(bot, itemId)) {
            return false;
        }
        int ammoId = defaultAmmoFor(AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        if (ammoId > 0 && bot.getInventory(InventoryType.USE).countById(ammoId) < 200) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, ammoId, (short) 500);
        }
        return true;
    }

    /** First shop weapon whose type the skill accepts and that differs from the equipped one, else 0. */
    static int alternateWeaponFor(int skillId, Character bot) {
        WeaponType equipped = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        for (int candidate : ALTERNATE_WEAPONS) {
            WeaponType type = AgentInventoryGatewayRuntime.inventory().getWeaponType(candidate);
            if (type != null && type != equipped && AgentCombatWeaponPolicy.canUseSkillWithWeapon(skillId, type)) {
                return candidate;
            }
        }
        return 0;
    }

    private static SkillProbeOutcome withDetail(SkillProbeOutcome outcome, String detail) {
        return new SkillProbeOutcome(outcome.skillId(), outcome.skillName(), outcome.skillLevel(), outcome.maxLevel(),
                outcome.bucket(), outcome.kind(), outcome.stage(), detail, outcome.hitLines(), outcome.missLines(),
                outcome.mobHpDelta(), outcome.autobanSignals(), outcome.elapsedMs());
    }

    private static SkillProbeOutcome probeHeal(AgentRuntimeEntry entry, Character bot, Skill skill, int level,
                                               String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        AgentCombatSkillCacheRuntime.rebuildSkillCacheIfNeeded(entry, bot);
        int maxHp = bot.getCurrentMaxHp();
        bot.addHP(-(maxHp * 7 / 10));
        int before = bot.getHp();
        // The heal tick only runs while following or grinding; while following it may jump toward the
        // leader ("jump heal"), which leaves the Agent airborne for the rest of the probe. Grind, don't follow.
        boolean wasGrinding = AgentModeStateRuntime.grinding(entry);
        boolean wasFollowing = AgentModeStateRuntime.following(entry);
        AgentModeStateRuntime.setGrinding(entry, true);
        AgentModeStateRuntime.setFollowing(entry, false);
        boolean cast;
        try {
            cast = AgentCombatHealRuntime.tickSupportHealing(entry, bot, AgentCombatConfig.cfg);
        } finally {
            AgentModeStateRuntime.setGrinding(entry, wasGrinding);
            AgentModeStateRuntime.setFollowing(entry, wasFollowing);
        }
        Thread.sleep(SETTLE_MS);
        for (int i = 0; i < 20 && !AgentMovementStateRuntime.grounded(entry); i++) {
            Thread.sleep(100L);
        }
        int after = bot.getHp();
        SkillProbeOutcome.Stage stage;
        String detail = "hp " + before + " -> " + after + " of " + maxHp;
        if (!cast) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = "tickSupportHealing did not cast (cached heal=" + AgentCombatSkillCacheStateRuntime.healSkillId(entry) + "); " + detail;
        } else if (after <= before) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
        } else {
            stage = SkillProbeOutcome.Stage.OK;
        }
        bot.healHpMp();
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.HEAL, stage, detail, 0, 0, 0L, List.of(), System.currentTimeMillis() - started);
    }

    private static SkillProbeOutcome probeDebuff(AgentRuntimeEntry entry, Character bot, MapleMap map, Skill skill,
                                                 int level, StatEffect effect, String bucket, SkillProbeOptions options,
                                                 Set<Integer> probeMobOids) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        waitGrounded(entry);
        clearProbeMobs(map, bot, probeMobOids);
        // The skill box is relative to the caster's facing; put a dummy on each side.
        Monster right = spawnProbeMobAt(map, bot, options.mobId(), probeMobOids, 40);
        Monster left = spawnProbeMobAt(map, bot, options.mobId(), probeMobOids, -40);
        if (right == null || left == null) {
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.DEBUFF, SkillProbeOutcome.Stage.ERROR,
                    "dummy monster could not be spawned", 0, 0, 0L, List.of(), 0L);
        }
        Thread.sleep(SETTLE_MS);
        long eventFloor = latestSecuritySequence();
        AgentCombatDebuffRuntime.DebuffResult result = AgentCombatDebuffRuntime.tryDebuff(entry, bot, skill.getId());
        Thread.sleep(SETTLE_MS * 4);  // status application has been observed to land late (Hypnotize)
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        boolean verifiable = !effect.getMonsterStati().isEmpty();
        boolean applied = false;
        for (Monster mob : List.of(right, left)) {
            for (var status : effect.getMonsterStati().keySet()) {
                applied |= mob.isAlive() && mob.isBuffed(status);
            }
        }
        SkillProbeOutcome.Stage stage;
        String detail;
        if (result != AgentCombatDebuffRuntime.DebuffResult.CAST) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = "tryDebuff=" + result;
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = "server flagged " + signals;
        } else if (verifiable && !applied) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = "cast dispatched but neither dummy carries " + effect.getMonsterStati().keySet()
                    + " (chance " + Math.round(effect.getProp() * 100) + "%)";
        } else {
            stage = SkillProbeOutcome.Stage.OK;
            detail = verifiable ? "status " + effect.getMonsterStati().keySet() + " on dummy" : "dispatched (no status to verify)";
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.DEBUFF, stage, detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    private static SkillProbeOutcome probeSummon(AgentRuntimeEntry entry, Character bot, Skill skill, int level,
                                                 String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        waitGrounded(entry);
        StatEffect effect = skill.getEffect(level);
        String provisioned = "";
        if (effect.getItemConNo() > 0 && !effect.hasItemCon(bot)) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, effect.getItemCon(), (short) (effect.getItemConNo() * 5));
            provisioned = "provisioned itemCon " + effect.getItemCon() + "; ";
        }
        // Only one summon at a time: clear whatever the previous probe left.
        bot.cancelEffectFromBuffStat(BuffStat.SUMMON);
        bot.cancelEffectFromBuffStat(BuffStat.PUPPET);
        long eventFloor = latestSecuritySequence();
        AgentCombatSummonRuntime.SummonResult result = AgentCombatSummonRuntime.trySummon(entry, bot, skill.getId());
        Thread.sleep(SETTLE_MS * 2);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        boolean present = bot.getSummonByKey(skill.getId()) != null;
        SkillProbeOutcome.Stage stage;
        String detail;
        if (result != AgentCombatSummonRuntime.SummonResult.CAST) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = "trySummon=" + result;
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = "server flagged " + signals;
        } else if (!present) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = "cast dispatched but no summon registered for the skill";
        } else {
            stage = SkillProbeOutcome.Stage.OK;
            detail = "summon present";
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.SUMMON, stage, provisioned + detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    private static SkillProbeOutcome probeAttack(Character operator, AgentRuntimeEntry entry, Character bot, MapleMap map,
                                                 Skill skill, int level, StatEffect effect, String bucket,
                                                 SkillProbeOptions options, Set<Integer> probeMobOids)
            throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        waitGrounded(entry);
        String allyNote = skill.getId() == DragonKnight.DRAGON_ROAR ? ensureHealerAlly(operator, bot) + "; " : "";
        long eventFloor = latestSecuritySequence();

        Monster mob = null;
        AgentAttackPlan plan = null;
        String precondition = allyNote;
        for (int offset : MOB_OFFSETS_X) {
            // Leftover dummies would be picked as the closest target in a projectile's path.
            clearProbeMobs(map, bot, probeMobOids);
            mob = spawnProbeMobAt(map, bot, options.mobId(), probeMobOids, offset);
            if (mob == null) {
                return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                        SkillProbeOutcome.Kind.ATTACK, SkillProbeOutcome.Stage.ERROR,
                        "dummy monster " + options.mobId() + " could not be spawned", 0, 0, 0L, List.of(), 0L);
            }
            if (AgentCombatSkillClassifier.isEnergyAttackSkill(skill.getId()) && precondition.isEmpty()) {
                precondition = fillEnergyCharge(bot) + "; ";
            }
            if (GameConstants.isFinisherSkill(skill.getId()) && precondition.isEmpty()) {
                precondition = buildComboOrbs(entry, bot, mob) + "; ";
                if (!mob.isAlive()) {
                    // The warm-up hits often kill a low-HP dummy; the finisher needs a live target.
                    mob = spawnProbeMobAt(map, bot, options.mobId(), probeMobOids, offset);
                    if (mob == null) {
                        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                                SkillProbeOutcome.Kind.ATTACK, SkillProbeOutcome.Stage.ERROR,
                                precondition + "dummy monster could not be respawned", 0, 0, 0L, List.of(), 0L);
                    }
                }
            }
            if (skill.getId() == ChiefBandit.MESO_EXPLOSION) {
                precondition = dropMesosAround(bot, map, mob) + "; ";
            }
            plan = AgentSkillAttackPlanRuntime.planSkillAttack(bot, mob, skill.getId(), AgentCombatConfig.cfg);
            if (plan != null) {
                precondition = precondition + "dummy at " + offset + "px; ";
                break;
            }
        }
        long hpBefore = mob.getHp();
        if (plan == null) {
            // Diagnose against every offset and keep the attempt that got furthest through the gates.
            Pair<SkillProbeOutcome.Stage, String> best = null;
            for (int offset : MOB_OFFSETS_X) {
                clearProbeMobs(map, bot, probeMobOids);
                Monster candidate = spawnProbeMobAt(map, bot, options.mobId(), probeMobOids, offset);
                if (candidate == null) {
                    continue;
                }
                AgentSkillAttackPlanRuntime.planSkillAttack(bot, candidate, skill.getId(), AgentCombatConfig.cfg);
                Pair<SkillProbeOutcome.Stage, String> diagnosis = diagnosePlanFailure(bot, candidate, skill, level, effect);
                diagnosis = new Pair<>(diagnosis.getLeft(), "dummy at " + offset + "px: " + diagnosis.getRight());
                if (best == null || diagnosis.getLeft().ordinal() > best.getLeft().ordinal()) {
                    best = diagnosis;
                }
            }
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.ATTACK, best.getLeft(), precondition + best.getRight(),
                    0, 0, 0L, List.of(), System.currentTimeMillis() - started);
        }

        AgentAttackTransactionResult result = null;
        for (int attempt = 0; attempt < EXEC_RETRIES; attempt++) {
            result = AgentCombatAttackRuntime.attackMonster(entry, bot, plan);
            if (result.status() != AgentAttackTransactionResult.Status.DEFERRED) {
                break;
            }
            AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
            Thread.sleep(EXEC_RETRY_SLEEP_MS);
        }
        Thread.sleep(SETTLE_MS);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        long hpAfter = mob.isAlive() ? mob.getHp() : 0L;
        long hpDelta = hpBefore - hpAfter;
        long elapsed = System.currentTimeMillis() - started;

        SkillProbeOutcome.Stage stage;
        String detail = precondition + "route=" + plan.route + " lines=" + plan.numDamage + " targets=" + plan.targets.size();
        if (result == null || result.status() == AgentAttackTransactionResult.Status.DEFERRED) {
            stage = SkillProbeOutcome.Stage.EXEC_DEFERRED;
            detail = (result == null ? "no result" : result.reason().name())
                    + " after " + EXEC_RETRIES + " retries; " + detail;
        } else if (result.status() == AgentAttackTransactionResult.Status.REJECTED) {
            stage = result.reason() == AgentAttackTransactionResult.Reason.HANDLER_REJECTED
                    ? SkillProbeOutcome.Stage.EXEC_HANDLER_REJECTED
                    : SkillProbeOutcome.Stage.EXEC_REJECTED;
            detail = result.reason().name() + "; " + detail;
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = "server flagged " + signals + "; " + detail;
        } else if (result.hitLines() == 0 && hpDelta <= 0) {
            stage = SkillProbeOutcome.Stage.EXEC_NO_DAMAGE;
            detail = "committed but no damage lines landed; " + detail;
        } else {
            stage = SkillProbeOutcome.Stage.OK;
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.ATTACK, stage, detail,
                result == null ? 0 : result.hitLines(), result == null ? 0 : result.missLines(),
                hpDelta, signals, elapsed);
    }

    /**
     * Finisher precondition: Combo Attack up and a few close-range hits landed, because the server
     * grants one orb per hit ({@code CloseRangeDamageHandler.applyCloseRangeEffects}) and the
     * planner refuses Panic/Coma below one orb. Returns a summary for the outcome detail.
     */
    private static String buildComboOrbs(AgentRuntimeEntry entry, Character bot, Monster mob) throws InterruptedException {
        int comboSkill = bot.isCygnus() ? DawnWarrior.COMBO : Crusader.COMBO;
        if (bot.getBuffedValue(BuffStat.COMBO) == null) {
            AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
            AgentCombatBuffRuntime.tryCastExplicitUtilityBuff(entry, bot, comboSkill);
            Thread.sleep(SETTLE_MS);
        }
        int warmupSkill = 0;
        for (int candidate : new int[]{Warrior.POWER_STRIKE, Warrior.SLASH_BLAST, DawnWarrior.POWER_STRIKE, DawnWarrior.SLASH_BLAST}) {
            if (bot.getSkillLevel(candidate) > 0) {
                warmupSkill = candidate;
                break;
            }
        }
        int landed = 0;
        for (int hit = 0; hit < COMBO_WARMUP_HITS && warmupSkill != 0 && mob.isAlive(); hit++) {
            AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
            AgentAttackPlan warmup = AgentSkillAttackPlanRuntime.planSkillAttack(bot, mob, warmupSkill, AgentCombatConfig.cfg);
            if (warmup != null && AgentCombatAttackRuntime.attackMonster(entry, bot, warmup).committed()) {
                landed++;
            }
            Thread.sleep(SETTLE_MS);
        }
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        Integer combo = bot.getBuffedValue(BuffStat.COMBO);
        return "combo warmup: " + landed + " hits, orbs=" + (combo == null ? "none" : String.valueOf(combo - 1));
    }

    /**
     * Dragon Roar precondition: the planner wants a party member with a heal skill within support
     * range. The operator is a GM in the Agent's party; give it Heal + Dispel and stand it beside the Agent.
     */
    private static String ensureHealerAlly(Character operator, Character bot) {
        if (operator == null) {
            return "no operator for the healer-ally rule";
        }
        Skill gmHeal = SkillFactory.getSkill(SuperGM.HEAL_PLUS_DISPEL);
        if (gmHeal != null && operator.getSkillLevel(gmHeal) <= 0) {
            operator.changeSkillLevel(gmHeal, (byte) 1, 1, -1);
        }
        if (operator.getMap() != bot.getMap() && bot.getMap() != null) {
            AgentMapGatewayRuntime.map().changeMap(operator, bot.getMap(), bot.getPosition());
        }
        operator.setPosition(new Point(bot.getPosition()));
        if (!bot.getPartyMembersOnSameMap().contains(operator)) {
            // Party.createParty refuses leaders under level 10; a fresh GM operator is level 1.
            int guard = 0;
            while (operator.getLevel() < 10 && guard++ < 10) {
                operator.levelUp(false);
            }
            // The operator's party fills up with earlier probe companions; re-form it around this Agent.
            AgentPartyGatewayRuntime.party().leaveCurrentParty(operator);
            AgentPartyLifecycleService.joinAgentToLeaderParty(operator, bot);
        }
        boolean inParty = bot.getPartyMembersOnSameMap().contains(operator);
        return "healer ally " + operator.getName() + (inParty ? " beside the Agent" : " NOT in the Agent's party");
    }

    /**
     * Everything the combat classifier ignores: passives are verified as learned, movement skills are
     * checked against the navigation policy, castable buffs the combat loop deliberately never uses
     * (Dark Sight, mounts, Oak Barrel, beginner buffs, Hero's Will) are cast directly, Resurrection
     * revives a dead operator, and whatever is left is reported as UNSUPPORTED with the reason.
     */
    private static SkillProbeOutcome probeOther(Character operator, AgentRuntimeEntry entry, Character bot, Skill skill,
                                                int level, StatEffect effect, String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        String movement = AgentMovementSkillPolicy.movementSkillSupport(bot, skill.getId());
        if (movement != null) {
            boolean eligible = movement.startsWith("eligible");
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.MOVEMENT, eligible ? SkillProbeOutcome.Stage.OK : SkillProbeOutcome.Stage.UNSUPPORTED,
                    "navigation: " + movement, 0, 0, 0L, List.of(), System.currentTimeMillis() - started);
        }
        if (skill.getId() == Bishop.RESURRECTION) {
            return probeResurrection(operator, entry, bot, skill, level, bucket);
        }
        boolean castable = effect.isOverTime() && (effect.getMpCon() > 0 || effect.getHpCon() > 0 || skill.isBeginnerSkill());
        if (castable) {
            return probeDirectBuff(entry, bot, skill, level, effect, bucket);
        }
        if (skill.getId() == DarkKnight.AURA_OF_BEHOLDER || skill.getId() == DarkKnight.HEX_OF_BEHOLDER) {
            // Not player-cast: Character.beholderBuffSchedule applies these while the Beholder is out.
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.PASSIVE, level > 0 ? SkillProbeOutcome.Stage.OK : SkillProbeOutcome.Stage.UNSUPPORTED,
                    "applied by the Beholder summon on its own schedule; learned " + level + "/" + skill.getMaxLevel(),
                    0, 0, 0L, List.of(), 0L);
        }
        if (skill.getId() == Pirate.DASH || skill.getId() == Gunslinger.WINGS) {
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.MOVEMENT, SkillProbeOutcome.Stage.UNSUPPORTED,
                    "dash/glide movement is not modelled by the Agent physics", 0, 0, 0L, List.of(), 0L);
        }
        boolean passive = !skill.getAction() && effect.getMpCon() <= 0 && effect.getHpCon() <= 0
                && (!effect.isOverTime() || effect.getStatups().isEmpty());
        if (passive) {
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.PASSIVE, level > 0 ? SkillProbeOutcome.Stage.OK : SkillProbeOutcome.Stage.UNSUPPORTED,
                    "passive, learned " + level + "/" + skill.getMaxLevel(), 0, 0, 0L, List.of(), 0L);
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.PASSIVE_OR_OTHER, SkillProbeOutcome.Stage.UNSUPPORTED,
                "no Agent runtime executes this; classifier: " + AgentCombatSkillClassifier.ignoreReason(skill, effect),
                0, 0, 0L, List.of(), 0L);
    }

    /** Casts a buff straight through the special-move gateway, bypassing the combat loop's blacklist and action-node test. */
    private static SkillProbeOutcome probeDirectBuff(AgentRuntimeEntry entry, Character bot, Skill skill, int level,
                                                     StatEffect effect, String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        waitGrounded(entry);
        if (effect.getItemConNo() > 0 && !effect.hasItemCon(bot)) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, effect.getItemCon(), (short) (effect.getItemConNo() * 5));
        }
        long eventFloor = latestSecuritySequence();
        int timestamp = AgentCombatGatewayRuntime.combat().currentTimestamp();
        boolean cast = AgentCombatGatewayRuntime.combat().dispatchSupportSpecialMove(bot, skill.getId(), level, timestamp);
        Thread.sleep(SETTLE_MS * 2);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        BuffStat applied = null;
        for (Pair<BuffStat, Integer> statup : effect.getStatups()) {
            if (bot.getBuffedValue(statup.getLeft()) != null) {
                applied = statup.getLeft();
                break;
            }
        }
        SkillProbeOutcome.Stage stage;
        String detail;
        String note = AgentCombatSkillClassifier.isBuffBlacklisted(skill.getId()) ? "blacklisted for automatic use; " : "direct cast; ";
        if (!cast) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = note + "special move not dispatched";
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = note + "server flagged " + signals;
        } else if (!effect.getStatups().isEmpty() && applied == null) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = note + "none of " + effect.getStatups().stream().map(p -> p.getLeft().name()).toList() + " is active";
        } else {
            stage = SkillProbeOutcome.Stage.OK;
            detail = note + (applied == null ? "dispatched (no stat to verify)" : "active " + applied.name());
        }
        // Mounts and morphs would otherwise stay on the Agent for the rest of the run.
        bot.cancelEffectFromBuffStat(BuffStat.MONSTER_RIDING);
        bot.cancelEffectFromBuffStat(BuffStat.MORPH);
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.BUFF, stage, detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    /** Resurrection needs a dead party member in the skill box: the operator volunteers. */
    private static SkillProbeOutcome probeResurrection(Character operator, AgentRuntimeEntry entry, Character bot,
                                                       Skill skill, int level, String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        String ally = ensureHealerAlly(operator, bot);
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        operator.addHP(-operator.getHp());
        Thread.sleep(SETTLE_MS);
        boolean dead = !operator.isAlive();
        long eventFloor = latestSecuritySequence();
        int timestamp = AgentCombatGatewayRuntime.combat().currentTimestamp();
        boolean cast = dead && AgentCombatGatewayRuntime.combat().dispatchSummonSpecialMove(bot, skill.getId(), level, timestamp);
        Thread.sleep(SETTLE_MS * 2);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        boolean revived = operator.isAlive() && operator.getHp() > 0;
        SkillProbeOutcome.Stage stage;
        String detail;
        if (!dead) {
            stage = SkillProbeOutcome.Stage.ERROR;
            detail = ally + "; could not put the operator at 0 HP";
        } else if (!cast) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = ally + "; special move not dispatched";
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = ally + "; server flagged " + signals;
        } else if (!revived) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = ally + "; operator still dead after the cast";
        } else {
            stage = SkillProbeOutcome.Stage.OK;
            detail = ally + "; operator revived at " + operator.getHp() + " HP";
        }
        if (!operator.isAlive()) {
            operator.healHpMp();
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.UTILITY, stage, detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    /** Energy attack precondition: a full Energy Charge bar (each hit adds 102; full parks at 15000). */
    private static String fillEnergyCharge(Character bot) {
        int gains = 0;
        while (bot.getEnergyBar() < 15000 && gains++ < 120) {
            bot.handleEnergyChargeGain();
        }
        return "energy bar " + bot.getEnergyBar() + " after " + gains + " gains";
    }

    private static SkillProbeOutcome probeUtility(AgentRuntimeEntry entry, Character bot, Skill skill, int level,
                                                  String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        waitGrounded(entry);
        boolean chakra = skill.getId() == ChiefBandit.CHAKRA;
        boolean mpRecovery = skill.getId() == Brawler.MP_RECOVERY;
        if (chakra) {
            bot.addHP(-(bot.getCurrentMaxHp() / 2));
        }
        if (mpRecovery) {
            bot.addMP(-(bot.getCurrentMaxMp() / 2));
        }
        int hpBefore = bot.getHp();
        int mpBefore = bot.getMp();
        long eventFloor = latestSecuritySequence();
        AgentCombatDebuffRuntime.DebuffResult result = AgentCombatDebuffRuntime.tryDebuff(entry, bot, skill.getId());
        Thread.sleep(SETTLE_MS * 2);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        SkillProbeOutcome.Stage stage;
        String detail;
        if (result != AgentCombatDebuffRuntime.DebuffResult.CAST) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = "special move not dispatched: " + result;
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = "server flagged " + signals;
        } else if (chakra && bot.getHp() <= hpBefore) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = "Chakra dispatched but hp " + hpBefore + " -> " + bot.getHp();
        } else if (mpRecovery && bot.getMp() <= mpBefore) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
            detail = "MP Recovery dispatched but mp " + mpBefore + " -> " + bot.getMp();
        } else {
            stage = SkillProbeOutcome.Stage.OK;
            detail = chakra ? "hp " + hpBefore + " -> " + bot.getHp()
                    : mpRecovery ? "mp " + mpBefore + " -> " + bot.getMp() : "dispatched";
        }
        bot.healHpMp();
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.UTILITY, stage, detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    /** Meso Explosion precondition: the Agent's own meso drops around the target. */
    private static String dropMesosAround(Character bot, MapleMap map, Monster mob) throws InterruptedException {
        if (bot.getMeso() < MESO_DROP_COUNT * MESO_DROP_AMOUNT) {
            bot.gainMeso(MESO_DROP_COUNT * MESO_DROP_AMOUNT, false);
        }
        for (int i = 0; i < MESO_DROP_COUNT; i++) {
            bot.gainMeso(-MESO_DROP_AMOUNT, false);
            map.spawnMesoDrop(MESO_DROP_AMOUNT, new Point(mob.getPosition().x + (i - 2) * 10, mob.getPosition().y),
                    bot, bot, true, (byte) 2, (short) 0);
        }
        Thread.sleep(SETTLE_MS);
        return "dropped " + MESO_DROP_COUNT + "x" + MESO_DROP_AMOUNT + " mesos";
    }

    /** Casts and plans refuse an airborne Agent; give the physics up to two seconds to land. */
    private static void waitGrounded(AgentRuntimeEntry entry) throws InterruptedException {
        for (int i = 0; i < 20 && !AgentMovementStateRuntime.grounded(entry); i++) {
            Thread.sleep(100L);
        }
    }

    /** Offense that the classifier drops only because it carries no MP/HP cost (not a WZ passive). */
    static boolean isUnclassifiedOffense(Skill skill, StatEffect effect) {
        return skill.getAction()
                && AgentCombatSkillClassifier.declaresOffense(effect)
                && !effect.isOverTime()
                && skill.getSkillType() != 1 && skill.getSkillType() != 3
                && effect.getMpCon() <= 0 && effect.getHpCon() <= 0
                && !skill.isBeginnerSkill();
    }

    /**
     * Replays the planner's gates one by one so a {@code null} plan gets a named stage. Mirrors
     * {@link AgentSkillAttackPlanRuntime#planSkillAttack}; keep the order in sync.
     */
    static Pair<SkillProbeOutcome.Stage, String> diagnosePlanFailure(Character bot, Monster mob, Skill skill,
                                                                     int level, StatEffect effect) {
        int skillId = skill.getId();
        if (!AgentComboFinisherPolicy.canPlan(skillId, bot.getBuffedValue(BuffStat.COMBO))) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_COMBO_ORBS_REQUIRED, "combo finisher without combo orbs");
        }
        if (bot.skillIsCooling(skillId)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_SKILL_COOLDOWN, "skill cooldown active");
        }
        if (!effect.canPaySkillCost(bot)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_CANNOT_PAY_COST, "mpCon=" + effect.getMpCon()
                    + " hpCon=" + effect.getHpCon() + " bot mp=" + bot.getMp() + " hp=" + bot.getHp());
        }
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!AgentCombatWeaponPolicy.canUseAttackSkillWithWeapon(skillId, weaponType)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_WEAPON_INCOMPATIBLE, "equipped=" + weaponType);
        }
        AgentAttackRoute route = AgentAttackExecutionProvider.determineSkillRoute(bot, skillId);
        int ammoCost = Math.max(effect.getBulletCount(), effect.getBulletConsume())
                * Math.max(1, AgentCombatHitCounter.shadowPartnerHitMultiplier(bot, route));
        if (ammoCost > 0 && route == AgentAttackRoute.RANGED
                && AgentCombatAmmoCounter.countAmmo(bot, weaponType) < ammoCost) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_INSUFFICIENT_AMMO, "need " + ammoCost);
        }
        String action = AgentAttackExecutionProvider.resolveSkillAttackAction(bot, skill, level, weaponType);
        Rectangle hitBox = AgentCombatSkillHitboxPolicy.calculateSkillHitBox(effect, bot, mob, route, skillId, action);
        if (hitBox == null) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_NO_HITBOX, "action=" + action + " route=" + route);
        }
        if (!AgentCombatHitboxIntersection.intersectsMonster(hitBox, mob)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_TARGET_UNREACHABLE,
                    "hitbox " + hitBox + " misses mob at " + mob.getPosition() + " (bot at " + bot.getPosition() + ")");
        }
        Point aim = AgentCombatAimPointPolicy.aimPoint(bot, mob);
        if (!AgentAttackExecutionProvider.canUseRangedAttackRoute(route, weaponType, bot.getPosition(), aim)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_RANGED_ROUTE_BLOCKED, "route=" + route + " weapon=" + weaponType
                    + " aim=" + aim + " within degenerate range of " + bot.getPosition());
        }
        if (skillId == DragonKnight.DRAGON_ROAR) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_REJECTED_UNKNOWN, "Dragon Roar needs a nearby healer ally (AgentCombatSupportPolicy)");
        }
        if (!AgentCombatSkillUsePolicy.canPaySkillCost(bot, skillId, level)) {
            return new Pair<>(SkillProbeOutcome.Stage.PLAN_CANNOT_PAY_COST, "AgentCombatSkillUsePolicy refused");
        }
        return new Pair<>(SkillProbeOutcome.Stage.PLAN_REJECTED_UNKNOWN,
                "planner gate '" + AgentSkillAttackPlanRuntime.lastRejection() + "' (route=" + route
                        + " action=" + action + " hitbox=" + hitBox + " mob=" + mob.getPosition() + ")");
    }

    private static SkillProbeOutcome probeBuff(AgentRuntimeEntry entry, Character bot, Skill skill, int level,
                                               StatEffect effect, String bucket) throws InterruptedException {
        String name = SkillFactory.getSkillName(skill.getId());
        long started = System.currentTimeMillis();
        WeaponType weaponType = AgentAttackExecutionProvider.getEquippedWeaponType(bot);
        if (!AgentCombatWeaponPolicy.canUseSkillWithWeapon(skill.getId(), weaponType)) {
            return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                    SkillProbeOutcome.Kind.BUFF, SkillProbeOutcome.Stage.PLAN_WEAPON_INCOMPATIBLE,
                    "equipped=" + weaponType, 0, 0, 0L, List.of(), 0L);
        }
        bot.healHpMp();
        AgentCombatCooldownStateRuntime.clearAttackCooldown(entry);
        String provisioned = "";
        if (effect.getItemConNo() > 0 && !effect.hasItemCon(bot)) {
            // Same footing as ammo: the probe measures the cast path, not the Agent's shopping.
            AgentInventoryGatewayRuntime.inventory().addItem(bot, effect.getItemCon(), (short) (effect.getItemConNo() * 5));
            provisioned = "provisioned itemCon " + effect.getItemCon() + "; ";
        }
        int ammoId = defaultAmmoFor(AgentAttackExecutionProvider.getEquippedWeaponType(bot));
        if (effect.getBulletConsume() > 0 && ammoId > 0
                && AgentCombatAmmoCounter.largestAmmoStack(bot, AgentAttackExecutionProvider.getEquippedWeaponType(bot)) < effect.getBulletConsume()) {
            AgentInventoryGatewayRuntime.inventory().addItem(bot, ammoId, (short) 500);
            provisioned += "provisioned " + ammoId + " for bulletConsume " + effect.getBulletConsume() + "; ";
        }
        long eventFloor = latestSecuritySequence();
        boolean cast = AgentCombatBuffRuntime.tryCastExplicitUtilityBuff(entry, bot, skill.getId());
        Thread.sleep(SETTLE_MS * 4);
        List<String> signals = autobanSignalsSince(bot.getId(), eventFloor);
        SkillProbeOutcome.Stage stage;
        String detail;
        if (!cast) {
            stage = SkillProbeOutcome.Stage.BUFF_NOT_CAST;
            detail = "tryCastExplicitUtilityBuff returned false (cooldown, cost, air/climb, or not a support skill)";
        } else if (!signals.isEmpty()) {
            stage = SkillProbeOutcome.Stage.EXEC_AUTOBAN_SIGNAL;
            detail = "server flagged " + signals;
        } else {
            BuffStat applied = null;
            for (Pair<BuffStat, Integer> statup : effect.getStatups()) {
                if (bot.getBuffedValue(statup.getLeft()) != null) {
                    applied = statup.getLeft();
                    break;
                }
            }
            if (applied == null) {
                stage = SkillProbeOutcome.Stage.BUFF_NOT_APPLIED;
                detail = "cast reported but none of "
                        + effect.getStatups().stream().map(p -> p.getLeft().name()).toList() + " is active"
                        + " (buffEffect=" + effect.getStatups().stream()
                        .map(p -> p.getLeft().name() + "=" + (bot.getBuffEffect(p.getLeft()) == null ? "none" : "present")).toList()
                        + " mp=" + bot.getMp() + "/" + bot.getMaxMp() + " cost=" + effect.getMpCon() + ")";
            } else {
                stage = SkillProbeOutcome.Stage.OK;
                detail = "active " + applied.name();
            }
        }
        return new SkillProbeOutcome(skill.getId(), name, level, skill.getMaxLevel(), bucket,
                SkillProbeOutcome.Kind.BUFF, stage, provisioned + detail, 0, 0, 0L, signals, System.currentTimeMillis() - started);
    }

    private static Monster spawnProbeMob(MapleMap map, Character bot, int mobId, Set<Integer> probeMobOids, int side) {
        return spawnProbeMobAt(map, bot, mobId, probeMobOids, side * MOB_OFFSET_X);
    }

    private static Monster spawnProbeMobAt(MapleMap map, Character bot, int mobId, Set<Integer> probeMobOids, int offsetX) {
        Monster mob = LifeFactory.getMonster(mobId);
        if (mob == null || map == null || bot.getPosition() == null) {
            return null;
        }
        Point at = new Point(bot.getPosition().x + offsetX, bot.getPosition().y);
        map.spawnMonsterOnGroundBelow(mob, at);
        probeMobOids.add(mob.getObjectId());
        return mob;
    }

    private static void clearProbeMobs(MapleMap map, Character bot, Set<Integer> probeMobOids) {
        if (map == null) {
            return;
        }
        for (int oid : probeMobOids) {
            Monster mob = map.getMonsterByOid(oid);
            if (mob != null && mob.isAlive()) {
                map.killMonster(mob, bot, false, (short) 0);
            }
        }
        probeMobOids.clear();
    }

    private static long latestSecuritySequence() {
        List<SecurityEvent> events = SecurityEventRuntime.snapshot();
        return events.isEmpty() ? 0L : events.get(events.size() - 1).sequence();
    }

    private static List<String> autobanSignalsSince(int characterId, long floorSequence) {
        List<String> signals = new ArrayList<>();
        for (SecurityEvent event : SecurityEventRuntime.snapshot()) {
            if (event.sequence() <= floorSequence || event.type() != SecurityEventType.AUTOBAN_SIGNAL
                    || event.characterId() != characterId) {
                continue;
            }
            Map<String, String> evidence = event.evidence();
            signals.add(evidence.getOrDefault("signal", "?")
                    + (evidence.containsKey("action") ? ":" + evidence.get("action") : ""));
        }
        return signals;
    }
}
