package server.agents.capabilities.partyquest.kpq;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

/** Authored KPQ rules shared by live execution, checkpoints, and diagnostics. */
public final class AgentKpqDefinition {
    public static final int RECRUIT_MAP = 103_000_000;
    public static final int EXIT_MAP = 103_000_890;
    public static final int STAGE_1_MAP = 103_000_800;
    public static final int STAGE_2_MAP = 103_000_801;
    public static final int STAGE_3_MAP = 103_000_802;
    public static final int STAGE_4_MAP = 103_000_803;
    public static final int STAGE_5_MAP = 103_000_804;
    public static final int BONUS_MAP = 103_000_805;

    public static final int ENTRY_NPC = 9_020_000;
    public static final int CLOTO_NPC = 9_020_001;
    public static final int EXIT_NPC = 9_020_002;
    public static final int COUPON_ITEM = 4_001_007;
    public static final int PASS_ITEM = 4_001_008;
    public static final int SQUISHY_SHOES = 1_072_369;
    public static final int NEXT_PORTAL_ID = 2;

    private static final int[] QUESTION_ANSWERS = {0, 10, 35, 20, 25, 25, 30, 8};

    private static final List<CombinationStage> COMBINATION_STAGES = List.of(
            stage(2, STAGE_2_MAP, "2stageclear", "stg2Property", HoldMode.ROPE,
                    rect(-721, -340, 4, 166), rect(-586, -326, 4, 150),
                    rect(-755, -132, 4, 218), rect(-483, -181, 4, 222)),
            stage(3, STAGE_3_MAP, "3stageclear", "stg3Property", HoldMode.GROUNDED,
                    rect(608, -180, 140, 50), rect(791, -117, 140, 45),
                    rect(958, -180, 140, 50), rect(876, -238, 140, 45),
                    rect(702, -238, 140, 45)),
            stage(4, STAGE_4_MAP, "4stageclear", "stg4Property", HoldMode.GROUNDED,
                    rect(910, -236, 35, 5), rect(877, -184, 35, 5),
                    rect(946, -184, 35, 5), rect(845, -132, 35, 5),
                    rect(910, -132, 35, 5), rect(981, -132, 35, 5))
    );

    private AgentKpqDefinition() {
    }

    public static int couponTarget(int questionIndex) {
        return questionIndex > 0 && questionIndex < QUESTION_ANSWERS.length
                ? QUESTION_ANSWERS[questionIndex] : -1;
    }

    public static List<Integer> couponTargets() {
        return java.util.Arrays.stream(QUESTION_ANSWERS).skip(1).boxed().toList();
    }

    public static CombinationStage combinationStage(int stageNumber) {
        return COMBINATION_STAGES.stream()
                .filter(stage -> stage.stageNumber() == stageNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Not a KPQ combination stage: " + stageNumber));
    }

    /** Mirrors the one-based occupied rectangles in scripts/npc/9020001.js. */
    public static List<Integer> answerCombination(int stageNumber, int answerIndex) {
        CombinationStage stage = combinationStage(stageNumber);
        List<List<Integer>> combinations = AgentKpqCombinationOrder.forPositionCount(stage.positions().size());
        if (answerIndex < 0 || answerIndex >= combinations.size()) {
            throw new IllegalArgumentException("Invalid Stage " + stageNumber + " answer index " + answerIndex);
        }
        // The NPC script enumerates masks by excluded positions, which is the reverse of the
        // coordinator's ascending occupied-position order.
        return combinations.get(combinations.size() - answerIndex - 1);
    }

    public static int stageForMap(int mapId) {
        return mapId >= STAGE_1_MAP && mapId <= STAGE_5_MAP
                ? mapId - STAGE_1_MAP + 1 : 0;
    }

    private static Rectangle rect(int x, int y, int width, int height) {
        return new Rectangle(x, y, width, height);
    }

    private static CombinationStage stage(int stageNumber,
                                          int mapId,
                                          String clearProperty,
                                          String answerProperty,
                                          HoldMode holdMode,
                                          Rectangle... positions) {
        return new CombinationStage(stageNumber, mapId, clearProperty, answerProperty,
                holdMode, List.of(positions));
    }

    public enum HoldMode { ROPE, GROUNDED }

    public record CombinationStage(int stageNumber,
                                   int mapId,
                                   String clearProperty,
                                   String answerProperty,
                                   HoldMode holdMode,
                                   List<Rectangle> positions) {
        public CombinationStage {
            positions = positions.stream().map(Rectangle::new).toList();
            if (stageNumber < 2 || stageNumber > 4 || positions.size() != stageNumber + 2) {
                throw new IllegalArgumentException("Invalid KPQ combination stage definition");
            }
        }

        public Point center(int oneBasedPosition) {
            Rectangle area = positions.get(oneBasedPosition - 1);
            return new Point((int) area.getCenterX(), (int) area.getCenterY());
        }

        public boolean contains(int oneBasedPosition, Point point) {
            return point != null && positions.get(oneBasedPosition - 1).contains(point);
        }

        public boolean containsAny(Point point) {
            return point != null && positions.stream().anyMatch(area -> area.contains(point));
        }
    }
}
