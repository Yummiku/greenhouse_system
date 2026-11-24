package greenhouse.environment;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 登录用户的环境采样接口：查询、详情、最新值、统计、趋势、打印数据、CSV 导出和人工记录维护。
 *
 * <p>路由沿用 web.xml 中既有的 /api/environment 映射（由戴锦晨统一维护），本模块不改 web.xml，
 * 避免与其它模块的尾段追加产生覆盖冲突。自动采集不经过这里：设备上传走
 * {@link EnvironmentIngestServlet}，使用独立采集凭据，这样“登录会话”和“设备凭据”两类身份不会互相借道。
 */
public class EnvironmentServlet extends HttpServlet {

    private static final Set<String> WRITES = Set.of("create", "update", "delete");
    private static final Set<String> ROLES = Set.of("1", "2", "3");
    private static final Set<String> SOURCES = Set.of("real", "simulated", "manual");
    private static final int MAX_REPORT_ROWS = 20000;
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        run(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        run(request, response);
    }

    private void run(HttpServletRequest q, HttpServletResponse p) throws IOException {
        q.setCharacterEncoding("UTF-8");
        p.setContentType("application/json;charset=UTF-8");
        p.setHeader("Cache-Control", "no-store");

        HttpSession session = q.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (!(loginUser instanceof Map)) {
            reply(p, 401, "请先登录", null);
            return;
        }
        Map<?, ?> user = (Map<?, ?>) loginUser;
        String role = String.valueOf(user.get("roleId"));
        if (!ROLES.contains(role)) {
            reply(p, 403, "角色无效", null);
            return;
        }

        String action = param(q, "action", "list");
        if (WRITES.contains(action) && "3".equals(role)) {
            reply(p, 403, "普通用户只读，维护人工记录需要管理员角色", null);
            return;
        }
        if (WRITES.contains(action) && !"POST".equals(q.getMethod())) {
            reply(p, 405, "维护操作必须使用POST", null);
            return;
        }

        try (Connection c = EnvironmentDao.open()) {
            EnvironmentDao dao = new EnvironmentDao();
            switch (action) {
                case "context" -> reply(p, 200, "成功", context(c, dao, role, user));
                case "list" -> reply(p, 200, "成功", list(c, dao, q));
                case "detail" -> reply(p, 200, "成功", detail(c, dao, q));
                case "latest" -> reply(p, 200, "成功", latest(c, dao, q));
                case "stats" -> reply(p, 200, "成功", stats(c, dao, q));
                case "trend" -> reply(p, 200, "成功", trend(c, dao, q));
                case "report" -> reply(p, 200, "成功", report(c, dao, q));
                case "export" -> export(c, dao, q, p);
                case "create" -> reply(p, 200, "保存成功", create(c, dao, q, user));
                case "update" -> reply(p, 200, "保存成功", update(c, dao, q, user));
                case "delete" -> reply(p, 200, "删除成功", delete(c, dao, q, user));
                default -> throw new IllegalArgumentException("未知操作");
            }
        } catch (IllegalArgumentException e) {
            reply(p, 400, e.getMessage(), null);
        } catch (SQLIntegrityConstraintViolationException e) {
            reply(p, 409, "数据与现有记录冲突，请检查设备归属与唯一键", null);
        } catch (SQLException e) {
            getServletContext().log("environment", e);
            reply(p, 500, "数据库操作失败，请检查21环境脚本与数据库配置", null);
        } catch (JSONException e) {
            getServletContext().log("environment-json", e);
            reply(p, 500, "响应处理失败", null);
        }
    }

    // ------------------------------------------------------------------ 读操作

    private JSONObject context(Connection c, EnvironmentDao dao, String role, Map<?, ?> user)
            throws SQLException, JSONException {
        JSONArray metrics = new JSONArray();
        for (MetricType metric : MetricType.values()) {
            metrics.put(new JSONObject()
                    .put("code", metric.code())
                    .put("label", metric.label())
                    .put("unit", metric.unit())
                    .put("min", metric.min())
                    .put("max", metric.max())
                    .put("range", metric.rangeText()));
        }
        Object displayName = user.get("displayName");
        String operator = displayName == null || String.valueOf(displayName).isBlank()
                ? String.valueOf(user.get("username"))
                : String.valueOf(displayName);
        return new JSONObject()
                .put("role", role)
                .put("can_manage", !"3".equals(role))
                .put("metrics", metrics)
                .put("greenhouses", dao.greenhouses(c))
                .put("devices", dao.devices(c))
                .put("operator", operator)
                .put("server_time", LocalDateTime.now().format(TIME));
    }

