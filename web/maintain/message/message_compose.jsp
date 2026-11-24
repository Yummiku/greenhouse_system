<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "消息发件");
  Object composeUser = session.getAttribute("loginUser");
  String composeRole = "未识别";
  if (composeUser instanceof java.util.Map) {
    composeRole = String.valueOf(((java.util.Map<?, ?>) composeUser).get("roleId"));
  }
  boolean composeCanSend = "1".equals(composeRole) || "2".equals(composeRole);
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport"
        content="width=device-width, initial-scale=1">

  <title>农业大棚系统 - 消息发件</title>

  <%@ include file="/home/frame/frame_style.jsp" %>

  <style>
    .log-bar { background: #e9f5ee; height: 18px; border-radius: 3px; overflow: hidden; }
    .log-bar > span { display: block; height: 100%; background: #2f8f5b; }
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

      <% if (!composeCanSend) { %>
      <div class="alert alert-warning">
        您没有权限创建或发送消息，只能查看本人收件箱。
      </div>
      <% } else { %>

      <div class="row">
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">发件总数</div>
              <div class="bold font-lg" id="outbox_stat_total">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">草稿</div>
              <div class="bold font-lg" id="outbox_stat_draft">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">已发送</div>
              <div class="bold font-lg" id="outbox_stat_sent">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">已撤回</div>
              <div class="bold font-lg" id="outbox_stat_withdrawn">—</div>
            </div>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold">发件类型分布</span>
            <span class="caption-helper" id="outbox_dist_note">按当前筛选范围统计</span>
          </div>
        </div>
        <div class="portlet-body">
          <div id="outbox_type_chart" class="log-chart">
            <p class="muted">加载中…</p>
          </div>
        </div>
      </div>

      <div class="portlet light bordered">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-envelope-open"></i>
            <span class="caption-subject bold">发件记录</span>
          </div>
          <div class="actions">
            <button type="button" id="message_new_draft" class="btn blue btn-sm">新建消息</button>
            <button type="button" id="message_outbox_export" class="btn default btn-sm">导出CSV</button>
            <button type="button" id="message_outbox_print" class="btn default btn-sm">打印</button>
            <button type="button" id="message_outbox_refresh" class="btn default btn-sm">刷新</button>
          </div>
        </div>
        <div class="portlet-body">

          <div class="row margin-bottom-10">
            <div class="col-md-2">
              <label>状态</label>
              <select id="outbox_status" class="form-control">
                <option value="">全部</option>
                <option value="draft">草稿</option>
                <option value="sent">已发送</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>排序字段</label>
              <select id="outbox_sort" class="form-control">
                <option value="createdAt">创建时间</option>
                <option value="sentAt">发送时间</option>
                <option value="id">编号</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>排序方向</label>
              <select id="outbox_dir" class="form-control">
                <option value="desc">降序</option>
                <option value="asc">升序</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>每页</label>
              <select id="outbox_size" class="form-control">
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="50">50</option>
              </select>
            </div>
            <div class="col-md-2">
              <label>&nbsp;</label>
              <div>
                <button type="button" id="outbox_search" class="btn blue btn-sm">查询</button>
                <button type="button" id="outbox_reset" class="btn default btn-sm">重置</button>
              </div>
            </div>
          </div>

          <p id="message_outbox_status"
             class="margin-bottom-10"
             role="status"
             aria-live="polite">
            正在加载发件记录……
          </p>

          <table class="table table-striped table-bordered table-hover">
            <thead>
            <tr>
              <th style="width: 80px;">编号</th>
              <th>标题</th>
              <th style="width: 110px;">类型</th>
              <th style="width: 100px;">接收人ID</th>
              <th style="width: 100px;">状态</th>
              <th style="width: 170px;">发送时间</th>
              <th style="width: 200px;">操作</th>
            </tr>
            </thead>
            <tbody id="message_outbox_body">
            </tbody>
          </table>

          <div class="row">
            <div class="col-md-6">
              <p id="message_outbox_page_info" class="form-control-static">—</p>
            </div>
            <div class="col-md-6 text-right">
              <button type="button" id="message_outbox_prev" class="btn default btn-sm">上一页</button>
              <button type="button" id="message_outbox_next" class="btn default btn-sm">下一页</button>
            </div>
          </div>

        </div>
      </div>

      <% } %>

    </div>
  </div>

</div>

<% if (composeCanSend) { %>
<div class="modal fade" id="message_form_modal" tabindex="-1" role="dialog">
  <div class="modal-dialog" role="document">
    <div class="modal-content">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-label="关闭">
          <span aria-hidden="true">&times;</span>
        </button>
        <h4 class="modal-title" id="message_modal_title">新建消息草稿</h4>
      </div>
      <div class="modal-body">

        <form id="message_compose_form" class="form-horizontal">

          <div class="form-group">
            <label class="col-md-3 control-label">标题</label>
            <div class="col-md-8">
              <input type="text"
                     id="message_title"
                     class="form-control"
                     maxlength="200"
                     placeholder="不超过200字">
            </div>
          </div>

          <div class="form-group">
            <label class="col-md-3 control-label">类型</label>
            <div class="col-md-4">
              <select id="message_type" class="form-control">
                <option value="notice">通知</option>
                <option value="task">任务</option>
                <option value="system">系统</option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label class="col-md-3 control-label">接收人ID</label>
            <div class="col-md-4">
              <input type="number"
                     id="message_recipient"
                     class="form-control"
                     min="1"
                     placeholder="有效账号ID">
            </div>
            <div class="col-md-4">
              <p class="form-control-static muted">账号ID，用户下拉接口到位后替换</p>
            </div>
          </div>

          <div class="form-group">
            <label class="col-md-3 control-label">正文</label>
            <div class="col-md-8">
              <textarea id="message_content"
                        class="form-control"
                        rows="6"
                        placeholder="消息正文"></textarea>
            </div>
          </div>

          <div class="form-group">
            <div class="col-md-offset-3 col-md-8">
              <span id="message_form_status"
                    role="status"
                    aria-live="polite"></span>
            </div>
          </div>

        </form>

      </div>
      <div class="modal-footer">
        <button type="button" class="btn default" data-dismiss="modal">取消</button>
        <button type="button" id="message_save_draft" class="btn blue">保存草稿</button>
      </div>
    </div>
  </div>
</div>
<% } %>

<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/message/message_compose.js"></script>

</body>
</html>
