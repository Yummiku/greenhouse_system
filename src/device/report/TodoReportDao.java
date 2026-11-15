package device.report;

import device.dao.Db;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** 第6、7、8题共用的统计和报表查询；业务表使用 project_todo。 */
public class TodoReportDao {
    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static class Query {
        public final Long id;
        public final String title;
        public final LocalDate date;
        public final String orderBy;
        public final String direction;

        public Query(String idText, String titleText, String dateText,
                     String orderText, String directionText) {
            try {
                id = blank(idText) ? null : Long.valueOf(idText.trim());
                if (id != null && id <= 0) throw new IllegalArgumentException("记录编号必须为正整数");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("记录编号必须为正整数");
            }
            title = titleText == null ? "" : titleText.trim();
            if (title.length() > 255) throw new IllegalArgumentException("标题最多255个字符");
            try {
                date = blank(dateText) ? null : LocalDate.parse(dateText.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd");
            }
            // 排序字段只从白名单选择，不直接拼接请求中的 SQL。
            orderBy = "title".equals(orderText) ? "title"
                    : "id".equals(orderText) ? "id" : "create_time";
            direction = "asc".equalsIgnoreCase(directionText) ? "ASC" : "DESC";
        }

        private static boolean blank(String value) {
            return value == null || value.trim().isEmpty();
        }
    }

    private static class Conditions {
        final String sql;
        final List<Object> values = new ArrayList<>();

        Conditions(Query query) {
            StringBuilder where = new StringBuilder(" WHERE 1=1");
            if (query.id != null) {
                where.append(" AND id = ?");
                values.add(query.id);
            }
            if (!query.title.isEmpty()) {
                // 使用 ! 转义 LIKE 通配符，按用户输入的标题文字查询。
                where.append(" AND title LIKE ? ESCAPE '!'");
                values.add("%" + query.title.replace("!", "!!")
                        .replace("%", "!%").replace("_", "!_") + "%");
            }
            if (query.date != null) {
                where.append(" AND create_time >= ? AND create_time < ?");
                values.add(Timestamp.valueOf(query.date.atStartOfDay()));
                values.add(Timestamp.valueOf(query.date.plusDays(1).atStartOfDay()));
            }
            sql = where.toString();
        }

        void bind(PreparedStatement statement) throws SQLException {
            for (int i = 0; i < values.size(); i++) statement.setObject(i + 1, values.get(i));
        }
    }

    private Connection connection() throws SQLException {
        return new Db("greenhouse_system").getConnection();
    }

    public JSONObject dashboard() throws SQLException, JSONException {
        JSONObject result = new JSONObject();
        try (Connection connection = connection()) {
            String sql = "SELECT COUNT(*) AS total_count, "
                    + "COALESCE(SUM(create_time >= CURDATE() AND create_time < CURDATE() + INTERVAL 1 DAY), 0) AS today_count, "
                    + "COALESCE(SUM(create_time >= DATE_FORMAT(CURDATE(), '%Y-%m-01') "
                    + "AND create_time < DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 MONTH), 0) AS month_count, "
                    + "MAX(create_time) AS latest_time FROM project_todo";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rows = statement.executeQuery()) {
                rows.next();
                result.put("total_count", rows.getLong("total_count"));
                result.put("today_count", rows.getLong("today_count"));
                result.put("month_count", rows.getLong("month_count"));
                result.put("latest_time", format(rows.getTimestamp("latest_time")));
            }
            JSONArray months = new JSONArray();
            String groupedSql = "SELECT DATE_FORMAT(create_time, '%Y-%m') AS month, COUNT(*) AS record_count "
                    + "FROM project_todo WHERE create_time IS NOT NULL "
                    + "GROUP BY DATE_FORMAT(create_time, '%Y-%m') ORDER BY month DESC LIMIT 12";
            try (PreparedStatement statement = connection.prepareStatement(groupedSql);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    JSONObject month = new JSONObject();
                    month.put("month", rows.getString("month"));
                    month.put("record_count", rows.getLong("record_count"));
                    months.put(month);
                }
            }
            result.put("months", months);
        }
        return result;
    }

    /** 第7、8题共用同一筛选、排序结果，避免导出和打印使用不同数据。 */
    public JSONArray records(Query query) throws SQLException, JSONException {
        Conditions conditions = new Conditions(query);
        String sql = "SELECT id, title, create_time FROM project_todo" + conditions.sql
                + " ORDER BY " + query.orderBy + " " + query.direction
                + ("id".equals(query.orderBy) ? "" : ", id DESC");
        JSONArray result = new JSONArray();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            conditions.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    JSONObject item = new JSONObject();
                    item.put("id", rows.getLong("id"));
                    item.put("title", rows.getString("title") == null ? "" : rows.getString("title"));
                    item.put("create_time", format(rows.getTimestamp("create_time")));
                    result.put(item);
                }
            }
        }
        return result;
    }

    /** 第8题：当前查询结果的记录数及最早、最晚创建时间。 */
    public JSONObject summary(Query query) throws SQLException, JSONException {
        Conditions conditions = new Conditions(query);
        String sql = "SELECT COUNT(*) AS record_count, MIN(create_time) AS earliest_time, "
                + "MAX(create_time) AS latest_time FROM project_todo" + conditions.sql;
        JSONObject result = new JSONObject();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            conditions.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                result.put("record_count", rows.getLong("record_count"));
                result.put("earliest_time", format(rows.getTimestamp("earliest_time")));
                result.put("latest_time", format(rows.getTimestamp("latest_time")));
            }
        }
        return result;
    }

    private static String format(Timestamp timestamp) {
        return timestamp == null ? "" : timestamp.toLocalDateTime().format(DISPLAY);
    }
}
