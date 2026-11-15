jQuery(function ($) {
    var menu = $("#role_sidebar_menu");

    if (!menu.length) {
        return;
    }

    var context = menu.attr("data-context");

    getMenuRecord();

    function getMenuRecord() {
        $.ajax({
            url: context + "/device_file_servlet_action",
            type: "POST",
            dataType: "json",
            data: {
                action: "get_menu_record"
            }
        }).done(function (res) {
            if (Number(res.result_code) !== 0) {
                showError(res.result_msg || "菜单加载失败");
                return;
            }

            showMenuRecord(res.aaData || []);
        }).fail(function (xhr) {
            var res = xhr.responseJSON;

            showError(
                res && res.result_msg
                    ? res.result_msg
                    : "菜单请求失败，请检查后台日志"
            );
        });
    }

    function showMenuRecord(list) {
        $("#role_menu_status").remove();
        menu.children(".role-menu-item").remove();

        var roots = list.filter(function (record) {
            return String(record.parent_id) === "0";
        });

        var count = 0;

        roots.forEach(function (root) {
            var children = list.filter(function (record) {
                return String(record.parent_id)
                    === String(root.item_id);
            });

            if (!children.length) {
                return;
            }

            var parent = $("<li>")
                .addClass("role-menu-item start open");

            var parentLink = $("<a>")
                .attr("href", "javascript:;");

            parentLink.append(
                $("<i>").addClass("icon-home"),
                $("<span>").addClass("title")
                    .text(root.item_name),
                $("<span>").addClass("arrow open")
            );

            var subMenu = $("<ul>")
                .addClass("sub-menu")
                .css("display", "block");

            children.forEach(function (record) {
                var path = record.item_url || "";

                // 本项目的菜单地址统一从 /maintain 开始
                if (path.indexOf("/maintain/") !== 0) {
                    return;
                }

                var url = context + path;
                var item = $("<li>");
                var link = $("<a>").attr("href", url);

                link.append(
                    $("<i>").addClass("icon-list"),
                    $("<span>").text(record.item_name)
                );

                if (window.location.pathname === url) {
                    item.addClass("active");
                    parent.addClass("active");
                }

                item.append(link);
                subMenu.append(item);
                count++;
            });

            parent.append(parentLink, subMenu);
            menu.append(parent);
        });

        if (count === 0) {
            showError("当前角色没有可显示的菜单");
        }
    }

    function showError(message) {
        $("#role_menu_status").remove();

        var item = $("<li>")
            .attr("id", "role_menu_status");

        item.append(
            $("<a>").attr("href", "javascript:;")
                .text(message)
        );

        menu.append(item);
    }
});