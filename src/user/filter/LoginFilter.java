package user.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import user.dao.UserDao;

public class LoginFilter implements Filter {
    protected UserDao userDao(){return new UserDao();}

    @Override
    public void doFilter(
            ServletRequest servletRequest,
            ServletResponse servletResponse,
            FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request =
                (HttpServletRequest) servletRequest;

        HttpServletResponse response =
                (HttpServletResponse) servletResponse;

        // 小程序接口交由MiniTodoServlet校验访问码
        String w08Path = request.getRequestURI()
                .substring(request.getContextPath().length());

        if ("/api/week08/todo".equals(w08Path)
                || "/api/week08/location".equals(w08Path)
                || ("/api/week08/bind".equals(w08Path)
                && "ping".equals(request.getParameter("action")))
                || "/api/week08/auth".equals(w08Path)) {
            chain.doFilter(request, response);
            return;
        }

        // 未登录时也不能放行页面，但采集上传不依赖登录会话：它只用 X-Collector-Key，
        // 由 EnvironmentIngestServlet 自己校验凭据，因此在这里精确放行这一条路径。
        if ("/api/environment/ingest".equals(w08Path)) {
            chain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();

        // 登录页面、登录接口和静态资源直接放行
        boolean publicResource =
                "/home/main/login.jsp".equals(path)
                        || "/user_center_servlet_action".equals(path)
                        || path.startsWith("/assets/")
                        || "/favicon.ico".equals(path);

        if (publicResource) {
            chain.doFilter(request, response);
            return;
        }

        // 受保护的内容不使用浏览器缓存
        response.setHeader("Cache-Control", "no-store");

        // false 表示没有 Session 时不新建
        HttpSession session = request.getSession(false);

        if (session != null && session.getAttribute("loginUser") instanceof Map) {
            Map<?,?> old=(Map<?,?>)session.getAttribute("loginUser");
            try {
                Map<String,String> fresh=userDao().findActiveById(String.valueOf(old.get("id")));
                if(fresh!=null && fresh.get("authVersion").equals(old.get("authVersion"))) {
                    session.setAttribute("loginUser",fresh);
                    chain.doFilter(request,response);
                    return;
                }
                session.invalidate();
            } catch(SQLException e) {
                request.getServletContext().log("账号有效性校验失败",e);
                response.setStatus(503);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"result_code\":503,\"result_msg\":\"账号服务暂不可用，请检查数据库连接及09升级脚本\"}");
                return;
            }
        }

        // 接口请求返回 JSON，避免前端把登录页当成 JSON
        boolean apiRequest =
                "/device_file_servlet_action".equals(path)
                        || "/device_data_servlet_action".equals(path)
                        || path.startsWith("/api/")
                        || ("/todo_report_servlet_action".equals(path)
                        && !"export".equals(
                        request.getParameter("action")))
                        || "XMLHttpRequest".equalsIgnoreCase(
                        request.getHeader("X-Requested-With"));

        if (apiRequest) {
            response.setStatus(401);
            response.setContentType(
                    "application/json;charset=UTF-8");

            response.getWriter().write(
                    "{\"result_code\":401,"
                            + "\"result_msg\":\"请先登录\"}");
            return;
        }

        // 普通页面请求：未登录就返回登录页
        response.sendRedirect(
                request.getContextPath()
                        + "/home/main/login.jsp"
        );
    }
}