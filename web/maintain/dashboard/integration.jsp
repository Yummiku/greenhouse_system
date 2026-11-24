<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "集成驾驶舱");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 集成驾驶舱</title>
  <%@ include file="/home/frame/frame_style.jsp" %>
  <style>
    .dash-num { font-size: 26px; font-weight: 600; color: #1f6f46; }
    .dash-note { font-size: 12px; color: #7a8a80; }
    .dash-bar { background: #e9f5ee; height: 16px; border-radius: 3px; overflow: hidden; }
    .dash-bar > span { display: block; height: 100%; background: #2f8f5b; }
    .dash-bar.fail > span { background: #d9534f; }
    .dash-bar-wrap { margin-bottom: 8px; font-size: 12px; }
    .status-connected { color: #1f6f46; }
    .status-pending { color: #b07c00; }
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

      <div class="portlet light bordered">
        <div class="portlet-body">
          <div class="row">
            <div class="col-md-3">
              <label>起始日期</label>
              <input type="date" id="dash_start" class="form-control">
            </div>
            <div class="col-md-3">
              <label>结束日期</label>
              <input type="date" id="dash_end" class="form-control">
            </div>
            <div class="col-md-6 text-right" style="padding-top:24px;">
              <button type="button" id="dash_refresh" class="btn default">立即刷新</button>
              <span class="dash-note" id="dash_updated">—</span>
            </div>
          </div>
        </div>
      </div>

      <div class="row">
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">成功登录次数</div>
              <div class="dash-num" id="card_login_success">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">失败登录次数</div>
              <div class="dash-num" id="card_login_failure">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">活跃用户数</div>
              <div class="dash-num" id="card_active_users">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">操作总数</div>
              <div class="dash-num" id="card_op_total">—</div>
            </div>
          </div>
        </div>
      </div>

      <div class="row">
        <div class="col-md-4">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">账号（未删除）</div>
              <div class="dash-num" id="card_account_total">—</div>
              <div class="dash-note" id="card_account_detail">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">个人消息总量</div>
              <div class="dash-num" id="card_message_total">—</div>
              <div class="dash-note" id="card_message_detail">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-4">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">操作结果分布</div>
              <div class="dash-note" id="card_op_detail">—</div>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold">登录趋势与操作模块分布</span>
          </div>
        </div>
        <div class="portlet-body">
          <div class="row">
            <div class="col-md-6">
              <div id="login_trend"><p class="muted">加载中…</p></div>
            </div>
            <div class="col-md-6">
              <div id="module_chart"><p class="muted">加载中…</p></div>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <span class="caption-subject bold">指标来源</span>
          </div>
        </div>
        <div class="portlet-body">
          <table class="table table-bordered">
            <thead>
            <tr><th>来源</th><th>状态</th><th>责任人</th><th>说明</th></tr>
            </thead>
            <tbody id="source_body">
            <tr><td colspan="4" class="muted">加载中…</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <p id="dash_status" class="dash-note" role="status" aria-live="polite">加载中…</p>

    </div>
  </div>
</div>

<%@ include file="/home/frame/frame_javascript.jsp" %>
<script>window.GH_ROLE_ID = "${sessionScope.loginUser.roleId}";</script>
<script src="<%= request.getContextPath() %>/maintain/dashboard/integration.js"></script>
</body>
</html>
