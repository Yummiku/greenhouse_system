package greenhouse.environment;

import device.dao.Db;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 环境采样模块的持久层。所有 SQL 都是常量 + PreparedStatement 参数绑定，
 * 页面/接口传入的值从不拼进 SQL；列名与排序方向只接受白名单。
 */
public final class EnvironmentDao {

    public static final String DB_NAME = "greenhouse_system";

    private static final DateTimeFormatter ISO_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_MINUTES =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 列表/导出/打印的公共投影：大棚与设备同时给出主键和业务编号，避免两者混淆。 */
    private static final String SELECT =
            "SELECT e.id, e.sample_key, e.greenhouse_id, g.greenhouse_code, g.name AS greenhouse_name, "
            + "e.device_pk, d.device_id AS device_code, d.device_name, "
            + "e.metric_type, e.value, e.unit, e.sample_time, e.received_at, e.source, "
            + "e.created_by, u.display_name AS created_by_name, e.created_at, e.updated_at, "
            + "e.is_deleted, e.deleted_at, e.delete_reason "
            + "FROM environment_sample e "
            + "JOIN greenhouse_file g ON g.id = e.greenhouse_id "
            + "JOIN device_file d ON d.id = e.device_pk "
            + "LEFT JOIN todo_login_user u ON u.id = e.created_by ";

    public static final Set<String> SORT_COLUMNS =
            Set.of("sample_time", "value", "received_at", "id");

    public EnvironmentDao() {
    }

    // ------------------------------------------------------------------ 连接与通用执行

    public static Connection open() throws SQLException {
        return new Db(DB_NAME).getConnection();
    }

