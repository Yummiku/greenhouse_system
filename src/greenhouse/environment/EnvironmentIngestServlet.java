package greenhouse.environment;

import greenhouse.config.LocalSettings;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 设备采集上传入口：/api/environment/ingest。
 *
 * <p>这条路由不使用登录会话，只用采集凭据（请求头 X-Collector-Key）来证明“这是一台已登记的设备”，
 * 凭据来自本机配置文件 greenhouse.local.properties 的 greenhouse.environment.collectorKey，
 * 或环境变量 GH_COLLECTOR_KEY。凭据缺失时直接 503，不做“无凭据放行”。
 *
 * <p>去重语义：
 * <ul>
 *   <li>同一个 sample_key、内容一致 → 计入 duplicated，不重复入库（重试安全）；</li>
 *   <li>同一个 sample_key、内容不一致 → 计入 rejected，报冲突，绝不覆盖原始记录。</li>
 * </ul>
 */
@WebServlet("/api/environment/ingest")
public class EnvironmentIngestServlet extends HttpServlet {

    private static final int MAX_BODY = 256 * 1024;
    private static final int MAX_BATCH = 200;
    private static final Set<String> SOURCES = Set.of("real", "simulated");
    private static final String KEY_PATTERN = "[A-Za-z0-9_.:\\-]{8,128}";

    @Override
    protected void doPost(HttpServletRequest q, HttpServletResponse p) throws IOException {
        q.setCharacterEncoding("UTF-8");
        p.setContentType("application/json;charset=UTF-8");
        p.setHeader("Cache-Control", "no-store");

        String configured = LocalSettings.get(
                "greenhouse.environment.collectorKey", "GH_COLLECTOR_KEY", null);
        if (configured == null || configured.isBlank()) {
            EnvironmentServlet.reply(p, 503,
                    "服务端未配置采集凭据，请在本机配置 greenhouse.environment.collectorKey", null);
            return;
        }
        String supplied = q.getHeader("X-Collector-Key");
        if (supplied == null || supplied.isBlank()) {
            EnvironmentServlet.reply(p, 401, "缺少采集凭据", null);
            return;
        }
        if (!MessageDigest.isEqual(
                supplied.trim().getBytes(StandardCharsets.UTF_8),
                configured.trim().getBytes(StandardCharsets.UTF_8))) {
            EnvironmentServlet.reply(p, 401, "采集凭据无效", null);
            return;
        }

        JSONObject body;
        try {
            body = new JSONObject(read(q));
        } catch (JSONException e) {
            EnvironmentServlet.reply(p, 400, "请求体不是合法JSON", null);
            return;
        } catch (IllegalArgumentException e) {
            EnvironmentServlet.reply(p, 400, e.getMessage(), null);
            return;
        }

        try (Connection c = EnvironmentDao.open()) {
            EnvironmentDao dao = new EnvironmentDao();
            Result result = ingest(c, dao, body, p);
            if (result.accepted == 0 && result.duplicated == 0) {
                EnvironmentServlet.reply(p, 400, "本批数据全部未入库",
                        new JSONObject()
                                .put("accepted", 0)
                                .put("duplicated", 0)
                                .put("rejected", result.rejected));
                return;
            }
            EnvironmentServlet.reply(p, 200, "已处理", new JSONObject()
                    .put("accepted", result.accepted)
                    .put("duplicated", result.duplicated)
                    .put("rejected", result.rejected));
        } catch (IllegalArgumentException e) {
            EnvironmentServlet.reply(p, 400, e.getMessage(), null);
        } catch (SQLException e) {
            getServletContext().log("environment-ingest", e);
            EnvironmentServlet.reply(p, 500, "数据库操作失败，请检查21环境脚本与数据库配置", null);
        } catch (JSONException e) {
            getServletContext().log("environment-ingest-json", e);
            EnvironmentServlet.reply(p, 500, "响应处理失败", null);
        }
    }

