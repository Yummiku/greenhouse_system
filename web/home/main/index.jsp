<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
    request.setAttribute("pageTitle", "系统首页");
%>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport"
          content="width=device-width, initial-scale=1">

    <title>农业大棚系统 - 首页</title>

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
                <div class="col-md-12">

                    <div class="portlet light bordered">
                        <div class="portlet-title">
                            <div class="caption">
                                <i class="icon-home"></i>
                                <span class="caption-subject bold">
                                    欢迎使用农业大棚系统
                                </span>
                            </div>
                        </div>

                        <div class="portlet-body">
                            <p>
                                本系统用于大棚环境监测、设备管理、
                                异常预警及工作协同。
                            </p>
                            <p>
                                请从左侧菜单进入已开放的功能。
                            </p>
                        </div>
                    </div>

                    <div class="portlet light bordered">
                        <div class="portlet-title">
                            <div class="caption">
                                <i class="icon-list"></i>
                                <span class="caption-subject bold">
                                    当前功能入口
                                </span>
                            </div>
                        </div>

                        <div class="portlet-body">
                            <a class="btn default" href="<%= request.getContextPath() %>/maintain/user/index.jsp">用户信息</a>
                            <a class="btn green"
                               href="<%= request.getContextPath() %>/maintain/greenhouse/index.jsp">
                                <i class="icon-home"></i>
                                大棚档案
                            </a>
                            <a class="btn blue"
                               href="<%= request.getContextPath() %>/maintain/todo/todo_crud.jsp">
                                <i class="icon-list"></i>
                                工作待办
                            </a>

                            <a class="btn green"
                               href="<%= request.getContextPath() %>/maintain/todo/todo_dashboard.jsp">
                                <i class="icon-bar-chart"></i>
                                待办数据看板
                            </a>
                        </div>
                    </div>

                    <div class="portlet light bordered">
                        <div class="portlet-title">
                            <div class="caption">
                                <i class="icon-grid"></i>
                                <span class="caption-subject bold">
                                    农业业务模块
                                </span>
                            </div>
                        </div>

                        <div class="portlet-body">
                            <table class="table table-bordered">
                                <thead>
                                <tr>
                                    <th>模块</th>
                                    <th>主要内容</th>
                                    <th>开放状态</th>
                                </tr>
                                </thead>
                                <tbody>
                                <tr>
                                    <td>环境监测</td>
                                    <td>环境指标、历史记录与趋势统计</td>
                                    <td>待接入</td>
                                </tr>
                                <tr>
                                    <td>大棚档案</td>
                                    <td>大棚资料维护、查询、统计与输出</td>
                                    <td><a href="<%= request.getContextPath() %>/maintain/greenhouse/index.jsp">进入模块</a></td>
                                </tr>
                                <tr>
                                    <td>设备管理</td>
                                    <td>设备档案、归属及运行状态</td>
                                    <td>待接入</td>
                                </tr>
                                <tr>
                                    <td>预警与控制</td>
                                    <td>阈值规则、异常报警与控制记录</td>
                                    <td>待接入</td>
                                </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>

                </div>
            </div>

        </div>
    </div>

</div>

<%@ include file="/home/frame/frame_footer.jsp" %>
<%@ include file="/home/frame/frame_javascript.jsp" %>

</body>
</html>