package user.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class BusinessPermissionFilter implements Filter {

    // 当前允许的查询动作。
    private static final Set<String> READ_ACTIONS = Set.of(
            "get_menu_record",
            "get_todo_record",
            "get_device_record"
    );

    // 当前允许管理员执行的维护动作。
    private static final Set<String> WRITE_ACTIONS = Set.of(
            "add_todo_record",
            "modify_todo_record",
            "delete_todo_record",
            "add_device_record",
            "modify_device_record",
            "delete_device_record"
    );

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest request =
                (HttpServletRequest) servletRequest;

        HttpServletResponse response =
                (HttpServletResponse) servletResponse;

        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Object loginUser = session == null
                ? null : session.getAttribute("loginUser");

        if (!(loginUser instanceof Map)) {
            reject(response, 401, "请先登录");
            return;
        }

        Map<?, ?> user = (Map<?, ?>) loginUser;
        String roleId = String.valueOf(user.get("roleId"));

        boolean administrator =
                "1".equals(roleId) || "2".equals(roleId);

        boolean normalUser = "3".equals(roleId);

        if (!administrator && !normalUser) {
            reject(response, 403, "角色无效，请重新登录");
            return;
        }

        String action = request.getParameter("action");

        if (action == null || action.isBlank()) {
            reject(response, 400, "缺少业务操作action");
            return;
        }

        // 所有角色都禁止使用通用SQL入口。
        if ("query_record".equals(action)
                || "update_record".equals(action)) {
            reject(response, 403,
                    "通用SQL接口已关闭，请使用业务接口");
            return;
        }

        if ("upload_file".equals(action)) {
            reject(response, 501, "旧教学上传入口未接入业务附件管理");
            return;
        }

        if (WRITE_ACTIONS.contains(action)) {

            if (!administrator) {
                reject(response, 403,
                        "当前角色无权执行此维护操作");
                return;
            }

            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setHeader("Allow", "POST");
                reject(response, 405, "维护操作必须使用POST");
                return;
            }

        } else if (!READ_ACTIONS.contains(action)) {
            reject(response, 400, "未注册的业务操作");
            return;
        }

        chain.doFilter(request, response);
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);
        response.setContentType(
                "application/json;charset=UTF-8"
        );

        response.getWriter().write(
                "{\"result_code\":" + status
                        + ",\"result_msg\":"
                        + JSONObject.quote(message)
                        + "}"
        );
    }
}