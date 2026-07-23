package server.agents.capabilities.townlife;

import java.awt.Point;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Read-only, per-town tuning data consumed by the town-agnostic TownLife engine. */
public record AgentTownLifeProfile(
        String profileId,
        int mapId,
        Geometry geometry,
        Distribution distribution,
        Extensions extensions,
        List<ArrivalPortal> arrivalPortals,
        List<RestSpot> restSpots,
        List<PointSpec> roamFallbackSpots,
        List<NpcSpot> npcSpots,
        List<Integer> shopMapIds,
        List<Venue> venues) {

    public AgentTownLifeProfile {
        if (profileId == null || profileId.isBlank() || mapId <= 0
                || geometry == null || distribution == null
                || arrivalPortals == null || arrivalPortals.isEmpty()) {
            throw new IllegalArgumentException("town-life profile identity and geometry are required");
        }
        extensions = extensions == null ? Extensions.generic() : extensions;
        arrivalPortals = List.copyOf(arrivalPortals);
        restSpots = List.copyOf(restSpots == null ? List.of() : restSpots);
        roamFallbackSpots = List.copyOf(roamFallbackSpots == null ? List.of() : roamFallbackSpots);
        npcSpots = List.copyOf(npcSpots == null ? List.of() : npcSpots);
        shopMapIds = List.copyOf(shopMapIds == null ? List.of() : shopMapIds);
        venues = List.copyOf(venues == null ? List.of() : venues);
        long uniqueVenueIds = venues.stream().map(Venue::id).distinct().count();
        if (uniqueVenueIds != venues.size()) {
            throw new IllegalArgumentException("town-life venue ids must be unique");
        }
    }

    public record Extensions(String arrival) {
        public Extensions {
            arrival = arrival == null || arrival.isBlank() ? "generic" : arrival;
        }

        static Extensions generic() {
            return new Extensions("generic");
        }
    }

    public List<Point> restPoints() {
        return restSpots.stream().map(RestSpot::point).toList();
    }

    public List<Point> roamFallbackPoints() {
        return roamFallbackSpots.stream().map(PointSpec::point).toList();
    }

    public int mapSeatId(Point point) {
        if (point == null) {
            return -1;
        }
        int authoredSeat = restSpots.stream()
                .filter(spot -> spot.seatId() >= 0 && spot.x() == point.x && spot.y() == point.y)
                .mapToInt(RestSpot::seatId)
                .findFirst()
                .orElse(-1);
        if (authoredSeat >= 0) {
            return authoredSeat;
        }
        return venues.stream()
                .flatMap(venue -> venue.spots().stream())
                .filter(spot -> spot.seatId() >= 0 && spot.x() == point.x && spot.y() == point.y)
                .mapToInt(VenueSpot::seatId)
                .findFirst()
                .orElse(-1);
    }

    public Optional<Venue> venue(String venueId) {
        if (venueId == null || venueId.isBlank()) {
            return Optional.empty();
        }
        return venues.stream().filter(candidate -> candidate.id().equals(venueId)).findFirst();
    }

    public List<Venue> venuesFor(AgentTownLifeState.Activity activity) {
        if (activity == null || activity == AgentTownLifeState.Activity.NONE) {
            return List.of();
        }
        Affordance affordance = Affordance.forActivity(activity);
        return affordance == null ? List.of() : venues.stream()
                .filter(venue -> venue.affordances().contains(affordance))
                .toList();
    }

    public int shopMapId(int index) {
        if (shopMapIds.isEmpty()) {
            return mapId;
        }
        return shopMapIds.get(Math.floorMod(index, shopMapIds.size()));
    }

    public String arrivalPortal(int identitySeed) {
        int total = arrivalPortals.stream().mapToInt(ArrivalPortal::weight).sum();
        int roll = Math.floorMod(identitySeed, Math.max(1, total));
        for (ArrivalPortal portal : arrivalPortals) {
            if (roll < portal.weight()) {
                return portal.name();
            }
            roll -= portal.weight();
        }
        return arrivalPortals.getLast().name();
    }

    public record Geometry(int upperMaxY,
                           int middleMaxY,
                           int miniPlatformMaxWidth,
                           int minimumPlatformWidth,
                           int edgeInsetPx,
                           int slotSpacingPx,
                           int maxSlotsPerPlatform) {
        public Geometry {
            if (middleMaxY < upperMaxY || miniPlatformMaxWidth <= 0
                    || minimumPlatformWidth <= 0 || edgeInsetPx < 0
                    || slotSpacingPx <= 0 || maxSlotsPerPlatform <= 0) {
                throw new IllegalArgumentException("town-life geometry is invalid");
            }
        }
    }

    public record Distribution(int upperWeight,
                               int middleWeight,
                               int lowerWeight,
                               int anyWeight,
                               int miniPlatformPercent,
                               int crossDistrictEveryActivities) {
        public Distribution {
            if (upperWeight < 0 || middleWeight < 0 || lowerWeight < 0 || anyWeight < 0
                    || upperWeight + middleWeight + lowerWeight + anyWeight <= 0
                    || miniPlatformPercent < 0 || miniPlatformPercent > 100
                    || crossDistrictEveryActivities <= 0) {
                throw new IllegalArgumentException("town-life distribution is invalid");
            }
        }
    }

    public record PointSpec(int x, int y) {
        public Point point() {
            return new Point(x, y);
        }
    }

    public record RestSpot(int x, int y, int seatId) {
        public Point point() {
            return new Point(x, y);
        }
    }

    public record NpcSpot(int npcId, int offsetX) {
    }

    public record ArrivalPortal(String name, int weight) {
        public ArrivalPortal {
            if (name == null || name.isBlank() || weight <= 0) {
                throw new IllegalArgumentException("town-life arrival portal is invalid");
            }
        }
    }

    /** Semantic action supported by a venue. Coordinates remain town profile data. */
    public enum Affordance {
        REST,
        SOCIAL,
        NPC_PAUSE,
        ROAM,
        SHOP_VISIT,
        WEAPON_FLOURISH,
        WAIT,
        SIGHTSEE;

        static Affordance forActivity(AgentTownLifeState.Activity activity) {
            try {
                return valueOf(activity.name().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    /** A named, capacity-bounded place that future controllers may select safely. */
    public record Venue(String id,
                        String label,
                        AgentTownLifeState.District district,
                        AgentTownLifeState.PlatformKind platformKind,
                        int capacity,
                        List<Affordance> affordances,
                        List<VenueSpot> spots,
                        int destinationMapId) {
        public Venue {
            if (id == null || id.isBlank() || capacity <= 0) {
                throw new IllegalArgumentException("town-life venue identity and capacity are required");
            }
            id = id.trim();
            label = label == null || label.isBlank() ? id : label.trim();
            district = district == null ? AgentTownLifeState.District.ANY : district;
            platformKind = platformKind == null ? AgentTownLifeState.PlatformKind.ANY : platformKind;
            affordances = List.copyOf(affordances == null ? List.of() : affordances);
            spots = List.copyOf(spots == null ? List.of() : spots);
            if (affordances.isEmpty() || (spots.isEmpty() && destinationMapId <= 0)
                    || (!spots.isEmpty() && capacity > spots.size())) {
                throw new IllegalArgumentException("town-life venue affordances, spots, and capacity are invalid");
            }
        }

        public boolean supports(AgentTownLifeState.Activity activity) {
            Affordance affordance = Affordance.forActivity(activity);
            return affordance != null && affordances.contains(affordance);
        }
    }

    public record VenueSpot(int x, int y, int seatId) {
        public Point point() {
            return new Point(x, y);
        }
    }
}
