package server.physics.foothold;

import server.physics.PhysicsBounds;
import server.physics.PhysicsTerrain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, bucketed terrain index derived from Journey FootholdTree.cpp.
 * Unlike Journey's per-pixel multimap, this uses fixed-width buckets to avoid large-map memory growth.
 */
public final class FootholdPhysicsIndex implements PhysicsTerrain {
    private static final int BUCKET_WIDTH = 64;
    private static final double HORIZONTAL_INSET = 25.0;
    private static final double TOP_MARGIN = 300.0;
    private static final double BOTTOM_MARGIN = 100.0;

    private final Map<Integer, FootholdSegment> byId;
    private final Map<Integer, List<FootholdSegment>> horizontalBuckets;
    private final PhysicsBounds bounds;

    public FootholdPhysicsIndex(Collection<FootholdSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("terrain requires at least one foothold");
        }
        Map<Integer, FootholdSegment> ids = new HashMap<>();
        Map<Integer, List<FootholdSegment>> buckets = new HashMap<>();
        double left = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (FootholdSegment segment : segments) {
            if (ids.putIfAbsent(segment.id(), segment) != null) {
                throw new IllegalArgumentException("duplicate foothold id " + segment.id());
            }
            left = Math.min(left, segment.left());
            right = Math.max(right, segment.right());
            top = Math.min(top, segment.top());
            bottom = Math.max(bottom, segment.bottom());
            if (!segment.wall()) {
                int first = bucket(segment.left());
                int last = bucket(segment.right());
                for (int bucket = first; bucket <= last; bucket++) {
                    buckets.computeIfAbsent(bucket, ignored -> new ArrayList<>()).add(segment);
                }
            }
        }
        Map<Integer, List<FootholdSegment>> immutableBuckets = new HashMap<>();
        buckets.forEach((key, value) -> immutableBuckets.put(key, List.copyOf(value)));
        byId = Collections.unmodifiableMap(ids);
        horizontalBuckets = Collections.unmodifiableMap(immutableBuckets);
        double inset = Math.min(HORIZONTAL_INSET, Math.max(0.0, (right - left) / 4.0));
        bounds = new PhysicsBounds(left + inset, right - inset, top - TOP_MARGIN, bottom + BOTTOM_MARGIN);
    }

    @Override
    public FootholdSegment foothold(int id) {
        return byId.get(id);
    }

    @Override
    public FootholdSegment findBelow(double x, double y) {
        FootholdSegment best = null;
        double bestY = bounds.bottom();
        for (FootholdSegment segment : horizontalBuckets.getOrDefault(bucket(x), List.of())) {
            if (!segment.containsX(x)) {
                continue;
            }
            double ground = segment.groundY(x);
            if (ground >= y && ground <= bestY) {
                best = segment;
                bestY = ground;
            }
        }
        return best;
    }

    @Override
    public double wallBoundary(int footholdId, boolean left, double y) {
        FootholdSegment current = foothold(footholdId);
        if (current == null) {
            return left ? bounds.left() : bounds.right();
        }
        double verticalTop = y - 50.0;
        double verticalBottom = y - 1.0;
        FootholdSegment adjacent = foothold(left ? current.previousId() : current.nextId());
        if (adjacent != null && adjacent.blocks(verticalTop, verticalBottom)) {
            return left ? current.left() : current.right();
        }
        FootholdSegment second = adjacent == null ? null
                : foothold(left ? adjacent.previousId() : adjacent.nextId());
        if (second != null && second.blocks(verticalTop, verticalBottom)) {
            return left ? adjacent.left() : adjacent.right();
        }
        return left ? bounds.left() : bounds.right();
    }

    @Override
    public double edgeBoundary(int footholdId, boolean left) {
        FootholdSegment current = foothold(footholdId);
        if (current == null) {
            return left ? bounds.left() : bounds.right();
        }
        FootholdSegment adjacent = foothold(left ? current.previousId() : current.nextId());
        if (adjacent == null) {
            return left ? current.left() : current.right();
        }
        FootholdSegment second = foothold(left ? adjacent.previousId() : adjacent.nextId());
        if (second == null) {
            return left ? adjacent.left() : adjacent.right();
        }
        return left ? bounds.left() : bounds.right();
    }

    @Override
    public PhysicsBounds bounds() {
        return bounds;
    }

    public int size() {
        return byId.size();
    }

    private static int bucket(double x) {
        return Math.floorDiv((int) Math.floor(x), BUCKET_WIDTH);
    }
}
