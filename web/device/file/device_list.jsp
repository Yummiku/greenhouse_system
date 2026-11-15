<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
	<meta charset="UTF-8">
	<title>待办事项管理</title>
	<style>
		body { font-family: "Microsoft YaHei", sans-serif; margin: 30px; }
		table { width: 100%; border-collapse: collapse; }
		th, td { border: 1px solid #ccc; padding: 10px; text-align: left; }
		th { background: #f2f2f2; }
		#message { margin: 15px 0; }
	</style>
</head>
<body>
<h2>待办事项列表</h2>

<div id="message">正在读取数据……</div>

<table>
	<thead>
	<tr>
		<th>编号</th>
		<th>待办标题</th>
		<th>创建时间</th>
	</tr>
	</thead>
	<tbody id="todo_rows"></tbody>
</table>

<script src="../../assets/js/jquery.min.js"></script>
<script>
	$(function () {
		$.ajax({
			url: "<%= request.getContextPath() %>/device_file_servlet_action",
			type: "POST",
			dataType: "json",
			data: {
				action: "get_device_record",
				ajax: "true"
			},
			success: function (json) {
				if (json.result_code != 0 || !$.isArray(json.aaData)) {
					$("#message").text(json.result_msg || "返回数据异常，请查看控制台");
					return;
				}

				var rows = json.aaData;
				$("#todo_rows").empty();

				$.each(rows, function (index, record) {
					var tr = $("<tr>");
					$("<td>").text(record.id).appendTo(tr);
					$("<td>").text(record.title || "").appendTo(tr);
					$("<td>").text(record.create_time || "").appendTo(tr);
					$("#todo_rows").append(tr);
				});

				$("#message").text("共读取 " + rows.length + " 条待办事项");
			},
			error: function (xhr) {
				$("#message").text("读取失败，HTTP 状态码：" + xhr.status);
			}
		});
	});
</script>
</body>
</html>