jQuery(document).ready(function () {
    // 点击按钮切换视图
    $("#show_table").on("click", function () {
        $("#todo_table_view").show();
        $("#todo_bar_view").hide();

        $("#show_table").removeClass("default").addClass("blue");
        $("#show_bar").removeClass("blue").addClass("default");
    });

    $("#show_bar").on("click", function () {
        $("#todo_table_view").hide();
        $("#todo_bar_view").show();

        $("#show_bar").removeClass("default").addClass("blue");
        $("#show_table").removeClass("blue").addClass("default");
    });

    // 使用 media 结构，一条记录生成一个横条。
    function renderTodoBars(list) {
        var container = $("#todo_bars");
        container.empty();

        if (list.length === 0) {
            container.text("暂无待办事项");
            return;
        }

        for (var i = 0; i < list.length; i++) {
            var record = list[i];

            var bar = $("<div>")
                .addClass("media")
                .css({
                    "padding": "16px 0",
                    "border-bottom": "1px solid #ddd"
                });

            // 左侧显示记录编号。
            var left = $("<div>").addClass("pull-left");
            $("<span>")
                .addClass("badge badge-info")
                .text(record.id)
                .appendTo(left);

            // 右侧显示标题和创建时间。
            var body = $("<div>").addClass("media-body");

            $("<h4>")
                .addClass("media-heading")
                .text(record.title || "无标题")
                .appendTo(body);

            $("<p>")
                .addClass("text-muted")
                .text("创建时间：" + (record.create_time || ""))
                .appendTo(body);

            bar.append(left, body);
            container.append(bar);
        }
    }

    function renderText(data, type) {
        if (type !== "display") {
            return data;
        }
        return $("<div>").text(data == null ? "" : data).html();
    }

    $("#todo_table").dataTable({
        "processing": true,
        "paging": true,
        "searching": true,
        "ordering": true,

        // 数据接口及 JSON 中存放记录的字段。
        "sAjaxSource": window.TODO_API,
        "sAjaxDataProp": "aaData",

        // 列的顺序必须与 JSP 中的三个表头一致。
        "aoColumns": [
            {"mData": "id"},
            {"mData": "title", "mRender": renderText},
            {"mData": "create_time", "mRender": renderText}
        ],

        "iDisplayLength": 5,
        "aLengthMenu": [
            [5, 10, 20, -1],
            [5, 10, 20, "全部"]
        ],

        // 默认按创建时间降序排列。
        "aaSorting": [[2, "desc"]],

        "oLanguage": {
            "sProcessing": "正在读取数据……",
            "sLengthMenu": "每页显示 _MENU_ 条",
            "sZeroRecords": "没有匹配的待办事项",
            "sEmptyTable": "暂无待办事项",
            "sInfo": "显示第 _START_ 至 _END_ 条，共 _TOTAL_ 条",
            "sInfoEmpty": "共 0 条记录",
            "sInfoFiltered": "（从 _MAX_ 条记录中过滤）",
            "sSearch": "搜索：",
            "oPaginate": {
                "sFirst": "首页",
                "sPrevious": "上一页",
                "sNext": "下一页",
                "sLast": "末页"
            }
        },

        // 使用老师现有 Servlet 的请求参数。
        "fnServerData": function (source, parameters, callback) {
            parameters.push(
                {"name": "action", "value": "get_device_record"},
                {"name": "ajax", "value": "true"}
            );

            $.ajax({
                "url": source,
                "type": "POST",
                "dataType": "json",
                "data": parameters,

                "success": function (json) {
                    if (json.result_code != 0 ||
                        !$.isArray(json.aaData)) {
                        $("#todo_message").text(
                            json.result_msg || "返回数据异常"
                        );
                        callback({"aaData": []});
                        return;
                    }

                    $("#todo_message").text(
                        "共读取 " + json.aaData.length + " 条待办事项"
                    );

                    // 同一份记录同时用于生成横条。
                    renderTodoBars(json.aaData);

                    // 将数据交给 DataTable。
                    callback(json);
                },

                "error": function (xhr) {
                    $("#todo_message").text(
                        "读取失败，HTTP 状态码：" + xhr.status
                    );
                    callback({"aaData": []});
                }
            });
        }
    });
});