    private Result ingest(Connection c, EnvironmentDao dao, JSONObject body, HttpServletResponse p)
            throws SQLException, JSONException {
        long greenhouseId;
        int devicePk;
        try {
            greenhouseId = EnvironmentServlet.longValue(
                    String.valueOf(body.get("greenhouse_id")), "大棚");
            devicePk = EnvironmentServlet.intValue(String.valueOf(body.get("device_pk")), "设备");
        } catch (JSONException e) {
            throw new IllegalArgumentException("缺少 greenhouse_id 或 device_pk");
        }
        String source = body.optString("source", "").trim();
        if (!SOURCES.contains(source)) {
            throw new IllegalArgumentException("source 只允许 real 或 simulated；人工记录请在页面录入");
        }
        if (dao.greenhouse(c, greenhouseId) == null) {
            throw new IllegalArgumentException("大棚不存在或已删除");
        }
        EnvironmentDao.Binding binding = dao.binding(c, greenhouseId, devicePk);
        if (binding == null) throw new IllegalArgumentException("设备不属于该大棚");

        JSONArray samples = body.optJSONArray("samples");
        if (samples == null || samples.length() == 0) {
            throw new IllegalArgumentException("samples 不能为空");
        }
        if (samples.length() > MAX_BATCH) {
            throw new IllegalArgumentException("单批不能超过" + MAX_BATCH + "条，请分组上传");
        }

        MetricType boundMetric = MetricType.find(binding.metricType())
                .orElseThrow(() -> new IllegalArgumentException("设备指标配置异常，请检查11/12关联数据"));
        if (!boundMetric.unit().equals(binding.unit())) {
            throw new IllegalArgumentException("设备单位与指标单位不一致，请检查设备关联数据");
        }

        Result result = new Result();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (int i = 0; i < samples.length(); i++) {
            JSONObject sample = samples.optJSONObject(i);
            if (sample == null) {
                result.reject(i, "", "样本不是对象");
                continue;
            }
            String sampleKey = sample.optString("sample_key", "").trim();
            if (!sampleKey.matches(KEY_PATTERN)) {
                result.reject(i, sampleKey, "sample_key 缺失或格式不合法（8-128位字母数字_.:-）");
                continue;
            }
            BigDecimal value;
            try {
                value = new BigDecimal(sample.get("value").toString());
            } catch (JSONException | NumberFormatException e) {
                result.reject(i, sampleKey, "value 缺失或不是数字");
                continue;
            }
            if (value.scale() > 3) {
                result.reject(i, sampleKey, "value 最多三位小数");
                continue;
            }
            Timestamp sampleTime;
            try {
                sampleTime = EnvironmentDao.parseTime(sample.optString("sample_time", ""), "sample_time");
            } catch (IllegalArgumentException e) {
                result.reject(i, sampleKey, "sample_time 应为 yyyy-MM-dd HH:mm:ss");
                continue;
            }
            if (sampleTime == null) {
                result.reject(i, sampleKey, "sample_time 必填");
                continue;
            }
            if (sampleTime.after(new Timestamp(now.getTime() + 5 * 60 * 1000L))
                    || sampleTime.before(new Timestamp(now.getTime() - 366L * 24 * 3600 * 1000))) {
                result.reject(i, sampleKey, "sample_time 超出允许时间窗口");
                continue;
            }
            if (!boundMetric.allows(value)) {
                result.reject(i, sampleKey, "value 超出该指标范围：" + boundMetric.rangeText());
                continue;
            }

            JSONObject existing = dao.bySampleKey(c, sampleKey);
            if (existing != null) {
                boolean same = existing.getLong("greenhouse_id") == greenhouseId
                        && existing.getInt("device_pk") == devicePk
                        && existing.getString("metric_type").equals(boundMetric.code())
                        && new BigDecimal(String.valueOf(existing.get("value"))).compareTo(value) == 0
                        && existing.getString("sample_time")
                                .equals(EnvironmentDao.time(sampleTime));
                if (same) {
                    result.duplicated++;
                } else {
                    result.reject(i, sampleKey, "sample_key 已存在且内容不同，拒绝覆盖原始记录");
                }
                continue;
            }
            try {
                dao.insertSample(c, sampleKey, greenhouseId, devicePk, boundMetric.code(), value,
                        boundMetric.unit(), sampleTime, source, null);
                result.accepted++;
            } catch (SQLIntegrityConstraintViolationException e) {
                // 并发重试可能刚被别的连接插入同键，按重复处理而不是报错。
                result.duplicated++;
            }
        }
        getServletContext().log("environment-ingest device=" + devicePk
                + " accepted=" + result.accepted + " duplicated=" + result.duplicated
                + " rejected=" + result.rejected.length());
        return result;
    }

    private static String read(HttpServletRequest q) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = q.getReader()) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                text.append(buffer, 0, read);
                if (text.length() > MAX_BODY) {
                    throw new IllegalArgumentException("请求体超过256KB，请减小批次");
                }
            }
        }
        if (text.length() == 0) throw new IllegalArgumentException("请求体为空");
        return text.toString();
    }

    /** 一批上传的统计结果；rejected 逐条带原因，便于采集端展示失败样本。 */
    private static final class Result {
        int accepted;
        int duplicated;
        final JSONArray rejected = new JSONArray();

        void reject(int index, String sampleKey, String reason) throws JSONException {
            rejected.put(new JSONObject()
                    .put("index", index)
                    .put("sample_key", sampleKey)
                    .put("reason", reason));
        }
    }
}

