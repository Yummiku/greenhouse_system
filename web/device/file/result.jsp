<%@ page language="java" import="java.util.*" pageEncoding="UTF-8"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
<head>
<title>操作结果</title>
</head>

<body>
	<input type="hidden" id="page_id" name="page_id" value="result"/>
	<div id="result_div">
		操作结果：<span id="result_code"></span> - <span id="result_msg"></span>
	</div>
	<script>
		// 从URL参数获取结果
		var urlParams = new URLSearchParams(window.location.search);
		document.getElementById('result_code').innerText = urlParams.get('result_code');
		document.getElementById('result_msg').innerText = urlParams.get('result_msg');
	</script>
</body>
</html>

