package greenhouse.log.servlet;

import greenhouse.log.dao.LogDao;
import greenhouse.log.dao.LogQuery;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * /api/logs 日志查询接口（只读，不提供新增/编辑/删除）。
 * 权限：角色1 全局范围；角色2/3 只能查询本人关联记录（后台强制，前端参数无效）。
 */
public class LogServlet extends HttpServlet {

    private static final String ROLE_ADMIN = "1";
    private static final LogDao LOG_DAO = new LogDao();
    private static final int EXPORT_LIMIT = 5000;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");

        Map<?, ?> actor = loginUser(request);
        String roleId = actor == null ? "" : String.valueOf(actor.get("roleId"));
        if (actor == null) {
            reject(response, 401, "请先登录");
            return;
        }
        if (!"1".equals(roleId) && !"2".equals(roleId) && !"3".equals(roleId)) {
            reject(response, 403, "角色无效");
            return;
        }

        String action = trimmed(request.getParameter("action"), "loginList");
        boolean admin = ROLE_ADMIN.equals(roleId);
        int scopeUserId = admin ? 0 : parseInt(actor.get("id"), 0);
        if (!admin && scopeUserId <= 0) {
            reject(response, 403, "您没有权限查看日志");
            return;
        }

        try {
            LogQuery query = parseQuery(request);
            if (query == null) {
                reject(response, 400, "日期格式应为 yyyy-MM-dd");
                return;
            }

            if ("loginList".equals(action)) {
                ok(response, LOG_DAO.searchLogin(query, scopeUserId));
                return;
            }
            if ("loginStats".equals(action)) {
                ok(response, LOG_DAO.statsLogin(query, scopeUserId));
                return;
            }
            if ("loginTrend".equals(action)) {
                JSONObject data = new JSONObject();
                data.put("trend", LOG_DAO.loginTrend(query, scopeUserId));
                ok(response, data);
                return;
            }
            if ("loginReport".equals(action)) {
                ok(response, report(true, query, scopeUserId));
                return;
            }
            if ("loginExport".equals(action)) {
                export(response, true, query, scopeUserId);
                return;
            }
            if ("opList".equals(action)) {
                ok(response, LOG_DAO.searchOperation(query, scopeUserId));
                return;
            }
            if ("opStats".equals(action)) {
                ok(response, LOG_DAO.statsOperation(query, scopeUserId));
                return;
            }
            if ("opTrend".equals(action)) {
                JSONObject data = new JSONObject();
                data.put("trend", LOG_DAO.operationTrend(query, scopeUserId));
                ok(response, data);
                return;
            }
            if ("opReport".equals(action)) {
                ok(response, report(false, query, scopeUserId));
                return;
            }
            if ("opExport".equals(action)) {
                export(response, false, query, scopeUserId);
                return;
            }
            reject(response, 400, "未注册的日志操作");
        } catch (SQLException | JSONException e) {
            log("日志查询失败", e);
            reject(response, 500, "日志查询失败，请检查后台日志和建表脚本");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        reject(response, 405, "日志为只读模块，不提供新增、编辑与删除");
    }

    /** 打印报表：与列表/CSV 同条件，取全部结果（上限5000）。 */
    private JSONObject report(boolean login, LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        List<JSONObject> rows = collect(login, query, scopeUserId);
        JSONArray list = new JSONArray();
        for (JSONObject row : rows) {
            list.put(row);
        }
        return new JSONObject().put("total", rows.size())
                .put("roleScope", scopeUserId == 0 ? "global" : "self")
                .put("list", list);
    }

    private List<JSONObject> collect(boolean login, LogQuery query, int scopeUserId)
            throws SQLException, JSONException {

        List<JSONObject> rows = new ArrayList<JSONObject>();
        int page = 1;
        query.setPageSize(100);
        while (true) {
            query.setPage(page);
            JSONObject data = login
                    ? LOG_DAO.searchLogin(query, scopeUserId)
                    : LOG_DAO.searchOperation(query, scopeUserId);
            JSONArray list = data.getJSONArray("list");
            if (list.length() == 0) {
                break;
            }
            for (int i = 0; i < list.length(); i++) {
                rows.add(list.getJSONObject(i));
            }
            long total = data.getLong("total");
            if ((long) page * 100 >= total || rows.size() >= EXPORT_LIMIT) {
                break;
            }
            page++;
        }
        return rows;
    }

