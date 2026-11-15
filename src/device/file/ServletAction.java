package device.file;

import device.dao.Data;
import device.dao.DeviceDao;
import device.dao.TodoDao;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Map;

/** 每次请求独立保存参数；操作权限由BusinessPermissionFilter校验。 */
public class ServletAction extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }
    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        process(request, response);
    }

    private void process(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        try {
            HttpSession session = request.getSession(false);
            Object current = session == null ? null : session.getAttribute("loginUser");
            if (!(current instanceof Map)) { reject(response, 401, "请先登录"); return; }
            Map<?, ?> user = (Map<?, ?>) current;
            String role = String.valueOf(user.get("roleId"));
            if (!"1".equals(role) && !"2".equals(role) && !"3".equals(role)) {
                reject(response, 403, "角色无效，请重新登录"); return;
            }
            String action = request.getParameter("action");
            if (action == null || action.isBlank()) { reject(response, 400, "缺少业务操作action"); return; }
            if ("query_record".equals(action) || "update_record".equals(action)) {
                reject(response, 403, "通用SQL接口已关闭，请使用业务接口"); return;
            }
            if ("upload_file".equals(action)) {
                reject(response, 501, "旧教学上传入口未接入业务附件管理"); return;
            }
            // 防止将来调整Filter映射时意外放开维护权限。
            boolean write = action.startsWith("add_") || action.startsWith("modify_") || action.startsWith("delete_");
            if (write && "3".equals(role)) { reject(response, 403, "当前角色无权执行此维护操作"); return; }
            if (write && !"POST".equals(request.getMethod())) {
                response.setHeader("Allow", "POST"); reject(response, 405, "维护操作必须使用POST"); return;
            }
            Data data = parameters(request);
            data.getParam().put("creator_id", String.valueOf(user.get("id")));
            Object displayName = user.get("displayName");
            data.getParam().put("creator", displayName == null ? "" : String.valueOf(displayName));
            JSONObject json = new JSONObject();
            DeviceDao device = new DeviceDao();
            TodoDao todo = new TodoDao();
            switch (action) {
                case "get_menu_record": device.getMenuRecord(role, json); break;
                case "get_todo_record": todo.getTodoRecord(data, json); break;
                case "add_todo_record": todo.addTodoRecord(data, json); break;
                case "modify_todo_record": todo.modifyTodoRecord(data, json); break;
                case "delete_todo_record": todo.deleteTodoRecord(data, json); break;
                case "get_device_record": device.getDeviceRecord(data, json); break;
                case "add_device_record": device.addDeviceRecord(data, json); break;
                case "modify_device_record": device.modifyDeviceRecord(data, json); break;
                case "delete_device_record": device.deleteDeviceRecord(data, json); break;
                default: reject(response, 400, "未注册的业务操作"); return;
            }
            response.getWriter().print(json.toString());
        } catch (IllegalArgumentException e) {
            reject(response, 400, e.getMessage());
        } catch (SQLException | JSONException e) {
            log("业务接口执行失败", e);
            reject(response, 500, "操作失败，请检查后台日志");
        }
    }

    private Data parameters(HttpServletRequest request) throws JSONException {
        Data data = new Data();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            data.getParam().put(name, request.getParameter(name));
        }
        return data;
    }

    private void reject(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().print("{\"result_code\":" + status
                + ",\"result_msg\":" + JSONObject.quote(message) + "}");
    }
}
