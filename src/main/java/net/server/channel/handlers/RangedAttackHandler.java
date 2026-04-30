/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as
published by the Free Software Foundation version 3 as published by
the Free Software Foundation. You may not use, modify or distribute
this program under any other version of the GNU Affero General Public
License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.server.channel.handlers;

import client.BuffStat;
import client.Character;
import client.Client;
import client.Skill;
import client.SkillFactory;
import client.inventory.Inventory;
import client.inventory.InventoryType;
import client.inventory.Item;
import client.inventory.WeaponType;
import client.inventory.manipulator.InventoryManipulator;
import config.YamlConfig;
import constants.id.ItemId;
import constants.id.MapId;
import constants.inventory.ItemConstants;
import constants.skills.Aran;
import constants.skills.Buccaneer;
import constants.skills.NightLord;
import constants.skills.NightWalker;
import constants.skills.Shadower;
import constants.skills.ThunderBreaker;
import constants.skills.WindArcher;
import net.packet.InPacket;
import net.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.ItemInformationProvider;
import server.StatEffect;
import tools.PacketCreator;
import tools.Randomizer;

import static java.util.concurrent.TimeUnit.SECONDS;


public final class RangedAttackHandler extends AbstractDealDamageHandler {
    private static final Logger log = LoggerFactory.getLogger(RangedAttackHandler.class);

    @Override
    public void handlePacket(InPacket p, Client c) {
        Character chr = c.getPlayer();
        
        /*long timeElapsed = currentServerTime() - chr.getAutobanManager().getLastSpam(8);
        if(timeElapsed < 300) {
            AutobanFactory.FAST_ATTACK.alert(chr, "Time: " + timeElapsed);
        }
        chr.getAutobanManager().spam(8);*/

        AttackInfo attack = parseDamage(p, chr, true, false);

        if (chr.getBuffEffect(BuffStat.MORPH) != null) {
            if (chr.getBuffEffect(BuffStat.MORPH).isMorphWithoutAttack()) {
                // How are they attacking when the client won't let them?
                chr.getClient().disconnect(false, false);
                return;
            }
        }

        if (MapId.isDojo(chr.getMap().getId()) && attack.numAttacked > 0) {
            chr.setDojoEnergy(chr.getDojoEnergy() + YamlConfig.config.server.DOJO_ENERGY_ATK);
            c.sendPacket(PacketCreator.getEnergy("energy", chr.getDojoEnergy()));
        }

        applyRangedAttackEffects(attack, chr, c);
    }

    public static void applyRangedAttackEffects(AttackInfo attack, Character chr, Client c) {
        if (attack.skill == Buccaneer.ENERGY_ORB || attack.skill == ThunderBreaker.SPARK || attack.skill == Shadower.TAUNT || attack.skill == NightLord.TAUNT) {
            chr.getMap().broadcastMessage(chr, PacketCreator.rangedAttack(chr, attack.skill, attack.skilllevel,
                    attack.stance, attack.numAttackedAndDamage, 0, attack.targets, attack.speed,
                    attack.direction, attack.display), false);
            applyAttack(attack, chr, 1);
            return;
        }

        if (attack.skill == ThunderBreaker.SHARK_WAVE && chr.getSkillLevel(ThunderBreaker.SHARK_WAVE) > 0) {
            chr.getMap().broadcastMessage(chr, PacketCreator.rangedAttack(chr, attack.skill, attack.skilllevel,
                    attack.stance, attack.numAttackedAndDamage, 0, attack.targets, attack.speed,
                    attack.direction, attack.display), false);
            applyAttack(attack, chr, 1);

            for (int i = 0; i < attack.numAttacked; i++) {
                chr.handleEnergyChargeGain();
            }
            return;
        }

        if (attack.skill == Aran.COMBO_SMASH || attack.skill == Aran.COMBO_FENRIR || attack.skill == Aran.COMBO_TEMPEST) {
            chr.getMap().broadcastMessage(chr, PacketCreator.rangedAttack(chr, attack.skill, attack.skilllevel,
                    attack.stance, attack.numAttackedAndDamage, 0, attack.targets, attack.speed,
                    attack.direction, attack.display), false);
            if (attack.skill == Aran.COMBO_SMASH && chr.getCombo() >= 30) {
                chr.setCombo((short) 0);
                applyAttack(attack, chr, 1);
            } else if (attack.skill == Aran.COMBO_FENRIR && chr.getCombo() >= 100) {
                chr.setCombo((short) 0);
                applyAttack(attack, chr, 2);
            } else if (attack.skill == Aran.COMBO_TEMPEST && chr.getCombo() >= 200) {
                chr.setCombo((short) 0);
                applyAttack(attack, chr, 4);
            }
            return;
        }

        Item weapon = chr.getInventory(InventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null) {
            return;
        }

        WeaponType type = ItemInformationProvider.getInstance().getWeaponType(weapon.getItemId());
        if (type == WeaponType.NOT_A_WEAPON) {
            return;
        }

        ProjectileContext projectileContext = resolveProjectileContext(attack, chr, c, weapon, type);
        if (!projectileContext.canAttack()) {
            return;
        }

        applyRangedWithProjectile(attack, chr, c, projectileContext.visibleProjectile, projectileContext.bulletCount);
    }

