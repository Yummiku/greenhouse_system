<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>待办事项统计首页</title>
    <%@ include file="/home/frame/frame_style.jsp" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/maintain/todo/todo_report.css">
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %>
<div class="clearfix"></div>
<div class="page-container">
    <%@ include file="/home/frame/frame_left_sidebar.jsp" %>
    <div class="page-content-wrapper">
        <div class="page-content">
            <%@ include file="/home/frame/frame_page_header.jsp" %>
            <%@ include file="todo_dashboard_content.jsp" %>
        </div>
    </div>
</div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/todo/todo_report.js"></script>
</body>
</html>
