package server.agents.field;

import client.Character;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;

/** Catalog boundary for map farming geometry. */
public interface AgentFarmingCellCatalog {
    List<AgentFarmingCell> cells(AgentRuntimeEntry entry, Character agent);
}
