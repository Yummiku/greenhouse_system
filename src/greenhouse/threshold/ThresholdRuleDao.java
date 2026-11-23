package greenhouse.threshold;

import device.dao.Db;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** 所有查询跟随 Db 的本机库名配置，维护操作使用事务及参数绑定。 */
public final class ThresholdRuleDao {
    public record EffectiveRule(long id, BigDecimal lowerLimit, BigDecimal upperLimit, String unit) {}

    private static final String SELECT_RULE = """
        SELECT r.id, r.rule_name name, r.greenhouse_id greenhouseId, r.metric_type metricType,
               r.lower_limit lowerBound, r.upper_limit upperBound, r.unit, r.enabled, r.remark,
               r.created_at createdAt, r.updated_at updatedAt,
               g.name greenhouseName, g.greenhouse_code greenhouseCode, g.status greenhouseStatus,
               g.is_deleted greenhouseDeleted,
               (g.id IS NOT NULL AND g.is_deleted=0 AND g.status='enabled') greenhouseValid,
               creator.display_name createdBy, updater.display_name updatedBy
        FROM threshold_rule r
        LEFT JOIN greenhouse_file g ON g.id=r.greenhouse_id
        LEFT JOIN todo_login_user creator ON creator.id=r.created_by
        LEFT JOIN todo_login_user updater ON updater.id=r.updated_by
        """;

    private Connection open() throws SQLException {
        return new Db("greenhouse_system").getConnection();
    }

    private Map<String, Object> readRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        ResultSetMetaData meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            Object value = rs.getObject(i);
            if (value instanceof BigDecimal decimal) value = decimal.stripTrailingZeros().toPlainString();
            if (value instanceof Timestamp) value = value.toString();
            row.put(meta.getColumnLabel(i), value);
        }
        row.put("id", rs.getString("id"));
        row.put("greenhouseId", rs.getString("greenhouseId"));
        row.put("enabled", rs.getBoolean("enabled"));
        row.put("greenhouseValid", rs.getBoolean("greenhouseValid"));
        return row;
    }

    public List<Map<String, Object>> listRules() throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement s = c.prepareStatement(SELECT_RULE +
                     " WHERE r.is_deleted=0 ORDER BY r.updated_at DESC,r.id DESC");
             ResultSet rs = s.executeQuery()) {
            while (rs.next()) rows.add(readRow(rs));
        }
        return rows;
    }

    public Map<String, Object> detail(long id) throws SQLException {
        try (Connection c = open();
             PreparedStatement s = c.prepareStatement(SELECT_RULE + " WHERE r.id=? AND r.is_deleted=0")) {
            s.setLong(1, id);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? readRow(rs) : null;
            }
        }
    }

    public Optional<EffectiveRule> findEffective(long greenhouseId, String metricType) throws SQLException {
        String sql = """
            SELECT r.id,r.lower_limit,r.upper_limit,r.unit FROM threshold_rule r
            JOIN greenhouse_file g ON g.id=r.greenhouse_id
            WHERE r.greenhouse_id=? AND r.metric_type=? AND r.active_slot=1
              AND g.is_deleted=0 AND g.status='enabled'
            """;
        try (Connection c = open(); PreparedStatement s = c.prepareStatement(sql)) {
            s.setLong(1, greenhouseId);
            s.setString(2, metricType);
            try (ResultSet rs = s.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new EffectiveRule(rs.getLong(1),
                        rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getString(4)));
            }
        }
    }

    private boolean lockHouse(Connection c, long id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT status,is_deleted FROM greenhouse_file WHERE id=? FOR UPDATE")) {
            s.setLong(1, id);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() && "enabled".equals(rs.getString(1)) && !rs.getBoolean(2);
            }
        }
    }

    public long create(String name, long house, String metric, String unit,
                       ThresholdBounds.Limits limits, String remark, long user) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                if (!lockHouse(c, house)) throw new IllegalArgumentException("大棚已停用、删除或不存在，请重新选择");
                String sql = """
                    INSERT INTO threshold_rule(rule_name,greenhouse_id,metric_type,unit,
                        lower_limit,upper_limit,remark,created_by,updated_by,enabled)
                    VALUES(?,?,?,?,?,?,?,?,?,0)
                    """;
                long id;
                try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    s.setString(1, name); s.setLong(2, house); s.setString(3, metric); s.setString(4, unit);
                    s.setBigDecimal(5, limits.lower()); s.setBigDecimal(6, limits.upper());
                    s.setString(7, remark); s.setLong(8, user); s.setLong(9, user);
                    s.executeUpdate();
                    try (ResultSet rs = s.getGeneratedKeys()) { rs.next(); id = rs.getLong(1); }
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }

    /** 大棚行先锁定，随后锁规则行，使并发启用及删除以数据库当前状态为准。 */
    public void change(long id, String action, String name, ThresholdBounds.Limits limits,
                       String remark, long user) throws SQLException {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                long house;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT greenhouse_id FROM threshold_rule WHERE id=? AND is_deleted=0")) {
                    s.setLong(1, id);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("规则不存在或已删除，请刷新");
                        house = rs.getLong(1);
                    }
                }
                boolean valid = lockHouse(c, house);
                boolean enabled;
                String metric;
                try (PreparedStatement s = c.prepareStatement(
                        "SELECT enabled,metric_type FROM threshold_rule WHERE id=? AND is_deleted=0 FOR UPDATE")) {
                    s.setLong(1, id);
                    try (ResultSet rs = s.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("规则不存在或已删除，请刷新");
                        enabled = rs.getBoolean(1); metric = rs.getString(2);
                    }
                }
                if ("enable".equals(action)) {
                    if (!valid) throw new IllegalArgumentException("所属大棚已失效，不能启用规则");
                    try (PreparedStatement s = c.prepareStatement("""
                            SELECT id,rule_name FROM threshold_rule
                            WHERE greenhouse_id=? AND metric_type=? AND active_slot=1 AND id<>?
                            """)) {
                        s.setLong(1, house); s.setString(2, metric); s.setLong(3, id);
                        try (ResultSet rs = s.executeQuery()) {
                            if (rs.next()) throw new IllegalArgumentException(
                                    "请先停用规则【" + rs.getString(2) + "】（编号 " + rs.getLong(1) + "）");
                        }
                    }
                }
                if ("delete".equals(action) && enabled) throw new IllegalArgumentException("请先停用规则再删除");
                String sql;
                if ("edit".equals(action)) {
                    sql = "UPDATE threshold_rule SET rule_name=?,lower_limit=?,upper_limit=?,remark=?,"
                            + "updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
                    try (PreparedStatement s = c.prepareStatement(sql)) {
                        s.setString(1, name); s.setBigDecimal(2, limits.lower());
                        s.setBigDecimal(3, limits.upper()); s.setString(4, remark);
                        s.setLong(5, user); s.setLong(6, id); s.executeUpdate();
                    }
                } else {
                    sql = "delete".equals(action)
                            ? "UPDATE threshold_rule SET is_deleted=1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?"
                            : "UPDATE threshold_rule SET enabled=" + ("enable".equals(action) ? "1" : "0")
                              + ",updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=?";
                    try (PreparedStatement s = c.prepareStatement(sql)) {
                        s.setLong(1, user); s.setLong(2, id); s.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException | RuntimeException e) { c.rollback(); throw e; }
        }
    }
}

