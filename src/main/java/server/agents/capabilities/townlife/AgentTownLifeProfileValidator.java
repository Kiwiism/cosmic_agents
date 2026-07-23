package server.agents.capabilities.townlife;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure validation for authored town data; it has no runtime or movement dependencies. */
public final class AgentTownLifeProfileValidator {
    private AgentTownLifeProfileValidator() {
    }

    public static Validation validate(AgentTownLifeProfile profile) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Set<String> venueIds = new HashSet<>();
        Set<Integer> seatIds = new HashSet<>();
        Map<Integer, Point> nativeSeats = new HashMap<>();
        for (AgentTownLifeProfile.RestSpot spot : profile.restSpots()) {
            if (spot.seatId() >= 0 && !seatIds.add(spot.seatId())) {
                errors.add("duplicate native seat id " + spot.seatId());
            }
            if (spot.seatId() >= 0) {
                nativeSeats.put(spot.seatId(), spot.point());
            }
            if (!profile.allowsOccupancy(spot.point())) {
                errors.add("rest spot lies in excluded traffic zone at " + point(spot.point()));
            }
        }
        for (AgentTownLifeProfile.Venue venue : profile.venues()) {
            if (!venueIds.add(venue.id())) {
                errors.add("duplicate venue id " + venue.id());
            }
            Set<Point> points = new HashSet<>();
            for (AgentTownLifeProfile.VenueSpot spot : venue.spots()) {
                if (!points.add(spot.point())) {
                    errors.add("duplicate spot in venue " + venue.id() + " at " + point(spot.point()));
                }
                if (!profile.allowsOccupancy(spot.point())) {
                    errors.add("venue " + venue.id() + " lies in excluded traffic zone at "
                            + point(spot.point()));
                }
                if (spot.seatId() >= 0
                        && !spot.point().equals(nativeSeats.get(spot.seatId()))) {
                    errors.add("venue " + venue.id() + " references unknown or mismatched seat "
                            + spot.seatId() + " at " + point(spot.point()));
                }
            }
            if ((venue.affordances().contains(AgentTownLifeProfile.Affordance.SOCIAL)
                    || venue.affordances().contains(AgentTownLifeProfile.Affordance.WEAPON_FLOURISH))
                    && venue.capacity() < 2) {
                errors.add("social venue " + venue.id() + " requires capacity >= 2");
            }
            if (venue.affordances().contains(AgentTownLifeProfile.Affordance.SHOP_VISIT)
                    && venue.destinationMapId() <= 0) {
                errors.add("shop venue " + venue.id() + " requires a destination map");
            }
        }
        Set<String> zoneIds = new HashSet<>();
        for (AgentTownLifeProfile.TrafficZone zone : profile.trafficZones()) {
            if (!zoneIds.add(zone.id())) {
                errors.add("duplicate traffic-zone id " + zone.id());
            }
        }
        if (profile.venues().isEmpty()) {
            warnings.add("profile has no semantic venues");
        }
        if (profile.roamFallbackSpots().isEmpty()) {
            warnings.add("profile has no roam fallback spots");
        }
        if (profile.venuesFor(AgentTownLifeState.Activity.REST).isEmpty()) {
            warnings.add("profile has no REST venue");
        }
        return new Validation(profile.profileId(), profile.mapId(),
                List.copyOf(errors), List.copyOf(warnings));
    }

    public static void requireValid(AgentTownLifeProfile profile) {
        Validation result = validate(profile);
        if (!result.valid()) {
            throw new IllegalArgumentException("invalid town-life profile "
                    + profile.profileId() + ": " + String.join("; ", result.errors()));
        }
    }

    private static String point(Point point) {
        return point.x + "," + point.y;
    }

    public record Validation(String profileId,
                             int mapId,
                             List<String> errors,
                             List<String> warnings) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }
}
