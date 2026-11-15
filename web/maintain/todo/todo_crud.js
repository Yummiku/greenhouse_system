jQuery(document).ready(function () {

    // 系统管理员和普通管理员可以维护数据。
    var canWrite = window.TODO_CAN_WRITE === true;

    // 普通用户不显示添加按钮。
    $("#todo_add").toggle(canWrite);

    function showError(message) {
        $("#todo_status").text(message);
        $("#todo_form_error").text(message);
    }

    // 统一发送请求并检查后端结果。
    function api(parameters, success, failure) {
        return $.ajax({
            url: window.TODO_API,
            type: "POST",
            dataType: "json",
            data: parameters
        }).done(function (json) {
            if (json.result_code != 0) {
                showError(json.result_msg || "操作失败");
                if (failure) failure();
                return;
            }

            success(json);

        }).fail(function (xhr) {
            var json = xhr.responseJSON;
            showError(json && json.result_msg
                ? json.result_msg
                : "请求失败，HTTP 状态码：" + xhr.status);

            if (failure) failure();
        });
    }

    function renderText(value, type) {
        if (type !== "display") return value;
        return $("<div>").text(value == null ? "" : value).html();
    }

    // 初始化列表。
    var table = $("#todo_crud_table").DataTable({
        searching: false,
        ajax: function (parameters, callback) {
            // 报表沿用最后一次实际查询条件，未点击查询的输入不影响导出。
            window.TODO_REPORT_FILTERS = {
                id: $("#query_id").val(),
                title: $("#query_title").val().trim(),
                create_date: $("#query_date").val()
            };
            api($.extend({action: "get_todo_record"}, window.TODO_REPORT_FILTERS), function (json) {
                callback({data: json.aaData});
            }, function () {
                callback({data: []});
            });
        },

        columns: [
            {data: "id"},
            {data: "title", render: renderText},
            {data: "create_time", render: renderText},
            {
                data: null,
                visible: canWrite,
                orderable: false,
                searchable: false,
                render: function (data, type, row) {

                    if (!canWrite) {
                        return "";
                    }

                    return '<button type="button"'
                        + ' class="btn btn-link btn-xs todo-edit"'
                        + ' data-id="' + row.id + '">【修改记录】</button>'
                        + '<button type="button"'
                        + ' class="btn btn-link btn-xs todo-delete"'
                        + ' data-id="' + row.id + '">【删除记录】</button>';
                }
            }
        ],

        pageLength: 5,
        lengthMenu: [[5, 10, 20, -1], [5, 10, 20, "全部"]],
        order: [[2, "desc"], [0, "desc"]],

        language: {
            emptyTable: "暂无待办事项",
            zeroRecords: "没有匹配的记录",
            lengthMenu: "每页显示 _MENU_ 条",
            search: "搜索：",
            info: "显示第 _START_ 至 _END_ 条，共 _TOTAL_ 条",
            infoEmpty: "共 0 条记录",
            infoFiltered: "（从 _MAX_ 条记录中过滤）",
            paginate: {
                previous: "上一页",
                next: "下一页"
            }
        }
    });

    // 查询：读取表单条件，重新请求后端，从第一页显示。
    $("#todo_query_form").on("submit", function (event) {
        event.preventDefault();

        $("#todo_status").text("");
        table.search("");
        table.ajax.reload();
    });

    // 重置：清空所有条件，重新查询全部记录。
    $("#todo_query_reset").on("click", function () {
        $("#todo_query_form")[0].reset();

        $("#todo_status").text("");
        table.search("");
        table.ajax.reload();
    });

    // 点击添加：清空编号，设置默认时间。
    $("#todo_add").on("click", function () {
        $("#todo_edit_form")[0].reset();
        $("#todo_id").val("");
        $("#todo_form_error").text("");
        $("#todo_modal_title").text("添加待办");

        var now = new Date();
        var localTime = new Date(
            now.getTime() - now.getTimezoneOffset() * 60000
        ).toISOString().slice(0, 19);

        $("#todo_time").val(localTime);
        $("#todo_modal").modal("show");
    });

    // 点击修改：按 id 查询，然后回显到弹窗。
    $("#todo_crud_table").on("click", ".todo-edit", function () {
        var id = $(this).attr("data-id");

        api({
            action: "get_todo_record",
            id: id
        }, function (json) {
            if (!json.aaData || json.aaData.length !== 1) {
                showError("记录不存在，请刷新列表");
                return;
            }

            var record = json.aaData[0];

            $("#todo_id").val(record.id);
            $("#todo_title").val(record.title);
            $("#todo_time").val(
                record.create_time.replace(" ", "T")
            );

            $("#todo_form_error").text("");
            $("#todo_modal_title").text("修改待办");
            $("#todo_modal").modal("show");
        });
    });

    // 保存：有编号是修改，没有编号是添加。
    $("#todo_edit_form").on("submit", function (event) {
        event.preventDefault();
        if (!canWrite) {
            showError("当前角色只能查看数据");
            return;
        }

        // 校验失败时停止，不发送添加或修改请求
        if (!window.todoValidation.validate()) {
            return;
        }

        var id = $("#todo_id").val();
        $("#todo_save").prop("disabled", true);
        $("#todo_form_error").text("");

        api({
            action: id ? "modify_todo_record" : "add_todo_record",
            id: id,
            title: $("#todo_title").val(),
            create_time: $("#todo_time").val()
        }, function () {
            $("#todo_modal").modal("hide");
            $("#todo_status").text(id ? "修改成功" : "添加成功");
            table.ajax.reload(null, false);
        }).always(function () {
            $("#todo_save").prop("disabled", false);
        });
    });

    // 删除：确认后删除指定记录。
    $("#todo_crud_table").on("click", ".todo-delete", function () {
        var id = $(this).attr("data-id");

        if (!confirm("确定删除编号为 " + id + " 的待办事项吗？")) {
            return;
        }

        api({
            action: "delete_todo_record",
            id: id
        }, function () {
            $("#todo_status").text("删除成功");
            table.ajax.reload(null, false);
        });
    });
});