    private static ProjectileContext resolveProjectileContext(AttackInfo attack, Character chr, Client c, Item weapon, WeaponType type) {
        short slot = -1;
        int projectile = 0;
        short bulletCount = 1;
        StatEffect effect = null;

        if (attack.skill != 0) {
            effect = attack.getAttackEffect(chr, null);
            if (effect == null) {
                return ProjectileContext.unavailable();
            }

            bulletCount = effect.getBulletCount();
            if (effect.getCooldown() > 0) {
                c.sendPacket(PacketCreator.skillCooldown(attack.skill, effect.getCooldown()));
            }

            if (attack.skill == 4111004) {
                bulletCount = 0;

                int money = effect.getMoneyCon();
                if (money != 0) {
                    int moneyMod = money / 2;
                    money += Randomizer.nextInt(moneyMod);
                    if (money > chr.getMeso()) {
                        money = chr.getMeso();
                    }
                    chr.gainMeso(-money, false);
                }
            }
        }

        boolean hasShadowPartner = chr.getBuffedValue(BuffStat.SHADOWPARTNER) != null;
        if (hasShadowPartner) {
            bulletCount *= 2;
        }

        Inventory inv = chr.getInventory(InventoryType.USE);
        for (short i = 1; i <= inv.getSlotLimit(); i++) {
            Item item = inv.getItem(i);
            if (item == null) {
                continue;
            }

            int id = item.getItemId();
            slot = item.getPosition();
            boolean bow = ItemConstants.isArrowForBow(id);
            boolean cbow = ItemConstants.isArrowForCrossBow(id);
            if (item.getQuantity() < bulletCount) {
                continue;
            }

            if (type == WeaponType.CLAW && ItemConstants.isThrowingStar(id) && weapon.getItemId() != ItemId.MAGICAL_MITTEN) {
                if (((id == ItemId.HWABI_THROWING_STARS || id == ItemId.BALANCED_FURY) && chr.getLevel() < 70)
                        || (id == ItemId.CRYSTAL_ILBI_THROWING_STARS && chr.getLevel() < 50)) {
                    continue;
                }
                projectile = id;
                break;
            }

            if (type == WeaponType.GUN && ItemConstants.isBullet(id)) {
                if (id == ItemId.BLAZE_CAPSULE || id == ItemId.GLAZE_CAPSULE) {
                    if (chr.getLevel() >= 70) {
                        projectile = id;
                        break;
                    }
                    continue;
                }

                if (chr.getLevel() > (id % 10) * 20 + 9) {
                    projectile = id;
                    break;
                }
                continue;
            }

            if ((type == WeaponType.BOW && bow) || (type == WeaponType.CROSSBOW && cbow)
                    || (weapon.getItemId() == ItemId.MAGICAL_MITTEN && (bow || cbow))) {
                projectile = id;
                break;
            }
        }

        boolean soulArrow = chr.getBuffedValue(BuffStat.SOULARROW) != null;
        boolean shadowClaw = chr.getBuffedValue(BuffStat.SHADOW_CLAW) != null;
        if (projectile != 0 && !soulArrow && !shadowClaw
                && attack.skill != 11101004 && attack.skill != 15111007 && attack.skill != 14101006) {
            short bulletConsume = bulletCount;
            if (effect != null && effect.getBulletConsume() != 0) {
                bulletConsume = (byte) (effect.getBulletConsume() * (hasShadowPartner ? 2 : 1));
            }

            if (slot < 0) {
                log.warn("<ERROR> Projectile to use was unable to be found.");
            } else {
                consumeProjectile(chr, c, slot, bulletConsume);
            }
        }

        boolean canAttack = projectile != 0 || soulArrow || attack.skill == 11101004 || attack.skill == 15111007
                || attack.skill == 14101006 || attack.skill == 4111004 || attack.skill == 13101005;
        if (!canAttack) {
            return ProjectileContext.unavailable();
        }

        int visibleProjectile = projectile;
        if (ItemConstants.isThrowingStar(projectile)) {
            Inventory cash = chr.getInventory(InventoryType.CASH);
            for (int i = 1; i <= cash.getSlotLimit(); i++) {
                Item item = cash.getItem((short) i);
                if (item != null && item.getItemId() / 1000 == 5021) {
                    visibleProjectile = item.getItemId();
                    break;
                }
            }
        } else if (soulArrow || attack.skill == 3111004 || attack.skill == 3211004
                || attack.skill == 11101004 || attack.skill == 15111007
                || attack.skill == 14101006 || attack.skill == 13101005) {
            visibleProjectile = 0;
        }

        return new ProjectileContext(true, bulletCount, visibleProjectile);
    }

