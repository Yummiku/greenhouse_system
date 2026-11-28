<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
    boolean permissionReview = Boolean.TRUE.equals(request.getAttribute("permissionReview"));
    request.setAttribute("pageTitle", permissionReview ? "权限审批" : "我的权限申请");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>农业大棚系统 - <%=permissionReview ? "权限审批" : "我的权限申请"%></title>
    <%@ include file="/home/frame/frame_style.jsp" %>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/permission/permission.css">
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %><div class="clearfix"></div>
<div class="page-container">
    <%@ include file="/home/frame/frame_left_sidebar.jsp" %>
    <div class="page-content-wrapper"><main class="page-content" id="permission-page" data-scope="<%=permissionReview ? "review" : "mine"%>">
        <%@ include file="/home/frame/frame_page_header.jsp" %>
        <p id="permission-message" role="status" aria-live="polite">正在加载……</p>
        <div id="permission-app" hidden>
            <section class="portlet light bordered">
                <h3>申请统计</h3><p id="permission-range"></p>
                <div class="permission-cards" id="permission-statistics"></div>
            </section>
            <section class="portlet light bordered">
                <h3>查询条件</h3>
                <form id="permission-filter" class="permission-filters">
                    <label>账号、名称或申请理由<input class="form-control" name="keyword" maxlength="100"></label>
                    <label>申请状态<select class="form-control" name="status"><option value="">全部状态</option><option value="pending">待审批</option><option value="approved">已批准</option><option value="rejected">已驳回</option><option value="withdrawn">已撤回</option></select></label>
                    <label>申请日期从<input class="form-control" type="date" name="start"></label>
                    <label>申请日期到<input class="form-control" type="date" name="end"></label>
                    <label>申请时间排序<select class="form-control" name="dir"><option value="desc">降序</option><option value="asc">升序</option></select></label>
                    <label>每页<select class="form-control" name="size"><option>10</option><option>20</option><option>50</option></select></label>
                    <div class="permission-actions"><button class="btn blue" type="submit">查询</button><button class="btn default" id="permission-reset" type="button">重置</button></div>
                </form>
            </section>
            <section class="portlet light bordered">
                <div class="permission-actions">
                    <button type="button" class="btn blue" id="permission-add" hidden>新增申请</button>
                    <button type="button" class="btn default" id="permission-export">导出CSV</button>
                    <button type="button" class="btn default" id="permission-print">打印</button>
                    <button type="button" class="btn default" id="permission-refresh">刷新</button>
                </div>
                <p id="permission-help"></p><p id="permission-summary"></p>
                <div class="table-responsive"><table class="table table-striped table-bordered">
                    <thead><tr><th>申请人</th><th>申请角色</th><th>申请理由</th><th>状态</th><th>申请时间</th><th>审批人</th><th>操作</th></tr></thead>
                    <tbody id="permission-rows"></tbody>
                </table></div>
                <div class="permission-actions"><button class="btn default" type="button" id="permission-prev">上一页</button><span id="permission-pages"></span><button class="btn default" type="button" id="permission-next">下一页</button></div>
            </section>
        </div>
    </main></div>
</div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<div class="modal fade" id="permission-editor" tabindex="-1" role="dialog" aria-labelledby="permission-editor-title">
    <div class="modal-dialog" role="document"><div class="modal-content">
        <form id="permission-form">
            <div class="modal-header"><button type="button" class="close" data-dismiss="modal" aria-label="关闭"></button><h3 class="modal-title" id="permission-editor-title">新增权限申请</h3></div>
            <div class="modal-body">
                <label>申请角色<input class="form-control" value="普通管理员" readonly></label>
                <label>申请理由（必填，最多500字符）<textarea class="form-control" name="reason" rows="5" required maxlength="500"></textarea></label>
                <p>提交后不可修改。待审批申请可撤回；批准后需重新登录，新的角色才会生效。</p>
                <p id="permission-form-error" role="alert"></p>
            </div>
            <div class="modal-footer"><button class="btn blue" type="submit">提交申请</button><button type="button" class="btn default" data-dismiss="modal">取消</button></div>
        </form>
    </div></div>
</div>
<div class="modal fade" id="permission-detail" tabindex="-1" role="dialog" aria-labelledby="permission-detail-title">
    <div class="modal-dialog" role="document"><div class="modal-content">
        <div class="modal-header"><button type="button" class="close" data-dismiss="modal" aria-label="关闭"></button><h3 class="modal-title" id="permission-detail-title">申请详情</h3></div>
        <div class="modal-body">
            <dl id="permission-detail-body" class="permission-details"></dl>
            <div id="permission-review-controls" hidden>
                <label>审批意见（驳回必填，最多500字符）<textarea class="form-control" id="permission-opinion" rows="4" maxlength="500"></textarea></label>
                <p>批准仅适用于当前启用的普通用户。批准会使其旧登录会话失效。</p>
            </div>
            <p id="permission-detail-error" role="alert"></p>
        </div>
        <div class="modal-footer"><span id="permission-review-actions" hidden><button class="btn blue" type="button" id="permission-approve">批准</button><button class="btn btn-danger" type="button" id="permission-reject">驳回</button></span><button class="btn default" type="button" data-dismiss="modal">关闭</button></div>
    </div></div>
</div>
<section id="permission-print-area"></section>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%=request.getContextPath()%>/maintain/permission/permission.js"></script>
</body></html>
