package greenhouse.environment;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public class EnvironmentServlet extends HttpServlet {

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        HttpSession session = request.getSession(false);
        Object loginUser = session == null
                ? null : session.getAttribute("loginUser");

        if (!(loginUser instanceof Map)) {
            reject(response, 401, "请先登录");
            return;
        }

        Map<?, ?> user = (Map<?, ?>) loginUser;
        String roleId = String.valueOf(user.get("roleId"));

        if (!"1".equals(roleId)
                && !"2".equals(roleId)
                && !"3".equals(roleId)) {
            reject(response, 403, "角色无效");
            return;
        }

        String action = request.getParameter("action");

        if (!"ping".equals(action)) {
            reject(response, 400, "未实现的业务操作");
            return;
        }

        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        response.getWriter().write(
                "{\"result_code\":0,"
                        + "\"result_msg\":\"接口入口可用，业务功能尚未接入\","
                        + "\"data\":{"
                        + "\"module\":\"environment\","
                        + "\"business_ready\":false"
                        + "}}"
        );
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        response.setHeader("Allow", "GET");
        reject(response, 405, "当前入口仅支持GET检查");
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        response.getWriter().write(
                "{\"result_code\":" + status
                        + ",\"result_msg\":"
                        + JSONObject.quote(message)
                        + "}"
        );
    }
}