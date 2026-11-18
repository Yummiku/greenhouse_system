<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "用户信息"); %>
<!DOCTYPE html><html lang="zh-CN"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>农业大棚系统 - 用户信息</title>
<%@ include file="/home/frame/frame_style.jsp" %>
<link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/user/user.css">
</head><body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %><div class="clearfix"></div>
<div class="page-container"><%@ include file="/home/frame/frame_left_sidebar.jsp" %>
<div class="page-content-wrapper"><main class="page-content">
<%@ include file="/home/frame/frame_page_header.jsp" %>
<p id="user-message" role="status" aria-live="polite">正在加载账号信息……</p>
<section class="portlet light bordered"><h3>我的资料</h3><dl id="user-profile" class="user-details"></dl></section>
<section id="user-management" class="portlet light bordered" hidden>
<h3>用户管理</h3><p>仅系统管理员可以维护账号。停用或删除账号不会删除其关联业务记录。</p>
<form id="user-filter" class="user-filters">
<label>账号或名称 <input class="form-control" name="keyword" maxlength="100"></label>
<label>角色 <select class="form-control" name="role_id"><option value="">全部角色</option><option value="1">系统管理员</option><option value="2">普通管理员</option><option value="3">普通用户</option></select></label>
<label>状态 <select class="form-control" name="status"><option value="">未删除账号</option><option value="active">启用</option><option value="disabled">停用</option><option value="deleted">已删除</option></select></label>
<label>排序 <select class="form-control" name="sort"><option value="created_at">创建时间</option><option value="username">账号</option><option value="updated_at">更新时间</option><option value="role_id">角色</option><option value="status">状态</option></select></label>
<label>顺序 <select class="form-control" name="dir"><option value="desc">降序</option><option value="asc">升序</option></select></label>
<button class="btn blue" type="submit">查询</button><button class="btn default" id="user-reset" type="button">重置</button>
</form>
<div class="user-actions"><button class="btn blue" id="user-add" type="button">新增账号</button><button class="btn default" id="user-export" type="button">导出全部筛选结果</button><button class="btn default" id="user-print" type="button">打印清单与统计</button></div>
<p id="user-summary"></p><div id="user-statistics" class="user-charts"></div>
<div class="table-responsive"><table class="table table-bordered"><thead><tr><th>账号</th><th>名称</th><th>角色</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody id="user-rows"></tbody></table></div>
<div class="user-actions"><button id="user-prev" type="button" class="btn default">上一页</button><span id="user-pages"></span><button id="user-next" type="button" class="btn default">下一页</button></div>
</section>
</main></div></div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<dialog id="user-editor"><h3 id="user-editor-title">新增账号</h3>
<form id="user-form"><input type="hidden" name="id"><input type="hidden" name="version">
<label>账号<input class="form-control" name="username" required minlength="3" maxlength="64" pattern="[A-Za-z0-9_.@\-]{3,64}" autocomplete="off"></label>
<label>显示名称<input class="form-control" name="display_name" required maxlength="100"></label>
<label>角色<select class="form-control" name="role_id"><option value="3">普通用户</option><option value="2">普通管理员</option><option value="1">系统管理员</option></select></label>
<label>状态<select class="form-control" name="status"><option value="active">启用</option><option value="disabled">停用</option></select></label>
<label id="user-initial-password">初始密码<input class="form-control" type="password" name="password" minlength="8" autocomplete="new-password"></label>
<p>账号创建后不可改名。密码至少8字符，UTF-8不超过72字节；账号及密码由各成员自行设置。</p>
<p id="user-form-message" role="alert"></p><button id="user-save" type="submit" class="btn blue">保存</button><button type="button" class="btn default" data-close="user-editor">取消</button>
</form></dialog>
<dialog id="user-detail"><h3>账号详情</h3><dl id="user-detail-body" class="user-details"></dl><button type="button" class="btn default" data-close="user-detail">关闭</button></dialog>
<dialog id="user-password-dialog"><h3>重置密码</h3><form id="user-password-form"><p id="user-password-target"></p><label>新密码<input class="form-control" name="password" type="password" minlength="8" required autocomplete="new-password"></label><label>确认密码<input class="form-control" name="confirmation" type="password" minlength="8" required autocomplete="new-password"></label><p>重置后该账号的旧登录会话失效，需要使用新密码重新登录。</p><p id="user-password-message" role="alert"></p><button id="user-password-save" class="btn blue" type="submit">确认重置</button><button type="button" class="btn default" data-close="user-password-dialog">取消</button></form></dialog>
<section id="user-print-area"></section>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%=request.getContextPath()%>/maintain/user/user.js"></script>
</body></html>
