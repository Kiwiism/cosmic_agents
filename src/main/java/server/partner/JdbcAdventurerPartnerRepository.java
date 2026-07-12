package server.partner;

import tools.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** JDBC adapter for Partner link and transition-journal persistence. */
public final class JdbcAdventurerPartnerRepository implements AdventurerPartnerRepository {
    public static final JdbcAdventurerPartnerRepository INSTANCE = new JdbcAdventurerPartnerRepository();

    private static final String LINK_COLUMNS = "id, account_id, world_id, first_character_id, "
            + "second_character_id, preferred_mode, enabled, created_at, updated_at";
    private static final String SESSION_COLUMNS = "id, link_id, player_actor_character_id, partner_character_id, "
            + "mode, current_profile_orientation, generation, lifecycle_status, activated_at, "
            + "last_transition_at, closed_at, failure_reason";

    private JdbcAdventurerPartnerRepository() {
    }

    @Override
    public List<PartnerRosterCandidate> findRosterCandidates(int accountId,
                                                             int worldId,
                                                             int excludedCharacterId) {
        String sql = "SELECT id, accountid, world, name, level, job, "
                + "EXISTS(SELECT 1 FROM worldtransfers wt WHERE wt.characterid = characters.id "
                + "AND wt.completionTime IS NULL) AS pending_transfer FROM characters "
                + "WHERE accountid = ? AND world = ? AND id <> ? ORDER BY name";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setInt(2, worldId);
            ps.setInt(3, excludedCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                List<PartnerRosterCandidate> candidates = new ArrayList<>();
                while (rs.next()) {
                    candidates.add(new PartnerRosterCandidate(
                            rs.getInt("id"),
                            rs.getInt("accountid"),
                            rs.getInt("world"),
                            rs.getString("name"),
                            rs.getInt("level"),
                            rs.getInt("job"),
                            rs.getBoolean("pending_transfer")));
                }
                return candidates;
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to load Partner roster", e);
        }
    }

    @Override
    public Optional<PartnerLink> findActiveLinkForCharacter(int characterId) {
        String sql = "SELECT " + LINK_COLUMNS + " FROM adventurer_partner_links "
                + "WHERE enabled = TRUE AND (first_character_id = ? OR second_character_id = ?) LIMIT 1";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, characterId);
            ps.setInt(2, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readLink(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to load Partner link for character " + characterId, e);
        }
    }

