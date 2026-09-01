package server.agents.capabilities.partyquest.opq;

import java.util.Arrays;

/** Session-shared discoveries for authored trial portals; it never reads hidden event solutions. */
public final class AgentOpqPortalRoute {
    private final int[] correctChoice;
    private final int[] nextTrial;

    public AgentOpqPortalRoute(int rows, int choicesPerRow) {
        if (rows <= 0 || choicesPerRow <= 1) throw new IllegalArgumentException("valid portal route dimensions required");
        correctChoice = new int[rows];
        nextTrial = new int[rows];
        Arrays.fill(correctChoice, -1);
        Arrays.fill(nextTrial, 0);
        this.choicesPerRow = choicesPerRow;
    }

    private final int choicesPerRow;

    public synchronized int choice(int row) {
        validateRow(row);
        return correctChoice[row] >= 0 ? correctChoice[row] : nextTrial[row];
    }

    public synchronized void observe(int row, int choice, boolean advanced) {
        validateRow(row);
        if (choice < 0 || choice >= choicesPerRow) throw new IllegalArgumentException("invalid portal choice");
        if (advanced) correctChoice[row] = choice;
        else if (correctChoice[row] < 0 && nextTrial[row] == choice) {
            nextTrial[row] = (nextTrial[row] + 1) % choicesPerRow;
        }
    }

    public synchronized boolean solved(int row) { validateRow(row); return correctChoice[row] >= 0; }
    public synchronized int solvedChoice(int row) { validateRow(row); return correctChoice[row]; }
    private void validateRow(int row) {
        if (row < 0 || row >= correctChoice.length) throw new IllegalArgumentException("invalid portal row");
    }
}
