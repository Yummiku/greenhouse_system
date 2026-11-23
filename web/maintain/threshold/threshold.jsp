<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="greenhouse.greenhouse.CsrfGuard,java.util.Map" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
request.setAttribute("pageTitle", "阈值规则管理");
Map<?, ?> thresholdUser = (Map<?, ?>) session.getAttribute("loginUser");
boolean thresholdCanManage = "1".equals(String.valueOf(thresholdUser.get("roleId")))
        || "2".equals(String.valueOf(thresholdUser.get("roleId")));
String thresholdToken = CsrfGuard.token(session);
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>农业大棚系统 - 阈值规则管理</title>
<%@ include file="/home/frame/frame_style.jsp" %>
<link rel="stylesheet" href="<%= request.getContextPath() %>/maintain/threshold/threshold.css">
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">
<%@ include file="/home/frame/frame_header.jsp" %>
<div class="clearfix"></div>
<div class="page-container">
<%@ include file="/home/frame/frame_left_sidebar.jsp" %>
<div class="page-content-wrapper"><div class="page-content">
<%@ include file="/home/frame/frame_page_header.jsp" %>
<div id="threshold-app"
     data-base="<%= request.getContextPath() %>" data-manage="<%= thresholdCanManage %>">
<input id="threshold-token" type="hidden" value="<%= thresholdToken %>">
<section id="threshold-statistics" class="portlet light bordered" aria-labelledby="threshold-statistics-title">
<div class="portlet-title"><div class="caption"><span id="threshold-statistics-title" class="caption-subject bold">规则统计</span></div></div>
<div class="portlet-body">
<p id="threshold-statistics-scope" class="text-muted">统计范围：默认全部规则，正在读取…</p>
<div class="row threshold-counts">
<div class="col-sm-6 col-md-3"><div class="threshold-count"><span>规则总数</span><strong id="threshold-total">—</strong></div></div>
<div class="col-sm-6 col-md-3"><div class="threshold-count"><span>启用数</span><strong id="threshold-enabled">—</strong></div></div>
<div class="col-sm-6 col-md-3"><div class="threshold-count"><span>停用数</span><strong id="threshold-disabled">—</strong></div></div>
<div class="col-sm-6 col-md-3"><div class="threshold-count"><span>因大棚无效暂不生效</span><strong id="threshold-invalid">—</strong></div></div>
</div>
<p id="threshold-summary" role="status">正在加载统计…</p>
<div id="threshold-charts" class="threshold-charts"></div>
</div></section>
<section class="portlet light bordered" aria-labelledby="threshold-list-title">
<div class="portlet-title"><div class="caption"><span id="threshold-list-title" class="caption-subject bold">规则查询与管理</span></div></div>
<div class="portlet-body">
<form id="threshold-query" class="threshold-filters">
<label>规则名称<input name="name" class="form-control" maxlength="100"></label>
<label>大棚<select name="greenhouseId" class="form-control"><option value="">全部大棚</option></select></label>
<label>监测指标<select name="metric" class="form-control"><option value="">全部指标</option></select></label>
<label>规则状态<select name="state" class="form-control"><option value="">全部状态</option><option value="true">启用</option><option value="false">停用</option></select></label>
<label>排序字段<select name="sort" class="form-control"><option value="updatedAt">更新时间</option><option value="name">规则名称</option></select></label>
<label>排序方向<select name="dir" class="form-control"><option value="desc">降序</option><option value="asc">升序</option></select></label>
<div class="threshold-query-buttons"><button class="btn blue" type="submit">查询</button><button class="btn btn-default" type="reset">重置</button></div>
</form>
<div class="threshold-actions">
<% if (thresholdCanManage) { %><button id="threshold-new" class="btn blue" type="button">新增规则</button><% } %>
<button id="threshold-export" class="btn btn-default" type="button">导出CSV</button>
<button id="threshold-print" class="btn btn-default" type="button">打印报表</button>
<button id="threshold-refresh" class="btn btn-default" type="button">刷新</button>
</div>
<p id="threshold-result-count">正在读取规则条数…</p>
<p id="threshold-status" role="status" aria-live="polite">正在加载…</p>
<div class="table-responsive">
<table class="table table-bordered table-striped threshold-table">
<thead><tr><th>规则编号</th><th>规则名称</th><th>所属大棚</th><th>监测指标</th><th>下限</th><th>上限</th><th>单位</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead>
<tbody id="threshold-rows"></tbody>
</table></div>
<div class="threshold-actions threshold-pagination"><label>每页 <select id="threshold-size" class="form-control"><option>10</option><option>20</option><option>50</option></select> 条</label>
<button id="threshold-prev" class="btn btn-default" type="button">上一页</button><span id="threshold-page"></span>
<button id="threshold-next" class="btn btn-default" type="button">下一页</button></div>
</div></section></div>
</div></div></div>

<span id="threshold-actor" hidden><%= String.valueOf(thresholdUser.get("displayName")).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;") %></span>
<div id="threshold-dialog" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="threshold-dialog-title">
<div class="modal-dialog" role="document"><div class="modal-content">
<div class="modal-header">
<button id="threshold-dialog-x" class="close" type="button" aria-label="关闭弹窗"><span aria-hidden="true">×</span></button>
<h4 id="threshold-dialog-title" class="modal-title"></h4>
</div><div class="modal-body">
<p id="threshold-dialog-status" role="status"></p>
<dl id="threshold-detail" class="threshold-details" hidden></dl>
<form id="threshold-form" hidden novalidate>
<p>保存后为停用状态，需在列表中启用后供报警流程使用。</p>
<div class="form-group"><label class="control-label" for="threshold-name">规则名称 <span class="required" aria-hidden="true">*</span></label><input id="threshold-name" name="name" class="form-control" required maxlength="100"></div>
<div class="form-group"><label class="control-label" for="threshold-house">适用大棚 <span class="required" aria-hidden="true">*</span></label><select id="threshold-house" name="greenhouseId" class="form-control" required></select>
<button id="threshold-house-retry" class="btn btn-default" type="button" hidden>重试加载大棚</button></div>
<div class="row"><div class="col-sm-8 form-group"><label class="control-label" for="threshold-metric">监测指标 <span class="required" aria-hidden="true">*</span></label><select id="threshold-metric" name="metric" class="form-control" required></select></div>
<div class="col-sm-4 form-group"><label class="control-label" for="threshold-unit">单位</label><input id="threshold-unit" name="unit" class="form-control" readonly></div></div>
<p class="help-block">至少填写一个边界，最多12位整数和6位小数。两边都填写时下限必须小于上限。</p>
<div class="row"><div class="col-sm-6 form-group"><label class="control-label" for="threshold-lower">下限</label><input id="threshold-lower" name="lower" class="form-control" inputmode="decimal" maxlength="100"></div>
<div class="col-sm-6 form-group"><label class="control-label" for="threshold-upper">上限</label><input id="threshold-upper" name="upper" class="form-control" inputmode="decimal" maxlength="100"></div></div>
<div class="form-group"><label class="control-label" for="threshold-remark">备注</label><textarea id="threshold-remark" name="remark" class="form-control" maxlength="500" rows="3"></textarea></div>
</form>
</div><div class="modal-footer"><button id="threshold-save" type="submit" form="threshold-form" class="btn blue" hidden>保存</button><button id="threshold-close" type="button" class="btn btn-default">关闭</button></div>
</div></div></div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/threshold/threshold.js"></script>
</body></html>



