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
            if ((venue.affordances().contains(AgentTownLifeProfile.Affordance.SOCIALIZE)
                    || venue.affordances().contains(AgentTownLifeProfile.Affordance.SHOW_OFF))
                    && venue.capacity() < 2) {
                errors.add("social venue " + venue.id() + " requires capacity >= 2");
            }
            if (venue.affordances().contains(AgentTownLifeProfile.Affordance.BROWSE)
                    && venue.spots().isEmpty()) {
                errors.add("browse venue " + venue.id() + " requires a local approach spot");
            }
        }
        Set<String> zoneIds = new HashSet<>();
        for (AgentTownLifeProfile.TrafficZone zone : profile.trafficZones()) {
            if (!zoneIds.add(zone.id())) {
                errors.add("duplicate traffic-zone id " + zone.id());
            }
        }
        Set<String> platformPolicyIds = new HashSet<>();
        for (AgentTownLifeProfile.PlatformPolicy policy : profile.platformPolicies()) {
            if (!platformPolicyIds.add(policy.id())) {
                errors.add("duplicate platform-policy id " + policy.id());
            }
        }
        Set<String> facilityIds = new HashSet<>();
        for (AgentTownLifeProfile.Facility facility : profile.facilities()) {
            if (!facilityIds.add(facility.id())) {
                errors.add("duplicate facility id " + facility.id());
            }
        }
        Set<String> hotspotIds = new HashSet<>();
        for (AgentTownLifeProfile.Hotspot hotspot : profile.hotspots()) {
            if (!hotspotIds.add(hotspot.id())) {
                errors.add("duplicate hotspot id " + hotspot.id());
            }
            if (!venueIds.contains(hotspot.venueId())) {
                errors.add("hotspot " + hotspot.id() + " references unknown venue "
                        + hotspot.venueId());
            }
        }
        Set<String> handlers = AgentTownLifeActivityExtensionRegistry.defaultRegistry().ids();
        for (String handler : profile.extensions().activityHandlers()) {
            if (!handlers.contains(handler)) {
                errors.add("unknown activity extension " + handler);
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
