package server.maps.reservation;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public final class CharacterSpaceReservationRuntime {
    private record SpaceKey(CharacterSpaceScope scope, String catalogId, int spotNumber) {
    }

    private static final Map<SpaceKey, CharacterSpaceOwner> OWNER_BY_SPACE = new HashMap<>();
    private static final Map<CharacterSpaceOwner, CharacterSpaceReservation> RESERVATION_BY_OWNER = new HashMap<>();

    private CharacterSpaceReservationRuntime() {
    }

    public static synchronized Optional<CharacterSpaceReservation> reserveRandom(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            int footprintSlots) {
        return reserveRandom(scope, owner, candidates, footprintSlots, ignored -> true);
    }

    public static synchronized Optional<CharacterSpaceReservation> reserveRandom(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            int footprintSlots,
            Predicate<Point> positionAvailable) {
        if (!validRequest(scope, owner, candidates, footprintSlots, positionAvailable)) {
            return Optional.empty();
        }
        Optional<CharacterSpaceReservation> existing = matchingReservation(scope, owner, candidates);
        if (existing.isPresent()) {
            return existing;
        }
        release(owner);

        int start = ThreadLocalRandom.current().nextInt(candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            CharacterSpace candidate = candidates.get((start + offset) % candidates.size());
            Optional<CharacterSpaceReservation> reservation = tryReserve(
                    scope, owner, candidates, candidate, footprintSlots, positionAvailable);
            if (reservation.isPresent()) {
                return reservation;
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<CharacterSpaceReservation> reserveNearestLeftOrRight(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            Point origin,
            int maximumDistancePx,
            int footprintSlots,
            Predicate<Point> positionAvailable) {
        if (!validRequest(scope, owner, candidates, footprintSlots, positionAvailable)
                || origin == null || maximumDistancePx < 0) {
            return Optional.empty();
        }
        Optional<CharacterSpaceReservation> existing = matchingReservation(scope, owner, candidates);
        if (existing.isPresent()) {
            return existing;
        }
        release(owner);

        Comparator<CharacterSpace> byDistance = Comparator.comparingDouble(
                candidate -> candidate.position().distanceSq(origin));
        CharacterSpace left = candidates.stream()
                .filter(candidate -> candidate.x() <= origin.x)
                .min(byDistance)
                .orElse(null);
        CharacterSpace right = candidates.stream()
                .filter(candidate -> candidate.x() >= origin.x)
                .min(byDistance)
                .orElse(null);

        Set<CharacterSpace> nearestSides = new LinkedHashSet<>();
        if (left != null) {
            nearestSides.add(left);
        }
        if (right != null) {
            nearestSides.add(right);
        }
        List<CharacterSpace> ordered = new ArrayList<>(nearestSides);
        ordered.sort(byDistance);
        long maximumDistanceSquared = (long) maximumDistancePx * maximumDistancePx;
        for (CharacterSpace candidate : ordered) {
            if (candidate.position().distanceSq(origin) > maximumDistanceSquared) {
                continue;
            }
            Optional<CharacterSpaceReservation> reservation = tryReserve(
                    scope, owner, candidates, candidate, footprintSlots, positionAvailable);
            if (reservation.isPresent()) {
                return reservation;
            }
        }
        return Optional.empty();
    }

    public static synchronized Optional<CharacterSpaceReservation> reserveExact(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            CharacterSpace candidate,
            int footprintSlots) {
        return reserveExact(scope, owner, candidates, candidate, footprintSlots, ignored -> true);
    }

    public static synchronized Optional<CharacterSpaceReservation> reserveExact(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            CharacterSpace candidate,
            int footprintSlots,
            Predicate<Point> positionAvailable) {
        if (!validRequest(scope, owner, candidates, footprintSlots, positionAvailable)
                || candidate == null || !candidates.contains(candidate)) {
            return Optional.empty();
        }
        release(owner);
        return tryReserve(scope, owner, candidates, candidate, footprintSlots, positionAvailable);
    }

    public static synchronized Optional<CharacterSpaceReservation> reservation(CharacterSpaceOwner owner) {
        return Optional.ofNullable(RESERVATION_BY_OWNER.get(owner));
    }

    public static synchronized void release(CharacterSpaceOwner owner) {
        if (owner == null) {
            return;
        }
        CharacterSpaceReservation reservation = RESERVATION_BY_OWNER.remove(owner);
        if (reservation == null) {
            return;
        }
        for (CharacterSpace space : reservation.occupiedSpaces()) {
            OWNER_BY_SPACE.remove(key(reservation.scope(), space), owner);
        }
    }

    public static synchronized int occupiedCount() {
        return OWNER_BY_SPACE.size();
    }

    public static synchronized void clear() {
        OWNER_BY_SPACE.clear();
        RESERVATION_BY_OWNER.clear();
    }

    private static Optional<CharacterSpaceReservation> matchingReservation(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates) {
        CharacterSpaceReservation existing = RESERVATION_BY_OWNER.get(owner);
        if (existing != null && existing.scope().equals(scope)
                && candidates.contains(existing.centerSpace())) {
            return Optional.of(existing);
        }
        return Optional.empty();
    }

    private static Optional<CharacterSpaceReservation> tryReserve(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            CharacterSpace candidate,
            int footprintSlots,
            Predicate<Point> positionAvailable) {
        List<CharacterSpace> footprint = footprint(candidates, candidate, footprintSlots);
        if (footprint.size() != footprintSlots) {
            return Optional.empty();
        }
        for (CharacterSpace space : footprint) {
            if (OWNER_BY_SPACE.containsKey(key(scope, space))) {
                return Optional.empty();
            }
        }
        Point position = centeredPosition(footprint);
        if (!positionAvailable.test(position)) {
            return Optional.empty();
        }

        CharacterSpaceReservation reservation = new CharacterSpaceReservation(
                scope, owner, candidate, position, footprint);
        for (CharacterSpace space : footprint) {
            OWNER_BY_SPACE.put(key(scope, space), owner);
        }
        RESERVATION_BY_OWNER.put(owner, reservation);
        return Optional.of(reservation);
    }

    private static List<CharacterSpace> footprint(
            List<CharacterSpace> candidates,
            CharacterSpace candidate,
            int footprintSlots) {
        int firstSlot = candidate.slotIndex() - (footprintSlots - 1) / 2;
        List<CharacterSpace> result = candidates.stream()
                .filter(space -> space.catalogId().equals(candidate.catalogId())
                        && space.mapId() == candidate.mapId()
                        && space.rowId() == candidate.rowId()
                        && space.slotIndex() >= firstSlot
                        && space.slotIndex() < firstSlot + footprintSlots)
                .sorted(Comparator.comparingInt(CharacterSpace::slotIndex))
                .toList();
        if (result.size() != footprintSlots) {
            return List.of();
        }
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).slotIndex() != firstSlot + i) {
                return List.of();
            }
        }
        return result;
    }

    private static Point centeredPosition(List<CharacterSpace> footprint) {
        long x = 0;
        long y = 0;
        for (CharacterSpace space : footprint) {
            x += space.x();
            y += space.y();
        }
        return new Point((int) (x / footprint.size()), (int) (y / footprint.size()));
    }

    private static SpaceKey key(CharacterSpaceScope scope, CharacterSpace space) {
        return new SpaceKey(scope, space.catalogId(), space.spotNumber());
    }

    private static boolean validRequest(
            CharacterSpaceScope scope,
            CharacterSpaceOwner owner,
            List<CharacterSpace> candidates,
            int footprintSlots,
            Predicate<Point> positionAvailable) {
        return scope != null && owner != null && candidates != null && !candidates.isEmpty()
                && footprintSlots > 0 && positionAvailable != null
                && candidates.stream().allMatch(space -> space.mapId() == scope.mapId());
    }
}