    static void consumeProjectile(Character chr, Client c, short slot, short bulletConsume) {
        if (chr == null) {
            return;
        }
        if (c != null && c.getPlayer() == chr) {
            InventoryManipulator.removeFromSlot(c, InventoryType.USE, slot, bulletConsume, false, true);
            return;
        }

        Inventory inv = chr.getInventory(InventoryType.USE);
        if (inv == null) {
            return;
        }
        Item item = inv.getItem(slot);
        if (item == null) {
            return;
        }
        inv.removeItem(slot, bulletConsume, ItemConstants.isRechargeable(item.getItemId()));
    }

    private static Packet createAttackPacket(AttackInfo attack, Character chr, int visibleProjectile) {
        return switch (attack.skill) {
            case 3121004, 3221001, 5221004, 13111002 ->
                    PacketCreator.rangedAttack(chr, attack.skill, attack.skilllevel, attack.rangedirection,
                            attack.numAttackedAndDamage, visibleProjectile, attack.targets, attack.speed,
                            attack.direction, attack.display);
            default ->
                    PacketCreator.rangedAttack(chr, attack.skill, attack.skilllevel, attack.stance,
                            attack.numAttackedAndDamage, visibleProjectile, attack.targets, attack.speed,
                            attack.direction, attack.display);
        };
    }

    private static void applyRangedWithProjectile(AttackInfo attack, Character chr, Client c,
                                                   int projectile, int bulletCount) {
        Packet packet = createAttackPacket(attack, chr, projectile);
        chr.getMap().broadcastMessage(chr, packet, false, true);
        applyCooldownIfNeeded(attack, chr, c);
        cancelStealthBuffsIfNeeded(attack, chr);
        applyAttack(attack, chr, bulletCount);
    }

    static void applyCooldownIfNeeded(AttackInfo attack, Character chr, Client c) {
        if (attack.skill == 0) {
            return;
        }

        Skill skill = SkillFactory.getSkill(attack.skill);
        StatEffect effect = skill.getEffect(chr.getSkillLevel(skill));
        if (effect.getCooldown() <= 0) {
            return;
        }

        if (chr.skillIsCooling(attack.skill)) {
            return;
        }

        c.sendPacket(PacketCreator.skillCooldown(attack.skill, effect.getCooldown()));
        chr.addCooldown(attack.skill, currentServerTime(), SECONDS.toMillis(effect.getCooldown()));
    }

    static void cancelStealthBuffsIfNeeded(AttackInfo attack, Character chr) {
        if (chr.getSkillLevel(SkillFactory.getSkill(NightWalker.VANISH)) > 0
                && chr.getBuffedValue(BuffStat.DARKSIGHT) != null
                && attack.numAttacked > 0
                && chr.getBuffSource(BuffStat.DARKSIGHT) != 9101004) {
            chr.cancelEffectFromBuffStat(BuffStat.DARKSIGHT);
            chr.cancelBuffStats(BuffStat.DARKSIGHT);
            return;
        }

        if (chr.getSkillLevel(SkillFactory.getSkill(WindArcher.WIND_WALK)) > 0
                && chr.getBuffedValue(BuffStat.WIND_WALK) != null
                && attack.numAttacked > 0) {
            chr.cancelEffectFromBuffStat(BuffStat.WIND_WALK);
            chr.cancelBuffStats(BuffStat.WIND_WALK);
        }
    }

    private record ProjectileContext(boolean canAttack, short bulletCount, int visibleProjectile) {
        private static ProjectileContext unavailable() {
            return new ProjectileContext(false, (short) 0, 0);
        }
    }
}
