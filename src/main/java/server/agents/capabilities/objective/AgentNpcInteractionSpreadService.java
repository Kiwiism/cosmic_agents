package server.agents.capabilities.objective;

import client.Character;
import server.agents.capabilities.movement.AgentGroundingService;
import server.maps.Foothold;
import server.maps.MapleMap;
import server.maps.Rope;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds safe, reproducible NPC waiting slots on nearby footholds, ropes, and ladders. */
public final class AgentNpcInteractionSpreadService {
    private static final int EDGE_INSET_PX = config.AgentTuning.intValue("server.agents.capabilities.objective.AgentNpcInteractionSpreadService.EDGE_INSET_PX");
    private static final int SLOT_SPACING_PX = config.AgentTuning.intValue("server.agents.capabilities.objective.AgentNpcInteractionSpreadService.SLOT_SPACING_PX");
    private static final int CLIMB_SLOT_SPACING_PX = config.AgentTuning.intValue("server.agents.capabilities.objective.AgentNpcInteractionSpreadService.CLIMB_SLOT_SPACING_PX");
    private AgentNpcInteractionSpreadService() {
    }

    public static List<Point> candidates(Character agent, Point currentPosition,
                                  Point npcPosition, int interactionRangePx) {
        MapleMap map = agent == null ? null : agent.getMap();
        if (map == null || map.getFootholds() == null) {
            return List.of();
        }
        List<Foothold> footholds = new ArrayList<>();
        Foothold currentFoothold = AgentGroundingService.findGroundFoothold(map, currentPosition);
        if (currentFoothold != null) {
            footholds.add(currentFoothold);
        }
        for (Foothold foothold : map.getFootholds().getAllFootholds()) {
            if (currentFoothold == null || foothold.getId() != currentFoothold.getId()) {
                footholds.add(foothold);
            }
        }
        LinkedHashSet<Point> candidates = new LinkedHashSet<>(
                candidates(footholds, npcPosition, interactionRangePx));
        candidates.addAll(climbableCandidates(map.getRopes(), npcPosition, interactionRangePx));
        return List.copyOf(candidates);
    }

    public static List<Point> candidates(Foothold foothold, Point npcPosition, int interactionRangePx) {
        return foothold == null
                ? List.of()
                : candidates(List.of(foothold), npcPosition, interactionRangePx);
    }

    public static List<Point> candidates(List<Foothold> footholds, Point npcPosition, int interactionRangePx) {
        if (footholds == null || footholds.isEmpty()
                || npcPosition == null || interactionRangePx <= 0) {
            return List.of();
        }

        long rangeSquared = (long) interactionRangePx * interactionRangePx;
        Set<Point> candidates = new LinkedHashSet<>();
        for (Foothold foothold : footholds) {
            if (foothold == null || foothold.isWall()) {
                continue;
            }
            int minimumX = Math.min(foothold.getX1(), foothold.getX2()) + EDGE_INSET_PX;
            int maximumX = Math.max(foothold.getX1(), foothold.getX2()) - EDGE_INSET_PX;
            if (minimumX > maximumX) {
                continue;
            }
            int firstSampleX = Math.floorDiv(minimumX + SLOT_SPACING_PX - 1, SLOT_SPACING_PX)
                    * SLOT_SPACING_PX;
            for (int x = firstSampleX; x <= maximumX; x += SLOT_SPACING_PX) {
                Point candidate = new Point(x, footingY(foothold, x));
                if (candidate.distanceSq(npcPosition) <= rangeSquared) {
                    candidates.add(candidate);
                }
            }
        }
        return List.copyOf(candidates);
    }

    public static List<Point> climbableCandidates(List<Rope> ropes, Point npcPosition, int interactionRangePx) {
        if (ropes == null || ropes.isEmpty() || npcPosition == null || interactionRangePx <= 0) {
            return List.of();
        }
        long rangeSquared = (long) interactionRangePx * interactionRangePx;
        Set<Point> candidates = new LinkedHashSet<>();
        for (Rope rope : ropes) {
            int minimumY = rope.topY() + EDGE_INSET_PX;
            int maximumY = rope.bottomY() - EDGE_INSET_PX;
            if (minimumY > maximumY) {
                continue;
            }
            int centerY = Math.clamp(npcPosition.y, minimumY, maximumY);
            addClimbableCandidate(candidates, rope.x(), centerY,
                    npcPosition, rangeSquared, minimumY, maximumY);
            addClimbableCandidate(candidates, rope.x(), centerY - CLIMB_SLOT_SPACING_PX,
                    npcPosition, rangeSquared, minimumY, maximumY);
            addClimbableCandidate(candidates, rope.x(), centerY + CLIMB_SLOT_SPACING_PX,
                    npcPosition, rangeSquared, minimumY, maximumY);
        }
        return List.copyOf(candidates);
    }

    public static boolean isClimbableAnchor(Character agent, Point anchor) {
        MapleMap map = agent == null ? null : agent.getMap();
        if (map == null || map.getRopes() == null || anchor == null) {
            return false;
        }
        return map.getRopes().stream().anyMatch(rope -> rope.x() == anchor.x
                && anchor.y >= rope.topY() + EDGE_INSET_PX
                && anchor.y <= rope.bottomY() - EDGE_INSET_PX);
    }

    private static void addClimbableCandidate(Set<Point> candidates,
                                               int x,
                                               int y,
                                               Point npcPosition,
                                               long rangeSquared,
                                               int minimumY,
                                               int maximumY) {
        if (y < minimumY || y > maximumY) {
            return;
        }
        Point candidate = new Point(x, y);
        if (candidate.distanceSq(npcPosition) <= rangeSquared) {
            candidates.add(candidate);
        }
    }

    /**
     * Keeps the full spread available while giving the half nearest the Agent's
     * arrival position three times the selection weight.
     */
    public static List<Point> selectionPool(List<Point> candidates, Point arrivalPosition) {
        if (candidates == null || candidates.size() < 2 || arrivalPosition == null) {
            return candidates == null ? List.of() : List.copyOf(candidates);
        }
        List<Point> nearestFirst = candidates.stream()
                .sorted(Comparator.comparingDouble(arrivalPosition::distanceSq))
                .toList();
        int favoredCount = Math.max(1, (nearestFirst.size() + 1) / 2);
        ArrayList<Point> weighted = new ArrayList<>(nearestFirst.size() + favoredCount * 2);
        weighted.addAll(nearestFirst);
        for (int index = 0; index < favoredCount; index++) {
            weighted.add(nearestFirst.get(index));
            weighted.add(nearestFirst.get(index));
        }
        return List.copyOf(weighted);
    }

    private static int footingY(Foothold foothold, int x) {
        int deltaX = foothold.getX2() - foothold.getX1();
        if (deltaX == 0) {
            return foothold.getY1();
        }
        double ratio = (double) (x - foothold.getX1()) / deltaX;
        return (int) Math.round(foothold.getY1()
                + (foothold.getY2() - foothold.getY1()) * ratio);
    }

}
