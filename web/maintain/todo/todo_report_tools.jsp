<%@ page pageEncoding="UTF-8" %>
<!-- 此片段由 todo_crud.jsp 引入，脚本将工具栏移动到原列表上方。 -->
<div id="todo_report_tools" data-context="<%= request.getContextPath() %>" style="margin:15px 0">
    <div class="btn-group">
        <button type="button" class="btn blue" id="todo_export_csv"><i class="fa fa-download"></i> 导出CSV</button>
        <button type="button" class="btn blue" id="todo_open_print"><i class="fa fa-print"></i> 打印报表</button>
        <a class="btn default" href="<%= request.getContextPath() %>/maintain/todo/todo_dashboard.jsp">统计首页</a>
    </div>
    <p class="help-block">导出和打印包含当前查询条件下的全部记录，并采用当前表格的首个排序条件。</p>
    <p id="todo_query_summary" aria-live="polite">正在读取统计……</p>
    <p id="todo_report_error" class="text-danger" role="alert"></p>
</div>