    private JSONObject list(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        EnvironmentDao.Filter filter = filter(q);
        int page = intParam(q, "page", 1, 1, 1000000, "页码");
        int size = intParam(q, "size", 10, 1, 100, "每页条数");
        String sort = sortParam(q);
        String dir = dirParam(q);
        long total = dao.count(c, filter);
        JSONArray items = total == 0 ? new JSONArray() : dao.list(c, filter, sort, dir, page, size);
        return new JSONObject()
                .put("total", total)
                .put("page", page)
                .put("size", size)
                .put("sort", sort)
                .put("dir", dir)
                .put("items", items);
    }

    private JSONObject detail(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        long id = longParam(q, "id", "记录ID");
        JSONObject sample = dao.detail(c, id);
        if (sample == null) throw new IllegalArgumentException("记录不存在或已删除");
        return new JSONObject().put("sample", sample).put("revisions", dao.revisions(c, id));
    }

    private JSONObject latest(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        long greenhouseId = longParam(q, "greenhouse_id", "大棚");
        if (dao.greenhouse(c, greenhouseId) == null) throw new IllegalArgumentException("大棚不存在");
        Integer devicePk = optionalInt(q, "device_pk");
        JSONArray cards = dao.latest(c, greenhouseId, devicePk);
        // 每张卡片附上“距最后一次刷新多久”，>10 秒由前端标记过期；0 值仍是有效数据。
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (int i = 0; i < cards.length(); i++) {
            JSONObject card = cards.getJSONObject(i);
            if (!card.getBoolean("has_data")) continue;
            JSONObject sample = card.getJSONObject("sample");
            Timestamp sampleTime = EnvironmentDao.parseTime(sample.getString("sample_time"), "采样时间");
            long age = sampleTime == null ? -1
                    : Math.max(0, (now.getTime() - sampleTime.getTime()) / 1000);
            card.put("age_seconds", age);
            card.put("stale", age < 0 || age > 10);
        }
        return new JSONObject()
                .put("greenhouse_id", greenhouseId)
                .put("device_pk", devicePk == null ? JSONObject.NULL : devicePk)
                .put("cards", cards)
                .put("server_time", LocalDateTime.now().format(TIME));
    }

    private JSONObject stats(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        EnvironmentDao.Filter filter = filter(q);
        JSONArray summary = dao.summary(c, filter);
        if (summary.length() == 0) {
            return new JSONObject().put("empty", true).put("summary", summary)
                    .put("sources", new JSONArray());
        }
        return new JSONObject().put("empty", false).put("summary", summary)
                .put("sources", dao.sourceBreakdown(c, filter));
    }

    private JSONObject trend(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        EnvironmentDao.Filter filter = filter(q);
        if (filter.metricType() == null) throw new IllegalArgumentException("趋势需要指定指标");
        int points = intParam(q, "points", 120, 2, 500, "趋势点数");
        MetricType metric = MetricType.find(filter.metricType())
                .orElseThrow(() -> new IllegalArgumentException("指标无效"));
        return new JSONObject()
                .put("metric_type", metric.code())
                .put("label", metric.label())
                .put("unit", metric.unit())
                .put("points", dao.trend(c, filter, points));
    }

    private JSONObject report(Connection c, EnvironmentDao dao, HttpServletRequest q)
            throws SQLException, JSONException {
        EnvironmentDao.Filter filter = filter(q);
        String sort = sortParam(q);
        String dir = dirParam(q);
        JSONArray items = dao.all(c, filter, sort, dir, MAX_REPORT_ROWS);
        return new JSONObject().put("items", items).put("total", items.length())
                .put("truncated", items.length() >= MAX_REPORT_ROWS);
    }

    private void export(Connection c, EnvironmentDao dao, HttpServletRequest q,
            HttpServletResponse p) throws SQLException, IOException, JSONException {
        EnvironmentDao.Filter filter = filter(q);
        String sort = sortParam(q);
        String dir = dirParam(q);
        p.setStatus(200);
        p.setContentType("text/csv;charset=UTF-8");
        p.setHeader("Content-Disposition", "attachment; filename=environment-samples.csv");
        p.setHeader("Cache-Control", "no-store");
        Writer writer = p.getWriter();
        dao.exportCsv(c, filter, sort, dir, writer);
    }

    // ------------------------------------------------------------------ 写操作