    private void export(HttpServletResponse response, boolean login, LogQuery query, int scopeUserId)
            throws IOException, SQLException, JSONException {

        List<JSONObject> rows = collect(login, query, scopeUserId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        if (login) {
            csv.append("事件编号,时间,尝试账号,用户ID,结果,原因类别,来源,IP,客户端\r\n");
        } else {
            csv.append("日志编号,时间,操作人ID,操作人,角色,模块,动作,目标编号,目标名称,结果,原因类别,摘要,追踪标识\r\n");
        }
        for (JSONObject row : rows) {
            if (login) {
                csv.append(csvText(row.get("id"))).append(',')
                        .append(csvText(row.get("eventTime"))).append(',')
                        .append(csvText(row.get("usernameAttempt"))).append(',')
                        .append(csvText(nullable(row.get("userId")))).append(',')
                        .append(csvText(loginResultLabel(String.valueOf(row.get("result"))))).append(',')
                        .append(csvText(reasonLabel(String.valueOf(row.get("reasonCode"))))).append(',')
                        .append(csvText(sourceLabel(String.valueOf(row.get("source"))))).append(',')
                        .append(csvText(row.get("ip"))).append(',')
                        .append(csvText(row.get("clientInfo"))).append("\r\n");
            } else {
                csv.append(csvText(row.get("id"))).append(',')
                        .append(csvText(row.get("operateTime"))).append(',')
                        .append(csvText(nullable(row.get("operatorId")))).append(',')
                        .append(csvText(row.get("operatorName"))).append(',')
                        .append(csvText(roleLabel(nullable(row.get("roleId"))))).append(',')
                        .append(csvText(row.get("module"))).append(',')
                        .append(csvText(row.get("action"))).append(',')
                        .append(csvText(row.get("targetId"))).append(',')
                        .append(csvText(row.get("targetName"))).append(',')
                        .append(csvText(opResultLabel(String.valueOf(row.get("result"))))).append(',')
                        .append(csvText(row.get("reasonCode"))).append(',')
                        .append(csvText(row.get("summary"))).append(',')
                        .append(csvText(row.get("traceId"))).append("\r\n");
            }
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = (login ? "登录日志_" : "操作日志_") + stamp + ".csv";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name())
                .replaceAll("\\+", "%20");
        response.setStatus(200);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"logs.csv\"; filename*=UTF-8''" + encoded);
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        response.setContentLength(bytes.length);
        OutputStream output = response.getOutputStream();
        output.write(bytes);
        output.flush();
    }

    private LogQuery parseQuery(HttpServletRequest request) {
        LogQuery query = new LogQuery();
        query.setKeyword(param(request, "keyword"));
        query.setResult(param(request, "result"));
        query.setReason(param(request, "reason"));
        query.setSource(param(request, "source"));
        query.setModule(param(request, "module"));
        query.setAction(param(request, "actionName"));
        String start = param(request, "startDate");
        String end = param(request, "endDate");
        if (!start.isEmpty() && !isDate(start)) {
            return null;
        }
        if (!end.isEmpty() && !isDate(end)) {
            return null;
        }
        query.setStartDate(start);
        query.setEndDate(end);
        query.setSortBy(param(request, "sortBy"));
        query.setSortDir(param(request, "sortDir"));
        query.setPage(parseInt(param(request, "page"), 1));
        query.setPageSize(parseInt(param(request, "size"), 10));
        return query;
    }

    private void ok(HttpServletResponse response, Object data) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("result_code", 0);
            body.put("result_msg", "ok");
            body.put("data", data == null ? JSONObject.NULL : data);
            response.setStatus(200);
            response.getWriter().print(body.toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("result_code", status);
            body.put("result_msg", message);
            body.put("data", new JSONObject());
            response.setStatus(status);
            response.getWriter().print(body.toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static Map<?, ?> loginUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute("loginUser");
        return user instanceof Map ? (Map<?, ?>) user : null;
    }

    private static String param(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        return value == null ? "" : value.trim();
    }

    private static String trimmed(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String csvText(Object value) {
        String text = value == null || JSONObject.NULL.equals(value)
                ? "" : String.valueOf(value);
        text = text.replace("\r\n", " ").replace("\n", " ").replace("\r", " ");
        if (!text.isEmpty()) {
            char first = text.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@') {
                text = "'" + text;
            }
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static String nullable(Object value) {
        return value == null || JSONObject.NULL.equals(value) ? "" : String.valueOf(value);
    }

    private static String loginResultLabel(String result) {
        return "success".equals(result) ? "成功" : "失败";
    }

    private static String opResultLabel(String result) {
        if ("success".equals(result)) {
            return "成功";
        }
        return "denied".equals(result) ? "拒绝" : "失败";
    }

    private static String reasonLabel(String reason) {
        if ("credential_error".equals(reason)) return "凭据错误";
        if ("account_disabled".equals(reason)) return "账号停用";
        if ("invalid_input".equals(reason)) return "输入不完整";
        if ("auth_service_error".equals(reason)) return "认证服务失败";
        if ("no_permission".equals(reason)) return "无权限";
        if ("validation_failed".equals(reason)) return "校验未通过";
        if ("conflict".equals(reason)) return "状态冲突";
        if ("db_error".equals(reason)) return "数据库异常";
        return "";
    }

    private static String sourceLabel(String source) {
        if ("wx".equals(source)) return "微信";
        if ("mini".equals(source)) return "小程序";
        return "PC";
    }

    private static String roleLabel(String roleId) {
        if ("1".equals(roleId)) return "系统管理员";
        if ("2".equals(roleId)) return "普通管理员";
        if ("3".equals(roleId)) return "普通用户";
        return "";
    }
}
