<%@ page contentType="text/html;charset=UTF-8" trimDirectiveWhitespaces="true" %>
<%
    response.setHeader("Cache-Control", "no-store");
    String target = session.getAttribute("loginUser") == null
            ? "/home/main/login.jsp"
            : "/home/main/index.jsp";
    response.sendRedirect(request.getContextPath() + target);
%>
