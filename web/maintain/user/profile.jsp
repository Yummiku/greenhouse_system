<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "个人中心"); %>
<!DOCTYPE html><html lang="zh-CN"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>农业大棚系统 - 个人中心</title>
<%@ include file="/home/frame/frame_style.jsp" %>
<link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/user/profile.css">
</head><body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %><div class="clearfix"></div>
<div class="page-container"><%@ include file="/home/frame/frame_left_sidebar.jsp" %>
<div class="page-content-wrapper"><main class="page-content">
<%@ include file="/home/frame/frame_page_header.jsp" %>
<div id="account-center"><p id="account-status" role="status" aria-live="polite">正在加载本人资料……</p>
<section id="profile" class="portlet light bordered"><h3>个人资料</h3>
<dl id="account-details"></dl>
<form id="account-profile"><label for="account-name">显示名称</label>
<input id="account-name" class="form-control" maxlength="100" required disabled autocomplete="name">
<p>账号、角色和状态由系统管理员维护。</p>
<button id="account-profile-save" class="btn green" disabled>保存资料</button></form></section>
<section id="password" class="portlet light bordered"><h3>修改密码</h3>
<p>请输入原密码。新密码至少8字符，UTF-8编码不超过72字节；修改成功后需要重新登录。</p>
<form id="account-password">
<label for="account-old">原密码</label><input id="account-old" class="form-control" type="password" autocomplete="current-password" required disabled>
<label for="account-new">新密码</label><input id="account-new" class="form-control" type="password" autocomplete="new-password" minlength="8" required disabled>
<label for="account-confirm">确认新密码</label><input id="account-confirm" class="form-control" type="password" autocomplete="new-password" minlength="8" required disabled>
<button id="account-password-save" class="btn green" disabled>确认修改密码</button>
</form></section>
<a class="btn default" href="<%=request.getContextPath()%>/maintain/message/message_inbox.jsp">我的消息</a>
</div></main></div></div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%=request.getContextPath()%>/maintain/user/profile.js"></script>
</body></html>
