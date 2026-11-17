package greenhouse.threshold;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class ThresholdRuleServlet extends HttpServlet {
    private final ThresholdRuleDao dao = new ThresholdRuleDao();

    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            writeJson(response, 401, "请先登录", new JSONArray());
            return;
        }

        if (!"list".equals(request.getParameter("action"))) {
            writeJson(response, 400, "不支持的操作", new JSONArray());
            return;
        }

        JSONArray data = new JSONArray();

        try {
            for (ThresholdRuleDao.RuleRow rule : dao.listRules()) {
                JSONObject item = new JSONObject();
                item.put("id", rule.id());
                item.put("name", rule.name());
                item.put("lower", rule.lower() == null ? JSONObject.NULL : rule.lower());
                item.put("upper", rule.upper() == null ? JSONObject.NULL : rule.upper());
                item.put("unit", rule.unit());
                item.put("enabled", rule.enabled());
                item.put("greenhouseId", Long.toString(rule.greenhouseId()));
                item.put("metricType", rule.metricType());
                item.put("remark", rule.remark() == null ? "" : rule.remark());
                item.put("updatedAt", rule.updatedAt());
                data.put(item);
            }
        } catch (SQLException | JSONException e) {
            log("查询阈值规则失败", e);
            writeJson(response, 500, "查询阈值规则失败", new JSONArray());
            return;
        }

        writeJson(response, 200, "ok", data);
    }

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        Object loginUser = session == null ? null : session.getAttribute("loginUser");
        if (!(loginUser instanceof Map<?, ?> user)) {
            writeJson(response, 401, "请先登录", new JSONArray());
            return;
        }
        String role = String.valueOf(user.get("roleId"));
        if (!"1".equals(role) && !"2".equals(role)) {
            writeJson(response, 403, "没有维护规则的权限", new JSONArray());
            return;
        }
        String action = request.getParameter("action");

        try {
            if ("delete".equals(action)) {
                long id = Long.parseLong(request.getParameter("id"));
                if (id <= 0) {
                    throw new IllegalArgumentException("规则编号无效");
                }
                long operatorId = Long.parseLong(String.valueOf(user.get("id")));
                boolean deleted = dao.deleteDisabled(id, operatorId);
                writeJson(response, deleted ? 200 : 409,
                        deleted ? "规则已删除" : "规则已不存在或仍在启用，请刷新列表核对",
                        new JSONArray());
                return;
            }
            if ("edit".equals(action) || "enable".equals(action) || "disable".equals(action)) {
                long id = Long.parseLong(request.getParameter("id"));
                if (id <= 0) {
                    throw new IllegalArgumentException("规则编号无效");
                }
                long operatorId = Long.parseLong(String.valueOf(user.get("id")));
                String remark = request.getParameter("remark");
                if (remark != null && remark.length() > 500) {
                    throw new IllegalArgumentException("备注最多 500 个字符");
                }
                boolean changed = "edit".equals(action)
                        ? dao.editBounds(id, request.getParameter("lower"), request.getParameter("upper"), remark, operatorId)
                        : dao.setEnabled(id, "enable".equals(action), operatorId);
                writeJson(response, changed ? 200 : 404,
                        changed ? "规则已更新" : "规则不存在或已删除", new JSONArray());
                return;
            }
            if (!"create".equals(action)) {
                writeJson(response, 400, "不支持的操作", new JSONArray());
                return;
            }

            String name = request.getParameter("name");
            if (name == null || name.isBlank() || name.strip().length() > 100) {
                throw new IllegalArgumentException("规则名称须为 1 至 100 个字符");
            }
            String metric = request.getParameter("metric");
            String unit = switch (metric == null ? "" : metric) {
                case "air_temperature", "soil_temperature" -> "℃";
                case "air_humidity", "soil_moisture" -> "%";
                case "illuminance" -> "lx";
                default -> throw new IllegalArgumentException("请选择有效指标");
            };

            long greenhouseId = Long.parseLong(request.getParameter("greenhouseId"));
            if (greenhouseId <= 0) {
                throw new IllegalArgumentException("请选择有效大棚");
            }
            long userId = Long.parseLong(String.valueOf(user.get("id")));
            dao.insert(name.strip(), greenhouseId, metric, unit,
                    request.getParameter("lower"), request.getParameter("upper"), userId);
        } catch (IllegalArgumentException e) {
            writeJson(response, 400, e.getMessage(), new JSONArray());
            return;
        } catch (SQLException e) {
            if ("enable".equals(action) && e.getErrorCode() == 1062) {
                writeJson(response, 409, "同一大棚和指标已有启用规则，请先停用原规则", new JSONArray());
                return;
            }
            log("保存阈值规则失败", e);
            writeJson(response, 500, "保存阈值规则失败", new JSONArray());
            return;
        }
        writeJson(response, 201, "保存成功", new JSONArray());
    }

    private void writeJson(HttpServletResponse response, int status,
                           String message, JSONArray data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");

        try {
            JSONObject result = new JSONObject();
            result.put("result_code", status < 400 ? 0 : status);
            result.put("result_msg", message);
            result.put("data", data);
            response.getWriter().write(result.toString());
        } catch (JSONException e) {
            throw new IOException("生成 JSON 响应失败", e);
        }
    }
}




