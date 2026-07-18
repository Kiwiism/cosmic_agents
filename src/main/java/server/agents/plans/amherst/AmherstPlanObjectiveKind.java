package server.agents.plans.amherst;

import java.util.Arrays;

public enum AmherstPlanObjectiveKind {
    QUEST_START("quest-start"),
    QUEST_COMPLETE("quest-complete"),
    FORCE_COMPLETE_QUEST("force-complete-quest"),
    QUEST_CHAIN("quest-chain"),
    QUEST_CHAIN_IF_AVAILABLE("quest-chain-if-available"),
    USE_ITEM("use-item"),
    KILL_MOBS("kill-mobs"),
    REACTOR_HIT("reactor-hit"),
    REACTOR_BOX_ITEMS("reactor-box-items"),
    STOP_PLAN("stop-plan");

    private final String jsonName;

    AmherstPlanObjectiveKind(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    public boolean waitsForWorldResource() {
        return switch (this) {
            case KILL_MOBS, REACTOR_HIT, REACTOR_BOX_ITEMS -> true;
            default -> false;
        };
    }

    public static AmherstPlanObjectiveKind fromJsonName(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.jsonName.equals(value))
                .findFirst()
                .orElse(null);
    }
}
