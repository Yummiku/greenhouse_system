package greenhouse.log.dao;

import device.dao.Db;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 登录日志与操作日志的数据访问。
 * 查询范围由 scopeUserId 控制：0 = 全量（仅系统管理员），>0 = 只看该用户ID关联记录。
 */
public class LogDao {

    private static final String DB_NAME = "greenhouse_system";
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 写入一条登录日志。失败由调用方兜底，不在此抛给页面。 */
    public void insertLogin(String usernameAttempt, Integer userId, String result,
                            String reasonCode, String source, String ip, String client)
            throws SQLException {

        String sql = "INSERT INTO login_log"
                + " (event_time, username_attempt, user_id, result, reason_code, source, ip, client_info)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, DATE_TIME.format(LocalDateTime.now()));
                statement.setString(2, cut(usernameAttempt, 64));
                if (userId == null) {
                    statement.setNull(3, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(3, userId);
                }
                statement.setString(4, result);
                statement.setString(5, cut(reasonCode, 32));
                statement.setString(6, cut(source, 16));
                statement.setString(7, cut(ip, 64));
                statement.setString(8, cut(client, 128));
                statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    /** 写入一条操作日志。 */
    public void insertOperation(Integer operatorId, String operatorName, Integer roleId,
                                String module, String action, String targetId, String targetName,
                                String result, String reasonCode, String summary, String traceId)
            throws SQLException {

        String sql = "INSERT INTO operation_log"
                + " (operate_time, operator_id, operator_name, role_id, module, action,"
                + " target_id, target_name, result, reason_code, summary, trace_id)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, DATE_TIME.format(LocalDateTime.now()));
                setNullableInt(statement, 2, operatorId);
                statement.setString(3, cut(operatorName, 100));
                setNullableInt(statement, 4, roleId);
                statement.setString(5, cut(module, 50));
                statement.setString(6, cut(action, 50));
                statement.setString(7, cut(targetId, 64));
                statement.setString(8, cut(targetName, 200));
                statement.setString(9, result);
                statement.setString(10, cut(reasonCode, 32));
                statement.setString(11, cut(summary, 1000));
                statement.setString(12, cut(traceId, 64));
                statement.executeUpdate();
            }
        } finally {
            db.close();
        }
    }

    // ==================== 登录日志 ====================

