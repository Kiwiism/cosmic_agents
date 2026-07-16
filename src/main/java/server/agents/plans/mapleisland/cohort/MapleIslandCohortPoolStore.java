package server.agents.plans.mapleisland.cohort;

import java.io.IOException;

public interface MapleIslandCohortPoolStore {
    MapleIslandCohortPoolSnapshot load() throws IOException;

    void save(MapleIslandCohortPoolSnapshot snapshot) throws IOException;
}
