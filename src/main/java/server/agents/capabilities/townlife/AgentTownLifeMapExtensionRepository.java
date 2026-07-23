package server.agents.capabilities.townlife;

import java.awt.Point;

/** Selects optional town-specific geography without coupling it to town-life behavior. */
final class AgentTownLifeMapExtensionRepository {
    private static final AgentTownLifeMapExtension GENERIC =
            point -> AgentTownLifeState.District.ANY;

    private AgentTownLifeMapExtensionRepository() {
    }

    static AgentTownLifeMapExtension forMap(int mapId) {
        return AgentTownLifeProfileRepository.defaultRepository().find(mapId)
                .<AgentTownLifeMapExtension>map(profile -> new ProfileExtension(profile.geometry()))
                .orElse(GENERIC);
    }

    private record ProfileExtension(AgentTownLifeProfile.Geometry geometry)
            implements AgentTownLifeMapExtension {
        @Override
        public AgentTownLifeState.District classify(Point point) {
            if (point == null) {
                return AgentTownLifeState.District.ANY;
            }
            if (point.y <= geometry.upperMaxY()) {
                return AgentTownLifeState.District.UPPER;
            }
            if (point.y <= geometry.middleMaxY()) {
                return AgentTownLifeState.District.MIDDLE;
            }
            return AgentTownLifeState.District.LOWER;
        }

        @Override
        public AgentTownLifeState.PlatformKind classifyPlatform(int width) {
            return width <= geometry.miniPlatformMaxWidth()
                    ? AgentTownLifeState.PlatformKind.MINI
                    : AgentTownLifeState.PlatformKind.MAIN;
        }
    }
}