    public JSONObject searchLogin(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = loginCondition(query, scopeUserId);
        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            long total = count(connection, "login_log", condition);
            JSONArray list = new JSONArray();
            String sql = "SELECT id, event_time, username_attempt, user_id, result, reason_code,"
                    + " source, ip, client_info FROM login_log" + condition.sql
                    + " ORDER BY " + (query.getSortBy().equals("id") ? "id" : "event_time")
                    + " " + query.getSortDir() + ", id " + query.getSortDir()
                    + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                statement.setInt(condition.values.size() + 1, query.getPageSize());
                statement.setInt(condition.values.size() + 2, query.getOffset());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        JSONObject row = new JSONObject();
                        row.put("id", rs.getLong("id"));
                        row.put("eventTime", format(rs.getTimestamp("event_time")));
                        row.put("usernameAttempt", nullToEmpty(rs.getString("username_attempt")));
                        Integer userId = (Integer) rs.getObject("user_id");
                        row.put("userId", userId == null ? JSONObject.NULL : userId);
                        row.put("result", rs.getString("result"));
                        row.put("reasonCode", nullToEmpty(rs.getString("reason_code")));
                        row.put("source", nullToEmpty(rs.getString("source")));
                        row.put("ip", nullToEmpty(rs.getString("ip")));
                        row.put("clientInfo", nullToEmpty(rs.getString("client_info")));
                        list.put(row);
                    }
                }
            }
            return page(list, total, query);
        } finally {
            db.close();
        }
    }

    public JSONObject statsLogin(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = loginCondition(query, scopeUserId);
        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            JSONObject data = new JSONObject();
            long total = count(connection, "login_log", condition);
            data.put("total", total);
            data.put("success", countResult(connection, "login_log", condition, "success"));
            data.put("failure", countResult(connection, "login_log", condition, "failure"));

            String sql = "SELECT COUNT(DISTINCT user_id) AS active_users FROM login_log"
                    + condition.sql + " AND result = 'success' AND user_id IS NOT NULL";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                try (ResultSet rs = statement.executeQuery()) {
                    data.put("activeUsers", rs.next() ? rs.getLong("active_users") : 0);
                }
            }

            JSONArray byReason = new JSONArray();
            String reasonSql = "SELECT reason_code, COUNT(*) AS c FROM login_log" + condition.sql
                    + " AND result = 'failure' GROUP BY reason_code ORDER BY c DESC";
            try (PreparedStatement statement = connection.prepareStatement(reasonSql)) {
                bind(statement, condition.values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        byReason.put(new JSONObject()
                                .put("reason", nullToEmpty(rs.getString("reason_code")))
                                .put("count", rs.getLong("c")));
                    }
                }
            }
            data.put("byReason", byReason);
            return data;
        } finally {
            db.close();
        }
    }

    /** 按天统计登录成功/失败，缺数据的日期补0，返回从起始日到结束日的连续序列。 */
    public JSONArray loginTrend(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = loginCondition(query, scopeUserId);
        JSONArray trend = new JSONArray();
        String sql = "SELECT DATE(event_time) AS d,"
                + " SUM(CASE WHEN result='success' THEN 1 ELSE 0 END) AS s,"
                + " SUM(CASE WHEN result='failure' THEN 1 ELSE 0 END) AS f"
                + " FROM login_log" + condition.sql
                + " GROUP BY DATE(event_time) ORDER BY d";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            List<String[]> rows = new ArrayList<String[]>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{rs.getString("d"),
                                String.valueOf(rs.getLong("s")), String.valueOf(rs.getLong("f"))});
                    }
                }
            }
            LocalDate start = parseDate(query.getStartDate());
            LocalDate end = parseDate(query.getEndDate());
            if (start == null) {
                start = LocalDate.now().minusDays(6);
            }
            if (end == null) {
                end = LocalDate.now();
            }
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                String key = day.format(DATE_ONLY);
                long s = 0;
                long f = 0;
                for (String[] row : rows) {
                    if (key.equals(row[0])) {
                        s = Long.parseLong(row[1]);
                        f = Long.parseLong(row[2]);
                        break;
                    }
                }
                trend.put(new JSONObject().put("date", key).put("success", s).put("failure", f));
            }
            return trend;
        } finally {
            db.close();
        }
    }

    // ==================== 操作日志 ====================

    public JSONObject searchOperation(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = operationCondition(query, scopeUserId);
        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            long total = count(connection, "operation_log", condition);
            JSONArray list = new JSONArray();
            String sql = "SELECT id, operate_time, operator_id, operator_name, role_id, module,"
                    + " action, target_id, target_name, result, reason_code, summary, trace_id"
                    + " FROM operation_log" + condition.sql
                    + " ORDER BY " + (query.getSortBy().equals("id") ? "id" : "operate_time")
                    + " " + query.getSortDir() + ", id " + query.getSortDir()
                    + " LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                statement.setInt(condition.values.size() + 1, query.getPageSize());
                statement.setInt(condition.values.size() + 2, query.getOffset());
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        JSONObject row = new JSONObject();
                        row.put("id", rs.getLong("id"));
                        row.put("operateTime", format(rs.getTimestamp("operate_time")));
                        Integer operatorId = (Integer) rs.getObject("operator_id");
                        row.put("operatorId", operatorId == null ? JSONObject.NULL : operatorId);
                        row.put("operatorName", nullToEmpty(rs.getString("operator_name")));
                        Integer roleId = (Integer) rs.getObject("role_id");
                        row.put("roleId", roleId == null ? JSONObject.NULL : roleId);
                        row.put("module", nullToEmpty(rs.getString("module")));
                        row.put("action", nullToEmpty(rs.getString("action")));
                        row.put("targetId", nullToEmpty(rs.getString("target_id")));
                        row.put("targetName", nullToEmpty(rs.getString("target_name")));
                        row.put("result", rs.getString("result"));
                        row.put("reasonCode", nullToEmpty(rs.getString("reason_code")));
                        row.put("summary", nullToEmpty(rs.getString("summary")));
                        row.put("traceId", nullToEmpty(rs.getString("trace_id")));
                        list.put(row);
                    }
                }
            }
            return page(list, total, query);
        } finally {
            db.close();
        }
    }

    public JSONObject statsOperation(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = operationCondition(query, scopeUserId);
        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            JSONObject data = new JSONObject();
            data.put("total", count(connection, "operation_log", condition));
            data.put("success", countResult(connection, "operation_log", condition, "success"));
            data.put("failed", countResult(connection, "operation_log", condition, "failed"));
            data.put("denied", countResult(connection, "operation_log", condition, "denied"));

            JSONArray byModule = new JSONArray();
            String sql = "SELECT module, COUNT(*) AS c FROM operation_log" + condition.sql
                    + " GROUP BY module ORDER BY c DESC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        byModule.put(new JSONObject()
                                .put("module", nullToEmpty(rs.getString("module")))
                                .put("count", rs.getLong("c")));
                    }
                }
            }
            data.put("byModule", byModule);
            return data;
        } finally {
            db.close();
        }
    }

    public JSONArray operationTrend(LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        Condition condition = operationCondition(query, scopeUserId);
        JSONArray trend = new JSONArray();
        String sql = "SELECT DATE(operate_time) AS d, COUNT(*) AS c FROM operation_log"
                + condition.sql + " GROUP BY DATE(operate_time) ORDER BY d";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            List<String[]> rows = new ArrayList<String[]>();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                bind(statement, condition.values);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        rows.add(new String[]{rs.getString("d"), String.valueOf(rs.getLong("c"))});
                    }
                }
            }
            LocalDate start = parseDate(query.getStartDate());
            LocalDate end = parseDate(query.getEndDate());
            if (start == null) {
                start = LocalDate.now().minusDays(6);
            }
            if (end == null) {
                end = LocalDate.now();
            }
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                String key = day.format(DATE_ONLY);
                long c = 0;
                for (String[] row : rows) {
                    if (key.equals(row[0])) {
                        c = Long.parseLong(row[1]);
                        break;
                    }
                }
                trend.put(new JSONObject().put("date", key).put("count", c));
            }
            return trend;
        } finally {
            db.close();
        }
    }

    // ==================== 聚合指标（集成驾驶舱） ====================

    /** 账号总量与状态分布（未删除账号计有效）。 */
    public JSONObject accountStat() throws SQLException, JSONException {
        String sql = "SELECT COUNT(*) AS total,"
                + " SUM(CASE WHEN status='active' THEN 1 ELSE 0 END) AS active,"
                + " SUM(CASE WHEN status='disabled' THEN 1 ELSE 0 END) AS disabled"
                + " FROM todo_login_user WHERE status <> 'deleted'";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            JSONObject data = new JSONObject();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    data.put("total", rs.getLong("total"));
                    data.put("active", rs.getLong("active"));
                    data.put("disabled", rs.getLong("disabled"));
                }
            }
            JSONArray byRole = new JSONArray();
            String roleSql = "SELECT role_id, COUNT(*) AS c FROM todo_login_user"
                    + " WHERE status <> 'deleted' GROUP BY role_id ORDER BY role_id";
            try (PreparedStatement statement = connection.prepareStatement(roleSql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    byRole.put(new JSONObject()
                            .put("roleId", rs.getInt("role_id"))
                            .put("count", rs.getLong("c")));
                }
            }
            data.put("byRole", byRole);
            return data;
        } finally {
            db.close();
        }
    }

    /** 个人消息总量与今日新增（只统计数量，不返回正文）。 */
    public JSONObject messageStat() throws SQLException, JSONException {
        String sql = "SELECT COUNT(*) AS total,"
                + " SUM(CASE WHEN created_at >= CURDATE() THEN 1 ELSE 0 END) AS today,"
                + " SUM(CASE WHEN send_status='draft' THEN 1 ELSE 0 END) AS draft,"
                + " SUM(CASE WHEN send_status='withdrawn' THEN 1 ELSE 0 END) AS withdrawn"
                + " FROM personal_message";

        Db db = new Db(DB_NAME);
        try {
            Connection connection = db.getConnection();
            JSONObject data = new JSONObject();
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    data.put("total", rs.getLong("total"));
                    data.put("today", rs.getLong("today"));
                    data.put("draft", rs.getLong("draft"));
                    data.put("withdrawn", rs.getLong("withdrawn"));
                }
            }
            return data;
        } finally {
            db.close();
        }
    }

    // ==================== 条件与工具 ====================

    private Condition loginCondition(LogQuery query, int scopeUserId) {
        Condition condition = new Condition();
        condition.add(" WHERE 1=1 ");
        if (scopeUserId > 0) {
            condition.add(" AND user_id = ? ", scopeUserId);
        }
        if (!query.getKeyword().isEmpty()) {
            condition.add(" AND username_attempt LIKE ? ", "%" + escapeLike(query.getKeyword()) + "%");
        }
        if (!query.getResult().isEmpty()) {
            condition.add(" AND result = ? ", query.getResult());
        }
        if (!query.getReason().isEmpty()) {
            condition.add(" AND reason_code = ? ", query.getReason());
        }
        if (!query.getSource().isEmpty()) {
            condition.add(" AND source = ? ", query.getSource());
        }
        addTimeRange(condition, "event_time", query);
        return condition;
    }

    private Condition operationCondition(LogQuery query, int scopeUserId) {
        Condition condition = new Condition();
        condition.add(" WHERE 1=1 ");
        if (scopeUserId > 0) {
            condition.add(" AND operator_id = ? ", scopeUserId);
        }
        if (!query.getKeyword().isEmpty()) {
            condition.add(" AND (operator_name LIKE ? OR target_name LIKE ?)",
                    "%" + escapeLike(query.getKeyword()) + "%",
                    "%" + escapeLike(query.getKeyword()) + "%");
        }
        if (!query.getModule().isEmpty()) {
            condition.add(" AND module = ? ", query.getModule());
        }
        if (!query.getAction().isEmpty()) {
            condition.add(" AND action = ? ", query.getAction());
        }
        if (!query.getResult().isEmpty()) {
            condition.add(" AND result = ? ", query.getResult());
        }
        addTimeRange(condition, "operate_time", query);
        return condition;
    }

    private void addTimeRange(Condition condition, String column, LogQuery query) {
        String start = query.getStartDate();
        String end = query.getEndDate();
        if (!start.isEmpty()) {
            condition.add(" AND " + column + " >= ? ", start + " 00:00:00");
        }
        if (!end.isEmpty()) {
            LocalDate endDay = parseDate(end);
            if (endDay != null) {
                condition.add(" AND " + column + " < ? ", endDay.plusDays(1).format(DATE_ONLY) + " 00:00:00");
            }
        }
    }

    private long count(Connection connection, String table, Condition condition) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("SELECT COUNT(*) FROM " + table + condition.sql)) {
            bind(statement, condition.values);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private long countResult(Connection connection, String table, Condition condition, String result)
            throws SQLException {
        Condition c = condition.copy();
        c.add(" AND result = ? ", result);
        return count(connection, table, c);
    }

    private JSONObject page(JSONArray list, long total, LogQuery query) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("total", total);
        data.put("page", query.getPage());
        data.put("pageSize", query.getPageSize());
        data.put("pages", total == 0 ? 0 : (total + query.getPageSize() - 1) / query.getPageSize());
        data.put("list", list);
        return data;
    }

    private void bind(PreparedStatement statement, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Integer) {
                statement.setInt(i + 1, (Integer) value);
            } else if (value instanceof Long) {
                statement.setLong(i + 1, (Long) value);
            } else {
                statement.setString(i + 1, String.valueOf(value));
            }
        }
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() > max ? clean.substring(0, max) : clean;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String format(java.sql.Timestamp value) {
        return value == null ? "" : DATE_TIME.format(value.toLocalDateTime());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_ONLY);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 只存 " WHERE ..." 片段与对应参数，避免把条件拼进 SELECT 造成重复或语法错。 */
    private static final class Condition {
        private String sql = "";
        private final List<Object> values = new ArrayList<Object>();

        void add(String fragment, Object... args) {
            sql += fragment;
            for (Object arg : args) {
                values.add(arg);
            }
        }

        Condition copy() {
            Condition c = new Condition();
            c.sql = sql;
            c.values.addAll(values);
            return c;
        }
    }
}
