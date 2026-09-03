package server.combat;

import client.BotClient;
import client.BuffStat;
import client.Character;
import client.Skill;
import client.SkillFactory;
import config.YamlConfig;
import constants.id.MapId;
import constants.skills.Aran;
import server.StatEffect;
import server.agents.capabilities.combat.data.AgentDefenseDataProvider;
import server.life.MobAttackInfo;
import server.life.MobAttackInfoFactory;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.life.MobSkillType;
import server.life.Monster;
import server.life.autonomy.BossAction;
import server.maps.MapleMap;
import tools.PacketCreator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Applies an autonomous ordinary mob hit through the existing character defenses. */
public final class ServerMobDamageService {
    private ServerMobDamageService() {
    }

    public static int applyOrdinaryAttack(
            Monster attacker, Character target, int attackIndex, boolean magic) {
        return applyOrdinaryAttack(attacker, target, attackIndex, magic,
                0, 0, false, 0, 0);
    }

    public static int applyOrdinaryAttack(
            Monster attacker, Character target, BossAction.OrdinaryAttack attack) {
        return applyOrdinaryAttack(attacker, target, attack.attackIndex(), attack.magic(),
                attack.physicalAttack(), attack.magicAttack(), attack.deadly(),
                attack.diseaseSkill(), attack.diseaseLevel());
    }

    private static int applyOrdinaryAttack(
            Monster attacker, Character target, int attackIndex, boolean magic,
            int physicalAttackOverride, int magicAttackOverride,
            boolean deadlyOverride, int diseaseSkillOverride,
            int diseaseLevelOverride) {
        if (attacker == null || target == null || !attacker.isAlive() || !target.isAlive()) {
            return 0;
        }
        MapleMap map = attacker.getMap();
        if (map == null || target.getMap() != map
                || (target.isChangingMaps() && !(target.getClient() instanceof BotClient))
                || target.isHidden()) {
            return 0;
        }

        AgentDefenseDataProvider defense = AgentDefenseDataProvider.getInstance();
        int damage = magic
                ? defense.rollMagicAttackDamage(target, attacker,
                        magicAttackOverride > 0 ? magicAttackOverride : attacker.getMADamage())
                : defense.rollPhysicalTouchDamage(target, attacker,
                        physicalAttackOverride > 0
                                ? physicalAttackOverride : attacker.getPADamage());
        int mpAttack = 0;
        boolean deadly = deadlyOverride;
        int diseaseSkillId = diseaseSkillOverride;
        int diseaseLevel = diseaseLevelOverride;
        List<Character> banishPlayers = new ArrayList<>();
        MobAttackInfo attackInfo = MobAttackInfoFactory.getMobAttackInfo(attacker, attackIndex);
        if (attackInfo != null) {
            if (attackInfo.isDeadlyAttack()) {
                mpAttack = Math.max(0, target.getMp() - 1);
                deadly = true;
            }
            mpAttack += attackInfo.getMpBurn();
            if (diseaseSkillId <= 0) {
                diseaseSkillId = attackInfo.getDiseaseSkill();
                diseaseLevel = attackInfo.getDiseaseLevel();
            }
        }
        Optional<MobSkillType> disease = MobSkillType.from(diseaseSkillId);
        int resolvedDiseaseLevel = diseaseLevel;
        Optional<MobSkill> diseaseSkill = disease.map(type ->
                MobSkillFactory.getMobSkillOrThrow(type, resolvedDiseaseLevel));
        if (diseaseSkill.isPresent() && damage > 0) {
            diseaseSkill.get().applyEffect(target, attacker, false, banishPlayers);
        }

        if (deadly && MapId.isDojo(map.getId()) && !YamlConfig.config.server.USE_DEADLY_DOJO) {
            damage = 0;
            mpAttack = 0;
        }
        if (damage > 0) {
            if (deadly) {
                damage = Math.max(0, target.getHp() - 1);
                mpAttack = Math.max(0, target.getMp() - 1);
                applyDirectLoss(target, damage, mpAttack);
            } else {
                damage = applyDamageReduction(target, damage);
                applyHpMpLoss(target, damage, mpAttack);
            }
        }

        int direction = target.getPosition().x < attacker.getPosition().x ? 0 : 1;
        map.broadcastMessage(target, PacketCreator.damagePlayer(
                attackIndex, attacker.getId(), target.getId(), damage, 0, direction,
                false, 0, true, attacker.getObjectId(), 0, 0), true);
        for (Character banished : banishPlayers) {
            banished.changeMapBanish(attacker.getBanish());
        }
        return damage;
    }

    private static int applyDamageReduction(Character target, int damage) {
        StatEffect comboBarrier = target.getBuffEffect(BuffStat.COMBO_BARRIER);
        if (comboBarrier != null) {
            damage = (int) (damage * (comboBarrier.getX() / 1000.0d));
        }

        int jobId = target.getJob().getId();
        if (jobId < 200 && jobId % 10 == 2) {
            Skill achilles = SkillFactory.getSkill(jobId * 10000 + (jobId == 112 ? 4 : 5));
            int level = target.getSkillLevel(achilles);
            if (level > 0) {
                damage = (int) (damage * (achilles.getEffect(level).getX() / 1000.0d));
            }
        }

        Skill highDefense = SkillFactory.getSkill(Aran.HIGH_DEFENSE);
        int highDefenseLevel = highDefense == null ? 0 : target.getSkillLevel(highDefense);
        if (highDefenseLevel > 0) {
            damage = (int) (damage * Math.ceil(
                    highDefense.getEffect(highDefenseLevel).getX() / 1000.0d));
        }
        return Math.max(0, damage);
    }

    private static void applyHpMpLoss(Character target, int damage, int mpAttack) {
        Integer magicGuard = target.getBuffedValue(BuffStat.MAGIC_GUARD);
        Integer mesoGuard = target.getBuffedValue(BuffStat.MESOGUARD);
        int hpLoss = damage;
        int mpLoss = mpAttack;
        if (magicGuard != null && mpAttack == 0) {
            int guardedMp = (int) (damage * (magicGuard.doubleValue() / 100.0d));
            hpLoss = damage - guardedMp;
            if (guardedMp > target.getMp()) {
                hpLoss += guardedMp - target.getMp();
                guardedMp = target.getMp();
            }
            mpLoss = guardedMp;
        } else if (mesoGuard != null) {
            hpLoss = Math.round(damage / 2.0f);
            int mesoLoss = (int) (hpLoss * (mesoGuard.doubleValue() / 100.0d));
            if (target.getMeso() < mesoLoss) {
                target.gainMeso(-target.getMeso(), false);
                target.cancelBuffStats(BuffStat.MESOGUARD);
            } else {
                target.gainMeso(-mesoLoss, false);
            }
        }

        if (target.isRidingBattleship()) {
            target.decreaseBattleshipHp(hpLoss);
        }
        if (target.getClient() instanceof BotClient) {
            target.addMPHPAndTriggerAutopot(-hpLoss, -mpLoss);
        } else {
            target.addMPHP(-hpLoss, -mpLoss);
        }
    }

    private static void applyDirectLoss(Character target, int hpLoss, int mpLoss) {
        if (target.isRidingBattleship()) {
            target.decreaseBattleshipHp(hpLoss);
        }
        if (target.getClient() instanceof BotClient) {
            target.addMPHPAndTriggerAutopot(-hpLoss, -mpLoss);
        } else {
            target.addMPHP(-hpLoss, -mpLoss);
        }
    }
}
