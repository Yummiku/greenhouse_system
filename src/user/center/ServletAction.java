package user.center;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import user.dao.UserDao;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class ServletAction extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");
        if (handleLogout(request, response)) {
            return;
        }

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if (username == null || password == null
                || username.trim().isEmpty() || password.isEmpty()) {

            response.sendRedirect(
                    request.getContextPath()
                            + "/home/main/login.jsp?error=1"
            );
            return;
        }

        username = username.trim();

        try {
            UserDao userDao = new UserDao();

            Map<String, String> user =
                    userDao.login(username, password);

            if (user == null) {
                // 账号或密码错误，返回登录页
                response.sendRedirect(
                        request.getContextPath()
                                + "/home/main/login.jsp?error=1"
                );
                return;
            }

            // 登录成功，把用户信息保存到 Session
            HttpSession session = request.getSession();
            request.changeSessionId();
            session.setAttribute("loginUser", user);

            // 跳转到系统首页
            response.sendRedirect(
                    request.getContextPath()
                            + "/home/main/index.jsp"
            );

        } catch (SQLException e) {
            throw new ServletException("登录查询失败，请检查数据库配置", e);
        }
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        if (handleLogout(request, response)) {
            return;
        }

        response.sendRedirect(
                request.getContextPath()
                        + "/home/main/login.jsp"
        );
    }

    private boolean handleLogout(
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        if (!"logout".equals(request.getParameter("action"))) {
            return false;
        }

        HttpSession session = request.getSession(false);

        if (session != null) {
            // 销毁 Session，清除登录用户和角色信息
            session.invalidate();
        }

        response.setHeader("Cache-Control", "no-store");

        response.sendRedirect(
                request.getContextPath()
                        + "/home/main/login.jsp"
        );

        return true;
    }
}