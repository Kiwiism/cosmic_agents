package server.agents.economy.experiment;

import java.util.List;

public final class EconomyExperimentManifest {
    public int schemaVersion;
    public String experimentId;
    public String description;
    public String design;
    public List<Pair> pairs;

    public static final class Pair {
        public String pairId;
        public long seed;
        public String baselineConfig;
        public String candidateConfig;
    }
}
