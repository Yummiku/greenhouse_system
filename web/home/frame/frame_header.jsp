<%@ page pageEncoding="UTF-8" %>
<%
    Object ghHeaderUser = session.getAttribute("loginUser");

    String ghHeaderName = "未登录";
    String ghHeaderRole = "未识别";

    if (ghHeaderUser instanceof java.util.Map) {
        java.util.Map<?, ?> ghHeaderMap =
                (java.util.Map<?, ?>) ghHeaderUser;

        Object ghNameValue = ghHeaderMap.get("displayName");

        if (ghNameValue == null
                || String.valueOf(ghNameValue).isBlank()) {
            ghNameValue = ghHeaderMap.get("username");
        }

        if (ghNameValue != null) {
            ghHeaderName = String.valueOf(ghNameValue);
        }

        String ghRoleId =
                String.valueOf(ghHeaderMap.get("roleId"));

        if ("1".equals(ghRoleId)) {
            ghHeaderRole = "系统管理员";
        } else if ("2".equals(ghRoleId)) {
            ghHeaderRole = "普通管理员";
        } else if ("3".equals(ghRoleId)) {
            ghHeaderRole = "普通用户";
        }
    }

    // 将姓名作为普通文字显示，避免被当成HTML。
    String ghHeaderSafeName = ghHeaderName
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
%>

<div class="page-header navbar navbar-fixed-top">
    <div class="page-header-inner">

        <div class="page-logo">
            <a href="<%= request.getContextPath() %>/home/main/index.jsp"
               style="color:#fff;font-size:20px;line-height:46px;
                      text-decoration:none;white-space:nowrap;">
                农业大棚系统
            </a>
        </div>

        <a href="javascript:;"
           class="menu-toggler responsive-toggler"
           data-toggle="collapse"
           data-target=".navbar-collapse">
        </a>

        <div class="top-menu">
            <ul class="nav navbar-nav pull-right">

                <li class="dropdown dropdown-user">
                    <a href="javascript:;"
                       class="dropdown-toggle"
                       data-toggle="dropdown"
                       data-close-others="true">

                        <i class="icon-user"></i>

                        <span class="username">
                            <%= ghHeaderSafeName %>
                        </span>

                        <i class="fa fa-angle-down"></i>
                    </a>

                    <ul class="dropdown-menu dropdown-menu-default">
                        <li>
                            <a href="javascript:;">
                                当前角色：<%= ghHeaderRole %>
                            </a>
                        </li>

                        <li class="divider"></li>

                        <li>
                            <a href="<%= request.getContextPath() %>/home/main/index.jsp">
                                <i class="icon-home"></i>
                                系统首页
                            </a>
                        </li>

                        <li>
                            <a href="<%= request.getContextPath() %>/user_center_servlet_action?action=logout">
                                <i class="icon-key"></i>
                                退出登录
                            </a>
                        </li>
                    </ul>
                </li>

            </ul>
        </div>

    </div>
</div>