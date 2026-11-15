<%@ page pageEncoding="UTF-8" %>
<%
    if (session.getAttribute("loginUser") == null) {
        response.sendRedirect(
                request.getContextPath() + "/home/main/login.jsp"
        );
        return;
    }

    response.setHeader("Cache-Control", "no-store");
%>