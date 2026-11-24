package greenhouse.dashboard.servlet;

import greenhouse.log.dao.LogDao;
import greenhouse.log.dao.LogQuery;
import greenhouse.message.dao.PersonalMessageDao;
import greenhouse.message.entity.MessageQuery;
import greenhouse.message.entity.MessageStats;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * /api/dashboard 驾驶舱数据接口。
 * my：所有已登录角色，管理员看全局，普通角色只看本人；
 * integration：仅系统管理员，聚合已接通真实来源，未接通业务明确标记“未接入”。
 */
public class DashboardServlet extends HttpServlet {

    private static final LogDao LOG_DAO = new LogDao();
    private static final PersonalMessageDao MESSAGE_DAO = new PersonalMessageDao();
    private static final DateTimeFormatter MINUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");

        Map<?, ?> actor = loginUser(request);
        if (actor == null) {
            reject(response, 401, "请先登录");
            return;
        }
        String roleId = String.valueOf(actor.get("roleId"));
        if (!"1".equals(roleId) && !"2".equals(roleId) && !"3".equals(roleId)) {
            reject(response, 403, "角色无效");
            return;
        }
        int userId = parseInt(actor.get("id"), 0);
        if (userId <= 0) {
            reject(response, 403, "您没有权限查看驾驶舱");
            return;
        }

        String action = request.getParameter("action");
        action = action == null || action.trim().isEmpty() ? "my" : action.trim();

        try {
            if ("my".equals(action)) {
                ok(response, my(actor, userId, roleId, request));
                return;
            }
            if ("integration".equals(action)) {
                if (!"1".equals(roleId)) {
                    reject(response, 403, "您没有权限查看集成驾驶舱");
                    return;
                }
                ok(response, integration(request));
                return;
            }
            reject(response, 400, "未注册的驾驶舱操作");
        } catch (SQLException | JSONException e) {
            log("驾驶舱数据读取失败", e);
            reject(response, 500, "驾驶舱数据读取失败，请检查后台日志");
        }
    }

    private JSONObject my(Map<?, ?> actor, int userId, String roleId, HttpServletRequest request)
            throws SQLException, JSONException {

        boolean admin = "1".equals(roleId);
        int scope = admin ? 0 : userId;
        LogQuery query = rangeQuery(request);

        JSONObject data = new JSONObject();
        data.put("roleId", Integer.parseInt(roleId));
        data.put("scope", admin ? "global" : "self");
        data.put("range", rangeInfo(query));
        data.put("updatedAt", MINUTE.format(LocalDateTime.now()));

        JSONObject login = LOG_DAO.statsLogin(query, scope);
        login.put("trend", LOG_DAO.loginTrend(query, scope));
        data.put("login", login);

        JSONObject operation = LOG_DAO.statsOperation(query, scope);
        operation.put("trend", LOG_DAO.operationTrend(query, scope));
        data.put("operation", operation);

        MessageQuery messageQuery = new MessageQuery();
        MessageStats stats = MESSAGE_DAO.statsInbox(userId, messageQuery);
        data.put("message", new JSONObject()
                .put("unread", stats.getUnread())
                .put("total", stats.getTotal())
                .put("read", stats.getRead()));

        data.put("displayName", String.valueOf(actor.get("displayName")));
        return data;
    }

    private JSONObject integration(HttpServletRequest request) throws SQLException, JSONException {
        LogQuery query = rangeQuery(request);

        JSONObject data = new JSONObject();
        data.put("range", rangeInfo(query));
        data.put("updatedAt", MINUTE.format(LocalDateTime.now()));

        JSONObject login = LOG_DAO.statsLogin(query, 0);
        login.put("trend", LOG_DAO.loginTrend(query, 0));
        data.put("login", login);

        JSONObject operation = LOG_DAO.statsOperation(query, 0);
        operation.put("trend", LOG_DAO.operationTrend(query, 0));
        data.put("operation", operation);

        data.put("messageSystem", LOG_DAO.messageStat());
        data.put("accounts", LOG_DAO.accountStat());

        // 已接通来源：登录日志、操作日志、个人消息、账号。其余业务本轮未提供接口，明确标记未接入。
        JSONArray sources = new JSONArray();
        sources.put(new JSONObject().put("name", "登录日志").put("status", "connected")
                .put("note", "login_log，服务端自动写入"));
        sources.put(new JSONObject().put("name", "操作日志").put("status", "connected")
                .put("note", "operation_log，业务写入口调用 OperationLogger"));
        sources.put(new JSONObject().put("name", "个人消息").put("status", "connected")
                .put("note", "personal_message，仅统计数量，不读正文"));
        sources.put(new JSONObject().put("name", "账号状态").put("status", "connected")
                .put("note", "todo_login_user，未删除账号计有效"));
        sources.put(new JSONObject().put("name", "大棚/设备/种植").put("status", "pending")
                .put("owner", "潘雨希").put("note", "等待提供驾驶舱指标接口"));
        sources.put(new JSONObject().put("name", "环境采样").put("status", "pending")
                .put("owner", "唐昕煜").put("note", "等待只读采样接口"));
        sources.put(new JSONObject().put("name", "阈值与报警").put("status", "pending")
                .put("owner", "李慧颖").put("note", "等待报警统计接口"));
        sources.put(new JSONObject().put("name", "待办与公告").put("status", "pending")
                .put("owner", "包屹恒").put("note", "等待待办完成率与公告数量接口"));
        data.put("sources", sources);
        return data;
    }

    private LogQuery rangeQuery(HttpServletRequest request) {
        LogQuery query = new LogQuery();
        String start = value(request.getParameter("startDate"));
        String end = value(request.getParameter("endDate"));
        if (start.isEmpty() || !isDate(start)) {
            start = LocalDate.now().minusDays(6).toString();
        }
        if (end.isEmpty() || !isDate(end)) {
            end = LocalDate.now().toString();
        }
        query.setStartDate(start);
        query.setEndDate(end);
        query.setPageSize(10);
        query.setPage(1);
        return query;
    }

    private JSONObject rangeInfo(LogQuery query) throws JSONException {
        return new JSONObject()
                .put("startDate", query.getStartDate())
                .put("endDate", query.getEndDate())
                .put("label", "按业务时间 " + query.getStartDate() + " 00:00 至 "
                        + LocalDate.parse(query.getEndDate()).plusDays(1) + " 00:00（不含）");
    }

    private void ok(HttpServletResponse response, Object data) throws IOException {
        try {
            JSONObject body = new JSONObject();
            body.put("result_code", 0);
            body.put("result_msg", "ok");
            body.put("data", data);
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
        Object user = request.getSession(false) == null
                ? null : request.getSession(false).getAttribute("loginUser");
        return user instanceof Map ? (Map<?, ?>) user : null;
    }

    private static String value(String text) {
        return text == null ? "" : text.trim();
    }

    private static boolean isDate(String text) {
        try {
            LocalDate.parse(text);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int parseInt(Object value, int fallback) {
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
