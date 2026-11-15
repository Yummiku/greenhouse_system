<%@ page pageEncoding="UTF-8" %>
<%
    Object ghPageTitleValue = request.getAttribute("pageTitle");

    String ghPageTitle = ghPageTitleValue == null
            ? "农业大棚系统"
            : String.valueOf(ghPageTitleValue);

    String ghSafePageTitle = ghPageTitle
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
%>

<h3 class="page-title">
    <%= ghSafePageTitle %>
    <small>农业大棚监测与管理平台</small>
</h3>

<div class="page-bar">
    <ul class="page-breadcrumb">
        <li>
            <i class="fa fa-home"></i>
            <a href="<%= request.getContextPath() %>/home/main/index.jsp">
                首页
            </a>
            <i class="fa fa-angle-right"></i>
        </li>
        <li>
            <span><%= ghSafePageTitle %></span>
        </li>
    </ul>
</div>