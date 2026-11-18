<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "个人消息");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport"
        content="width=device-width, initial-scale=1">

  <title>农业大棚系统 - 个人消息</title>

  <%@ include file="/home/frame/frame_style.jsp" %>
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
              <div class="muted">可见消息总数</div>
              <div class="bold font-lg" id="stat_total">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">未读</div>
              <div class="bold font-lg" id="stat_unread">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">已读</div>
              <div class="bold font-lg" id="stat_read">—</div>
            </div>
          </div>
        </div>
        <div class="col-md-3">
          <div class="portlet light bordered">
            <div class="portlet-body">
              <div class="muted">类型分布</div>
              <div class="bold" id="stat_types">—</div>
            </div>
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

            <div class="col-md-3">
              <label>关键词</label>
              <input type="text" id="filter_keyword" class="form-control"
                     placeholder="标题或正文">
            </div>

            <div class="col-md-2">
              <label>类型</label>
              <select id="filter_type" class="form-control">
                <option value="">全部</option>
                <option value="notice">通知</option>
                <option value="task">任务</option>
                <option value="system">系统</option>
              </select>
            </div>

            <div class="col-md-2">
              <label>已读状态</label>
              <select id="filter_read" class="form-control">
                <option value="">全部</option>
                <option value="unread">未读</option>
                <option value="read">已读</option>
              </select>
            </div>

            <div class="col-md-2">
              <label>起始日期</label>
              <input type="date" id="filter_start" class="form-control">
            </div>

            <div class="col-md-2">
              <label>结束日期</label>
              <input type="date" id="filter_end" class="form-control">
            </div>

            <div class="col-md-1">
              <label>每页</label>
              <select id="filter_size" class="form-control">
                <option value="10">10</option>
                <option value="20">20</option>
                <option value="5">5</option>
                <option value="50">50</option>
              </select>
            </div>

          </div>

          <div class="row margin-top-10">

            <div class="col-md-2">
              <label>排序字段</label>
              <select id="filter_sort" class="form-control">
                <option value="sentAt">发送时间</option>
                <option value="createdAt">创建时间</option>
              </select>
            </div>

            <div class="col-md-2">
              <label>排序方向</label>
              <select id="filter_dir" class="form-control">
                <option value="desc">降序</option>
                <option value="asc">升序</option>
              </select>
            </div>

            <div class="col-md-8">
              <label>&nbsp;</label>
              <div>
                <button type="button" id="filter_search" class="btn blue">查询</button>
                <button type="button" id="filter_reset" class="btn default">重置</button>
              </div>
            </div>

          </div>
        </div>

      </div>

      <div class="portlet light bordered">

        <div class="portlet-title">
          <div class="caption">
            <i class="icon-envelope-letter"></i>
            <span class="caption-subject bold">我的收件箱</span>
          </div>
          <div class="actions">
            <button type="button" id="message_export" class="btn green btn-sm">导出CSV</button>
            <button type="button" id="message_print" class="btn default btn-sm">打印</button>
            <button type="button" id="message_refresh" class="btn blue btn-sm">刷新</button>
          </div>
        </div>

        <div class="portlet-body">

          <p id="message_status" class="margin-bottom-10" role="status" aria-live="polite">
            正在加载收件箱……
          </p>

          <table class="table table-striped table-bordered table-hover">
            <thead>
            <tr>
              <th style="width: 80px;">编号</th>
              <th>标题</th>
              <th style="width: 110px;">类型</th>
              <th style="width: 170px;">发送时间</th>
              <th style="width: 90px;">状态</th>
              <th style="width: 200px;">操作</th>
            </tr>
            </thead>
            <tbody id="message_inbox_body">
            </tbody>
          </table>

          <div class="row">
            <div class="col-md-6">
              <p id="message_page_info" class="form-control-static">—</p>
            </div>
            <div class="col-md-6 text-right">
              <button type="button" id="message_prev" class="btn default btn-sm">上一页</button>
              <button type="button" id="message_next" class="btn default btn-sm">下一页</button>
            </div>
          </div>

        </div>

      </div>

    </div>
  </div>

</div>

<div class="modal fade" id="message_detail_modal" tabindex="-1" role="dialog">
  <div class="modal-dialog" role="document">
    <div class="modal-content">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-label="关闭">
          <span aria-hidden="true">&times;</span>
        </button>
        <h4 class="modal-title" id="detail_title">消息详情</h4>
      </div>
      <div class="modal-body">
        <table class="table table-bordered">
          <tbody>
          <tr><th style="width: 110px;">类型</th><td id="detail_type">—</td></tr>
          <tr><th>发送人ID</th><td id="detail_sender">—</td></tr>
          <tr><th>接收人ID</th><td id="detail_recipient">—</td></tr>
          <tr><th>状态</th><td id="detail_status">—</td></tr>
          <tr><th>发送时间</th><td id="detail_sent_at">—</td></tr>
          <tr><th>阅读时间</th><td id="detail_read_at">—</td></tr>
          <tr><th>正文</th><td id="detail_content" style="white-space: pre-wrap;">—</td></tr>
          </tbody>
        </table>
        <p id="detail_error" class="text-danger"></p>
      </div>
      <div class="modal-footer">
        <button type="button" class="btn default" data-dismiss="modal">关闭</button>
      </div>
    </div>
  </div>
</div>

<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/message/message_inbox.js"></script>

</body>
</html>
