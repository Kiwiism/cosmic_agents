package server.agents.capabilities.combat;

/** Splits incoming damage across HP and MP according to active defensive buffs. */
public final class AgentIncomingDamagePolicy {
    public record DamageSplit(int hpLoss, int mpLoss) {
    }

    private AgentIncomingDamagePolicy() {
    }

    public static DamageSplit splitMagicGuard(int damage, Integer magicGuardPercent, int currentMp) {
        int normalizedDamage = Math.max(0, damage);
        if (magicGuardPercent == null || magicGuardPercent <= 0) {
            return new DamageSplit(normalizedDamage, 0);
        }
        double ratio = Math.min(100, magicGuardPercent) / 100.0d;
        int mpLoss = (int) (normalizedDamage * ratio);
        int hpLoss = normalizedDamage - mpLoss;
        int availableMp = Math.max(0, currentMp);
        if (mpLoss > availableMp) {
            hpLoss += mpLoss - availableMp;
            mpLoss = availableMp;
        }
        return new DamageSplit(hpLoss, mpLoss);
    }
}
