package server.partner;

import client.BuffStat;
import client.Character;
import client.Client;
import client.Job;
import client.Skill;
import client.SkillFactory;
import config.AdventurerPartnerConfig;
import config.GenesisVisualProxyConfig;
import config.PartnerMedalEffectConditionConfig;
import config.PartnerMedalEffectConfig;
import config.PartnerMedalEffectLevelConfig;
import config.YamlConfig;
import constants.game.CharacterStance;
import constants.inventory.ItemConstants;
import constants.skills.Bishop;
import net.packet.Packet;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackInfo;
import net.server.channel.handlers.AbstractDealDamageHandler.AttackTarget;
import server.StatEffect;
import server.TimerManager;
import server.combat.CombatFormulaProvider;
import server.life.Monster;
import server.maps.MapleMap;
import server.maps.MapObject;
import server.maps.MapObjectType;
import tools.PacketCreator;
import tools.Pair;
import tools.Randomizer;

import java.awt.Point;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Resolves live, equipment-gated Partner medal levels and applies their server-side effects. */
public final class PartnerMedalEffectService {
    private static final int GENESIS_BODY_ACTION_ID = 69;
    private static final long GENESIS_DAMAGE_DELAY_MS = 1_800L;
    // Character IDs are database IDs and map object IDs begin above one billion.
    // A descending process-wide range prevents delayed cleanup from colliding
    // with a newer proxy after a map transition.
    private static final AtomicInteger NEXT_GENESIS_VISUAL_PROXY_ID =
            new AtomicInteger(900_000_000);

    public static final PartnerMedalEffectService INSTANCE = new PartnerMedalEffectService(
            YamlConfig.config.adventurerPartner, PartnerRuntimeRegistry.global());

    static final String SWITCH_SKILL = "SWITCH_SKILL";
    static final String SELF_BUFF_BOND = "SELF_BUFF_BOND";
    static final String MESO_FAME_BONUS = "MESO_FAME_BONUS";
    static final String ETC_FAME_EXTRA_ROLL = "ETC_FAME_EXTRA_ROLL";
    static final String DROP_RATE_BONUS = "DROP_RATE_BONUS";
    static final String EXP_BONUS = "EXP_BONUS";
    static final String REGULAR_MOB_BONUS_DAMAGE = "REGULAR_MOB_BONUS_DAMAGE";

    private final AdventurerPartnerConfig config;
    private final PartnerRuntimeRegistry runtimes;
    private final Map<SwitchEffectKey, Long> nextSwitchEffectAt = new ConcurrentHashMap<>();

    PartnerMedalEffectService(AdventurerPartnerConfig config, PartnerRuntimeRegistry runtimes) {
        this.config = config;
        this.runtimes = runtimes;
    }

    public boolean selfBuffSharingEnabled(PartnerMode mode) {
        return config.MEDAL_EFFECTS.stream().anyMatch(effect ->
                SELF_BUFF_BOND.equals(effect.EFFECT) && enabledInMode(effect, mode));
    }

    public int selfBuffSkillCap(Character recipient, Character partner, PartnerMode mode) {
        return resolvedEffects(SELF_BUFF_BOND, recipient, partner, mode, true).stream()
                .mapToInt(effect -> effect.level().MAX_SKILL_LEVEL)
                .max()
                .orElse(0);
    }

    /** Maximum safe client preload for the current pair; actual receipt still requires the equipped item. */
    public int configuredSelfBuffSkillCap(Character recipient, Character partner, PartnerMode mode) {
        return resolvedEffects(SELF_BUFF_BOND, recipient, partner, mode, false).stream()
                .mapToInt(effect -> effect.level().MAX_SKILL_LEVEL)
                .max()
                .orElse(0);
    }

    public int selfBuffBondItemId() {
        return config.MEDAL_EFFECTS.stream()
                .filter(effect -> SELF_BUFF_BOND.equals(effect.EFFECT))
                .mapToInt(effect -> effect.ITEM_ID)
                .findFirst()
                .orElse(0);
    }

