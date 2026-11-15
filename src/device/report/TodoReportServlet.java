package device.report;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;

/** 独立地址，保留前四题已有 Servlet 和 CRUD 处理逻辑。 */
public class TodoReportServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loginUser") == null) {
            if ("export".equals(action)) {
                response.sendRedirect(request.getContextPath() + "/home/main/login.jsp");
            } else {
                error(response, 401, "请先登录");
            }
            return;
        }
        if (!"dashboard".equals(action) && !"summary".equals(action)
                && !"records".equals(action) && !"export".equals(action)) {
            error(response, 400, "未知报表操作");
            return;
        }
        try {
            TodoReportDao dao = new TodoReportDao();
            if ("export".equals(action)) {
                // 先完成查询再设置下载响应，查询失败时不会下载一个损坏的文件。
                JSONArray rows = dao.records(query(request));
                response.setContentType("text/csv;charset=UTF-8");
                response.setHeader("Content-Disposition", "attachment; filename=todo-report.csv; "
                        + "filename*=UTF-8''%E5%BE%85%E5%8A%9E%E4%BA%8B%E9%A1%B9%E6%8A%A5%E8%A1%A8.csv");
                PrintWriter writer = response.getWriter();
                writer.write('\uFEFF');
                writer.print("编号,待办标题,创建时间\r\n");
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    writer.print(row.getLong("id") + "," + csvText(row.getString("title"))
                            + "," + csvText(row.getString("create_time")) + "\r\n");
                }
                return;
            }
            JSONObject result = new JSONObject();
            result.put("result_code", 0);
            if ("dashboard".equals(action)) {
                result.put("data", dao.dashboard());
            } else if ("summary".equals(action)) {
                result.put("data", dao.summary(query(request)));
            } else {
                JSONArray rows = dao.records(query(request));
                result.put("aaData", rows);
                // 与本次返回的记录一致，打印时不会因第二次查询产生计数偏差。
                result.put("record_count", rows.length());
            }
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().print(result.toString());
        } catch (IllegalArgumentException e) {
            error(response, 400, e.getMessage());
        } catch (SQLException | JSONException e) {
            log("待办事项报表处理失败", e);
            error(response, 500, "报表查询失败，请查看 Tomcat 控制台");
        }
    }

    private TodoReportDao.Query query(HttpServletRequest request) {
        return new TodoReportDao.Query(request.getParameter("id"), request.getParameter("title"),
                request.getParameter("create_date"), request.getParameter("order_by"),
                request.getParameter("order_dir"));
    }

    static String csvText(String value) {
        if (value == null) value = "";
        // 标题是文本；避免表格软件将以 =、+、-、@ 开头的文本当作公式。
        if (!value.isEmpty() && ("=+-@\t\r\n".indexOf(value.charAt(0)) >= 0
                || value.stripLeading().matches("^[=+\\-@].*"))) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void error(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        // JSONObject.quote 可用于旧版 json.jar，避免手工拼接错误消息。
        response.getWriter().print("{\"result_code\":" + status + ",\"result_msg\":"
                + JSONObject.quote(message) + "}");
    }
}
