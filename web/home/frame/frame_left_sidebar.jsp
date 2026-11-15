<%@ page pageEncoding="UTF-8" %>

<!-- BEGIN SIDEBAR -->
<div class="page-sidebar-wrapper">
    <div class="page-sidebar navbar-collapse collapse">

        <ul id="role_sidebar_menu"
            class="page-sidebar-menu"
            data-context="<%= request.getContextPath() %>"
            data-keep-expanded="false"
            data-auto-scroll="true"
            data-slide-speed="200">

            <li class="sidebar-toggler-wrapper">
                <div class="sidebar-toggler"></div>
            </li>

            <li class="heading">
                <h3>
                    ${sessionScope.loginUser.roleId == '1'
                            ? '系统管理员菜单'
                            : (sessionScope.loginUser.roleId == '2'
                            ? '普通管理员菜单'
                            : (sessionScope.loginUser.roleId == '3'
                            ? '普通用户菜单'
                            : '角色未识别'))}
                </h3>
            </li>

            <li id="role_menu_status">
                <a href="javascript:;">正在加载菜单...</a>
            </li>

        </ul>

    </div>
</div>
<!-- END SIDEBAR -->