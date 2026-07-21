package server.agents.catalog.decision;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Immutable, versioned snapshot of the decision catalogs used by live Agent queries. */
public record AgentDecisionCatalogSnapshot(
        Version version,
        Map<Integer, MapTopology> navigationByMapId,
        Map<Integer, CombatMapPolicy> combatByMapId) {

    public AgentDecisionCatalogSnapshot {
        if (version == null || navigationByMapId == null || combatByMapId == null) {
            throw new IllegalArgumentException("Decision catalog version and indexes are required");
        }
        navigationByMapId = Map.copyOf(navigationByMapId);
        combatByMapId = Map.copyOf(combatByMapId);
    }

    public Optional<MapTopology> navigation(int mapId) {
        return Optional.ofNullable(navigationByMapId.get(mapId));
    }

    public Optional<CombatMapPolicy> combat(int mapId) {
        return Optional.ofNullable(combatByMapId.get(mapId));
    }

    public record Version(int schemaVersion,
                          String generatedAt,
                          boolean allRegions,
                          List<String> regions,
                          Map<String, Integer> counts) {
        public Version {
            if (schemaVersion <= 0 || generatedAt == null || generatedAt.isBlank()
                    || regions == null || counts == null) {
                throw new IllegalArgumentException("Valid decision catalog version metadata is required");
            }
            regions = List.copyOf(regions);
            counts = Map.copyOf(counts);
        }
    }

    public record Point(int x, int y) {
    }

    public record Bounds(int minX, int maxX, int minY, int maxY) {
        public Bounds {
            if (minX > maxX || minY > maxY) {
                throw new IllegalArgumentException("Topology bounds must be ordered");
            }
        }
    }

    public record Foothold(int footholdId,
                           int componentId,
                           int x1,
                           int y1,
                           int x2,
                           int y2,
                           boolean wall) {
    }

    public record Component(int componentId,
                            Bounds bounds,
                            Point center,
                            Point safePoint,
                            List<Integer> footholdIds) {
        public Component {
            if (componentId <= 0 || bounds == null || center == null || footholdIds == null) {
                throw new IllegalArgumentException("Valid topology component facts are required");
            }
            footholdIds = List.copyOf(footholdIds);
        }
    }

    public record Transition(String type,
                             int fromComponentId,
                             int toComponentId,
                             boolean bidirectional) {
        public Transition {
            if (type == null || type.isBlank() || fromComponentId <= 0 || toComponentId <= 0) {
                throw new IllegalArgumentException("Valid transition candidate facts are required");
            }
        }

        public boolean connects(int firstComponentId, int secondComponentId) {
            return fromComponentId == firstComponentId && toComponentId == secondComponentId
                    || bidirectional && fromComponentId == secondComponentId
                    && toComponentId == firstComponentId;
        }
    }

    public record MapTopology(int mapId,
                              String mapName,
                              Map<Integer, Foothold> footholdsById,
                              Map<Integer, Component> componentsById,
                              List<Transition> transitions) {
        public MapTopology {
            if (mapId < 0 || mapName == null || footholdsById == null
                    || componentsById == null || transitions == null) {
                throw new IllegalArgumentException("Valid map topology facts are required");
            }
            footholdsById = Map.copyOf(footholdsById);
            componentsById = Map.copyOf(componentsById);
            transitions = List.copyOf(transitions);
        }
    }

    public record CombatAnchor(String anchorId,
                               int componentId,
                               Point center,
                               int spawnCount,
                               Set<Integer> mobIds) {
        public CombatAnchor {
            if (anchorId == null || anchorId.isBlank() || componentId <= 0 || center == null
                    || spawnCount < 0 || mobIds == null) {
                throw new IllegalArgumentException("Valid combat anchor facts are required");
            }
            mobIds = Set.copyOf(mobIds);
        }
    }

    public record PartitionGroup(int slot, List<String> anchorIds) {
        public PartitionGroup {
            if (slot <= 0 || anchorIds == null) {
                throw new IllegalArgumentException("Valid combat partition group is required");
            }
            anchorIds = List.copyOf(anchorIds);
        }
    }

    public record CombatPartition(int partySize, List<PartitionGroup> groups) {
        public CombatPartition {
            if (partySize <= 0 || groups == null) {
                throw new IllegalArgumentException("Valid combat partition is required");
            }
            groups = List.copyOf(groups);
        }
    }

    public record CombatMapPolicy(int mapId,
                                  int recommendedAgents,
                                  int maximumAgents,
                                  Map<String, CombatAnchor> anchorsById,
                                  Map<Integer, CombatPartition> partitionsByPartySize) {
        public CombatMapPolicy {
            if (mapId < 0 || recommendedAgents < 1 || maximumAgents < recommendedAgents
                    || anchorsById == null || partitionsByPartySize == null) {
                throw new IllegalArgumentException("Valid combat map policy is required");
            }
            anchorsById = Map.copyOf(anchorsById);
            partitionsByPartySize = Map.copyOf(partitionsByPartySize);
        }
    }
}
