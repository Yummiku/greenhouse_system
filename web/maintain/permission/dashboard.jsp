<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "个人驾驶舱"); %>
<!DOCTYPE html>
<html lang="zh-CN"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>农业大棚系统 - 个人驾驶舱</title>
    <%@ include file="/home/frame/frame_style.jsp" %>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/permission/permission.css">
</head><body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %><div class="clearfix"></div>
<div class="page-container"><%@ include file="/home/frame/frame_left_sidebar.jsp" %>
    <div class="page-content-wrapper"><main class="page-content">
        <%@ include file="/home/frame/frame_page_header.jsp" %>
        <p id="dashboard-message" role="status" aria-live="polite">正在加载……</p>
        <section class="portlet light bordered">
            <h3>当前状态概览</h3><p id="dashboard-scope"></p>
            <div id="dashboard-cards" class="permission-cards"></div>
            <p>统计为当前状态快照；活跃用户数待登录日志接入后提供。账号总数与活跃用户数分别统计。</p>
            <button id="dashboard-refresh" type="button" class="btn default">刷新统计</button>
        </section>
        <section class="portlet light bordered" id="dashboard-users" hidden>
            <h3>账号角色与状态分布</h3><p>仅系统管理员可查看全体账号；已删除账号单独列出。</p>
            <div id="dashboard-user-charts"></div>
        </section>
        <section class="portlet light bordered">
            <h3>权限申请状态分布</h3><div id="dashboard-request-charts"></div>
            <div class="permission-actions">
                <a class="btn blue" href="<%=request.getContextPath()%>/maintain/permission/index.jsp">我的权限申请</a>
                <a class="btn default" id="dashboard-review" hidden href="<%=request.getContextPath()%>/maintain/permission/review.jsp">处理待审批申请</a>
                <a class="btn default" href="<%=request.getContextPath()%>/maintain/user/index.jsp">用户信息</a>
            </div>
        </section>
    </main></div>
</div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%=request.getContextPath()%>/maintain/permission/dashboard.js"></script>
</body></html>
