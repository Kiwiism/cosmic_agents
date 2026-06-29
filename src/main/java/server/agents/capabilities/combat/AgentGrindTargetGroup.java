package server.agents.capabilities.combat;

import server.life.Monster;

public final class AgentGrindTargetGroup {
    private final int regionId;
    private int mobCount;
    private Monster bestMonster;
    private long bestLocalScore = Long.MAX_VALUE;
    private double bestDistanceSq = Double.MAX_VALUE;

    public AgentGrindTargetGroup(int regionId) {
        this.regionId = regionId;
    }

    public void add(Monster monster, long localScore, double distanceSq) {
        mobCount++;
        if (bestMonster == null
                || localScore < bestLocalScore
                || (localScore == bestLocalScore && distanceSq < bestDistanceSq)) {
            bestMonster = monster;
            bestLocalScore = localScore;
            bestDistanceSq = distanceSq;
        }
    }

    public int regionId() {
        return regionId;
    }

    public int mobCount() {
        return mobCount;
    }

    public Monster bestMonster() {
        return bestMonster;
    }

    public long bestLocalScore() {
        return bestLocalScore;
    }

    public double bestDistanceSq() {
        return bestDistanceSq;
    }
}
