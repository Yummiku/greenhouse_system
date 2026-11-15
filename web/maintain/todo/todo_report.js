(function () {
    "use strict";
    function ready(work) {
        if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", work);
        else work();
    }
    ready(function () {
        var dashboard = document.getElementById("todo_dashboard");
        var tools = document.getElementById("todo_report_tools");
        var printPage = document.getElementById("todo_print");
        var root = dashboard || tools || printPage;
        if (!root || root.dataset.initialized) return;
        root.dataset.initialized = "true";
        var context = root.getAttribute("data-context");
        var api = context + "/todo_report_servlet_action";
        function text(id, value) {
            var element = document.getElementById(id);
            if (element) element.textContent = value;
        }
        function reportError(error) {
            text("todo_report_error", error.message || "读取报表失败");
        }
        function request(params) {
            return fetch(api + "?" + params.toString(), {credentials: "same-origin", cache: "no-store"})
                .then(function (response) {
                    return response.json().then(function (json) {
                        if (!response.ok || Number(json.result_code) !== 0) {
                            throw new Error(json.result_msg || "读取报表失败");
                        }
                        return json;
                    });
                });
        }
        function cell(row, value) {
            var td = document.createElement("td");
            td.textContent = value == null ? "" : value;
            row.appendChild(td);
            return td;
        }
        if (dashboard) {
            var dashboardSequence = 0;
            function loadDashboard() {
                var sequence = ++dashboardSequence;
                text("todo_report_error", "");
                request(new URLSearchParams({action: "dashboard"})).then(function (json) {
                    if (sequence !== dashboardSequence) return;
                    var data = json.data;
                    text("todo_total_count", data.total_count);
                    text("todo_today_count", data.today_count);
                    text("todo_month_count", data.month_count);
                    text("todo_latest_time", data.latest_time || "暂无记录");
                    var tbody = document.getElementById("todo_month_rows");
                    tbody.textContent = "";
                    var max = Math.max.apply(null, [1].concat(data.months.map(function (m) { return m.record_count; })));
                    data.months.forEach(function (month) {
                        var row = document.createElement("tr");
                        cell(row, month.month);
                        cell(row, month.record_count);
                        var td = cell(row, "");
                        var bar = document.createElement("div");
                        bar.className = "progress";
                        var fill = document.createElement("div");
                        fill.className = "progress-bar progress-bar-info";
                        fill.style.width = (month.record_count / max * 100) + "%";
                        fill.textContent = month.record_count;
                        bar.appendChild(fill); td.appendChild(bar); tbody.appendChild(row);
                    });
                    if (!data.months.length) {
                        var empty = document.createElement("tr");
                        cell(empty, "暂无统计数据").colSpan = 3;
                        tbody.appendChild(empty);
                    }
                }).catch(function (error) {
                    if (sequence === dashboardSequence) reportError(error);
                });
            }
            document.getElementById("todo_dashboard_refresh").addEventListener("click", loadDashboard);
            loadDashboard();
        }
        if (tools) {
            var wrapper = document.getElementById("todo_crud_table_wrapper") || document.getElementById("todo_crud_table");
            if (wrapper) wrapper.parentNode.insertBefore(tools, wrapper);
            function filters() {
                var params = new URLSearchParams();
                [["id", "query_id"], ["title", "query_title"], ["create_date", "query_date"]].forEach(function (item) {
                    var input = document.getElementById(item[1]);
                    var value = window.TODO_REPORT_FILTERS
                        ? window.TODO_REPORT_FILTERS[item[0]] : input && input.value;
                    if (value && value.trim()) params.set(item[0], value.trim());
                });
                var order = [[2, "desc"]];
                if (window.jQuery && jQuery.fn.dataTable && jQuery.fn.dataTable.isDataTable("#todo_crud_table")) {
                    order = jQuery("#todo_crud_table").DataTable().order();
                }
                var columns = ["id", "title", "create_time"];
                var first = order.find(function (item) { return columns[item[0]]; }) || [2, "desc"];
                params.set("order_by", columns[first[0]]);
                params.set("order_dir", first[1]);
                return params;
            }
            var summarySequence = 0;
            function summary() {
                var sequence = ++summarySequence;
                var params = filters(); params.set("action", "summary");
                request(params).then(function (json) {
                    if (sequence !== summarySequence) return;
                    text("todo_report_error", "");
                    text("todo_query_summary", "当前查询共 " + json.data.record_count + " 条；最早创建："
                        + (json.data.earliest_time || "—") + "；最晚创建：" + (json.data.latest_time || "—"));
                }).catch(function (error) {
                    if (sequence !== summarySequence) return;
                    text("todo_query_summary", "统计加载失败"); reportError(error);
                });
            }
            document.getElementById("todo_export_csv").addEventListener("click", function () {
                var params = filters(); params.set("action", "export");
                window.location.assign(api + "?" + params.toString());
            });
            document.getElementById("todo_open_print").addEventListener("click", function () {
                window.open(context + "/maintain/todo/todo_print.jsp?" + filters().toString(), "_blank", "noopener");
            });
            var timer;
            function schedule() { clearTimeout(timer); timer = setTimeout(summary, 150); }
            if (window.jQuery) jQuery("#todo_crud_table").on("draw.dt.todoReport", schedule);
            var form = document.getElementById("todo_query_form");
            if (form) { form.addEventListener("submit", schedule); form.addEventListener("reset", schedule); }
            var reset = document.getElementById("todo_query_reset");
            if (reset) reset.addEventListener("click", schedule);
            summary();
        }
        if (printPage) {
            var source = new URLSearchParams(window.location.search);
            var params = new URLSearchParams({action: "records"});
            ["id", "title", "create_date", "order_by", "order_dir"].forEach(function (key) {
                if (source.has(key)) params.set(key, source.get(key));
            });
            var labels = {id: "编号", title: "待办标题", create_time: "创建时间"};
            var orderBy = labels[source.get("order_by")] ? source.get("order_by") : "create_time";
            text("todo_print_order", "排序：" + labels[orderBy] + (source.get("order_dir") === "asc" ? "升序" : "降序"));
            text("todo_print_filter", "查询条件：编号=" + (source.get("id") || "不限")
                + "；标题=" + (source.get("title") || "不限") + "；日期=" + (source.get("create_date") || "不限"));
            function loadPrint() {
                var button = document.getElementById("todo_do_print"); button.disabled = true;
                text("todo_report_error", "");
                document.getElementById("todo_print_rows").textContent = "";
                text("todo_print_summary", "正在读取报表……");
                request(params).then(function (json) {
                    var tbody = document.getElementById("todo_print_rows");
                    json.aaData.forEach(function (item) {
                        var row = document.createElement("tr");
                        cell(row, item.id); cell(row, item.title); cell(row, item.create_time); tbody.appendChild(row);
                    });
                    if (!json.aaData.length) {
                        var empty = document.createElement("tr"); cell(empty, "没有符合条件的记录").colSpan = 3; tbody.appendChild(empty);
                    }
                    text("todo_print_summary", "合计：" + json.record_count + " 条待办事项。");
                    text("todo_print_time", "报表生成时间：" + new Date().toLocaleString("zh-CN", {hour12: false}));
                    button.disabled = false;
                }).catch(function (error) { text("todo_print_summary", "报表加载失败"); reportError(error); });
            }
            document.getElementById("todo_do_print").addEventListener("click", function () { window.print(); });
            document.getElementById("todo_print_retry").addEventListener("click", loadPrint);
            loadPrint();
        }
    });
}());
