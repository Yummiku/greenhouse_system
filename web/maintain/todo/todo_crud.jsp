<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<!DOCTYPE html>
<!-- 
Template Name: Metronic - Responsive Admin Dashboard Template build with Twitter Bootstrap 3.3.5
Version: 4.1.0
Author: KeenThemes
Website: http://www.keenthemes.com/
Contact: support@keenthemes.com
Follow: www.twitter.com/keenthemes
Like: www.facebook.com/keenthemes
Purchase: http://themeforest.net/item/metronic-responsive-admin-dashboard-template/4021469?ref=keenthemes
License: You must have a valid license purchased only from themeforest(the above link) in order to legally use the theme for your project.
-->
<!--[if IE 8]> <html lang="en" class="ie8 no-js"> <![endif]-->
<!--[if IE 9]> <html lang="en" class="ie9 no-js"> <![endif]-->
<!--[if !IE]><!-->
<html lang="en">
<!--<![endif]-->
<!-- BEGIN HEAD -->
<head>
<meta charset="utf-8"/>
<title>待办事项增删改查</title>
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta content="width=device-width, initial-scale=1.0" name="viewport"/>
<meta http-equiv="Content-type" content="text/html; charset=utf-8">
<meta content="" name="description"/>
<meta content="" name="author"/>
<%@ include file="/home/frame/frame_style.jsp" %>
<link rel="shortcut icon" href="favicon.ico"/>


<link rel="stylesheet"
	  href="<%= request.getContextPath() %>/assets/global/plugins/datatables/plugins/bootstrap/dataTables.bootstrap.css">
</head>
<!-- END HEAD -->
<!-- BEGIN BODY -->
<!-- DOC: Apply "page-header-fixed-mobile" and "page-footer-fixed-mobile" class to body element to force fixed header or footer in mobile devices -->
<!-- DOC: Apply "page-sidebar-closed" class to the body and "page-sidebar-menu-closed" class to the sidebar menu element to hide the sidebar by default -->
<!-- DOC: Apply "page-sidebar-hide" class to the body to make the sidebar completely hidden on toggle -->
<!-- DOC: Apply "page-sidebar-closed-hide-logo" class to the body element to make the logo hidden on sidebar toggle -->
<!-- DOC: Apply "page-sidebar-hide" class to body element to completely hide the sidebar on sidebar toggle -->
<!-- DOC: Apply "page-sidebar-fixed" class to have fixed sidebar -->
<!-- DOC: Apply "page-footer-fixed" class to the body element to have fixed footer -->
<!-- DOC: Apply "page-sidebar-reversed" class to put the sidebar on the right side -->
<!-- DOC: Apply "page-full-width" class to the body element to have full width page without the sidebar menu -->
<body class="page-header-fixed page-quick-sidebar-over-content ">

<%@ include file="/home/frame/frame_header.jsp" %>

<div class="clearfix">
</div>
<!-- BEGIN CONTAINER -->
<div class="page-container">

	<%@ include file="/home/frame/frame_left_sidebar.jsp" %>

	<!-- BEGIN CONTENT -->
	<div class="page-content-wrapper">
		<div class="page-content">
			<!-- BEGIN SAMPLE PORTLET CONFIGURATION MODAL FORM-->

			<!-- END SAMPLE PORTLET CONFIGURATION MODAL FORM-->
			<!-- BEGIN STYLE CUSTOMIZER -->

			<!-- END STYLE CUSTOMIZER -->
			<% request.setAttribute("pageTitle", "待办事项管理"); %>
			<%@ include file="/home/frame/frame_page_header.jsp" %>
			<!-- BEGIN PAGE CONTENT-->
			<div class="row">
				<div class="col-md-12">


					<div class="margin-bottom-15">
						<button type="button" id="todo_add"
								class="btn blue" style="display:none;">
							添加
						</button>

						<button type="submit" form="todo_query_form" class="btn blue">
							查询
						</button>

						<button type="button" id="todo_query_reset" class="btn default">
							重置
						</button>
					</div>

					<form id="todo_query_form" class="form-horizontal">

						<div class="form-group">
							<label for="query_id" class="col-md-2 control-label">
								记录编号
							</label>
							<div class="col-md-6">
								<input type="number" id="query_id"
									   class="form-control"
									   min="1" step="1"
									   placeholder="输入完整编号，可留空">
							</div>
						</div>

						<div class="form-group">
							<label for="query_title" class="col-md-2 control-label">
								待办标题
							</label>
							<div class="col-md-6">
								<input type="text" id="query_title"
									   class="form-control"
									   maxlength="255"
									   placeholder="输入标题关键词，可留空">
							</div>
						</div>

						<div class="form-group">
							<label for="query_date" class="col-md-2 control-label">
								创建日期
							</label>
							<div class="col-md-6">
								<input type="date" id="query_date"
									   class="form-control">
							</div>
						</div>

					</form>

					<p id="todo_status"></p>

					<%@ include file="todo_report_tools.jsp" %>

					<table class="table table-striped table-bordered table-hover"
						   id="todo_crud_table">
						<thead>
						<tr>
							<th>编号</th>
							<th>待办标题</th>
							<th>创建时间</th>
							<th>操作</th>
						</tr>
						</thead>
						<tbody></tbody>
					</table>

				</div>
			</div>
			<!-- END PAGE CONTENT-->
		</div>
	</div>
	<!-- END CONTENT -->
	<!-- BEGIN QUICK SIDEBAR -->

	<!-- END QUICK SIDEBAR -->
</div>
<!-- END CONTAINER -->

<%@ include file="/home/frame/frame_footer.jsp" %>

<%@ include file="todo_edit_div.jsp" %>

<%@ include file="/home/frame/frame_javascript.jsp" %>

<script src="<%= request.getContextPath() %>/assets/global/plugins/datatables/media/js/jquery.dataTables.min.js"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/datatables/plugins/bootstrap/dataTables.bootstrap.js"></script>

<script>
	window.TODO_API =
			"<%= request.getContextPath() %>/device_file_servlet_action";

	window.TODO_CAN_WRITE =
	${sessionScope.loginUser.roleId == '1'
        || sessionScope.loginUser.roleId == '2'};
</script>

<script src="todo_validate.js"></script>
<script src="todo_crud.js"></script>
<script src="todo_report.js"></script>

</body>
<!-- END BODY -->
</html>