package server.agents.capabilities.equipment;

import client.Job;
import client.inventory.Equip;

public final class AgentEquipmentScoringPolicy {
    private AgentEquipmentScoringPolicy() {
    }

    public static int usefulStatSum(Equip equip, Job job) {
        return AgentEquipmentReservePolicy.usefulStatSum(equip, job);
    }

    /**
     * Expected per-hit damage when each roll is {@code uniform[rawMax/2, rawMax] - wdef} clamped to 1.
     */
    public static double expectedDamageAfterDef(int rawMax, int wdef) {
        if (rawMax <= 0) {
            return 1.0;
        }
        double rawMin = rawMax * 0.5;
        if (wdef <= rawMin) {
            return Math.max(1.0, (rawMin + rawMax) / 2.0 - wdef);
        }
        if (wdef >= rawMax) {
            return 1.0;
        }
        double range = rawMax - rawMin;
        double clampedFraction = (wdef - rawMin) / range;
        double aboveTail = rawMax - wdef;
        double aboveContribution = (aboveTail * aboveTail) / (2.0 * range);
        return Math.max(1.0, clampedFraction + aboveContribution);
    }
}
