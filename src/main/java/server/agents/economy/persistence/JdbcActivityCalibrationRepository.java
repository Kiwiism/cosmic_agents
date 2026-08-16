package server.agents.economy.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import server.agents.economy.activity.ActivityCalibration;
import server.agents.economy.activity.ActivityCalibrationRepository;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class JdbcActivityCalibrationRepository implements ActivityCalibrationRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<Integer, Integer>> COUNTS = new TypeReference<>() { };
    private final DataSource dataSource;

    public JdbcActivityCalibrationRepository(DataSource dataSource) { this.dataSource = Objects.requireNonNull(dataSource); }

    @Override
    public Optional<ActivityCalibration> find(String build, int mapId, int level,
                                               String jobFamily, int minimumSamples) {
        String sql = "SELECT * FROM activity_calibration_sample WHERE agent_build = ? AND map_id = ? "
                + "AND job_family = ? AND level BETWEEN ? AND ? ORDER BY completed_at DESC LIMIT 200";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, build);
            statement.setInt(2, mapId);
            statement.setString(3, jobFamily);
            statement.setInt(4, Math.max(1, level - 2));
            statement.setInt(5, level + 2);
            List<Sample> samples = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) samples.add(new Sample(
                        rows.getTimestamp("started_at").toInstant(),
                        rows.getTimestamp("completed_at").toInstant(),
                        JSON.readValue(rows.getString("kill_counts"), COUNTS),
                        JSON.readValue(rows.getString("consumed_items"), COUNTS),
                        rows.getBoolean("died")));
            }
            if (samples.size() < minimumSamples) return Optional.empty();
            return Optional.of(aggregate(build, mapId, level, jobFamily, samples));
        } catch (Exception failure) {
            throw new EconomyPersistenceException("Could not read activity calibration", failure);
        }
    }

    private static ActivityCalibration aggregate(String build, int mapId, int level,
                                                  String job, List<Sample> samples) {
        double totalMinutes = samples.stream().mapToDouble(Sample::minutes).sum();
        Map<Integer, Integer> kills = merge(samples, true);
        Map<Integer, Integer> consumed = merge(samples, false);
        double totalKills = kills.values().stream().mapToInt(Integer::intValue).sum();
        Map<Integer, Double> shares = new LinkedHashMap<>();
        kills.forEach((id, count) -> shares.put(id, totalKills == 0 ? 0 : count / totalKills));
        Map<Integer, Double> useRates = new LinkedHashMap<>();
        consumed.forEach((id, count) -> useRates.put(id, count / totalMinutes));
        long deaths = samples.stream().filter(Sample::died).count();
        double hours = totalMinutes / 60d;
        return new ActivityCalibration("observed:" + build + ":" + mapId + ":" + job,
                build, mapId, level, job, samples.stream().map(Sample::completed).max(Instant::compareTo).orElseThrow(),
                samples.size(), totalKills / totalMinutes, shares, useRates,
                hours == 0 ? 0 : Math.min(1, deaths / hours));
    }

    private static Map<Integer, Integer> merge(List<Sample> samples, boolean kill) {
        Map<Integer, Integer> merged = new TreeMap<>();
        for (Sample sample : samples) (kill ? sample.kills : sample.consumed)
                .forEach((id, count) -> merged.merge(id, count, Math::addExact));
        return merged;
    }

    private record Sample(Instant started, Instant completed, Map<Integer, Integer> kills,
                          Map<Integer, Integer> consumed, boolean died) {
        double minutes() { return java.time.Duration.between(started, completed).toMillis() / 60_000d; }
    }
}
