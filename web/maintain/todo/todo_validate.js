jQuery(function ($) {
    var form = $("#todo_edit_form");
    if (!form.length) return;

    var fields = ["todo_title", "todo_time"];

    function checkTime(value) {
        if (!value) return "请输入创建时间";

        // 年-月-日 时:分:秒，先检查格式和各部分范围。
        var pattern = /^([1-9]\d{3})-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01]) ([01]\d|2[0-3]):([0-5]\d):([0-5]\d)$/;
        var match = pattern.exec(value);
        if (!match) return "格式应为 yyyy-MM-dd HH:mm:ss，例如 2026-11-03 14:30:00";

        // 再检查真实日期，避免 2 月 30 日等不存在的日期通过。
        var year = Number(match[1]);
        var month = Number(match[2]);
        var day = Number(match[3]);
        var leap = year % 400 === 0
            || (year % 4 === 0 && year % 100 !== 0);
        var days = [31, leap ? 29 : 28, 31, 30, 31, 30,
                    31, 31, 30, 31, 30, 31];

        if (day > days[month - 1]) return "该日期不存在，请检查月份和日期";
        return "";
    }

    function validateField(id) {
        var input = $("#" + id);
        var value = input.val().trim();
        var message = "";

        if (id === "todo_title") {
            if (!value) message = "请输入待办标题，不能只输入空格";
            else if (value.length > 255) message = "待办标题不能超过255个字符";
        } else {
            message = checkTime(value);
        }

        input.closest(".form-group").toggleClass("has-error", !!message);
        input.attr("aria-invalid", message ? "true" : "false");
        $("#" + id + "_error").text(message).toggle(!!message);

        if (!message) input.val(value);
        return !message;
    }

    function clearValidation() {
        form.find(".form-group").removeClass("has-error");
        form.find(".field-error").text("").hide();
        form.find("input").attr("aria-invalid", "false");
        $("#todo_form_error").text("");
    }

    window.todoValidation = {
        validate: function () {
            var firstInvalid = null;
            $("#todo_form_error").text("");

            fields.forEach(function (id) {
                if (!validateField(id) && !firstInvalid) firstInvalid = id;
            });

            if (firstInvalid) $("#" + firstInvalid).focus();
            return firstInvalid === null;
        }
    };

    form.find("#todo_title, #todo_time")
        .on("blur change", function () {
            validateField(this.id);
        })
        .on("input", function () {
            if ($(this).closest(".form-group").hasClass("has-error")) {
                validateField(this.id);
            }
        });

    $("#todo_modal").on("show.bs.modal", function () {
        clearValidation();
        // 兼容原有添加、修改代码提供的 datetime-local 值。
        $("#todo_time").val($("#todo_time").val().replace("T", " "));
    });
});
