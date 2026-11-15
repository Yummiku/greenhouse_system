<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>待办事项列表 - 打印报表</title>
    <style>
        body { font-family:"Microsoft YaHei",sans-serif; color:#222; margin:24px; }
        h1 { text-align:center; font-size:24px; }
        .print-toolbar { margin:16px 0; }
        button { padding:8px 18px; cursor:pointer; }
        table { width:100%; border-collapse:collapse; margin-top:16px; }
        th,td { border:1px solid #555; padding:8px; text-align:left; overflow-wrap:anywhere; }
        th { background:#eee; }
        .text-danger { color:#b52323; }
        #todo_print_summary { margin-top:16px; }
        @page { size:A4 portrait; margin:15mm; }
        @media print {
            body { margin:0; font-size:11pt; }
            .print-toolbar { display:none !important; }
            thead { display:table-header-group; }
            tr { break-inside:avoid; }
            th { background:#eee !important; print-color-adjust:exact; }
        }
    </style>
</head>
<body id="todo_print" data-context="<%= request.getContextPath() %>">
<div class="print-toolbar">
    <button type="button" id="todo_do_print" disabled>打印 / 保存为PDF</button>
    <button type="button" id="todo_print_retry">重新加载</button>
    <p>数据加载完成后点击打印；报表包含当前筛选条件下的全部记录。</p>
</div>
<h1>待办事项列表</h1>
<p id="todo_print_filter"></p>
<p id="todo_print_order"></p>
<p id="todo_print_time"></p>
<p id="todo_report_error" class="text-danger" role="alert"></p>
<table>
    <thead><tr><th style="width:12%">编号</th><th>待办标题</th><th style="width:30%">创建时间</th></tr></thead>
    <tbody id="todo_print_rows"></tbody>
</table>
<p id="todo_print_summary">正在读取报表……</p>
<script src="<%= request.getContextPath() %>/maintain/todo/todo_report.js"></script>
</body>
</html>
