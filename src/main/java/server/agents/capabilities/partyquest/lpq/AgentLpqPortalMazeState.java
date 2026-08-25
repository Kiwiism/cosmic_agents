package server.agents.capabilities.partyquest.lpq;

import java.util.LinkedHashMap;
import java.util.Map;

/** Session-owned observations for LPQ Stage 6's authored number-portal maze. */
public final class AgentLpqPortalMazeState {
    private final Map<Integer, Integer> successfulPortalByRow = new LinkedHashMap<>();
    private final Map<Integer, Integer> nextCandidateByRow = new LinkedHashMap<>();
    private int currentRow;

    public synchronized void recordSuccess(int row, int portalId) {
        if (row < 0 || portalId < 0) throw new IllegalArgumentException("valid LPQ maze observation is required");
        successfulPortalByRow.put(row, portalId);
        currentRow = Math.max(currentRow, row + 1);
    }

    public synchronized Integer successfulPortal(int row) {
        return successfulPortalByRow.get(row);
    }

    public synchronized int nextCandidateOffset(int row) {
        return nextCandidateByRow.getOrDefault(row, 0);
    }

    public synchronized void recordFailure(int row) {
        if (row < 0) throw new IllegalArgumentException("valid LPQ maze row is required");
        nextCandidateByRow.put(row, (nextCandidateOffset(row) + 1) % 3);
    }

    public synchronized int currentRow() { return currentRow; }

    public synchronized Map<Integer, Integer> observations() {
        return Map.copyOf(successfulPortalByRow);
    }

    public synchronized void reset() {
        successfulPortalByRow.clear();
        nextCandidateByRow.clear();
        currentRow = 0;
    }
}
