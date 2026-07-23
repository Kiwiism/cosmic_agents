package server.agents.capabilities.townlife;

import java.awt.Point;

/** Map-authored classification seam used by the generic town-life destination engine. */
interface AgentTownLifeMapExtension {
    AgentTownLifeState.District classify(Point point);

    default AgentTownLifeState.PlatformKind classifyPlatform(int width) {
        return width <= 260
                ? AgentTownLifeState.PlatformKind.MINI
                : AgentTownLifeState.PlatformKind.MAIN;
    }
}
