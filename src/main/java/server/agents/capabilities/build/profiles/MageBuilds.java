package server.agents.capabilities.build.profiles;

import client.Job;
import client.Skill;
import client.SkillFactory;
import constants.skills.Bishop;
import constants.skills.Cleric;
import constants.skills.ILArchMage;
import constants.skills.ILMage;
import constants.skills.ILWizard;
import constants.skills.Magician;
import constants.skills.Priest;
import java.util.List;

public final class MageBuilds {

    private MageBuilds() {
    }

    public static List<BuildStep> getBuildOrder(Job job) {
        return switch (job) {
            case MAGICIAN -> magicianBuild();
            case IL_WIZARD -> ilWizardBuild();
            case IL_MAGE -> ilMageBuild();
            case IL_ARCHMAGE -> ilArchMageBuild();
            case CLERIC -> clericBuild();
            case PRIEST -> priestBuild();
            case BISHOP -> bishopBuild();
            default -> null;
        };
    }

    private static BuildStep s(int id, int to) {
        return new BuildStep(id, to);
    }

    private static int max(int skillId) {
        Skill skill = SkillFactory.getSkill(skillId);
        return skill != null ? skill.getMaxLevel() : 30;
    }

    private static List<BuildStep> magicianBuild() {
        return List.of(
                s(Magician.ENERGY_BOLT, 1),
                s(Magician.IMPROVED_MP_RECOVERY, 5),
                s(Magician.IMPROVED_MAX_MP_INCREASE, 10),
                s(Magician.IMPROVED_MP_RECOVERY, 16),
                s(Magician.MAGIC_CLAW, 20),
                s(Magician.MAGIC_GUARD, 20)
        );
    }

    // https://forum.maplelegends.com/index.php?threads/ice-lightning-mage-guide.12054/
    private static List<BuildStep> ilWizardBuild() {
        return List.of(
                s(ILWizard.TELEPORT, 1),
                s(ILWizard.THUNDERBOLT, max(ILWizard.THUNDERBOLT)),
                s(ILWizard.MEDITATION, 20),
                s(ILWizard.MP_EATER, 20),
                s(ILWizard.TELEPORT, 20),
                s(ILWizard.COLD_BEAM, max(ILWizard.COLD_BEAM))
        );
    }

    private static List<BuildStep> ilMageBuild() {
        return List.of(
                s(ILMage.ELEMENT_AMPLIFICATION, 1),
                s(ILMage.ICE_STRIKE, max(ILMage.ICE_STRIKE)),
                s(ILMage.SPELL_BOOSTER, 11),
                s(ILMage.ELEMENT_AMPLIFICATION, max(ILMage.ELEMENT_AMPLIFICATION)),
                s(ILMage.SPELL_BOOSTER, 20),
                s(ILMage.ELEMENT_COMPOSITION, max(ILMage.ELEMENT_COMPOSITION)),
                s(ILMage.SEAL, 20),
                s(ILMage.PARTIAL_RESISTANCE, max(ILMage.PARTIAL_RESISTANCE))
        );
    }

    private static List<BuildStep> ilArchMageBuild() {
        return List.of(
                s(ILArchMage.BLIZZARD, 3),
                s(ILArchMage.BLIZZARD, 10),
                s(ILArchMage.MAPLE_WARRIOR, 9),
                s(ILArchMage.BLIZZARD, max(ILArchMage.BLIZZARD)),
                s(ILArchMage.MAPLE_WARRIOR, 19),
                s(ILArchMage.CHAIN_LIGHTNING, max(ILArchMage.CHAIN_LIGHTNING)),
                s(ILArchMage.ICE_DEMON, 5),
                s(ILArchMage.IFRIT, max(ILArchMage.IFRIT)),
                s(ILArchMage.ICE_DEMON, max(ILArchMage.ICE_DEMON)),
                s(ILArchMage.MAPLE_WARRIOR, max(ILArchMage.MAPLE_WARRIOR)),
                s(ILArchMage.BIG_BANG, max(ILArchMage.BIG_BANG)),
                s(ILArchMage.INFINITY, max(ILArchMage.INFINITY)),
                s(ILArchMage.MANA_REFLECTION, max(ILArchMage.MANA_REFLECTION)),
                s(ILArchMage.HEROS_WILL, max(ILArchMage.HEROS_WILL))
        );
    }

    private static List<BuildStep> clericBuild() {
        return List.of(
                s(Cleric.HEAL, 30),
                s(Cleric.INVINCIBLE, 5),
                s(Cleric.BLESS, 20),
                s(Cleric.TELEPORT, 20),
                s(Cleric.MP_EATER, 20),
                s(Cleric.INVINCIBLE, 20),
                s(Cleric.HOLY_ARROW, 11)
        );
    }

    private static List<BuildStep> priestBuild() {
        return List.of(
                s(Priest.SHINING_RAY, 1),
                s(Priest.DISPEL, 3),
                s(Priest.ELEMENTAL_RESISTANCE, 1),
                s(Priest.MYSTIC_DOOR, 1),
                s(Priest.HOLY_SYMBOL, max(Priest.HOLY_SYMBOL)),
                s(Priest.SHINING_RAY, max(Priest.SHINING_RAY)),
                s(Priest.ELEMENTAL_RESISTANCE, max(Priest.ELEMENTAL_RESISTANCE)),
                s(Priest.SUMMON_DRAGON, max(Priest.SUMMON_DRAGON)),
                s(Priest.DISPEL, max(Priest.DISPEL)),
                s(Priest.MYSTIC_DOOR, max(Priest.MYSTIC_DOOR)),
                s(Priest.DOOM, 1)
        );
    }

    private static List<BuildStep> bishopBuild() {
        return List.of(
                s(Bishop.GENESIS, 10),
                s(Bishop.MAPLE_WARRIOR, 9),
                s(Bishop.RESURRECTION, max(Bishop.RESURRECTION)),
                s(Bishop.ANGEL_RAY, max(Bishop.ANGEL_RAY)),
                s(Bishop.BAHAMUT, max(Bishop.BAHAMUT)),
                s(Bishop.GENESIS, max(Bishop.GENESIS)),
                s(Bishop.MAPLE_WARRIOR, max(Bishop.MAPLE_WARRIOR)),
                s(Bishop.BIG_BANG, max(Bishop.BIG_BANG)),
                s(Bishop.HOLY_SHIELD, max(Bishop.HOLY_SHIELD)),
                s(Bishop.INFINITY, max(Bishop.INFINITY)),
                s(Bishop.MANA_REFLECTION, max(Bishop.MANA_REFLECTION)),
                s(Bishop.HEROS_WILL, max(Bishop.HEROS_WILL))
        );
    }
}
