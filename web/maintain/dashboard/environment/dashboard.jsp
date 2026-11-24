<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "环境监测驾驶舱"); %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 环境监测驾驶舱</title>
  <%@ include file="/home/frame/frame_style.jsp" %>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/environment/environment.css">
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">

<%@ include file="/home/frame/frame_header.jsp" %>
<div class="clearfix"></div>

<div class="page-container">
  <%@ include file="/home/frame/frame_left_sidebar.jsp" %>

  <div class="page-content-wrapper">
    <main class="page-content">
      <%@ include file="/home/frame/frame_page_header.jsp" %>

      <p id="dash-message" role="status" aria-live="polite">正在加载驾驶舱数据……</p>

      <section class="portlet light bordered" aria-labelledby="dash-title">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-graph"></i>
            <span class="caption-subject bold" id="dash-title">环境监测驾驶舱</span>
            <span class="caption-helper">所选大棚单一指标的数量、均值、极值与趋势</span>
          </div>
        </div>
        <div class="portlet-body">
          <form id="dash-query" class="env-filters">
            <label>大棚
              <select id="dash-greenhouse" class="form-control"></select>
            </label>
            <label>指标
              <select id="dash-metric" class="form-control"></select>
            </label>
            <label>时间范围
              <select id="dash-days" class="form-control">
                <option value="1">最近 1 天</option>
                <option value="7" selected>最近 7 天</option>
                <option value="30">最近 30 天</option>
              </select>
            </label>
            <label>&nbsp;
              <button class="btn blue form-control" type="submit">查询</button>
            </label>
            <label>&nbsp;
              <button class="btn default form-control" type="button" id="dash-refresh">立即刷新</button>
            </label>
          </form>

          <div id="dash-cards" class="env-cards"></div>
          <p id="dash-sources" class="env-note"></p>
          <div id="dash-chart" class="env-chart"></div>
          <div id="dash-latest"></div>
          <p id="dash-updated" class="env-note">统计范围读取中……</p>
          <p class="env-note">
            口径：按采样时间（sample_time）统计，默认最近 7 个自然日（含今天），起日 00:00 到结束日次日 00:00 之前；
            单位随指标，不同单位不混算；每 30 秒刷新已应用条件，失败保留上次数值并标明读取失败与上次成功时间。
          </p>
        </div>
      </section>
    </main>
  </div>
</div>

<%@ include file="/home/frame/frame_footer.jsp" %>

<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/dashboard/environment/dashboard.js"></script>
</body>
</html>
