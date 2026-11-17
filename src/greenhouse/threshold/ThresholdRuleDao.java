package greenhouse.threshold;

import device.dao.Db;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class ThresholdRuleDao {
    public record EffectiveRule(long id, BigDecimal lowerLimit, BigDecimal upperLimit, String unit) {}

    public Optional<EffectiveRule> findEffective(long greenhouseId, String metricType) throws SQLException {
        String sql = """
                SELECT id, lower_limit, upper_limit, unit
                FROM threshold_rule
                WHERE greenhouse_id = ? AND metric_type = ? AND active_slot = 1
                """;
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, greenhouseId);
            statement.setString(2, metricType);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return Optional.of(new EffectiveRule(results.getLong("id"),
                            results.getBigDecimal("lower_limit"), results.getBigDecimal("upper_limit"),
                            results.getString("unit")));
                }
                return Optional.empty();
            }
        }
    }
    public void insert(String ruleName, long greenhouseId, String metricType,
                       String unit, String lowerText, String upperText,
                       long userId) throws SQLException {
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }

        var limits = ThresholdBounds.parse(lowerText, upperText);

        String sql = """
            INSERT INTO threshold_rule
                (rule_name, greenhouse_id, metric_type, unit,
                 lower_limit, upper_limit, created_by, updated_by)
            SELECT ?, id, ?, ?, ?, ?, ?, ? FROM greenhouse_file WHERE id = ? AND is_deleted = 0 AND status = 'enabled'
            """;

        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ruleName.trim());
            statement.setString(2, metricType);
            statement.setString(3, unit);
            statement.setBigDecimal(4, limits.lower());
            statement.setBigDecimal(5, limits.upper());
            statement.setLong(6, userId);
            statement.setLong(7, userId);
            statement.setLong(8, greenhouseId);
            if (statement.executeUpdate() == 0) {
                throw new IllegalArgumentException("所选大棚不存在、已停用或已删除，请重新选择");
            }
        }
    }
    public record RuleRow(long id, String name, BigDecimal lower, BigDecimal upper,
                          String unit, boolean enabled, long greenhouseId, String metricType, String remark, String updatedAt) {}

    public java.util.List<RuleRow> listRules() throws SQLException {
        String sql = """
            SELECT id, rule_name, lower_limit, upper_limit, unit, enabled, greenhouse_id, metric_type, remark, updated_at
            FROM threshold_rule
            WHERE is_deleted = 0
            ORDER BY updated_at DESC, id DESC
            """;

        var rules = new java.util.ArrayList<RuleRow>();
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                rules.add(new RuleRow(
                        results.getLong("id"),
                        results.getString("rule_name"),
                        results.getBigDecimal("lower_limit"),
                        results.getBigDecimal("upper_limit"),
                        results.getString("unit"),
                        results.getBoolean("enabled"), results.getLong("greenhouse_id"),
                        results.getString("metric_type"), results.getString("remark"), results.getString("updated_at")));
            }
        }
        return rules;
    }

    public boolean editBounds(long id, String lowerText, String upperText, String remark, long userId)
            throws SQLException {
        var limits = ThresholdBounds.parse(lowerText, upperText);
        String sql = """
                UPDATE threshold_rule SET lower_limit = ?, upper_limit = ?, remark = ?, updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = 0
                """;
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, limits.lower());
            statement.setBigDecimal(2, limits.upper());
            statement.setString(3, remark);
            statement.setLong(4, userId);
            statement.setLong(5, id);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean setEnabled(long id, boolean enabled, long userId) throws SQLException {
        String sql = """
                UPDATE threshold_rule SET enabled = ?, updated_by = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = 0
                """;
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, enabled);
            statement.setLong(2, userId);
            statement.setLong(3, id);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteDisabled(long id, long userId) throws SQLException {
        String sql = """
                UPDATE threshold_rule SET is_deleted = 1, updated_by = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND is_deleted = 0 AND enabled = 0
                """;
        try (Connection connection = new Db("greenhouse_system").getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, id);
            return statement.executeUpdate() > 0;
        }
    }
}

