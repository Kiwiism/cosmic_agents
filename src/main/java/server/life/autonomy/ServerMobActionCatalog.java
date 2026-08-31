package server.life.autonomy;

import provider.Data;
import provider.DataProvider;
import provider.DataProviderFactory;
import provider.DataTool;
import provider.wz.WZFiles;
import server.life.MobSkill;
import server.life.MobSkillFactory;
import server.life.MobSkillType;
import tools.StringUtil;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Loads immutable monster action descriptions through the normal WZ provider path. */
public final class ServerMobActionCatalog {
    private static final DataProvider MOB_DATA = DataProviderFactory.getDataProvider(WZFiles.MOB);
    private static final Map<Integer, MonsterActions> CACHE = new ConcurrentHashMap<>();

    private ServerMobActionCatalog() {
    }

    public static MonsterActions forMob(int mobId) {
        return CACHE.computeIfAbsent(mobId, ServerMobActionCatalog::load);
    }

    private static MonsterActions load(int mobId) {
        Data mob = MOB_DATA.getData(StringUtil.getLeftPaddedStr(mobId + ".img", '0', 11));
        if (mob == null) {
            return new MonsterActions(List.of(), List.of());
        }

        List<BossAction.OrdinaryAttack> attacks = new ArrayList<>();
        for (int index = 0; ; index++) {
            Data attack = mob.getChildByPath("attack" + (index + 1));
            if (attack == null) {
                break;
            }
            Data info = attack.getChildByPath("info");
            int actionNumber = index + 1;
            int mpCost = DataTool.getIntConvert("conMP", info, 0);
            int impactDelay = DataTool.getIntConvert("attackAfter", info, 0);
            boolean magic = DataTool.getIntConvert("magic", info, 0) != 0;
            Point lt = DataTool.getPoint("range/lt", info, null);
            Point rb = DataTool.getPoint("range/rb", info, null);
            attacks.add(new BossAction.OrdinaryAttack(
                    index, actionNumber, mpCost, impactDelay,
                    animationTime(attack), magic, copy(lt), copy(rb),
                    DataTool.getIntConvert("range/start", info, 0),
                    DataTool.getIntConvert("range/areaCount", info, 0),
                    DataTool.getIntConvert("range/attackCount", info, 0),
                    info != null && info.getChildByPath("deadlyAttack") != null,
                    DataTool.getIntConvert("PADamage", info, 0),
                    DataTool.getIntConvert("MADamage", info, 0),
                    DataTool.getIntConvert("disease", info, 0),
                    DataTool.getIntConvert("level", info, 0),
                    DataTool.getIntConvert("tremble", info, 0) != 0));
        }

        List<BossAction.Skill> skills = new ArrayList<>();
        Data skillInfo = mob.getChildByPath("info/skill");
        if (skillInfo != null) {
            for (Data entry : skillInfo.getChildren()) {
                int skillId = DataTool.getInt("skill", entry, 0);
                int level = DataTool.getInt("level", entry, 0);
                int actionNumber = Math.max(1, DataTool.getInt("action", entry, 1));
                MobSkillType type = MobSkillType.from(skillId).orElse(null);
                if (type == null || level <= 0) {
                    continue;
                }
                MobSkill skill = MobSkillFactory.getMobSkill(type, level).orElse(null);
                if (skill == null) {
                    continue;
                }
                Data animation = mob.getChildByPath("skill" + actionNumber);
                int animationMs = animationTime(animation);
                int effectDelay = DataTool.getInt("effectAfter", entry, animationMs);
                if (effectDelay <= 0) {
                    effectDelay = animationMs;
                }
                skills.add(new BossAction.Skill(
                        skill, actionNumber, effectDelay, animationMs,
                        copy(skill.getLt()), copy(skill.getRb())));
            }
        }
        return new MonsterActions(List.copyOf(attacks), List.copyOf(skills));
    }

    private static int animationTime(Data animation) {
        if (animation == null) {
            return 0;
        }
        int total = 0;
        for (Data frame : animation.getChildren()) {
            total += Math.max(0, DataTool.getIntConvert("delay", frame, 0));
        }
        return total;
    }

    private static Point copy(Point point) {
        return point == null ? null : new Point(point);
    }

    public record MonsterActions(
            List<BossAction.OrdinaryAttack> attacks,
            List<BossAction.Skill> skills
    ) {
    }
}
