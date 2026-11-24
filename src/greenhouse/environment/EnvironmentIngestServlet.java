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
 * 第13周 环境采样上传入口：/api/environment/ingest（唐昕煜）。
 *
 * <p>这条路由不使用登录会话，只用采集凭据（请求头 X-Collector-Key）证明“这是一台已登记的采集端”；
 * 凭据来自本机配置 greenhouse.local.properties 的 greenhouse.environment.collectorKey 或环境变量
 * GH_COLLECTOR_KEY。凭据缺失时 503，不做“无凭据放行”。
 *
 * <p>本周按三人约定（w13-greenhouse-device-metric-v1）落实的硬规则：
 * <ol>
 *   <li>一台已绑定采样设备只对应一个指标；逐条核对样本 metric_type 与绑定是否一致，
 *       不一致直接拒绝并给出原因，<b>不再静默改写成绑定指标</b>；</li>
 *   <li>大棚必须存在、未删除且 status=enabled；控制设备（device_type=control）不能当采样源；</li>
 *   <li>sample_key 幂等：同键同内容（含 source）只算 duplicated，同键异内容拒绝覆盖；</li>
 *   <li>并发唯一键冲突必须重读原记录再比较，其他数据库约束失败按真实失败上报，不一律算重复；</li>
 *   <li>逐条回执 items 带 sample_id / ingest_status / alarm_status，供报警接入与采集端核对。</li>
 * </ol>
 *
 * <p>报警模块（李慧颖）在独立分支实现，尚未合入本分支，因此通过 {@link AlarmBridge} 反射挂接：
 * 报警类不在 classpath 时 alarm_status 为 NOT_READY，采样照常入库，不把“未接入”报告成判定成功。
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
            Batch batch = ingest(c, dao, body);
            JSONObject data = batch.toJson();
            if (batch.accepted == 0 && batch.duplicated == 0) {
                // 全部被拒绝：保留逐条原因，但整体按失败返回，避免采集端把拒绝当成功。
                EnvironmentServlet.reply(p, 400, "本批数据全部未入库", data);
                return;
            }
            EnvironmentServlet.reply(p, 200, "已处理", data);
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

    private Batch ingest(Connection c, EnvironmentDao dao, JSONObject body)
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

        JSONObject greenhouse = dao.greenhouse(c, greenhouseId);
        if (greenhouse == null) {
            throw new IllegalArgumentException("大棚不存在或已删除");
        }
        if (!"enabled".equalsIgnoreCase(greenhouse.optString("status", ""))) {
            throw new IllegalArgumentException("大棚已停用，不能接收采样");
        }

        EnvironmentDao.Binding binding = dao.binding(c, greenhouseId, devicePk);
        if (binding == null) {
            throw new IllegalArgumentException("设备不属于该大棚或未绑定采样指标");
        }
        if (!binding.sensor()) {
            throw new IllegalArgumentException("设备 " + binding.deviceCode()
                    + " 是控制设备，不能作为采样来源");
        }
        MetricType boundMetric = MetricType.find(binding.metricType())
                .orElseThrow(() -> new IllegalArgumentException("设备指标配置异常，请检查11/12关联数据"));
        if (!boundMetric.unit().equals(binding.unit())) {
            throw new IllegalArgumentException("设备单位与指标单位不一致，请检查设备关联数据");
        }

        JSONArray samples = body.optJSONArray("samples");
        if (samples == null || samples.length() == 0) {
            throw new IllegalArgumentException("samples 不能为空");
        }
        if (samples.length() > MAX_BATCH) {
            throw new IllegalArgumentException("单批不能超过" + MAX_BATCH + "条，请分组上传");
        }

        Batch batch = new Batch();
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (int i = 0; i < samples.length(); i++) {
            JSONObject sample = samples.optJSONObject(i);
            if (sample == null) {
                batch.addRejected(i, "", "样本不是对象");
                continue;
            }
            String sampleKey = sample.optString("sample_key", "").trim();
            if (!sampleKey.matches(KEY_PATTERN)) {
                batch.addRejected(i, sampleKey, "sample_key 缺失或格式不合法（8-128位字母数字_.:-）");
                continue;
            }

            // 1) 指标必须与设备绑定一致：不一致拒绝，绝不改写成绑定指标后入库。
            String metricText = sample.optString("metric_type", "").trim();
            if (metricText.isEmpty()) {
                batch.addRejected(i, sampleKey, "缺少 metric_type，无法核对设备绑定指标");
                continue;
            }
            MetricType sampleMetric = MetricType.find(metricText).orElse(null);
            if (sampleMetric == null) {
                batch.addRejected(i, sampleKey, "metric_type 未定义：" + metricText);
                continue;
            }
            if (!sampleMetric.code().equals(boundMetric.code())) {
                batch.addRejected(i, sampleKey, "指标与设备绑定不一致：设备 " + binding.deviceCode()
                        + " 只接受 " + boundMetric.code() + "（" + boundMetric.label()
                        + " " + boundMetric.unit() + "），本批收到 " + sampleMetric.code());
                continue;
            }

            BigDecimal value;
            try {
                value = new BigDecimal(sample.get("value").toString());
            } catch (JSONException | NumberFormatException e) {
                batch.addRejected(i, sampleKey, "value 缺失或不是数字");
                continue;
            }
            if (value.scale() > 3) {
                batch.addRejected(i, sampleKey, "value 最多三位小数");
                continue;
            }
            Timestamp sampleTime;
            try {
                sampleTime = EnvironmentDao.parseTime(sample.optString("sample_time", ""), "sample_time");
            } catch (IllegalArgumentException e) {
                batch.addRejected(i, sampleKey, "sample_time 应为 yyyy-MM-dd HH:mm:ss");
                continue;
            }
            if (sampleTime == null) {
                batch.addRejected(i, sampleKey, "sample_time 必填");
                continue;
            }
            if (sampleTime.after(new Timestamp(now.getTime() + 5 * 60 * 1000L))
                    || sampleTime.before(new Timestamp(now.getTime() - 366L * 24 * 3600 * 1000))) {
                batch.addRejected(i, sampleKey, "sample_time 超出允许时间窗口");
                continue;
            }
            if (!boundMetric.allows(value)) {
                batch.addRejected(i, sampleKey, "value 超出该指标范围：" + boundMetric.rangeText());
                continue;
            }

            JSONObject existing = dao.bySampleKey(c, sampleKey);
            if (existing != null) {
                String difference = difference(existing, greenhouseId, devicePk,
                        boundMetric.code(), value, sampleTime, source);
                if (difference == null) {
                    batch.addDuplicated(i, sampleKey, existing.getLong("id"), "同键同内容重试，未重复入库");
                } else {
                    batch.addRejected(i, sampleKey, "sample_key 已存在且内容不同（" + difference
                            + "），拒绝覆盖原始记录");
                }
                continue;
            }

            // 2) 每条采样独立事务：采样写入与报警登记同生共死，提交后才做判定。
            c.setAutoCommit(false);
            try {
                long sampleId = dao.insertSample(c, sampleKey, greenhouseId, devicePk,
                        boundMetric.code(), value, boundMetric.unit(), sampleTime, source, null);
                boolean enqueued = AlarmBridge.enqueue(c, sampleId);
                c.commit();
                String alarmStatus = enqueued
                        ? AlarmBridge.process(getServletContext(), sampleId)
                        : AlarmBridge.NOT_READY;
                batch.addAccepted(i, sampleKey, sampleId, alarmStatus);
            } catch (SQLIntegrityConstraintViolationException e) {
                // 并发重试：唯一键刚被别的连接写入，必须重读内容再比较，不能一律当重复。
                c.rollback();
                JSONObject raced = dao.bySampleKey(c, sampleKey);
                if (raced == null) {
                    batch.addRejected(i, sampleKey,
                            "入库失败：唯一键冲突但读不到原记录（" + e.getMessage() + "）");
                } else {
                    String difference = difference(raced, greenhouseId, devicePk,
                            boundMetric.code(), value, sampleTime, source);
                    if (difference == null) {
                        batch.addDuplicated(i, sampleKey, raced.getLong("id"), "并发重试命中同一采样，未重复入库");
                    } else {
                        batch.addRejected(i, sampleKey, "sample_key 已存在且内容不同（" + difference
                                + "），拒绝覆盖原始记录");
                    }
                }
            } catch (SQLException | JSONException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        getServletContext().log("environment-ingest device=" + devicePk
                + " source=" + source
                + " accepted=" + batch.accepted + " duplicated=" + batch.duplicated
                + " rejected=" + batch.rejected + " alarm=" + AlarmBridge.available());
        return batch;
    }

    /** 返回 null 表示与已入库记录内容一致；否则返回第一处差异，用于拒绝原因。 */
    private static String difference(JSONObject existing, long greenhouseId, int devicePk,
            String metricType, BigDecimal value, Timestamp sampleTime, String source) throws JSONException {
        if (existing.getLong("greenhouse_id") != greenhouseId) return "大棚不同";
        if (existing.getInt("device_pk") != devicePk) return "设备不同";
        if (!metricType.equals(existing.optString("metric_type", ""))) return "指标不同";
        if (new BigDecimal(String.valueOf(existing.get("value"))).compareTo(value) != 0) return "数值不同";
        if (!EnvironmentDao.time(sampleTime).equals(existing.optString("sample_time", ""))) return "采样时间不同";
        // 幂等比较含 source：模拟数据与实采数据不能因为同键就互相顶替。
        if (!source.equals(existing.optString("source", ""))) return "来源不同";
        return null;
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

    /** 一批上传的结果：保留原有 accepted/duplicated/rejected 计数，并附逐条回执 items。 */
    private static final class Batch {
        int accepted;
        int duplicated;
        int rejected;
        final JSONArray items = new JSONArray();

        void addAccepted(int index, String sampleKey, long sampleId, String alarmStatus) throws JSONException {
            accepted++;
            items.put(new JSONObject()
                    .put("input_index", index)
                    .put("sample_key", sampleKey)
                    .put("sample_id", sampleId)
                    .put("ingest_status", "accepted")
                    .put("alarm_status", alarmStatus == null ? AlarmBridge.NOT_READY : alarmStatus));
        }

        void addDuplicated(int index, String sampleKey, long sampleId, String reason) throws JSONException {
            duplicated++;
            items.put(new JSONObject()
                    .put("input_index", index)
                    .put("sample_key", sampleKey)
                    .put("sample_id", sampleId)
                    .put("ingest_status", "duplicated")
                    .put("alarm_status", "NOT_EVALUATED")
                    .put("reason", reason));
        }

        void addRejected(int index, String sampleKey, String reason) throws JSONException {
            rejected++;
            items.put(new JSONObject()
                    .put("input_index", index)
                    .put("sample_key", sampleKey)
                    .put("sample_id", JSONObject.NULL)
                    .put("ingest_status", "rejected")
                    .put("alarm_status", "NOT_EVALUATED")
                    .put("reason", reason));
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject()
                    .put("accepted", accepted)
                    .put("duplicated", duplicated)
                    .put("rejected", rejected)
                    .put("alarm_ready", AlarmBridge.available())
                    .put("items", items);
        }
    }
}