    public boolean ownsSelfBuffBond(Character character) {
        int itemId = selfBuffBondItemId();
        return itemId > 0 && character.haveItemWithId(itemId, true);
    }

    public boolean hasActiveSelfBuffBond(Character character) {
        int itemId = selfBuffBondItemId();
        return itemId > 0 && hasRequiredItem(character, itemId);
    }

    public String selfBuffBondStatus(Character character) {
        int itemId = selfBuffBondItemId();
        if (itemId <= 0) {
            return "Disabled";
        }
        if (hasRequiredItem(character, itemId)) {
            return "#bActive#k";
        }
        if (character.haveItemWithId(itemId, true) && ItemConstants.isEquipment(itemId)) {
            return "#rInactive - equip #t" + itemId + "##k";
        }
        return "#dNot owned#k";
    }

    public double dropRateMultiplier(Character holder) {
        return 1.0 + flatPercent(DROP_RATE_BONUS, holder) / 100.0;
    }

    public double expMultiplier(Character holder) {
        return 1.0 + flatPercent(EXP_BONUS, holder) / 100.0;
    }

    public double mesoMultiplier(Character holder) {
        EffectContext context = activeContext(holder).orElse(null);
        if (context == null || holder.getFame() <= 0) {
            return 1.0;
        }
        double percent = resolvedEffects(
                MESO_FAME_BONUS, holder, context.partner(), context.mode(), true).stream()
                .mapToDouble(effect -> famePercent(holder.getFame(), effect.level()))
                .sum();
        return 1.0 + percent / 100.0;
    }

    public double extraEtcDropChance(Character holder) {
        EffectContext context = activeContext(holder).orElse(null);
        if (context == null || holder.getFame() >= 0) {
            return 0.0;
        }
        double chance = resolvedEffects(
                ETC_FAME_EXTRA_ROLL, holder, context.partner(), context.mode(), true).stream()
                .mapToDouble(effect -> famePercent(Math.abs(holder.getFame()), effect.level()))
                .sum() / 100.0;
        return Math.min(1.0, chance);
    }

    public int regularMobBonusDamage(Character holder, int baseDamage) {
        if (baseDamage <= 0) {
            return 0;
        }
        double percent = flatPercent(REGULAR_MOB_BONUS_DAMAGE, holder);
        return clampPositiveDamage(baseDamage * percent / 100.0);
    }

    /** Records the medal proc without altering the client's canonical attack packet. */
    public boolean prepareRegularMobBonusDamage(Character holder, AttackInfo attack) {
        if (holder == null || holder.getMap() == null || attack == null
                || attack.targets == null || attack.targets.isEmpty()) {
            return false;
        }

        Map<Integer, Integer> bonusByTarget = new LinkedHashMap<>();
        boolean hasBonus = false;
        for (Map.Entry<Integer, AttackTarget> entry : attack.targets.entrySet()) {
            Monster monster = holder.getMap().getMonsterByOid(entry.getKey());
            int bonus = 0;
            if (monster != null && !monster.isBoss()) {
                long baseDamage = entry.getValue().damageLines().stream()
                        .mapToLong(PartnerMedalEffectService::decodeDamageLine)
                        .sum();
                bonus = regularMobBonusDamage(
                        holder, (int) Math.min(Integer.MAX_VALUE, baseDamage));
            }
            bonusByTarget.put(entry.getKey(), bonus);
            hasBonus |= bonus > 0;
        }
        if (!hasBonus) {
            return false;
        }
        attack.partnerBonusDamageByTarget = Map.copyOf(bonusByTarget);
        return true;
    }

