package server.agents.capabilities.shop;

import server.maps.Foothold;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

public final class AgentShopApproachPolicy {
    private AgentShopApproachPolicy() {
    }

    public static int manhattan(Point a, Point b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static List<Point> footholdCandidatesNear(Point npcPos,
                                                     Iterable<Foothold> footholds,
                                                     int manhattanRadius) {
        List<Point> candidates = new ArrayList<>();
        if (npcPos == null || footholds == null) {
            return candidates;
        }
        for (Foothold foothold : footholds) {
            addFootholdCandidates(candidates, npcPos, foothold, manhattanRadius);
        }
        return candidates;
    }

    private static void addFootholdCandidates(List<Point> candidates,
                                              Point npcPos,
                                              Foothold foothold,
                                              int manhattanRadius) {
        int fx1 = foothold.getX1();
        int fy1 = foothold.getY1();
        int fx2 = foothold.getX2();
        int fy2 = foothold.getY2();
        if (fx1 == fx2) {
            return;
        }

        int xMin = Math.min(fx1, fx2);
        int xMax = Math.max(fx1, fx2);
        int step = Math.max(1, (xMax - xMin) / 20);
        for (int x = xMin; x <= xMax; x += step) {
            double t = (double) (x - fx1) / (fx2 - fx1);
            int y = (int) (fy1 + t * (fy2 - fy1));
            Point candidate = new Point(x, y);
            if (manhattan(candidate, npcPos) <= manhattanRadius) {
                candidates.add(candidate);
            }
        }
    }
}
