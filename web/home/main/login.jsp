<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<!-- 
Template Name: Metronic - Responsive Admin Dashboard Template build with Twitter Bootstrap 3.3.5
Version: 3.3.0
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
<title>农业大棚系统 - 登录</title>
<meta http-equiv="X-UA-Compatible" content="IE=edge">
<meta content="width=device-width, initial-scale=1.0" name="viewport"/>
<meta http-equiv="Content-type" content="text/html; charset=utf-8">
<meta content="" name="description"/>
<meta content="" name="author"/>
<!-- BEGIN GLOBAL MANDATORY STYLES -->
<link href="http://fonts.googleapis.com/css?family=Open+Sans:400,300,600,700&subset=all" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/global/plugins/font-awesome/css/font-awesome.min.css" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/global/plugins/simple-line-icons/simple-line-icons.min.css" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/global/plugins/bootstrap/css/bootstrap.min.css" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/global/plugins/uniform/css/uniform.default.css" rel="stylesheet" type="text/css"/>
<!-- END GLOBAL MANDATORY STYLES -->
<!-- BEGIN PAGE LEVEL STYLES -->
<link href="<%= request.getContextPath() %>/assets/admin/pages/css/login.css" rel="stylesheet" type="text/css"/>
<!-- END PAGE LEVEL SCRIPTS -->
<!-- BEGIN THEME STYLES -->
<link href="<%= request.getContextPath() %>/assets/global/css/components.css" id="style_components" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/global/css/plugins.css" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/admin/layout/css/layout.css" rel="stylesheet" type="text/css"/>
<link href="<%= request.getContextPath() %>/assets/admin/layout/css/themes/green.css?v=20261123" rel="stylesheet" type="text/css" id="style_color"/>
<link href="<%= request.getContextPath() %>/assets/admin/layout/css/custom.css" rel="stylesheet" type="text/css"/>
<!-- END THEME STYLES -->
<link rel="shortcut icon" href="favicon.ico"/>
</head>
<!-- END HEAD -->
<!-- BEGIN BODY -->
<body class="login">
<!-- BEGIN SIDEBAR TOGGLER BUTTON -->
<div class="menu-toggler sidebar-toggler">
</div>
<!-- END SIDEBAR TOGGLER BUTTON -->
<!-- BEGIN LOGO -->
<div class="logo">
	<span style="color:#fff;font-size:28px;">农业大棚系统</span>
</div>
<!-- END LOGO -->
<!-- BEGIN LOGIN -->
<div class="content">
	<!-- BEGIN LOGIN FORM -->
	<form class="login-form"
		  action="<%= request.getContextPath() %>/user_center_servlet_action"
		  method="post">

		<input type="hidden" name="action" value="login">

		<h3 class="form-title">农业大棚系统</h3>
		<p>请输入账号和密码登录系统</p>

		<% if ("1".equals(request.getParameter("error"))) { %>
		<div class="alert alert-danger">
			账号或密码错误，请重新输入。
		</div>
		<% } %>

		<div class="form-group">
			<label for="username">账号</label>
			<input class="form-control form-control-solid placeholder-no-fix"
				   type="text"
				   id="username"
				   name="username"
				   placeholder="请输入账号"
				   autocomplete="username"
				   maxlength="64"
				   required>
		</div>

		<div class="form-group">
			<label for="password">密码</label>
			<input class="form-control form-control-solid placeholder-no-fix"
				   type="password"
				   id="password"
				   name="password"
				   placeholder="请输入密码"
				   autocomplete="current-password"
				   required>
		</div>

		<div class="form-actions">
			<button type="submit" class="btn btn-success uppercase">
				登录
			</button>
		</div>

	</form>
	<!-- END LOGIN FORM -->
	<!-- BEGIN FORGOT PASSWORD FORM -->

	<!-- END FORGOT PASSWORD FORM -->
	<!-- BEGIN REGISTRATION FORM -->

	<!-- END REGISTRATION FORM -->
</div>
<div class="copyright">
	XM06 · 农业大棚系统
</div>
<!-- END LOGIN -->
<!-- BEGIN JAVASCRIPTS(Load javascripts at bottom, this will reduce page load time) -->
<!-- BEGIN CORE PLUGINS -->
<!--[if lt IE 9]>
<script src="<%= request.getContextPath() %>/assets/global/plugins/respond.min.js"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/excanvas.min.js"></script> 
<![endif]-->
<script src="<%= request.getContextPath() %>/assets/global/plugins/jquery.min.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/jquery-migrate.min.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/bootstrap/js/bootstrap.min.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/jquery.blockui.min.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/jquery.cokie.min.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/global/plugins/uniform/jquery.uniform.min.js" type="text/javascript"></script>
<!-- END CORE PLUGINS -->
<!-- BEGIN PAGE LEVEL PLUGINS -->
<script src="<%= request.getContextPath() %>/assets/global/plugins/jquery-validation/js/jquery.validate.min.js" type="text/javascript"></script>
<!-- END PAGE LEVEL PLUGINS -->
<!-- BEGIN PAGE LEVEL SCRIPTS -->
<script src="<%= request.getContextPath() %>/assets/global/scripts/metronic.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/admin/layout/scripts/layout.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/admin/layout/scripts/demo.js" type="text/javascript"></script>
<script src="<%= request.getContextPath() %>/assets/admin/pages/scripts/login.js" type="text/javascript"></script>
<!-- END PAGE LEVEL SCRIPTS -->
<script>
jQuery(document).ready(function() {     
Metronic.init(); // init metronic core components
Layout.init(); // init current layout
Login.init();
Demo.init();
});
</script>
<!-- END JAVASCRIPTS -->
</body>
<!-- END BODY -->
</html>