<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "操作日志");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 操作日志</title>
  <%@ include file="/home/frame/frame_style.jsp" %>
  <style>
    .op-bar { background: #e9f5ee; height: 18px; border-radius: 3px; overflow: hidden; }
    .op-bar > span { display: block; height: 100%; background: #2f8f5b; }
    .op-bar-wrap { margin-bottom: 8px; font-size: 12px; }
  </style>
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">

<%@ include file="/home/frame/frame_header.jsp" %>

<div class="clearfix"></div>

<div class="page-container">

  <%@ include file="/home/frame/frame_left_sidebar.jsp" %>

  <div class="page-content-wrapper">
    <div class="page-content">

      <%@ include file="/home/frame/frame_page_header.jsp" %>

      <div class="row">
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">操作总数</div>
              <div class="bold font-lg" id="stat_total">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">成功</div>
              <div class="bold font-lg" id="stat_success">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">失败</div>
              <div class="bold font-lg" id="stat_failed">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">拒绝</div>
              <div class="bold font-lg" id="stat_denied">—</div>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold">模块分布</span>
            <span class="caption-helper" id="range_label">—</span>
          </div>
        </div>
        <div class="portlet-body">
          <div id="module_chart">
            <p class="muted">加载中…</p>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-magnifier"></i>
            <span class="caption-subject bold">查询条件</span>
          </div>
        </div>
        <div class="portlet-body">
          <div class="row">
            <div class="col-md-2">
              <label>起始日期</label>
              <input type="date" id="filter_start" class="form-control">
            </div>
            <div class="col-md-2">
              <label>结束日期</label>
              <input type="date" id="filter_end" class="form-control">
            </div>
            <div class="col-md-2">
              <label>模块</label>
              <select id="filter_module" class="form-control">
                <option value="">全部</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>结果</label>
              <select id="filter_result" class="form-control">
                <option value="">全部</option>
                <option value="success">成功</option>
                <option value="failed">失败</option>
                <option value="denied">拒绝</option>
              </select>
            </div>
            <div class="col-md-3">
              <label>操作人或目标名称</label>
              <input type="text" id="filter_keyword" class="form-control" placeholder="关键词">
            </div>
          </div>
          <div class="row margin-top-10">
            <div class="col-md-12 text-right">
              <button type="button" id="filter_search" class="btn blue">查询</button>
              <button type="button" id="filter_reset" class="btn default">重置</button>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-list"></i>
            <span class="caption-subject bold">操作记录</span>
            <span class="caption-helper" id="row_count">共 — 条</span>
          </div>
          <div class="actions">
            <button type="button" id="log_export" class="btn default btn-sm">导出CSV</button>
            <button type="button" id="log_print" class="btn default btn-sm">打印报表</button>
            <button type="button" id="log_refresh" class="btn default btn-sm">刷新</button>
          </div>
        </div>
        <div class="portlet-body">
          <p id="log_status" class="margin-bottom-10" role="status" aria-live="polite">加载中…</p>
          <div class="table-responsive">
            <table class="table table-striped table-bordered table-hover">
              <thead>
              <tr>
                <th>时间</th>
                <th>操作人</th>
                <th>角色</th>
                <th>模块</th>
                <th>动作</th>
                <th>目标</th>
                <th>结果</th>
                <th class="text-right">操作</th>
              </tr>
              </thead>
              <tbody id="log_body">
              <tr><td colspan="8" class="text-center muted">加载中…</td></tr>
              </tbody>
            </table>
          </div>
          <div class="row">
            <div class="col-md-6">
              <label class="control-label">每页</label>
              <select id="page_size" class="form-control input-inline" style="width:80px;">
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
              <span id="page_info" class="form-control-static">—</span>
            </div>
            <div class="col-md-6 text-right">
              <button type="button" id="page_prev" class="btn default btn-sm">上一页</button>
              <button type="button" id="page_next" class="btn default btn-sm">下一页</button>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</div>

<div class="modal fade" id="log_detail_modal" tabindex="-1" role="dialog">
  <div class="modal-dialog" role="document">
    <div class="modal-content">
      <div class="modal-header">
        <h4 class="modal-title pull-left">操作详情</h4>
        <button type="button" class="close" data-dismiss="modal" aria-label="关闭">
          <span aria-hidden="true">&times;</span>
        </button>
        <div class="clearfix"></div>
      </div>
      <div class="modal-body">
        <table class="table table-bordered">
          <tr><th style="width:140px;">日志编号</th><td id="detail_id">—</td></tr>
          <tr><th>操作时间</th><td id="detail_time">—</td></tr>
          <tr><th>操作人</th><td id="detail_operator">—</td></tr>
          <tr><th>角色</th><td id="detail_role">—</td></tr>
          <tr><th>模块 / 动作</th><td id="detail_action">—</td></tr>
          <tr><th>目标编号</th><td id="detail_target">—</td></tr>
          <tr><th>目标名称</th><td id="detail_target_name">—</td></tr>
          <tr><th>结果</th><td id="detail_result">—</td></tr>
          <tr><th>原因类别</th><td id="detail_reason">—</td></tr>
          <tr><th>变更摘要</th><td id="detail_summary" style="word-break:break-all;">—</td></tr>
          <tr><th>追踪标识</th><td id="detail_trace">—</td></tr>
        </table>
        <p class="muted">摘要只记录关键非敏感字段；密码重置只记录发生过，不保存新旧密码或哈希。</p>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn default" data-dismiss="modal">关闭</button>
      </div>
    </div>
  </div>
</div>

<%@ include file="/home/frame/frame_javascript.jsp" %>
<script>window.GH_ROLE_ID = "${sessionScope.loginUser.roleId}";</script>
<script src="<%= request.getContextPath() %>/maintain/log/operation_log.js"></script>
</body>
</html>
