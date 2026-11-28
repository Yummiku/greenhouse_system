jQuery(function ($) {
    var menu = $("#role_sidebar_menu");

    if (!menu.length) {
        return;
    }

    var context = menu.attr("data-context");

    // 使用模板已有的 Font Awesome 4.3；按页面匹配，不依赖各数据库的菜单编号。
    var iconsByPath = {
        "/maintain/todo/todo_crud.jsp": "fa-tasks",
        "/maintain/todo/todo_dashboard.jsp": "fa-bar-chart",
        "/maintain/environment/environment.jsp": "fa-tachometer",
        "/maintain/greenhouse/index.jsp": "fa-home",
        "/maintain/greenhouse/map.jsp": "fa-map-marker",
        "/maintain/greenhouse/devices.jsp": "fa-cogs",
        "/maintain/greenhouse/batches.jsp": "fa-pagelines",
        "/maintain/greenhouse/dashboard.jsp": "fa-bar-chart",
        "/maintain/threshold/threshold.jsp": "fa-sliders",
        "/maintain/user/index.jsp": "fa-user",
        "/maintain/message/message_inbox.jsp": "fa-inbox",
        "/maintain/message/message_compose.jsp": "fa-paper-plane",
        "/maintain/permission/index.jsp": "fa-key",
        "/maintain/permission/review.jsp": "fa-shield",
        "/maintain/permission/dashboard.jsp": "fa-tachometer"
    };
    var iconsByName = {
        "工作管理": "fa-briefcase",
        "农业管理": "fa-leaf",
        "大棚档案": "fa-home",
        "账号信息": "fa-users",
        "消息中心": "fa-envelope",
        "档案列表": "fa-home",
        "种植批次管理": "fa-pagelines",
        "设备归属管理": "fa-cogs",
        "大棚位置地图": "fa-map-marker",
        "阈值规则": "fa-sliders"
    };

    function menuIcon(record) {
        var path = String(record.item_url || "").split(/[?#]/)[0];
        var name = String(record.item_name || "").trim();
        var icon = Object.prototype.hasOwnProperty.call(iconsByPath, path)
            ? iconsByPath[path]
            : (Object.prototype.hasOwnProperty.call(iconsByName, name) ? iconsByName[name] : "fa-list-ul");
        return "fa fa-fw " + icon;
    }

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

        function build(record, ancestors) {
            var id = String(record.item_id);
            if (ancestors.indexOf(id) >= 0) return null;
            var path = record.item_url || "";
            var validPath = path.indexOf("/maintain/") === 0;
            var item = $("<li>");
            var link = $("<a>").attr("href", validPath ? context + path : "javascript:;");
            link.append($("<i>").addClass(menuIcon(record)).attr("aria-hidden", "true"), $("<span>").addClass("title").text(record.item_name));
            var subMenu = $("<ul>").addClass("sub-menu").css("display", "block");
            list.filter(function (child) { return String(child.parent_id) === id; }).forEach(function (child) {
                var node = build(child, ancestors.concat(id));
                if (node) subMenu.append(node);
            });
            if (!validPath && !subMenu.children().length) return null;
            if (validPath) {
                count++;
                if (window.location.pathname === context + path) item.addClass("active");
            }
            if (subMenu.children().length) {
                var arrow = $("<span>").addClass("arrow open").attr("role", "button").attr("tabindex", "0").attr("aria-label", "展开或收起" + record.item_name).attr("aria-expanded", "true");
                link.append(arrow);
                item.addClass("open");
                function toggle() {
                    var open = !item.hasClass("open");
                    item.toggleClass("open", open); arrow.toggleClass("open", open).attr("aria-expanded", String(open));
                    subMenu.toggle(open);
                }
                link.on("click", function (event) {
                    // 文字仍进入档案列表，箭头单独展开；阻止旧布局接管有地址的父菜单。
                    event.stopPropagation();
                    if (!validPath || $(event.target).closest(".arrow").length) { event.preventDefault(); toggle(); }
                });
                arrow.on("keydown", function (event) {
                    if (event.key === "Enter" || event.key === " ") { event.preventDefault(); event.stopPropagation(); toggle(); }
                });
                if (subMenu.find("li.active").length) item.addClass("active");
            }
            item.append(link);
            if (subMenu.children().length) item.append(subMenu);
            return item;
        }
        roots.forEach(function (root) {
            var node = build(root, []);
            if (node) menu.append(node.addClass("role-menu-item start"));
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