    /** Applies and presents a Monster Expert proc after the configured delay. */
    public void applyRegularMobBonusDamage(Character attacker,
                                           Map<Integer, Integer> bonusByTarget) {
        if (attacker == null || attacker.getMap() == null || bonusByTarget == null
                || bonusByTarget.values().stream().noneMatch(damage -> damage != null && damage > 0)) {
            return;
        }
        MapleMap expectedMap = attacker.getMap();
        int expectedProfileOwnerId = attacker.getProfileOwnerCharacterId();
        Runnable proc = () -> {
            if (attacker.getMap() != expectedMap
                    || attacker.getProfileOwnerCharacterId() != expectedProfileOwnerId) {
                return;
            }
            Map<Integer, Integer> liveDamage = new LinkedHashMap<>();
            for (Map.Entry<Integer, Integer> entry : bonusByTarget.entrySet()) {
                if (liveDamage.size() >= 15 || entry.getValue() == null || entry.getValue() <= 0) {
                    continue;
                }
                Monster monster = expectedMap.getMonsterByOid(entry.getKey());
                if (monster == null || !monster.isAlive() || monster.isBoss()) {
                    continue;
                }
                liveDamage.put(entry.getKey(), entry.getValue());
            }
            if (liveDamage.isEmpty()) {
                return;
            }
            for (Map.Entry<Integer, Integer> target : liveDamage.entrySet()) {
                Monster monster = expectedMap.getMonsterByOid(target.getKey());
                if (monster != null && monster.isAlive() && !monster.isBoss()) {
                    // DAMAGE_MONSTER is the stock v83 presentation for delayed,
                    // server-originated regular damage and does not replay an
                    // extra attack animation from the character.
                    expectedMap.broadcastMessage(
                            PacketCreator.damageMonster(monster.getObjectId(), target.getValue()),
                            monster.getPosition());
                    expectedMap.damageMonster(attacker, monster, target.getValue());
                }
            }
        };
        scheduleBonusDamage(proc);
    }

    /** Applies switch skills only to the profile that has just entered the player-controlled actor. */
    public void applySwitchInEffects(ActivePartnerSession active) {
        if (active == null || active.runtime().status() != PartnerLifecycleStatus.ACTIVE
                || active.isJournalClosed()) {
            return;
        }
        Character holder = active.humanActor();
        Character partner = active.partnerActorOrDormantProfile();
        for (ResolvedEffect effect : resolvedEffects(
                SWITCH_SKILL, holder, partner, active.runtime().mode(), true)) {
            SwitchEffectKey key = new SwitchEffectKey(
                    active.runtime().sessionId(), holder.getProfileOwnerCharacterId(),
                    effect.config().ITEM_ID, effect.level().SKILL_ID);
            long now = System.currentTimeMillis();
            if (now < nextSwitchEffectAt.getOrDefault(key, 0L)) {
                continue;
            }
            if (castConfiguredSwitchSkill(holder, effect.level())) {
                nextSwitchEffectAt.put(key, saturatedAdd(now, effect.level().COOLDOWN_MS));
            }
        }
    }

    public void clearSession(long sessionId) {
        nextSwitchEffectAt.keySet().removeIf(key -> key.sessionId() == sessionId);
    }

    private double flatPercent(String type, Character holder) {
        EffectContext context = activeContext(holder).orElse(null);
        if (context == null) {
            return 0.0;
        }
        return resolvedEffects(type, holder, context.partner(), context.mode(), true).stream()
                .mapToDouble(effect -> effect.level().PERCENT)
                .sum();
    }

    private Optional<EffectContext> activeContext(Character holder) {
        if (!config.ENABLED || holder == null) {
            return Optional.empty();
        }
        ActivePartnerSession active = runtimes.findByAnyActorId(holder.getId()).orElse(null);
        if (active == null || active.runtime().status() != PartnerLifecycleStatus.ACTIVE
                || active.isJournalClosed()) {
            return Optional.empty();
        }
        Character partner;
        if (holder == active.humanActor()) {
            partner = active.partnerActorOrDormantProfile();
        } else if (holder == active.partnerActorOrDormantProfile()) {
            partner = active.humanActor();
        } else {
            return Optional.empty();
        }
        return Optional.of(new EffectContext(active.runtime().mode(), partner));
    }

