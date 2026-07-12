package server.agents.plans.amherst;

import java.io.IOException;

public interface AmherstPlanProgressStore {
    AmherstPlanProgressSnapshot load(String planId, int characterId) throws IOException;

    void save(AmherstPlanProgressSnapshot snapshot) throws IOException;

    void delete(String planId, int characterId) throws IOException;
}
