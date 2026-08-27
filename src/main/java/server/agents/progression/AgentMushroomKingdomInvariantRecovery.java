package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.integration.PrimitiveCapabilityGateway;

import java.util.List;

/** Repairs only durable Mushroom Kingdom states whose legitimate prerequisite is already proven. */
final class AgentMushroomKingdomInvariantRecovery {
    private static final int NOT_STARTED = QuestStatus.Status.NOT_STARTED.getId();
    private static final int STARTED = QuestStatus.Status.STARTED.getId();
    private static final int COMPLETED = QuestStatus.Status.COMPLETED.getId();
    private static final int HELMET_PEPE_ITEM_ID = 4_001_317;
    private static final int HELMET_PEPE_PITY_KILLS = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomInvariantRecovery.HELMET_PEPE_PITY_KILLS");

    record Result(boolean recovered, String reason) {
        static Result none() { return new Result(false, "no evidence-backed invariant repair is available"); }
    }

    private AgentMushroomKingdomInvariantRecovery() { }

    static Result recover(Character agent, AgentMushroomKingdomState state,
                          PrimitiveCapabilityGateway gateway) {
        if (agent == null || state == null || gateway == null) return Result.none();

        if (gateway.questStatus(agent, 2334) == COMPLETED
                && gateway.questStatus(agent, 2336) == NOT_STARTED
                && gateway.questStatus(agent, 2331) != NOT_STARTED
                && gateway.forceStartQuest(agent, 2336, 1_300_002)) {
            grantMissing(agent, gateway, List.of(4_032_387, 4_032_386));
            return new Result(true, "reconciled Truth Revealed after the rescued-Princess ordering window");
        }
        if (gateway.questStatus(agent, 2336) == STARTED) {
            int granted = grantMissing(agent, gateway, List.of(4_032_387, 4_032_386));
            if (granted > 0) return new Result(true, "restored " + granted + " Truth Revealed item(s)");
        }
        if (gateway.questStatus(agent, 2335) == STARTED && gateway.itemCount(agent, 4_032_405) < 1
                && gateway.grantItem(agent, 4_032_405, 1)) {
            return new Result(true, "restored the q2335 secret-room key");
        }
        if (gateway.questStatus(agent, 2333) == COMPLETED
                && gateway.questStatus(agent, 2331) == STARTED
                && gateway.itemCount(agent, 4_001_318) < 1
                && gateway.forceStartQuest(agent, 2342, 1_300_002)
                && gateway.itemCount(agent, 4_001_318) > 0) {
            return new Result(true, "recovered the Royal Seal through q2342");
        }
        if (allYetiCredits(agent, gateway) && gateway.itemCount(agent, 4_032_388) < 1
                && gateway.grantItem(agent, 4_032_388, 1)) {
            return new Result(true, "synthesized the Wedding Hall key after three proven Yeti credits");
        }
        if (gateway.questStatus(agent, 2324) == STARTED && gateway.itemCount(agent, 2_430_015) < 1
                && gateway.grantItem(agent, 2_430_015, 1)) {
            return new Result(true, "restored the q2324 Thorn Remover");
        }
        if (gateway.questStatus(agent, 2318) == COMPLETED
                && gateway.questStatus(agent, AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                == NOT_STARTED && gateway.itemCount(agent, 2_430_014) < 1
                && gateway.forceStartQuest(agent, 2338, 1_300_007)) {
            if (gateway.itemCount(agent, 2_430_014) < 1) {
                gateway.grantItem(agent, 2_430_014, 1);
            }
            if (gateway.itemCount(agent, 2_430_014) > 0) {
                return new Result(true, "recovered the Killer Mushroom Spore through q2338");
            }
        }
        if (state.currentQuestId() == 2326 && state.helmetPepeKills() >= HELMET_PEPE_PITY_KILLS
                && gateway.itemCount(agent, HELMET_PEPE_ITEM_ID) < 1
                && gateway.grantItem(agent, HELMET_PEPE_ITEM_ID, 1)) {
            return new Result(true, "granted the q2326 rare item after 50 verified Helmet Pepe kills");
        }
        return Result.none();
    }

    static boolean allYetiCredits(Character agent, PrimitiveCapabilityGateway gateway) {
        return List.of(3_300_005, 3_300_006, 3_300_007).stream()
                .allMatch(mobId -> gateway.questProgress(agent, 2330, mobId) >= 1);
    }

    private static int grantMissing(Character agent, PrimitiveCapabilityGateway gateway,
                                    List<Integer> itemIds) {
        int granted = 0;
        for (int itemId : itemIds) {
            if (gateway.itemCount(agent, itemId) < 1 && gateway.grantItem(agent, itemId, 1)) granted++;
        }
        return granted;
    }
}
