<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
  request.setAttribute("pageTitle", "环境监测");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport"
        content="width=device-width, initial-scale=1">

  <title>农业大棚系统 - 环境监测</title>

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

        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold">
                            大棚环境监测
                        </span>
          </div>
        </div>

        <div class="portlet-body">
          <p>
            本模块用于查看空气温度、空气湿度、
            土壤温度、土壤湿度和光照强度。
          </p>

          <div class="alert alert-info">
            环境监测功能正在接入，当前尚无业务数据展示。
          </div>

          <div class="margin-top-15">
            <button type="button"
                    id="environment_check_connection"
                    class="btn blue">
              检查接口连接
            </button>

            <p id="environment_connection_status"
               class="margin-top-10"
               role="status"
               aria-live="polite">
              尚未检查连接。
            </p>
          </div>
        </div>

      </div>

    </div>
  </div>

</div>

<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/environment/environment.js"></script>

</body>
</html>