    private List<ResolvedEffect> resolvedEffects(String type,
                                                 Character holder,
                                                 Character partner,
                                                 PartnerMode mode,
                                                 boolean requireItem) {
        if (holder == null || partner == null) {
            return List.of();
        }
        List<ResolvedEffect> resolved = new ArrayList<>();
        for (PartnerMedalEffectConfig effect : config.MEDAL_EFFECTS) {
            if (!type.equals(effect.EFFECT) || !enabledInMode(effect, mode)
                    || (requireItem && !hasRequiredItem(holder, effect.ITEM_ID))) {
                continue;
            }
            PartnerMedalEffectLevelConfig selected = null;
            for (PartnerMedalEffectLevelConfig level : effect.LEVELS) {
                if (matches(level.CONDITIONS, holder, partner)) {
                    selected = level;
                }
            }
            if (selected != null) {
                resolved.add(new ResolvedEffect(effect, selected));
            }
        }
        return resolved;
    }

    private static boolean enabledInMode(PartnerMedalEffectConfig effect, PartnerMode mode) {
        return mode == PartnerMode.SOLO_TAG
                ? effect.SOLO_TAG_ENABLED : effect.DOUBLE_PARTNER_ENABLED;
    }

    private static boolean matches(PartnerMedalEffectConditionConfig conditions,
                                   Character holder,
                                   Character partner) {
        return partner.getLevel() >= conditions.MIN_PARTNER_LEVEL
                && partner.getLevel() <= conditions.MAX_PARTNER_LEVEL
                && holder.getLevel() >= conditions.MIN_CHARACTER_LEVEL
                && holder.getLevel() <= conditions.MAX_CHARACTER_LEVEL
                && holder.getFame() >= conditions.MIN_FAME
                && holder.getFame() <= conditions.MAX_FAME;
    }

    private static boolean hasRequiredItem(Character holder, int itemId) {
        return ItemConstants.isEquipment(itemId)
                ? holder.haveItemEquipped(itemId)
                : holder.haveItemWithId(itemId, false);
    }

    private static double famePercent(int fame, PartnerMedalEffectLevelConfig level) {
        if (level.FAME_PER_PERCENT <= 0) {
            return 0.0;
        }
        return Math.min(level.MAX_PERCENT, fame / level.FAME_PER_PERCENT);
    }

