package greenhouse.threshold;

import greenhouse.greenhouse.CsrfGuard;
import jakarta.servlet.http.*;
import org.json.*;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

public class ThresholdRuleServlet extends HttpServlet {
    private final ThresholdRuleDao dao = new ThresholdRuleDao();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("loginUser") instanceof Map)) {
            reply(response, 401, "请先登录", null); return;
        }
        try {
            switch (String.valueOf(request.getParameter("action"))) {
                case "list" -> reply(response, 200, "ok", new JSONArray(dao.listRules()));
                case "detail" -> {
                    Map<String, Object> row = dao.detail(positiveId(request.getParameter("id")));
                    reply(response, row == null ? 404 : 200,
                            row == null ? "规则不存在或已删除，请刷新" : "ok",
                            row == null ? null : new JSONObject(row));
                }
                default -> reply(response, 400, "不支持的操作", null);
            }
        } catch (IllegalArgumentException e) {
            reply(response, 400, e.getMessage(), null);
        } catch (SQLException e) {
            log("读取阈值规则失败", e);
            reply(response, 500, "规则读取失败，请稍后重试", null);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (!(loginUser instanceof Map<?, ?> user)) { reply(response, 401, "请先登录", null); return; }
        if (!Set.of("1", "2").contains(String.valueOf(user.get("roleId")))) {
            reply(response, 403, "当前角色无权维护规则", null); return;
        }
        if (!CsrfGuard.valid(request)) { reply(response, 403, "操作令牌失效，请刷新页面重新登录后重试", null); return; }
        String action = String.valueOf(request.getParameter("action"));
        if (!Set.of("create", "edit", "enable", "disable", "delete").contains(action)) {
            reply(response, 400, "不支持的操作", null); return;
        }
        try {
            long userId = positiveId(String.valueOf(user.get("id")));
            long id;
            String name = null, remark = null;
            ThresholdBounds.Limits limits = null;
            if ("create".equals(action) || "edit".equals(action)) {
                name = request.getParameter("name");
                name = name == null ? "" : name.strip();
                if (name.isEmpty() || name.length() > 100) throw new IllegalArgumentException("规则名称须为1至100字");
                remark = request.getParameter("remark");
                if (remark == null) remark = "";
                if (remark.length() > 500) throw new IllegalArgumentException("备注最多500字");
                limits = ThresholdBounds.parse(request.getParameter("lower"), request.getParameter("upper"));
            }
            if ("create".equals(action)) {
                String metric = request.getParameter("metric");
                String unit = switch (metric == null ? "" : metric) {
                    case "air_temperature", "soil_temperature" -> "℃";
                    case "air_humidity", "soil_moisture" -> "%";
                    case "illuminance" -> "lx";
                    default -> throw new IllegalArgumentException("请选择有效监测指标");
                };
                id = dao.create(name, positiveId(request.getParameter("greenhouseId")), metric,
                        unit, limits, remark, userId);
            } else {
                id = positiveId(request.getParameter("id"));
                dao.change(id, action, name, limits, remark, userId);
            }
            JSONObject data = new JSONObject();
            try { data.put("id", Long.toString(id)); } catch (JSONException e) { throw new IOException(e); }
            reply(response, "create".equals(action) ? 201 : 200,
                    "create".equals(action) ? "新增成功，当前为停用状态" : "操作成功", data);
        } catch (IllegalArgumentException e) {
            reply(response, 400, e.getMessage(), null);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {
                reply(response, 409, "同棚同指标已有启用规则，请刷新并先停用原规则", null);
            } else {
                log("维护阈值规则失败", e);
                reply(response, 500, "服务暂时不可用，请稍后重试", null);
            }
        }
    }

    private long positiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("记录编号无效，请重新选择");
    }

    private void reply(HttpServletResponse response, int status, String message, Object data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            JSONObject json = new JSONObject();
            json.put("result_code", status < 400 ? 0 : status);
            json.put("result_msg", message);
            json.put("data", data == null ? JSONObject.NULL : data);
            response.getWriter().write(json.toString());
        } catch (JSONException e) { throw new IOException("生成JSON响应失败", e); }
    }
}
