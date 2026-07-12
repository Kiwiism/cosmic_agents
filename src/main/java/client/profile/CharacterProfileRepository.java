package client.profile;

import client.Character;

import java.sql.SQLException;

public interface CharacterProfileRepository {
    Character loadDetached(int ownerCharacterId, int world, int channel) throws SQLException;

    Character loadDetachedForValidation(int ownerCharacterId, int world, int channel) throws SQLException;

    void restoreTransientState(Character profileHolder);

    void storeTransientStateForLogout(Character profileHolder);

    void saveCanonical(Character profileHolder);
}
