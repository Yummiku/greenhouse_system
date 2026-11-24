<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "登录日志");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 登录日志</title>
  <%@ include file="/home/frame/frame_style.jsp" %>
  <style>
    .log-bar { background: #e9f5ee; height: 18px; border-radius: 3px; overflow: hidden; }
    .log-bar > span { display: block; height: 100%; background: #2f8f5b; }
    .log-bar.fail > span { background: #d9534f; }
    .log-bar-wrap { margin-bottom: 8px; font-size: 12px; }
    .log-chart { padding: 6px 0 2px; }
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
              <div class="muted">登录事件总数</div>
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
              <div class="bold font-lg" id="stat_failure">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">活跃用户数（不同成功用户）</div>
              <div class="bold font-lg" id="stat_active">—</div>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold">按天趋势</span>
            <span class="caption-helper" id="range_label">—</span>
          </div>
        </div>
        <div class="portlet-body">
          <div id="login_trend" class="log-chart">
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
              <label>结果</label>
              <select id="filter_result" class="form-control">
                <option value="">全部</option>
                <option value="success">成功</option>
                <option value="failure">失败</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>失败原因类别</label>
              <select id="filter_reason" class="form-control">
                <option value="">全部</option>
                <option value="credential_error">凭据错误</option>
                <option value="account_disabled">账号停用</option>
                <option value="invalid_input">输入不完整</option>
                <option value="auth_service_error">认证服务失败</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>来源</label>
              <select id="filter_source" class="form-control">
                <option value="">全部</option>
                <option value="pc">PC</option>
                <option value="wx">微信</option>
                <option value="mini">小程序</option>
              </select>
            </div>
            <div class="col-md-2" id="keyword_box">
              <label>尝试账号</label>
              <input type="text" id="filter_keyword" class="form-control" placeholder="仅系统管理员可筛选账号">
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
            <span class="caption-subject bold">登录事件</span>
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
                <th>尝试账号</th>
                <th>用户ID</th>
                <th>结果</th>
                <th>原因类别</th>
                <th>来源</th>
                <th>IP</th>
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
        <h4 class="modal-title pull-left">登录事件详情</h4>
        <button type="button" class="close" data-dismiss="modal" aria-label="关闭">
          <span aria-hidden="true">&times;</span>
        </button>
        <div class="clearfix"></div>
      </div>
      <div class="modal-body">
        <table class="table table-bordered">
          <tr><th style="width:140px;">事件编号</th><td id="detail_id">—</td></tr>
          <tr><th>发生时间</th><td id="detail_time">—</td></tr>
          <tr><th>尝试账号</th><td id="detail_username">—</td></tr>
          <tr><th>用户ID</th><td id="detail_user">—</td></tr>
          <tr><th>结果</th><td id="detail_result">—</td></tr>
          <tr><th>原因类别</th><td id="detail_reason">—</td></tr>
          <tr><th>来源</th><td id="detail_source">—</td></tr>
          <tr><th>IP</th><td id="detail_ip">—</td></tr>
          <tr><th>客户端信息</th><td id="detail_client" style="word-break:break-all;">—</td></tr>
        </table>
        <p class="muted">日志不保存密码、密码哈希、验证码、会话Cookie、微信密钥或令牌。</p>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn default" data-dismiss="modal">关闭</button>
      </div>
    </div>
  </div>
</div>

<%@ include file="/home/frame/frame_javascript.jsp" %>
<script>window.GH_ROLE_ID = "${sessionScope.loginUser.roleId}";</script>
<script src="<%= request.getContextPath() %>/maintain/log/login_log.js"></script>
</body>
</html>
