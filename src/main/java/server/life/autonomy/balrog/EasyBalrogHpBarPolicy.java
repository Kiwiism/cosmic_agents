package server.life.autonomy.balrog;

import server.life.MonsterStats;

import java.util.Set;

/** Supplies the missing client-supported HP-bar style for Easy Balrog's multipart actors. */
public final class EasyBalrogHpBarPolicy {
    private static final Set<Integer> ACTOR_IDS = Set.of(
            EasyBalrogEncounterService.BODY_ID,
            EasyBalrogEncounterService.RELEASED_CLAW_ID,
            EasyBalrogEncounterService.INITIAL_CLAW_ID);
    private static final int TAG_COLOR = 1;
    private static final int TAG_BACKGROUND_COLOR = 5;

    private EasyBalrogHpBarPolicy() {
    }

    public static void applyMissingStyle(int mobId, MonsterStats stats) {
        if (stats == null || !ACTOR_IDS.contains(mobId) || stats.getTagColor() > 0) {
            return;
        }
        stats.setTagColor(TAG_COLOR);
        stats.setTagBgColor(TAG_BACKGROUND_COLOR);
    }

    static byte tagColor() {
        return TAG_COLOR;
    }

    static byte tagBackgroundColor() {
        return TAG_BACKGROUND_COLOR;
    }
}
