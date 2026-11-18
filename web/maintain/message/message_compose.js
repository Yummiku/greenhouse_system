(function () {
    "use strict";

    var saveButton = document.getElementById("message_save_draft");
    var formStatus = document.getElementById("message_form_status");
    var titleInput = document.getElementById("message_title");
    var typeSelect = document.getElementById("message_type");
    var recipientInput = document.getElementById("message_recipient");
    var contentInput = document.getElementById("message_content");
    var refreshButton = document.getElementById("message_outbox_refresh");
    var outboxStatus = document.getElementById("message_outbox_status");
    var outboxBody = document.getElementById("message_outbox_body");
    var statusSelect = document.getElementById("outbox_status");
    var sortSelect = document.getElementById("outbox_sort");
    var dirSelect = document.getElementById("outbox_dir");
    var pageSizeSelect = document.getElementById("outbox_size");
    var pageInfo = document.getElementById("message_outbox_page_info");
    var prevButton = document.getElementById("message_outbox_prev");
    var nextButton = document.getElementById("message_outbox_next");
    var exportButton = document.getElementById("message_outbox_export");
    var printButton = document.getElementById("message_outbox_print");
    var newDraftButton = document.getElementById("message_new_draft");
    var modalTitle = document.getElementById("message_modal_title");

    // 角色3页面上不渲染表单，直接结束。
    if (!saveButton || !outboxBody) return;

    var currentPage = 1;
    var currentPages = 0;
    var loading = false;
    var lastRows = [];
    var lastTotal = 0;
    /** 0 表示新建；大于 0 表示正在编辑的草稿编号。 */
    var editingId = 0;
    var TYPE_LABELS = { notice: "通知", task: "任务", system: "系统" };

    function typeLabel(type) {
        return TYPE_LABELS[type] || type || "未分类";
    }

    function emptyRow(text) {
        var row = document.createElement("tr");
        var cell = document.createElement("td");
        cell.colSpan = 7;
        cell.className = "text-center";
        cell.textContent = text;
        row.appendChild(cell);
        return row;
    }

    function cell(text) {
        var td = document.createElement("td");
        td.textContent = text;
        return td;
    }

    async function sendDraft(id, button) {
        button.disabled = true;
        try {
            await window.GHApi.post("/api/messages", { action: "sendDraft", id: id });
            outboxStatus.textContent = "消息 " + id + " 已发送。";
            await loadOutbox();
        } catch (error) {
            button.disabled = false;
            outboxStatus.textContent = error.status === 409
                    ? "这条消息状态已变化，请刷新后重试。"
                    : "发送失败：" + error.message;
        }
    }

    function statusLabel(status) {
        if (status === "draft") return "草稿";
        if (status === "withdrawn") return "已撤回";
        return "已发送";
    }

    async function withdrawMessage(id, button) {
        if (!window.confirm("确认撤回这条消息？撤回后接收人看不到它。")) {
            return;
        }
        button.disabled = true;
        try {
            await window.GHApi.post("/api/messages", { action: "withdraw", id: id });
            outboxStatus.textContent = "消息已撤回。";
            await loadOutbox();
        } catch (error) {
            button.disabled = false;
            outboxStatus.textContent = error.status === 403
                    ? "您没有权限撤回这条消息。"
                    : (error.status === 409 ? "这条消息当前不能撤回。"
                            : "撤回失败：" + error.message);
        }
    }

    async function deleteDraft(id, button) {
        if (!window.confirm("确认删除这条草稿？")) {
            return;
        }
        button.disabled = true;
        try {
            await window.GHApi.post("/api/messages", { action: "deleteDraft", id: id });
            outboxStatus.textContent = "草稿已删除。";
            await loadOutbox();
        } catch (error) {
            button.disabled = false;
            outboxStatus.textContent = error.status === 403
                    ? "您没有权限删除这条草稿。"
                    : (error.status === 409 ? "只有草稿可以删除。"
                            : "删除失败：" + error.message);
        }
    }

    /** 打开编辑弹窗：只有草稿行会调用，发送后的消息不提供编辑入口。 */
    function openEditor(message) {
        editingId = message.id;
        titleInput.value = message.title || "";
        typeSelect.value = message.messageType || "notice";
        recipientInput.value = message.recipientId == null ? "" : String(message.recipientId);
        contentInput.value = message.content || "";
        formStatus.textContent = "";
        if (modalTitle) {
            modalTitle.textContent = "编辑草稿 #" + message.id;
        }
        saveButton.textContent = "保存修改";
        if (window.jQuery) {
            window.jQuery("#message_form_modal").modal("show");
        }
        titleInput.focus();
    }

    function smallButton(label, cssClass, handler) {
        var button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-xs " + cssClass;
        button.style.marginRight = "4px";
        button.textContent = label;
        button.addEventListener("click", handler);
        return button;
    }

    function buildRow(message) {
        var row = document.createElement("tr");
        row.appendChild(cell(message.id));
        row.appendChild(cell(message.title));
        row.appendChild(cell(typeLabel(message.messageType)));
        row.appendChild(cell(message.recipientId));
        row.appendChild(cell(message.sendStatus === "draft" ? "草稿"
                : (message.sendStatus === "withdrawn" ? "已撤回" : "已发送")));
        row.appendChild(cell(message.sentAt || "未发送"));

        // 操作列禁用换行，三个按钮保持同一排，行高不会被撑高。
        var actionCell = document.createElement("td");
        actionCell.style.whiteSpace = "nowrap";
        if (message.sendStatus === "draft") {
            actionCell.appendChild(smallButton("编辑", "default", function () {
                openEditor(message);
            }));
            actionCell.appendChild(smallButton("发送", "green", function (event) {
                sendDraft(message.id, event.currentTarget);
            }));
            actionCell.appendChild(smallButton("删除", "red", function (event) {
                deleteDraft(message.id, event.currentTarget);
            }));
        } else if (message.sendStatus === "sent") {
            actionCell.appendChild(smallButton("撤回", "yellow-crusta", function (event) {
                withdrawMessage(message.id, event.currentTarget);
            }));
        } else {
            actionCell.textContent = "—";
        }
        row.appendChild(actionCell);
        return row;
    }

    function renderPager(data) {
        if (!pageInfo) return;
        var total = Number(data.total || 0);
        var page = Number(data.page || 1);
        var pages = Number(data.pages || 0);
        currentPage = page;
        currentPages = pages;
        pageInfo.textContent = total === 0
                ? "共 0 条"
                : "第 " + page + " / " + Math.max(pages, 1) + " 页，共 " + total + " 条";
        if (prevButton) prevButton.disabled = loading || page <= 1;
        if (nextButton) nextButton.disabled = loading || page >= pages;
    }

    async function loadOutbox() {
        if (loading) return;
        loading = true;
        refreshButton.disabled = true;
        outboxStatus.textContent = "正在加载发件记录……";
        outboxBody.textContent = "";

        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var result = await window.GHApi.get("/api/messages", {
                action: "listOutbox",
                sendStatus: statusSelect ? statusSelect.value : "",
                sortBy: sortSelect ? sortSelect.value : "createdAt",
                sortDir: dirSelect ? dirSelect.value : "desc",
                page: currentPage,
                pageSize: pageSizeSelect ? pageSizeSelect.value : 10
            });
            var data = result.data || {};
            var list = data.list || [];
            lastRows = list;
            lastTotal = Number(data.total || 0);

            if (list.length === 0) {
                outboxBody.appendChild(emptyRow("当前条件下没有发件记录。"));
                outboxStatus.textContent = "共 0 条发件记录。";
            } else {
                var drafts = 0;
                list.forEach(function (message) {
                    if (message.sendStatus === "draft") drafts++;
                    outboxBody.appendChild(buildRow(message));
                });
                outboxStatus.textContent = "本页 " + list.length + " 条，其中草稿 " + drafts + " 条。";
            }
            renderPager(data);
        } catch (error) {
            outboxBody.appendChild(emptyRow("加载失败。"));
            outboxStatus.textContent = error.status === 403
                    ? "您没有权限查看发件记录。"
                    : "加载失败：" + error.message;
            if (pageInfo) pageInfo.textContent = "—";
        } finally {
            loading = false;
            refreshButton.disabled = false;
            if (prevButton) prevButton.disabled = currentPage <= 1;
            if (nextButton) nextButton.disabled = currentPages === 0 || currentPage >= currentPages;
        }
    }

    async function saveDraft() {
        var title = titleInput.value.trim();
        var messageType = typeSelect.value;
        var content = contentInput.value.trim();
        var recipientId = recipientInput.value.trim();

        if (title === "") {
            formStatus.textContent = "请填写消息标题。";
            titleInput.focus();
            return;
        }
        if (!/^\d+$/.test(recipientId) || Number(recipientId) <= 0) {
            formStatus.textContent = "请填写有效的接收人ID。";
            recipientInput.focus();
            return;
        }

        saveButton.disabled = true;
        formStatus.textContent = editingId > 0 ? "正在保存修改……" : "正在保存草稿……";

        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var payload = {
                title: title,
                messageType: messageType,
                recipientId: recipientId,
                content: content
            };
            var result;
            if (editingId > 0) {
                payload.action = "updateDraft";
                payload.id = editingId;
                result = await window.GHApi.post("/api/messages", payload);
            } else {
                payload.action = "createDraft";
                result = await window.GHApi.post("/api/messages", payload);
            }
            var data = result.data || {};
            var savedId = editingId > 0 ? editingId : data.id;
            var savedText = editingId > 0
                    ? "草稿 " + savedId + " 已修改。"
                    : "草稿已保存，编号 " + savedId + "。";
            editingId = 0;
            formStatus.textContent = savedText;
            outboxStatus.textContent = savedText;
            titleInput.value = "";
            contentInput.value = "";
            currentPage = 1;
            if (window.jQuery) {
                window.jQuery("#message_form_modal").modal("hide");
            }
            await loadOutbox();
        } catch (error) {
            formStatus.textContent = error.status === 403
                    ? "您没有权限编辑这条草稿。"
                    : (error.status === 409 ? "已发送的消息不能再修改。"
                            : "保存失败：" + error.message);
        } finally {
            saveButton.disabled = false;
        }
    }

    function exportCsv() {
        var url = (window.GH_CONTEXT_PATH || "") + "/api/messages"
                + "?action=exportOutbox"
                + "&sendStatus=" + encodeURIComponent(statusSelect ? statusSelect.value : "")
                + "&sortBy=" + encodeURIComponent(sortSelect ? sortSelect.value : "createdAt")
                + "&sortDir=" + encodeURIComponent(dirSelect ? dirSelect.value : "desc");
        // 浏览器直接下载，不经过 GHApi。
        window.location.href = url;
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
                .replace(/&/g, "&amp;")
                .replace(/</g, "&lt;")
                .replace(/>/g, "&gt;")
                .replace(/"/g, "&quot;");
    }

    async function printList() {
        // 打印覆盖全部筛选结果：先取报表数据，失败则不打开打印窗口。
        if (printButton) printButton.disabled = true;
        outboxStatus.textContent = "正在准备打印数据……";
        var reportRows;
        var reportTotal;
        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var result = await window.GHApi.get("/api/messages", {
                action: "reportOutbox",
                sendStatus: statusSelect ? statusSelect.value : "",
                sortBy: sortSelect ? sortSelect.value : "createdAt",
                sortDir: dirSelect ? dirSelect.value : "desc"
            });
            var data = result.data || {};
            reportRows = data.list || [];
            reportTotal = Number(data.total == null ? reportRows.length : data.total);
        } catch (error) {
            outboxStatus.textContent = "打印数据获取失败：" + error.message;
            if (printButton) printButton.disabled = false;
            return;
        }
        if (printButton) printButton.disabled = false;
        outboxStatus.textContent = "打印数据共 " + reportTotal + " 条。";

        var now = new Date();
        var pad = function (value) {
            return value < 10 ? "0" + value : String(value);
        };
        var timeText = now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate())
                + " " + pad(now.getHours()) + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds());
        var statusTextValue = statusSelect && statusSelect.value
                ? (statusSelect.value === "draft" ? "草稿" : "已发送")
                : "全部";

        var rows = reportRows.map(function (message) {
            return "<tr>"
                    + "<td>" + escapeHtml(message.id) + "</td>"
                    + "<td>" + escapeHtml(message.title) + "</td>"
                    + "<td>" + escapeHtml(typeLabel(message.messageType)) + "</td>"
                    + "<td>" + escapeHtml(message.recipientId) + "</td>"
                    + "<td>" + escapeHtml(statusLabel(message.sendStatus)) + "</td>"
                    + "<td>" + escapeHtml(message.sentAt || "未发送") + "</td>"
                    + "</tr>";
        }).join("");

        var html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<title>个人消息 - 发件记录</title>"
                + "<style>"
                + "body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;color:#000;}"
                + "h2{margin:0 0 8px;font-size:20px;}"
                + ".meta{font-size:12px;color:#333;margin-bottom:6px;}"
                + "table{border-collapse:collapse;width:100%;font-size:13px;}"
                + "th,td{border:1px solid #444;padding:6px 8px;text-align:left;}"
                + "th{background:#eee;}"
                + "</style></head><body>"
                + "<h2>农业大棚系统 - 发件记录</h2>"
                + "<div class=\"meta\">状态筛选：" + escapeHtml(statusTextValue) + "</div>"
                + "<div class=\"meta\">打印时间：" + escapeHtml(timeText)
                + "　实际条数：" + reportTotal + " 条（全部筛选结果，非当前页）</div>"
                + "<table><thead><tr>"
                + "<th>编号</th><th>标题</th><th>类型</th><th>接收人ID</th><th>状态</th><th>发送时间</th>"
                + "</tr></thead><tbody>"
                + (rows === "" ? "<tr><td colspan=\"6\">当前条件下没有记录</td></tr>" : rows)
                + "</tbody></table>"
                + "</body></html>";

        var printWindow = window.open("", "_blank", "width=980,height=720");
        if (!printWindow) {
            outboxStatus.textContent = "浏览器拦截了打印窗口，请允许弹出窗口后重试。";
            return;
        }
        printWindow.document.open();
        printWindow.document.write(html);
        printWindow.document.close();
        printWindow.focus();
        printWindow.print();
    }

    if (newDraftButton) {
        newDraftButton.addEventListener("click", function () {
            editingId = 0;
            titleInput.value = "";
            recipientInput.value = "";
            contentInput.value = "";
            formStatus.textContent = "";
            if (modalTitle) {
                modalTitle.textContent = "新建消息草稿";
            }
            saveButton.textContent = "保存草稿";
            if (window.jQuery) {
                window.jQuery("#message_form_modal").modal("show");
            }
            titleInput.focus();
        });
    }

    if (exportButton) {
        exportButton.addEventListener("click", exportCsv);
    }

    if (printButton) {
        printButton.addEventListener("click", printList);
    }

    saveButton.addEventListener("click", saveDraft);
    refreshButton.addEventListener("click", function () {
        currentPage = 1;
        loadOutbox();
    });

    if (statusSelect) {
        statusSelect.addEventListener("change", function () {
            currentPage = 1;
            loadOutbox();
        });
    }

    [sortSelect, dirSelect, pageSizeSelect].forEach(function (select) {
        if (select) {
            select.addEventListener("change", function () {
                currentPage = 1;
                loadOutbox();
            });
        }
    });

    if (prevButton) {
        prevButton.addEventListener("click", function () {
            if (currentPage > 1) {
                currentPage--;
                loadOutbox();
            }
        });
    }

    if (nextButton) {
        nextButton.addEventListener("click", function () {
            if (currentPages > 0 && currentPage < currentPages) {
                currentPage++;
                loadOutbox();
            }
        });
    }

    loadOutbox();
})();