    public static JSONArray rows(Connection c, String sql, List<Object> args)
            throws SQLException, JSONException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, args);
            try (ResultSet r = s.executeQuery()) {
                return readRows(r);
            }
        }
    }

    public static JSONObject first(Connection c, String sql, List<Object> args)
            throws SQLException, JSONException {
        JSONArray rows = rows(c, sql, args);
        return rows.length() == 0 ? null : rows.getJSONObject(0);
    }

    public static int execute(Connection c, String sql, List<Object> args)
            throws SQLException, JSONException {
        try (PreparedStatement s = c.prepareStatement(sql)) {
            bind(s, args);
            return s.executeUpdate();
        }
    }

    public static long insert(Connection c, String sql, List<Object> args)
            throws SQLException, JSONException {
        try (PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(s, args);
            s.executeUpdate();
            try (ResultSet keys = s.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("未取得新增记录主键");
                return keys.getLong(1);
            }
        }
    }

    static void bind(PreparedStatement s, List<Object> args) throws SQLException, JSONException {
        for (int i = 0; i < args.size(); i++) {
            s.setObject(i + 1, args.get(i));
        }
    }

    private static JSONArray readRows(ResultSet r) throws SQLException, JSONException {
        JSONArray array = new JSONArray();
        ResultSetMetaData meta = r.getMetaData();
        while (r.next()) {
            JSONObject row = new JSONObject();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                Object value = r.getObject(i);
                // Timestamp 走 toString 保持 "yyyy-MM-dd HH:mm:ss"，前端和 CSV 都不必再猜格式。
                row.put(meta.getColumnLabel(i),
                        value == null ? JSONObject.NULL
                                : value instanceof Timestamp ? ((Timestamp) value).toLocalDateTime().format(ISO_SECONDS)
                                : value);
            }
            array.put(row);
        }
        return array;
    }

    // ------------------------------------------------------------------ 查询条件

    /** 组合筛选条件：全部为空即“未删除的全部采样”。 */
    public static final class Filter {
        private Long greenhouseId;
        private Integer devicePk;
        private String metricType;
        private String source;
        private Timestamp from;
        private Timestamp to;

        public Filter greenhouseId(Long value) {
            this.greenhouseId = value;
            return this;
        }

        public Filter devicePk(Integer value) {
            this.devicePk = value;
            return this;
        }

        public Filter metricType(String value) {
            this.metricType = value;
            return this;
        }

        public Filter source(String value) {
            this.source = value;
            return this;
        }

        public Filter from(Timestamp value) {
            this.from = value;
            return this;
        }

        public Filter to(Timestamp value) {
            this.to = value;
            return this;
        }

        public String metricType() {
            return metricType;
        }

        public String where() {
            StringBuilder sql = new StringBuilder(" WHERE e.is_deleted = 0");
            if (greenhouseId != null) sql.append(" AND e.greenhouse_id = ?");
            if (devicePk != null) sql.append(" AND e.device_pk = ?");
            if (metricType != null) sql.append(" AND e.metric_type = ?");
            if (source != null) sql.append(" AND e.source = ?");
            if (from != null) sql.append(" AND e.sample_time >= ?");
            if (to != null) sql.append(" AND e.sample_time <= ?");
            return sql.toString();
        }

        public List<Object> args() {
            List<Object> args = new ArrayList<>();
            if (greenhouseId != null) args.add(greenhouseId);
            if (devicePk != null) args.add(devicePk);
            if (metricType != null) args.add(metricType);
            if (source != null) args.add(source);
            if (from != null) args.add(from);
            if (to != null) args.add(to);
            return args;
        }
    }

    /** 排序只允许白名单列；相同排序值追加 id 保证翻页结果稳定。 */
    public static String order(String sort, String dir) {
        String column = SORT_COLUMNS.contains(sort) ? sort : "sample_time";
        String direction = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        return " ORDER BY e." + column + " " + direction + ", e.id " + direction;
    }

    // ------------------------------------------------------------------ 读

    public long count(Connection c, Filter f) throws SQLException, JSONException {
        JSONObject row = first(c, "SELECT COUNT(*) AS total FROM environment_sample e" + f.where(), f.args());
        return row == null ? 0L : row.getLong("total");
    }

    public JSONArray list(Connection c, Filter f, String sort, String dir, int page, int size)
            throws SQLException, JSONException {
        List<Object> args = f.args();
        args.add(size);
        args.add((page - 1) * size);
        return rows(c, SELECT + f.where() + order(sort, dir) + " LIMIT ? OFFSET ?", args);
    }

    public JSONObject detail(Connection c, long id) throws SQLException, JSONException {
        return first(c, SELECT + " WHERE e.id = ?", List.of(id));
    }

    public JSONArray revisions(Connection c, long sampleId) throws SQLException, JSONException {
        return rows(c,
                "SELECT r.id, r.sample_id, r.old_value, r.new_value, r.old_sample_time, "
                + "r.new_sample_time, r.changed_by, u.display_name AS changed_by_name, "
                + "r.changed_at, r.reason FROM environment_sample_revision r "
                + "LEFT JOIN todo_login_user u ON u.id = r.changed_by "
                + "WHERE r.sample_id = ? ORDER BY r.changed_at DESC, r.id DESC",
                List.of(sampleId));
    }

    /** 五类指标的“最新一条”，逐指标查询以保证 0 值、来源与时刻都是同一行数据。 */
    public JSONArray latest(Connection c, long greenhouseId, Integer devicePk)
            throws SQLException, JSONException {
        JSONArray cards = new JSONArray();
        for (MetricType metric : MetricType.values()) {
            List<Object> args = new ArrayList<>();
            StringBuilder sql = new StringBuilder(
                    "SELECT e.id, e.sample_key, e.value, e.unit, e.sample_time, e.received_at, e.source, "
                    + "e.device_pk, d.device_id AS device_code, d.device_name "
                    + "FROM environment_sample e JOIN device_file d ON d.id = e.device_pk "
                    + "WHERE e.is_deleted = 0 AND e.greenhouse_id = ? AND e.metric_type = ?");
            args.add(greenhouseId);
            args.add(metric.code());
            if (devicePk != null) {
                sql.append(" AND e.device_pk = ?");
                args.add(devicePk);
            }
            sql.append(" ORDER BY e.sample_time DESC, e.id DESC LIMIT 1");
            JSONObject row = first(c, sql.toString(), args);
            JSONObject card = new JSONObject()
                    .put("metric_type", metric.code())
                    .put("label", metric.label())
                    .put("unit", metric.unit())
                    .put("has_data", row != null);
            if (row != null) {
                card.put("sample", row);
            }
            cards.put(card);
        }
        return cards;
    }

    /** 单指标汇总；metric_type 为空表示五类分别汇总。 */
    public JSONArray summary(Connection c, Filter f) throws SQLException, JSONException {
        return rows(c,
                "SELECT e.metric_type, COUNT(*) AS total, AVG(e.value) AS avg_value, "
                + "MIN(e.value) AS min_value, MAX(e.value) AS max_value, "
                + "MIN(e.sample_time) AS first_time, MAX(e.sample_time) AS last_time "
                + "FROM environment_sample e" + f.where()
                + " GROUP BY e.metric_type ORDER BY e.metric_type",
                f.args());
    }

    public JSONArray sourceBreakdown(Connection c, Filter f) throws SQLException, JSONException {
        return rows(c,
                "SELECT e.source, COUNT(*) AS total FROM environment_sample e" + f.where()
                + " GROUP BY e.source ORDER BY e.source",
                f.args());
    }

    /** 趋势点按时间倒序取最近 N 条，再按时间正序返回，便于直接画线。 */
    public JSONArray trend(Connection c, Filter f, int points) throws SQLException, JSONException {
        List<Object> args = f.args();
        args.add(points);
        JSONArray rows = rows(c,
                "SELECT e.sample_time, e.value, e.unit, e.source FROM environment_sample e"
                + f.where() + " ORDER BY e.sample_time DESC, e.id DESC LIMIT ?", args);
        JSONArray ordered = new JSONArray();
        for (int i = rows.length() - 1; i >= 0; i--) ordered.put(rows.getJSONObject(i));
        return ordered;
    }

    /** 打印/统计用的全量结果；上限高于演示与验收可能出现的记录数。 */
    public JSONArray all(Connection c, Filter f, String sort, String dir, int limit)
            throws SQLException, JSONException {
        List<Object> args = f.args();
        args.add(limit);
        return rows(c, SELECT + f.where() + order(sort, dir) + " LIMIT ?", args);
    }

    // ------------------------------------------------------------------ 写

    public static final class Binding {
        private final int devicePk;
        private final String metricType;
        private final String unit;
        private final String deviceType;
        private final String deviceCode;

        Binding(int devicePk, String metricType, String unit, String deviceType, String deviceCode) {
            this.devicePk = devicePk;
            this.metricType = metricType;
            this.unit = unit;
            this.deviceType = deviceType;
            this.deviceCode = deviceCode;
        }

        public int devicePk() {
            return devicePk;
        }

        public String metricType() {
            return metricType;
        }

        public String unit() {
            return unit;
        }

        public String deviceType() {
            return deviceType;
        }

        public String deviceCode() {
            return deviceCode;
        }

        /** 只有 sensor 才能作为采样来源；device_type 为空按 sensor 处理以兼容早期演示数据。 */
        public boolean sensor() {
            return deviceType == null || deviceType.isBlank() || "sensor".equalsIgnoreCase(deviceType);
        }
    }

    /**
     * 设备必须已经在大棚设备关联表里登记，且指标与关联配置一致。
     * 同时带出 device_type / device_code：上传入口要拒绝控制设备，提示里给业务编号而不是主键。
     */
    public Binding binding(Connection c, long greenhouseId, int devicePk)
            throws SQLException, JSONException {
        JSONObject row = first(c,
                "SELECT b.device_pk, b.metric_type, b.unit, d.device_type, d.device_id AS device_code "
                + "FROM greenhouse_device_binding b JOIN device_file d ON d.id = b.device_pk "
                + "WHERE b.device_pk = ? AND b.greenhouse_id = ?",
                List.of(devicePk, greenhouseId));
        if (row == null) return null;
        return new Binding(row.getInt("device_pk"), row.getString("metric_type"), row.getString("unit"),
                row.optString("device_type", null), row.optString("device_code", null));
    }

    public JSONObject greenhouse(Connection c, long id) throws SQLException, JSONException {
        return first(c,
                "SELECT id, greenhouse_code, name, status FROM greenhouse_file "
                + "WHERE id = ? AND is_deleted = 0", List.of(id));
    }

    public JSONArray greenhouses(Connection c) throws SQLException, JSONException {
        return rows(c,
                "SELECT id, greenhouse_code, name, status FROM greenhouse_file "
                + "WHERE is_deleted = 0 ORDER BY greenhouse_code", List.of());
    }

    public JSONArray devices(Connection c) throws SQLException, JSONException {
        return rows(c,
                "SELECT b.device_pk, b.greenhouse_id, b.metric_type, b.unit, "
                + "d.device_id AS device_code, d.device_name FROM greenhouse_device_binding b "
                + "JOIN device_file d ON d.id = b.device_pk "
                + "ORDER BY b.greenhouse_id, b.metric_type", List.of());
    }

    public JSONObject bySampleKey(Connection c, String sampleKey) throws SQLException, JSONException {
        return first(c,
                "SELECT id, sample_key, greenhouse_id, device_pk, metric_type, value, unit, "
                + "sample_time, source FROM environment_sample WHERE sample_key = ?",
                List.of(sampleKey));
    }

    public long insertSample(Connection c, String sampleKey, long greenhouseId, int devicePk,
            String metricType, BigDecimal value, String unit, Timestamp sampleTime,
            String source, Long createdBy) throws SQLException, JSONException {
        // createdBy 可为 null（设备上传没有登录账号），List.of 不接受 null，故用 Arrays.asList。
        return insert(c,
                "INSERT INTO environment_sample(sample_key, greenhouse_id, device_pk, metric_type, "
                + "value, unit, sample_time, source, created_by) VALUES(?,?,?,?,?,?,?,?,?)",
                Arrays.asList(sampleKey, greenhouseId, devicePk, metricType, value, unit, sampleTime,
                        source, createdBy));
    }

    public int updateManual(Connection c, long id, BigDecimal value, Timestamp sampleTime)
            throws SQLException, JSONException {
        return execute(c,
                "UPDATE environment_sample SET value = ?, sample_time = ? "
                + "WHERE id = ? AND source = 'manual' AND is_deleted = 0",
                List.of(value, sampleTime, id));
    }

    public void insertRevision(Connection c, long sampleId, BigDecimal oldValue, BigDecimal newValue,
            Timestamp oldTime, Timestamp newTime, long changedBy, String reason)
            throws SQLException, JSONException {
        execute(c,
                "INSERT INTO environment_sample_revision(sample_id, old_value, new_value, "
                + "old_sample_time, new_sample_time, changed_by, reason) VALUES(?,?,?,?,?,?,?)",
                List.of(sampleId, oldValue, newValue, oldTime, newTime, changedBy, reason));
    }

    public int deleteManual(Connection c, long id, long deletedBy, String reason)
            throws SQLException, JSONException {
        return execute(c,
                "UPDATE environment_sample SET is_deleted = 1, deleted_by = ?, "
                + "deleted_at = NOW(), delete_reason = ? "
                + "WHERE id = ? AND source = 'manual' AND is_deleted = 0",
                List.of(deletedBy, reason, id));
    }

    // ------------------------------------------------------------------ 导出

    /** 流式写 CSV：不把全部结果读进内存，导出条件与列表完全一致。 */
    public void exportCsv(Connection c, Filter f, String sort, String dir, Writer out)
            throws SQLException, IOException, JSONException {
        try (PreparedStatement s = c.prepareStatement(SELECT + f.where() + order(sort, dir))) {
            s.setFetchSize(200);
            bind(s, f.args());
            try (ResultSet r = s.executeQuery()) {
                out.write('\ufeff');
                out.write("采样键,大棚编号,大棚名称,设备编号,设备名称,指标,数值,单位,来源,采样时间,接收时间\r\n");
                while (r.next()) {
                    String metric = r.getString("metric_type");
                    String label = MetricType.find(metric).map(MetricType::label).orElse(metric);
                    String source = r.getString("source");
                    out.write(String.join(",",
                            csv(r.getString("sample_key")),
                            csv(r.getString("greenhouse_code")),
                            csv(r.getString("greenhouse_name")),
                            csv(r.getString("device_code")),
                            csv(r.getString("device_name")),
                            csv(label),
                            csv(plain(r.getBigDecimal("value"))),
                            csv(r.getString("unit")),
                            csv(sourceName(source)),
                            csv(time(r.getTimestamp("sample_time"))),
                            csv(time(r.getTimestamp("received_at")))));
                    out.write("\r\n");
                }
                out.flush();
            }
        }
    }

    // ------------------------------------------------------------------ 工具

    public static String sourceName(String source) {
        if (source == null) return "";
        switch (source) {
            case "real":
                return "实采";
            case "simulated":
                return "模拟";
            case "manual":
                return "人工";
            default:
                return source;
        }
    }

    public static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public static String time(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(ISO_SECONDS);
    }

    /** CSV 转义 + 公式注入防护：以 = + @ - 开头的文本前置单引号。 */
    public static String csv(String text) {
        String value = text == null ? "" : text;
        if (value.matches("(?s)^\\s*[=+@-].*")) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** 接受 datetime-local（含 T）、带毫秒/时区后缀与数据库格式，统一转 Timestamp。 */
    public static Timestamp parseTime(String text, String field) {
        String value = text == null ? "" : text.trim().replace('T', ' ');
        if (value.isEmpty()) return null;
        // 只取到秒：采集端可能带毫秒或 +08:00 后缀，DATETIME 精度本就不需要它。
        if (value.length() > 19) value = value.substring(0, 19);
        try {
            if (value.length() == 16) {
                return Timestamp.valueOf(LocalDateTime.parse(value, ISO_MINUTES));
            }
            return Timestamp.valueOf(LocalDateTime.parse(value, ISO_SECONDS));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + "格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }
}
