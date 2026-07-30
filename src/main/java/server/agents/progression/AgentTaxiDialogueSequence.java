package server.agents.progression;

/**
 * Builds the selection sequences expected by Victoria Island taxi NPC scripts.
 *
 * <p>Phil in Lith Harbor has an extra menu before the destination list. Regular
 * town cabs open the destination list immediately, so sharing one raw sequence
 * between the two script families sends the destination choice at the wrong
 * dialogue status.</p>
 */
final class AgentTaxiDialogueSequence {
    private AgentTaxiDialogueSequence() {
    }

    static int[] lithHarborPhil(int destinationSelection) {
        requireSelection(destinationSelection);
        return new int[]{0, 1, destinationSelection, 0};
    }

    static int[] regularTownCab(int destinationSelection) {
        requireSelection(destinationSelection);
        return new int[]{0, destinationSelection, 0};
    }

    private static void requireSelection(int destinationSelection) {
        if (destinationSelection < 0) {
            throw new IllegalArgumentException("taxi destination selection must be non-negative");
        }
    }
}
