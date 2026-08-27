package server.agents.progression;

/** Immutable World Director facts for the exact Pepe weapon selected by the current build. */
public record AgentPepeEquipmentSnapshot(
        int desiredWeaponItemId,
        String desiredWeaponType,
        boolean owned,
        boolean equipped,
        int remainingUpgradeSlots,
        int scrollItemId,
        int rewardSelectionIndex) {

    public static final AgentPepeEquipmentSnapshot NONE =
            new AgentPepeEquipmentSnapshot(0, "", false, false, 0, 0, -1);

    public AgentPepeEquipmentSnapshot {
        desiredWeaponType = desiredWeaponType == null ? "" : desiredWeaponType.trim();
        if (desiredWeaponItemId < 0 || remainingUpgradeSlots < 0 || scrollItemId < 0
                || rewardSelectionIndex < -1) {
            throw new IllegalArgumentException("valid Pepe equipment facts are required");
        }
    }

    public boolean scrollable() {
        return owned && remainingUpgradeSlots > 0 && scrollItemId > 0
                && rewardSelectionIndex >= 0;
    }
}