    private static int clampPositiveDamage(double damage) {
        if (damage <= 0.0) {
            return 0;
        }
        return damage >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) Math.round(damage));
    }

    private static long saturatedAdd(long value, long amount) {
        return amount > Long.MAX_VALUE - value ? Long.MAX_VALUE : value + amount;
    }

    private boolean castConfiguredSwitchSkill(Character caster,
                                              PartnerMedalEffectLevelConfig configured) {
        // The initial medal set deliberately supports Genesis only. Other attack families
        // require their own packet and damage formula and must not be guessed from an ID.
        if (configured.SKILL_ID != Bishop.GENESIS || caster.getMap() == null) {
            return false;
        }
        Skill skill = SkillFactory.getSkill(configured.SKILL_ID);
        if (skill == null || configured.SKILL_LEVEL > skill.getMaxLevel()) {
            return false;
        }
        StatEffect effect = skill.getEffect(configured.SKILL_LEVEL);
        List<MapObject> candidates = effect.hasBoundingBox()
                ? caster.getMap().getMapObjectsInRect(
                effect.calculateBoundingBox(caster.getPosition(), caster.isFacingLeft()),
                List.of(MapObjectType.MONSTER))
                : caster.getMap().getMonsters();
        Map<Integer, AttackTarget> targets = new LinkedHashMap<>();
        int mobLimit = Math.max(1, effect.getMobCount());
        int attackCount = Math.max(1, effect.getAttackCount());
        int maxDamage = Math.max(1, clampPositiveDamage(
                CombatFormulaProvider.getInstance().magicDamageBase(
                        caster.getTotalMagic(), caster.getTotalInt()) * (double) effect.getMatk()));
        for (MapObject candidate : candidates) {
            Monster monster = (Monster) candidate;
            if (monster.getHp() <= 0 || targets.size() >= mobLimit) {
                continue;
            }
            List<Integer> lines = new ArrayList<>(attackCount);
            for (int i = 0; i < attackCount; i++) {
                lines.add(Randomizer.rand(Math.max(1, maxDamage * 8 / 10), maxDamage));
            }
            targets.put(monster.getObjectId(), new AttackTarget((short) 0, List.copyOf(lines)));
        }
        int encodedCount = (targets.size() << 4) | Math.min(15, attackCount);
        // A server-triggered switch skill has no originating client cast. Observers
        // accept the real caster's MAGIC_ATTACK, while the controlling v83 client
        // needs a nearby foreign actor to render the full target animation. The
        // proxy has its own packet-only appearance, so the real character is never
        // mutated and does not replay the same animation locally.
        MapleMap expectedMap = caster.getMap();
        int expectedProfileOwnerId = caster.getProfileOwnerCharacterId();
        if (!targets.isEmpty()) {
            int visualCasterId = NEXT_GENESIS_VISUAL_PROXY_ID.getAndDecrement();
            Point visualCasterPosition = genesisVisualProxyPosition(caster.getPosition());
            Client visualClient = caster.getClient();
            GenesisVisualProxyConfig visualConfig = config.GENESIS_VISUAL_PROXY;
            PacketCreator.VisualProxyAppearance visualAppearance =
                    genesisVisualProxyAppearance(visualConfig);
            visualClient.sendPacket(PacketCreator.spawnPlayerVisualProxy(
                    visualClient, caster, visualCasterId, visualCasterPosition,
                    Job.BISHOP.getId(), visualAppearance));
            if (!visualConfig.TRANSFORM_EFFECT_PATH.isBlank()) {
                visualClient.sendPacket(PacketCreator.showForeignInfo(
                        visualCasterId, visualConfig.TRANSFORM_EFFECT_PATH));
            }
            if (visualConfig.DARKSIGHT_ENABLED) {
                visualClient.sendPacket(PacketCreator.giveForeignBuff(
                        visualCasterId, List.of(new Pair<>(BuffStat.DARKSIGHT, 0))));
            }
            TimerManager.getInstance().schedule(() -> {
                if (!visualProxyStillRelevant(
                        caster, expectedMap, expectedProfileOwnerId, visualClient)) {
                    return;
                }
                Packet observerAttack = genesisAttackPacket(
                        caster, configured.SKILL_LEVEL, encodedCount, targets);
                expectedMap.broadcastMessage(caster, observerAttack, false, true);
                visualClient.sendPacket(genesisAttackPacket(
                        visualCasterId, caster, configured.SKILL_LEVEL, encodedCount, targets,
                        CharacterStance.isFacingLeft(visualAppearance.stance())));
            }, visualConfig.ATTACK_DELAY_MS);
            if (!visualConfig.TRANSFORM_EFFECT_PATH.isBlank()
                    && visualConfig.EXIT_EFFECT_LEAD_MS > 0L) {
                TimerManager.getInstance().schedule(() -> {
                    if (visualProxyStillRelevant(
                            caster, expectedMap, expectedProfileOwnerId, visualClient)) {
                        visualClient.sendPacket(PacketCreator.showForeignInfo(
                                visualCasterId, visualConfig.TRANSFORM_EFFECT_PATH));
                    }
                }, visualConfig.LIFETIME_MS - visualConfig.EXIT_EFFECT_LEAD_MS);
            }
            TimerManager.getInstance().schedule(
                    () -> visualClient.sendPacket(PacketCreator.removePlayerFromMap(visualCasterId)),
                    visualConfig.LIFETIME_MS);
        } else {
            expectedMap.broadcastMessage(caster, genesisAttackPacket(
                    caster, configured.SKILL_LEVEL, encodedCount, targets), false, true);
        }
        TimerManager.getInstance().schedule(() -> {
            if (caster.getMap() != expectedMap
                    || caster.getProfileOwnerCharacterId() != expectedProfileOwnerId) {
                return;
            }
            for (Map.Entry<Integer, AttackTarget> target : targets.entrySet()) {
                Monster monster = expectedMap.getMonsterByOid(target.getKey());
                if (monster == null || !monster.isAlive()) {
                    continue;
                }
                long summedDamage = target.getValue().damageLines().stream()
                        .mapToLong(Integer::longValue)
                        .sum();
                int damage = (int) Math.min(Integer.MAX_VALUE, summedDamage);
                expectedMap.damageMonster(caster, monster, damage);
            }
        }, config.GENESIS_VISUAL_PROXY.ATTACK_DELAY_MS + GENESIS_DAMAGE_DELAY_MS);
        return true;
    }

    private void scheduleBonusDamage(Runnable task) {
        if (config.MEDAL_DAMAGE_DELAY_MS <= 0L) {
            task.run();
        } else {
            TimerManager.getInstance().schedule(task, config.MEDAL_DAMAGE_DELAY_MS);
        }
    }

    static Packet genesisAttackPacket(Character caster, int skillLevel, int encodedCount,
                                       Map<Integer, AttackTarget> targets) {
        return genesisAttackPacket(caster.getId(), caster, skillLevel, encodedCount, targets);
    }

    static Packet genesisAttackPacket(int visualCasterId, Character caster, int skillLevel,
                                       int encodedCount, Map<Integer, AttackTarget> targets) {
        return genesisAttackPacket(
                visualCasterId, caster, skillLevel, encodedCount, targets, caster.isFacingLeft());
    }

    private static Packet genesisAttackPacket(int visualCasterId, Character caster, int skillLevel,
                                               int encodedCount, Map<Integer, AttackTarget> targets,
                                               boolean facingLeft) {
        int facingStance = facingLeft ? 0x80 : 0;
        return PacketCreator.magicAttackFromVisualProxy(
                visualCasterId, Bishop.GENESIS, skillLevel, facingStance,
                encodedCount, targets, -1, 4, GENESIS_BODY_ACTION_ID, 0);
    }

    static PacketCreator.VisualProxyAppearance genesisVisualProxyAppearance(
            GenesisVisualProxyConfig visualConfig) {
        return new PacketCreator.VisualProxyAppearance(
                visualConfig.NAME,
                visualConfig.GENDER,
                visualConfig.SKIN_COLOR_ID,
                visualConfig.FACE_ID,
                visualConfig.HAIR_ID,
                visualConfig.resolvedVisibleEquips(),
                visualConfig.CASH_WEAPON_ID,
                visualConfig.STANCE);
    }

    private static boolean visualProxyStillRelevant(Character caster, MapleMap expectedMap,
                                                     int expectedProfileOwnerId,
                                                     Client visualClient) {
        return caster.getMap() == expectedMap
                && caster.getProfileOwnerCharacterId() == expectedProfileOwnerId
                && caster.getClient() == visualClient;
    }

    static Point genesisVisualProxyPosition(Point casterPosition) {
        int x = casterPosition == null
                ? 0 : Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, casterPosition.x));
        int y = casterPosition == null
                ? 0 : Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, casterPosition.y));
        return new Point(x, y);
    }

    private static long decodeDamageLine(int encodedDamage) {
        return encodedDamage < 0 ? encodedDamage & Integer.MAX_VALUE : encodedDamage;
    }

    private record EffectContext(PartnerMode mode, Character partner) {
    }

    private record ResolvedEffect(PartnerMedalEffectConfig config,
                                  PartnerMedalEffectLevelConfig level) {
    }

    private record SwitchEffectKey(long sessionId, int profileOwnerId, int itemId, int skillId) {
    }
}