    @Override
    public Optional<PartnerRosterCandidate> findCharacter(int characterId) {
        String sql = "SELECT id, accountid, world, name, level, job, "
                + "EXISTS(SELECT 1 FROM worldtransfers wt WHERE wt.characterid = characters.id "
                + "AND wt.completionTime IS NULL) AS pending_transfer FROM characters WHERE id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, characterId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PartnerRosterCandidate(
                        rs.getInt("id"), rs.getInt("accountid"), rs.getInt("world"),
                        rs.getString("name"), rs.getInt("level"), rs.getInt("job"),
                        rs.getBoolean("pending_transfer")));
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to load Partner character " + characterId, e);
        }
    }

    @Override
    public PartnerLink registerLink(int requestingCharacterId,
                                    int partnerCharacterId,
                                    PartnerMode preferredMode) {
        if (requestingCharacterId == partnerCharacterId) {
            throw new IllegalArgumentException("A character cannot be its own Partner");
        }
        int firstCharacterId = Math.min(requestingCharacterId, partnerCharacterId);
        int secondCharacterId = Math.max(requestingCharacterId, partnerCharacterId);
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Map<Integer, CharacterOwner> owners = lockCharacterOwners(con, firstCharacterId, secondCharacterId);
                CharacterOwner requester = owners.get(requestingCharacterId);
                CharacterOwner partner = owners.get(partnerCharacterId);
                if (requester == null || partner == null) {
                    throw new PartnerPersistenceException("Both Partner characters must exist");
                }
                if (requester.accountId() != partner.accountId()) {
                    throw new PartnerPersistenceException("Partner characters must belong to the same account");
                }
                if (requester.worldId() != partner.worldId()) {
                    throw new PartnerPersistenceException("Partner characters must belong to the same world");
                }
                if (requester.pendingWorldTransfer() || partner.pendingWorldTransfer()) {
                    throw new PartnerPersistenceException(
                            "Partner characters cannot have a pending world transfer");
                }
                if (hasActiveLinkForEither(con, firstCharacterId, secondCharacterId)) {
                    throw new PartnerPersistenceException("One of the characters already has an active Partner link");
                }

                Optional<PartnerLink> priorPair = findLinkByPairForUpdate(
                        con, firstCharacterId, secondCharacterId);
                if (priorPair.isPresent()) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE adventurer_partner_links SET account_id = ?, world_id = ?, "
                                    + "preferred_mode = ?, enabled = TRUE WHERE id = ?")) {
                        ps.setInt(1, requester.accountId());
                        ps.setInt(2, requester.worldId());
                        ps.setString(3, preferredMode.name());
                        ps.setLong(4, priorPair.get().id());
                        requireSingleRow(ps.executeUpdate(), "Prior Partner pair could not be reactivated");
                    }
                    PartnerLink reactivated = findLinkById(con, priorPair.get().id())
                            .orElseThrow(() -> new SQLException("Reactivated Partner link could not be reloaded"));
                    con.commit();
                    return reactivated;
                }

                long linkId;
                String insert = "INSERT INTO adventurer_partner_links "
                        + "(account_id, world_id, first_character_id, second_character_id, preferred_mode) "
                        + "VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, requester.accountId());
                    ps.setInt(2, requester.worldId());
                    ps.setInt(3, firstCharacterId);
                    ps.setInt(4, secondCharacterId);
                    ps.setString(5, preferredMode.name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Partner link insert returned no generated key");
                        }
                        linkId = keys.getLong(1);
                    }
                }
                PartnerLink link = findLinkById(con, linkId)
                        .orElseThrow(() -> new SQLException("Inserted Partner link could not be reloaded"));
                con.commit();
                return link;
            } catch (RuntimeException | SQLException failure) {
                rollback(con, failure);
                if (failure instanceof PartnerPersistenceException persistenceFailure) {
                    throw persistenceFailure;
                }
                throw new PartnerPersistenceException("Failed to register Partner link", failure);
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to register Partner link", e);
        }
    }

    @Override
    public void updatePreferredMode(long linkId, PartnerMode preferredMode) {
        mutateInactiveLink(linkId, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE adventurer_partner_links SET preferred_mode = ? WHERE id = ? AND enabled = TRUE")) {
                ps.setString(1, preferredMode.name());
                ps.setLong(2, linkId);
                requireSingleRow(ps.executeUpdate(), "Partner link is not active");
            }
        });
    }

    @Override
    public void disableLink(long linkId) {
        mutateInactiveLink(linkId, con -> {
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE adventurer_partner_links SET enabled = FALSE WHERE id = ? AND enabled = TRUE")) {
                ps.setLong(1, linkId);
                requireSingleRow(ps.executeUpdate(), "Partner link is not active");
            }
        });
    }

    @Override
    public PartnerSessionRecord createSession(long linkId,
                                              int playerActorCharacterId,
                                              int partnerCharacterId,
                                              PartnerMode mode) {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                PartnerLink link = lockLink(con, linkId);
                if (!link.enabled() || !link.contains(playerActorCharacterId) || !link.contains(partnerCharacterId)
                        || playerActorCharacterId == partnerCharacterId) {
                    throw new PartnerPersistenceException("Session characters do not match the active Partner link");
                }
                if (hasOpenSessionForEither(con, playerActorCharacterId, partnerCharacterId)) {
                    throw new PartnerPersistenceException("A Partner session is already active for this pair");
                }

                long sessionId;
                String insert = "INSERT INTO adventurer_partner_sessions "
                        + "(link_id, player_actor_character_id, partner_character_id, mode) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = con.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, linkId);
                    ps.setInt(2, playerActorCharacterId);
                    ps.setInt(3, partnerCharacterId);
                    ps.setString(4, mode.name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Partner session insert returned no generated key");
                        }
                        sessionId = keys.getLong(1);
                    }
                }
                PartnerSessionRecord session = findSessionById(con, sessionId)
                        .orElseThrow(() -> new SQLException("Inserted Partner session could not be reloaded"));
                con.commit();
                return session;
            } catch (RuntimeException | SQLException failure) {
                rollback(con, failure);
                if (failure instanceof PartnerPersistenceException persistenceFailure) {
                    throw persistenceFailure;
                }
                throw new PartnerPersistenceException("Failed to create Partner session", failure);
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to create Partner session", e);
        }
    }

    @Override
    public void updateSession(long sessionId,
                              ProfileOrientation orientation,
                              long generation,
                              PartnerLifecycleStatus status,
                              String failureReason) {
        if (status.isTerminal()) {
            throw new IllegalArgumentException("Terminal Partner sessions must be closed through closeSession");
        }
        String sql = "UPDATE adventurer_partner_sessions SET current_profile_orientation = ?, generation = ?, "
                + "lifecycle_status = ?, last_transition_at = CURRENT_TIMESTAMP, failure_reason = ? "
                + "WHERE id = ? AND closed_at IS NULL AND generation <= ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, orientation.name());
            ps.setLong(2, generation);
            ps.setString(3, status.name());
            ps.setString(4, failureReason);
            ps.setLong(5, sessionId);
            ps.setLong(6, generation);
            int rows = ps.executeUpdate();
            if (rows > 1) {
                throw new PartnerPersistenceException("Partner session update affected multiple rows");
            }
            // Zero rows means a newer generation or terminal close won the race.
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to update Partner session " + sessionId, e);
        }
    }

    @Override
    public void closeSession(long sessionId,
                             ProfileOrientation orientation,
                             long generation,
                             PartnerLifecycleStatus terminalStatus,
                             String reason) {
        if (!terminalStatus.isTerminal()) {
            throw new IllegalArgumentException("Partner close status must be terminal");
        }
        String sql = "UPDATE adventurer_partner_sessions SET current_profile_orientation = ?, generation = ?, "
                + "lifecycle_status = ?, last_transition_at = CURRENT_TIMESTAMP, closed_at = CURRENT_TIMESTAMP, "
                + "failure_reason = ? WHERE id = ? AND closed_at IS NULL";
        executeSessionUpdate(sql, sessionId, orientation, generation, terminalStatus, reason);
    }

    @Override
    public int recoverOpenSessions(String reason) {
        String sql = "UPDATE adventurer_partner_sessions SET current_profile_orientation = 'CANONICAL', "
                + "generation = generation + 1, lifecycle_status = 'RECOVERED', "
                + "last_transition_at = CURRENT_TIMESTAMP, closed_at = CURRENT_TIMESTAMP, failure_reason = ? "
                + "WHERE closed_at IS NULL";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, reason);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to reconcile unfinished Partner sessions", e);
        }
    }

    private void executeSessionUpdate(String sql,
                                      long sessionId,
                                      ProfileOrientation orientation,
                                      long generation,
                                      PartnerLifecycleStatus status,
                                      String reason) {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, orientation.name());
            ps.setLong(2, generation);
            ps.setString(3, status.name());
            ps.setString(4, reason);
            ps.setLong(5, sessionId);
            requireSingleRow(ps.executeUpdate(), "Partner session is not open");
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to update Partner session " + sessionId, e);
        }
    }

    private void mutateInactiveLink(long linkId, SqlMutation mutation) {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                lockLink(con, linkId);
                if (hasOpenSessionForLink(con, linkId)) {
                    throw new PartnerPersistenceException(
                            "Partner registration or mode cannot change while a session is active");
                }
                mutation.apply(con);
                con.commit();
            } catch (RuntimeException | SQLException failure) {
                rollback(con, failure);
                if (failure instanceof PartnerPersistenceException persistenceFailure) {
                    throw persistenceFailure;
                }
                throw new PartnerPersistenceException("Failed to update Partner link " + linkId, failure);
            }
        } catch (SQLException e) {
            throw new PartnerPersistenceException("Failed to update Partner link " + linkId, e);
        }
    }

    private static Map<Integer, CharacterOwner> lockCharacterOwners(Connection con,
                                                                    int firstCharacterId,
                                                                    int secondCharacterId) throws SQLException {
        String sql = "SELECT id, accountid, world, "
                + "EXISTS(SELECT 1 FROM worldtransfers wt WHERE wt.characterid = characters.id "
                + "AND wt.completionTime IS NULL) AS pending_transfer "
                + "FROM characters WHERE id IN (?, ?) ORDER BY id FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, firstCharacterId);
            ps.setInt(2, secondCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<Integer, CharacterOwner> owners = new HashMap<>();
                while (rs.next()) {
                    CharacterOwner owner = new CharacterOwner(
                            rs.getInt("accountid"), rs.getInt("world"),
                            rs.getBoolean("pending_transfer"));
                    owners.put(rs.getInt("id"), owner);
                }
                return owners;
            }
        }
    }

    private static boolean hasActiveLinkForEither(Connection con,
                                                  int firstCharacterId,
                                                  int secondCharacterId) throws SQLException {
        String sql = "SELECT id FROM adventurer_partner_links WHERE enabled = TRUE AND "
                + "(first_character_id IN (?, ?) OR second_character_id IN (?, ?)) LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, firstCharacterId);
            ps.setInt(2, secondCharacterId);
            ps.setInt(3, firstCharacterId);
            ps.setInt(4, secondCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasOpenSessionForEither(Connection con,
                                                   int firstCharacterId,
                                                   int secondCharacterId) throws SQLException {
        String sql = "SELECT id FROM adventurer_partner_sessions WHERE closed_at IS NULL AND "
                + "(player_actor_character_id IN (?, ?) OR partner_character_id IN (?, ?)) LIMIT 1 FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, firstCharacterId);
            ps.setInt(2, secondCharacterId);
            ps.setInt(3, firstCharacterId);
            ps.setInt(4, secondCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean hasOpenSessionForLink(Connection con, long linkId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM adventurer_partner_sessions WHERE link_id = ? AND closed_at IS NULL LIMIT 1 FOR UPDATE")) {
            ps.setLong(1, linkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static PartnerLink lockLink(Connection con, long linkId) throws SQLException {
        String sql = "SELECT " + LINK_COLUMNS + " FROM adventurer_partner_links WHERE id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, linkId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new PartnerPersistenceException("Partner link " + linkId + " does not exist");
                }
                return readLink(rs);
            }
        }
    }

    private static Optional<PartnerLink> findLinkById(Connection con, long linkId) throws SQLException {
        String sql = "SELECT " + LINK_COLUMNS + " FROM adventurer_partner_links WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, linkId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readLink(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<PartnerLink> findLinkByPairForUpdate(Connection con,
                                                                  int firstCharacterId,
                                                                  int secondCharacterId) throws SQLException {
        String sql = "SELECT " + LINK_COLUMNS + " FROM adventurer_partner_links "
                + "WHERE first_character_id = ? AND second_character_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, firstCharacterId);
            ps.setInt(2, secondCharacterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readLink(rs)) : Optional.empty();
            }
        }
    }

    private static Optional<PartnerSessionRecord> findSessionById(Connection con, long sessionId) throws SQLException {
        String sql = "SELECT " + SESSION_COLUMNS + " FROM adventurer_partner_sessions WHERE id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(readSession(rs)) : Optional.empty();
            }
        }
    }

    private static PartnerLink readLink(ResultSet rs) throws SQLException {
        return new PartnerLink(
                rs.getLong("id"),
                rs.getInt("account_id"),
                rs.getInt("world_id"),
                rs.getInt("first_character_id"),
                rs.getInt("second_character_id"),
                PartnerMode.valueOf(rs.getString("preferred_mode")),
                rs.getBoolean("enabled"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static PartnerSessionRecord readSession(ResultSet rs) throws SQLException {
        Timestamp closedAt = rs.getTimestamp("closed_at");
        return new PartnerSessionRecord(
                rs.getLong("id"),
                rs.getLong("link_id"),
                rs.getInt("player_actor_character_id"),
                rs.getInt("partner_character_id"),
                PartnerMode.valueOf(rs.getString("mode")),
                ProfileOrientation.valueOf(rs.getString("current_profile_orientation")),
                rs.getLong("generation"),
                PartnerLifecycleStatus.valueOf(rs.getString("lifecycle_status")),
                rs.getTimestamp("activated_at").toInstant(),
                rs.getTimestamp("last_transition_at").toInstant(),
                closedAt == null ? null : closedAt.toInstant(),
                rs.getString("failure_reason"));
    }

    private static void requireSingleRow(int rowCount, String message) {
        if (rowCount != 1) {
            throw new PartnerPersistenceException(message);
        }
    }

    private static void rollback(Connection con, Throwable original) {
        try {
            con.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    @FunctionalInterface
    private interface SqlMutation {
        void apply(Connection con) throws SQLException;
    }

    private record CharacterOwner(int accountId, int worldId, boolean pendingWorldTransfer) {
    }
}
