package greenhouse.log.service;

import device.dao.Db;
import greenhouse.log.dao.LogDao;
import jakarta.servlet.http.HttpServletRequest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * 登录日志记录入口，由登录 Servlet 调用一次登录尝试记一条。
 * 边界：不保存密码、密码哈希、验证码、会话Cookie、微信密钥或令牌；
 *      日志写入失败不影响登录结果，只在服务端 stderr 留脱敏故障标记。
 */
public final class LoginLogService {

    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";

    public static final String REASON_CREDENTIAL = "credential_error";
    public static final String REASON_DISABLED = "account_disabled";
    public static final String REASON_INVALID_INPUT = "invalid_input";
    public static final String REASON_SERVICE = "auth_service_error";

    private static final LogDao DAO = new LogDao();
    private static final String DB_NAME = "greenhouse_system";

    private LoginLogService() {
    }

    /** 登录成功：关联真实用户ID。 */
    public static void success(HttpServletRequest request, Map<?, ?> user) {
        Integer userId = parseInt(user == null ? null : user.get("id"));
        String username = user == null ? "" : String.valueOf(user.get("username"));
        record(request, username, userId, RESULT_SUCCESS, "");
    }

    /** 登录失败：未识别用户不绑定虚构ID，只保留受限的尝试账号。 */
    public static void failure(HttpServletRequest request, String username, String reasonCode) {
        record(request, username, null, RESULT_FAILURE, reasonCode);
    }

    /**
     * 判断失败类别：账号存在且非启用 -> account_disabled；账号不存在或密码错 -> credential_error。
     * 该判断只用于服务端日志，不改变前端统一提示。
     */
    public static String classifyFailure(String username) {
        if (username == null || username.trim().isEmpty()) {
            return REASON_INVALID_INPUT;
        }
        String sql = "SELECT status FROM todo_login_user WHERE username = ? LIMIT 1";
        Db db = null;
        try {
            db = new Db(DB_NAME);
            Connection connection = db.getConnection();
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, username.trim());
                try (ResultSet rs = statement.executeQuery()) {
                    if (!rs.next()) {
                        return REASON_CREDENTIAL;
                    }
                    String status = rs.getString("status");
                    return "active".equals(status) ? REASON_CREDENTIAL : REASON_DISABLED;
                }
            }
        } catch (SQLException e) {
            return REASON_SERVICE;
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    private static void record(HttpServletRequest request, String username, Integer userId,
                               String result, String reasonCode) {
        String ip = "";
        String client = "";
        String source = "pc";
        try {
            ip = request.getRemoteAddr();
            String agent = request.getHeader("User-Agent");
            client = agent == null ? "" : agent;
        } catch (RuntimeException ignored) {
            // 记录来源失败时仍写入基本字段，不影响登录流程。
        }
        try {
            DAO.insertLogin(username, userId, result, reasonCode, source, ip,
                    client.length() > 128 ? client.substring(0, 128) : client);
        } catch (Throwable e) {
            // 不把堆栈返回给页面；用固定标记便于排查日志故障。
            System.err.println("[LOGIN_LOG_FAILED] user=" + safe(username)
                    + " result=" + result + " reason=" + reasonCode + " error=" + e.getClass().getSimpleName());
        }
    }

    private static Integer parseInt(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return clean.length() > 64 ? clean.substring(0, 64) : clean;
    }
}
