<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "阈值规则管理");
  Object thresholdUser = session.getAttribute("loginUser");
  String thresholdRole = thresholdUser instanceof java.util.Map
          ? String.valueOf(((java.util.Map<?, ?>) thresholdUser).get("roleId")) : "";
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 阈值规则管理</title>
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
      <div class="portlet light bordered">
        <div class="portlet-body">
          <% if ("1".equals(thresholdRole) || "2".equals(thresholdRole)) { %>
          <form id="threshold-create" method="post"
                action="<%= request.getContextPath() %>/api/threshold?action=create">
            <h3>新增规则</h3>
            <p>新规则保存后为停用状态。</p>
            <div class="form-group">
              <label for="rule-house">适用大棚</label>
              <select class="form-control" id="rule-house" name="greenhouseId" required disabled
                      data-url="<%= request.getContextPath() %>/api/greenhouses">
                <option value="">正在加载大棚…</option>
              </select>
            </div>
            <div class="form-group">
              <label for="rule-name">规则名称</label>
              <input class="form-control" id="rule-name" name="name" required maxlength="100">
            </div>
            <div class="form-group">
              <label for="rule-metric">指标</label>
              <select class="form-control" id="rule-metric" name="metric">
                <option value="air_temperature">空气温度（℃）</option>
                <option value="air_humidity">空气湿度（%）</option>
                <option value="soil_temperature">土壤温度（℃）</option>
                <option value="soil_moisture">土壤湿度（%）</option>
                <option value="illuminance">光照强度（lx）</option>
              </select>
            </div>
            <p>上下限至少填一项。</p>
            <div class="form-group">
              <label for="rule-lower">下限</label>
              <input class="form-control" type="number" step="any" id="rule-lower" name="lower">
            </div>
            <div class="form-group">
              <label for="rule-upper">上限</label>
              <input class="form-control" type="number" step="any" id="rule-upper" name="upper">
            </div>
            <button type="submit" class="btn blue" disabled>保存规则</button>
            <p id="threshold-save-status" role="status"></p>
          </form>
          <hr>
          <% } %>
          <section id="rule-panel" class="well" hidden>
            <h3 id="rule-panel-title"></h3>
            <p id="rule-details" style="white-space: pre-wrap; overflow-wrap: anywhere"></p>
            <form id="rule-edit">
              <div class="form-group"><label for="edit-lower">下限</label>
                <input class="form-control" id="edit-lower" name="lower" type="number" step="any"></div>
              <div class="form-group"><label for="edit-upper">上限</label>
                <input class="form-control" id="edit-upper" name="upper" type="number" step="any"></div>
              <div class="form-group"><label for="edit-remark">备注</label>
                <textarea class="form-control" id="edit-remark" name="remark" maxlength="500"></textarea></div>
              <button type="submit" class="btn blue">保存修改</button>
            </form>
            <p id="rule-edit-status" role="status"></p>
            <button id="rule-close" type="button" class="btn btn-default">关闭</button>
          </section>
          <div id="rule-filters" class="form-inline">
            <label>名称 <input id="filter-name" class="form-control" type="search"></label>
            <label>大棚编号 <input id="filter-house" class="form-control" type="number" min="1"></label>
            <label>指标 <select id="filter-metric" class="form-control"><option value="">全部</option></select></label>
            <label>状态 <select id="filter-state" class="form-control">
              <option value="">全部</option><option value="true">启用</option><option value="false">停用</option>
            </select></label>
            <label>排序 <select id="filter-order" class="form-control">
              <option value="updated-desc">最近更新</option><option value="updated-asc">最早更新</option>
              <option value="name-asc">名称升序</option><option value="name-desc">名称降序</option>
            </select></label>
            <button type="button" id="rule-export" class="btn btn-default">导出 CSV</button>
            <button type="button" id="rule-print" class="btn btn-default">打印</button>
          </div>
          <p id="rule-summary" class="margin-top-10"></p>
          <p id="threshold-status" role="status">正在加载规则…</p>
          <div class="table-responsive">
            <table class="table table-bordered table-striped">
              <thead><tr><th scope="col">规则名称</th><th scope="col">下限</th><th scope="col">上限</th><th scope="col">单位</th><th scope="col">状态</th><th scope="col">操作</th></tr></thead>
              <tbody id="threshold-rows" data-url="<%= request.getContextPath() %>/api/threshold?action=list"></tbody>
            </table>
            <div class="margin-bottom-15">
              <button type="button" id="rule-prev" class="btn btn-default">上一页</button>
              <span id="rule-page"></span>
              <button type="button" id="rule-next" class="btn btn-default">下一页</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</div>
<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/threshold/threshold.js"></script>
</body>
</html>