    private JSONObject create(Connection c, EnvironmentDao dao, HttpServletRequest q, Map<?, ?> user)
            throws SQLException, JSONException {
        long greenhouseId = longParam(q, "greenhouse_id", "大棚");
        int devicePk = intParam(q, "device_pk", 0, 1, Integer.MAX_VALUE, "设备");
        MetricType metric = MetricType.find(required(q, "metric_type", "指标"))
                .orElseThrow(() -> new IllegalArgumentException("指标无效"));
        BigDecimal value = value(c, dao, greenhouseId, devicePk, metric, q);
        Timestamp sampleTime = sampleTime(required(q, "sample_time", "采样时间"));
        // 备注字段没有真实保存与展示能力，第13周从表单与接口参数中去掉，避免用户填了丢失。

        String sampleKey = "manual:" + greenhouseId + ":" + metric.code() + ":"
                + sampleTime.toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + ":" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF));
        // 只落入人工来源；服务端固定 source=manual，页面无法冒充实采或模拟数据。
        long id = dao.insertSample(c, sampleKey, greenhouseId, devicePk, metric.code(), value,
                metric.unit(), sampleTime, "manual", operatorId(user));
        return new JSONObject().put("id", id).put("sample_key", sampleKey).put("source", "manual");
    }

    private JSONObject update(Connection c, EnvironmentDao dao, HttpServletRequest q, Map<?, ?> user)
            throws SQLException, JSONException {
        long id = longParam(q, "id", "记录ID");
        String reason = required(q, "reason", "更正原因");
        if (reason.length() > 255) throw new IllegalArgumentException("更正原因不能超过255字");
        JSONObject sample = dao.detail(c, id);
        if (sample == null) throw new IllegalArgumentException("记录不存在或已删除");
        if (!"manual".equals(sample.getString("source"))) {
            throw new IllegalArgumentException("实采与模拟数据不可直接更正，请改为新增人工记录");
        }
        MetricType metric = MetricType.find(sample.getString("metric_type"))
                .orElseThrow(() -> new IllegalArgumentException("指标配置异常"));
        BigDecimal oldValue = new BigDecimal(String.valueOf(sample.get("value")));
        Timestamp oldTime = EnvironmentDao.parseTime(sample.getString("sample_time"), "采样时间");
        BigDecimal newValue = value(c, dao, sample.getLong("greenhouse_id"),
                sample.getInt("device_pk"), metric, q);
        Timestamp newTime = q.getParameter("sample_time") == null || q.getParameter("sample_time").isBlank()
                ? oldTime : sampleTime(required(q, "sample_time", "采样时间"));

        c.setAutoCommit(false);
        try {
            if (dao.updateManual(c, id, newValue, newTime) == 0) {
                throw new IllegalArgumentException("记录不存在或不是人工记录");
            }
            dao.insertRevision(c, id, oldValue, newValue, oldTime, newTime, operatorId(user), reason);
            c.commit();
        } catch (SQLException | JSONException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
        return new JSONObject().put("id", id).put("old_value", oldValue)
                .put("new_value", newValue).put("reason", reason);
    }

    private JSONObject delete(Connection c, EnvironmentDao dao, HttpServletRequest q, Map<?, ?> user)
            throws SQLException, JSONException {
        long id = longParam(q, "id", "记录ID");
        // 第13周：删除改为浏览器原生确认框，原因可以不填；字段保留以兼容历史审计数据。
        // 后台仍然只允许删除 source=manual 的记录，并且只做逻辑删除。
        String reason = param(q, "reason", "");
        if (reason.length() > 255) throw new IllegalArgumentException("删除原因不能超过255字");
        JSONObject sample = dao.detail(c, id);
        if (sample == null) throw new IllegalArgumentException("记录不存在或已删除");
        if (!"manual".equals(sample.getString("source"))) {
            throw new IllegalArgumentException("实采与模拟数据不可删除，避免原始记录被抹除");
        }
        if (dao.deleteManual(c, id, operatorId(user), reason) == 0) {
            throw new IllegalArgumentException("记录不存在或不是人工记录");
        }
        return new JSONObject().put("id", id).put("reason", reason);
    }

    // ------------------------------------------------------------------ 参数与校验

    private EnvironmentDao.Filter filter(HttpServletRequest q) {
        EnvironmentDao.Filter filter = new EnvironmentDao.Filter();
        String greenhouse = param(q, "greenhouse_id", "");
        if (!greenhouse.isEmpty()) filter.greenhouseId(longValue(greenhouse, "大棚"));
        String device = param(q, "device_pk", "");
        if (!device.isEmpty()) filter.devicePk(intValue(device, "设备"));
        String metric = param(q, "metric_type", "");
        if (!metric.isEmpty()) {
            filter.metricType(MetricType.find(metric)
                    .orElseThrow(() -> new IllegalArgumentException("指标无效")).code());
        }
        String source = param(q, "source", "");
        if (!source.isEmpty()) {
            if (!SOURCES.contains(source)) throw new IllegalArgumentException("来源无效");
            filter.source(source);
        }
        Timestamp fromTime = EnvironmentDao.parseTime(param(q, "from", ""), "开始时间");
        Timestamp toTime = EnvironmentDao.parseTime(param(q, "to", ""), "结束时间");
        if (fromTime != null && toTime != null && fromTime.after(toTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        return filter.from(fromTime).to(toTime);
    }

    /** 指标值必须落在设备配置的指标范围内，且指标必须与设备关联一致。 */
    private BigDecimal value(Connection c, EnvironmentDao dao, long greenhouseId, int devicePk,
            MetricType metric, HttpServletRequest q) throws SQLException, JSONException {
        if (dao.greenhouse(c, greenhouseId) == null) {
            throw new IllegalArgumentException("大棚不存在或已删除");
        }
        EnvironmentDao.Binding binding = dao.binding(c, greenhouseId, devicePk);
        if (binding == null) throw new IllegalArgumentException("设备不属于所选大棚");
        if (!binding.metricType().equals(metric.code())) {
            throw new IllegalArgumentException("指标与设备配置不一致");
        }
        if (!binding.unit().equals(metric.unit())) {
            throw new IllegalArgumentException("设备单位配置与指标单位不一致，请检查11/12关联数据");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(required(q, "value", "数值"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数值必须是数字");
        }
        if (value.scale() > 3) throw new IllegalArgumentException("数值最多三位小数");
        if (!metric.allows(value)) throw new IllegalArgumentException("数值超出范围：" + metric.rangeText());
        return value;
    }

    private Timestamp sampleTime(String text) {
        Timestamp time = EnvironmentDao.parseTime(text, "采样时间");
        if (time == null) throw new IllegalArgumentException("采样时间必填");
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (time.after(new Timestamp(now.getTime() + 5 * 60 * 1000L))) {
            throw new IllegalArgumentException("采样时间不能晚于当前时间5分钟以上");
        }
        if (time.before(new Timestamp(now.getTime() - 366L * 24 * 3600 * 1000))) {
            throw new IllegalArgumentException("采样时间不能早于一年前");
        }
        return time;
    }

    private String sortParam(HttpServletRequest q) {
        String sort = param(q, "sort", "sample_time");
        if (!EnvironmentDao.SORT_COLUMNS.contains(sort)) {
            throw new IllegalArgumentException("排序字段无效");
        }
        return sort;
    }

    private String dirParam(HttpServletRequest q) {
        String dir = param(q, "dir", "desc");
        if (!"asc".equals(dir) && !"desc".equals(dir)) {
            throw new IllegalArgumentException("排序方向无效");
        }
        return dir;
    }

    static long operatorId(Map<?, ?> user) {
        try {
            return Long.parseLong(String.valueOf(user.get("id")));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("登录信息缺少账号主键，请重新登录");
        }
    }

    private Integer optionalInt(HttpServletRequest q, String name) {
        String value = param(q, name, "");
        return value.isEmpty() ? null : intValue(value, name);
    }

    static String required(HttpServletRequest q, String name, String label) {
        String value = param(q, name, "");
        if (value.isEmpty()) throw new IllegalArgumentException(label + "必填");
        return value;
    }

    static String param(HttpServletRequest q, String name, String fallback) {
        String value = q.getParameter(name);
        return value == null ? fallback : value.trim();
    }

    static long longValue(String text, String label) {
        try {
            long value = Long.parseLong(text);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "ID无效");
        }
    }

    static int intValue(String text, String label) {
        try {
            int value = Integer.parseInt(text);
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "ID无效");
        }
    }

    private int intParam(HttpServletRequest q, String name, int fallback, int min, int max, String label) {
        String text = param(q, name, "");
        if (text.isEmpty()) return fallback;
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
        if (value < min || value > max) throw new IllegalArgumentException(label + "超出允许范围");
        return value;
    }

    private long longParam(HttpServletRequest q, String name, String label) {
        return longValue(required(q, name, label), label);
    }

    static void reply(HttpServletResponse p, int code, String message, Object data) throws IOException {
        try {
            p.setStatus(code);
            p.getWriter().write(new JSONObject()
                    .put("result_code", code == 200 ? 0 : code)
                    .put("result_msg", message)
                    .put("data", data == null ? JSONObject.NULL : data)
                    .toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    static List<Object> args(Object... values) {
        List<Object> args = new ArrayList<>();
        for (Object value : values) args.add(value);
        return args;
    }

    static Optional<MetricType> metric(HttpServletRequest q) {
        return MetricType.find(param(q, "metric_type", ""));
    }
}

