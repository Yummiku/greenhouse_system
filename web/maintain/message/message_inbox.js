(function () {
    "use strict";

    var refreshButton = document.getElementById("message_refresh");
    var statusText = document.getElementById("message_status");
    var tableBody = document.getElementById("message_inbox_body");
    var pageInfo = document.getElementById("message_page_info");
    var prevButton = document.getElementById("message_prev");
    var nextButton = document.getElementById("message_next");
    var searchButton = document.getElementById("filter_search");
    var resetButton = document.getElementById("filter_reset");
    var exportButton = document.getElementById("message_export");
    var printButton = document.getElementById("message_print");
    if (!refreshButton || !tableBody || !pageInfo) return;

    var statTotal = document.getElementById("stat_total");
    var statUnread = document.getElementById("stat_unread");
    var statRead = document.getElementById("stat_read");
    var statTypes = document.getElementById("stat_types");
    var statTypeCount = document.getElementById("stat_type_count");
    var typeChart = document.getElementById("inbox_type_chart");

    var keywordInput = document.getElementById("filter_keyword");
    var typeSelect = document.getElementById("filter_type");
    var readSelect = document.getElementById("filter_read");
    var startInput = document.getElementById("filter_start");
    var endInput = document.getElementById("filter_end");
    var sizeSelect = document.getElementById("filter_size");
    var sortSelect = document.getElementById("filter_sort");
    var dirSelect = document.getElementById("filter_dir");

    var currentPage = 1;
    var currentPages = 0;
    var loading = false;
    var lastRows = [];
    var lastTotal = 0;

    var TYPE_LABELS = { notice: "通知", task: "任务", system: "系统" };

    function showFailure(target, message) {
        target.textContent = message;
        window.alert(message);
    }

    function typeLabel(type) {
        return TYPE_LABELS[type] || type || "未分类";
    }

    function filters() {
        return {
            keyword: keywordInput.value.trim(),
            messageType: typeSelect.value,
            readState: readSelect.value,
            startDate: startInput.value,
            endDate: endInput.value
        };
    }

    function emptyRow(text) {
        var row = document.createElement("tr");
        var cell = document.createElement("td");
        cell.colSpan = 6;
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

    function actionButton(label, cssClass, handler) {
        var button = document.createElement("button");
        button.type = "button";
        button.className = "btn btn-xs " + cssClass;
        button.style.marginRight = "4px";
        button.textContent = label;
        button.addEventListener("click", handler);
        return button;
    }

    async function openDetail(id) {
        var errorText = document.getElementById("detail_error");
        errorText.textContent = "";
        try {
            var result = await window.GHApi.get("/api/messages", { action: "detail", id: id });
            var data = result.data || {};
            document.getElementById("detail_title").textContent = data.title || "消息详情";
            document.getElementById("detail_type").textContent = typeLabel(data.messageType);
            document.getElementById("detail_sender").textContent = data.senderId;
            document.getElementById("detail_recipient").textContent = data.recipientId;
            document.getElementById("detail_status").textContent = data.sendStatus === "draft"
                    ? "草稿" : (data.withdrawnAt ? "已撤回" : "已发送");
            document.getElementById("detail_sent_at").textContent = data.sentAt || "未发送";
            document.getElementById("detail_read_at").textContent = data.readAt || "未读";
            document.getElementById("detail_content").textContent = data.content || "（无正文）";
            if (window.jQuery) {
                window.jQuery("#message_detail_modal").modal("show");
            }
        } catch (error) {
            errorText.textContent = error.status === 403
                    ? "您没有权限查看这条消息。"
                    : "详情加载失败：" + error.message;
            window.alert(errorText.textContent);
            if (window.jQuery) {
                window.jQuery("#message_detail_modal").modal("show");
            }
        }
    }

    /** 统计卡数字本地增减，避免整页重新请求导致闪烁。 */
    function adjustStats(totalDelta, unreadDelta, readDelta) {
        var total = parseInt(statTotal.textContent, 10);
        var unread = parseInt(statUnread.textContent, 10);
        var read = parseInt(statRead.textContent, 10);
        if (!isNaN(total) && totalDelta) statTotal.textContent = Math.max(0, total + totalDelta);
        if (!isNaN(unread) && unreadDelta) statUnread.textContent = Math.max(0, unread + unreadDelta);
        if (!isNaN(read) && readDelta) statRead.textContent = Math.max(0, read + readDelta);
    }

    function refreshPageInfo() {
        pageInfo.textContent = lastTotal === 0
                ? "共 0 条"
                : "第 " + currentPage + " / " + Math.max(currentPages, 1) + " 页，共 " + lastTotal + " 条";
    }

    /** 已读状态只改本行，不重新拉列表和统计。 */
    function applyReadLocally(id) {
        lastRows.forEach(function (message) {
            if (message.id === id) message.read = true;
        });

        var row = tableBody.querySelector('tr[data-id="' + id + '"]');
        if (row) {
            row.children[4].textContent = "已读";
            var actionCell = row.children[5];
            actionCell.textContent = "";
            actionCell.appendChild(actionButton("详情", "default", function () {
                openDetail(id);
            }));
            actionCell.appendChild(actionButton("删除", "red", function (event) {
                deleteReceived(id, event.currentTarget);
            }));
        }
        adjustStats(0, -1, 1);
    }

    async function markRead(id, button) {
        // 先本地置为已读，请求失败再回滚，界面不闪。
        button.disabled = true;
        try {
            await window.GHApi.post("/api/messages", { action: "markRead", id: id });
            applyReadLocally(id);
            statusText.textContent = "已标为已读。";
        } catch (error) {
            button.disabled = false;
            showFailure(statusText, "标记已读失败：" + (error.message || "请稍后重试。"));
        }
    }

    /** 删除只影响本人：从当前表格移除该行并调整统计，不整页刷新。 */
    function applyDeleteLocally(id) {
        var message = null;
        lastRows.forEach(function (item) {
            if (item.id === id) message = item;
        });
        lastRows = lastRows.filter(function (item) {
            return item.id !== id;
        });

        var row = tableBody.querySelector('tr[data-id="' + id + '"]');
        if (row && row.parentNode) {
            row.parentNode.removeChild(row);
        }

        lastTotal = Math.max(0, lastTotal - 1);
        var wasRead = message ? message.read : true;
        adjustStats(-1, wasRead ? 0 : -1, wasRead ? -1 : 0);
        refreshPageInfo();
    }

    async function deleteReceived(id, button) {
        if (!window.confirm("确认删除这条消息？")) {
            return;
        }
        button.disabled = true;
        try {
            await window.GHApi.post("/api/messages", { action: "deleteReceived", id: id });
            applyDeleteLocally(id);
            statusText.textContent = "消息已删除。";
        } catch (error) {
            button.disabled = false;
            showFailure(statusText, "删除失败：" + (error.message || "请稍后重试。"));
        }
    }

    function buildRow(message) {
        var row = document.createElement("tr");
        row.setAttribute("data-id", message.id);
        row.appendChild(cell(message.id));
        row.appendChild(cell(message.title));
        row.appendChild(cell(typeLabel(message.messageType)));
        row.appendChild(cell(message.sentAt || "未发送"));
        row.appendChild(cell(message.read ? "已读" : "未读"));

        var actionCell = document.createElement("td");
        actionCell.style.whiteSpace = "nowrap";
        actionCell.appendChild(actionButton("详情", "default", function () {
            openDetail(message.id);
        }));
        if (!message.read) {
            actionCell.appendChild(actionButton("标为已读", "blue", function (event) {
                markRead(message.id, event.currentTarget);
            }));
        } else {
            actionCell.appendChild(actionButton("删除", "red", function (event) {
                deleteReceived(message.id, event.currentTarget);
            }));
        }
        row.appendChild(actionCell);
        return row;
    }

    function renderPager(data) {
        var total = Number(data.total || 0);
        var page = Number(data.page || 1);
        var pages = Number(data.pages || 0);
        currentPage = page;
        currentPages = pages;
        pageInfo.textContent = total === 0
                ? "共 0 条"
                : "第 " + page + " / " + Math.max(pages, 1) + " 页，共 " + total + " 条";
        prevButton.disabled = loading || page <= 1;
        nextButton.disabled = loading || page >= pages;
    }

    async function loadStats() {
        try {
            var params = filters();
            var result = await window.GHApi.get("/api/messages", {
                action: "stats",
                keyword: params.keyword,
                messageType: params.messageType,
                startDate: params.startDate,
                endDate: params.endDate
            });
            var data = result.data || {};
            statTotal.textContent = data.total == null ? 0 : data.total;
            statUnread.textContent = data.unread == null ? 0 : data.unread;
            statRead.textContent = data.read == null ? 0 : data.read;

            var byType = data.byType || [];
            if (statTypeCount) {
                statTypeCount.textContent = byType.length === 0 ? 0 : byType.length;
            }
            if (typeChart) {
                if (byType.length === 0) {
                    typeChart.innerHTML = "<p class=\"muted\">当前条件下暂无数据</p>";
                } else {
                    var max = 1;
                    byType.forEach(function (item) {
                        max = Math.max(max, Number(item.count || 0));
                    });
                    typeChart.innerHTML = byType.map(function (item) {
                        var width = Math.round(Number(item.count || 0) * 100 / max);
                        return "<div class=\"log-bar-wrap\">"
                                + "<div class=\"muted\">" + typeLabel(item.type)
                                + "　" + item.count + " 条</div>"
                                + "<div class=\"log-bar\"><span style=\"width:" + width + "%\"></span></div>"
                                + "</div>";
                    }).join("");
                }
            }
            if (statTypes) {
                statTypes.textContent = byType.length === 0
                        ? "无数据"
                        : byType.map(function (item) {
                            return typeLabel(item.type) + " " + item.count;
                        }).join("，");
            }
        } catch (error) {
            statTotal.textContent = "—";
            statUnread.textContent = "—";
            statRead.textContent = "—";
            if (statTypeCount) statTypeCount.textContent = "—";
            if (typeChart) typeChart.innerHTML = "<p class=\"muted\">统计加载失败</p>";
        }
    }

    async function loadInbox() {
        if (loading) return;
        loading = true;
        refreshButton.disabled = true;
        prevButton.disabled = true;
        nextButton.disabled = true;
        statusText.textContent = "正在加载收件箱……";
        tableBody.textContent = "";

        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var params = filters();
            var result = await window.GHApi.get("/api/messages", {
                action: "listInbox",
                keyword: params.keyword,
                messageType: params.messageType,
                readState: params.readState,
                startDate: params.startDate,
                endDate: params.endDate,
                sortBy: sortSelect.value,
                sortDir: dirSelect.value,
                page: currentPage,
                pageSize: sizeSelect.value
            });
            var data = result.data || {};
            var list = data.list || [];
            lastRows = list;
            lastTotal = Number(data.total || 0);

            if (list.length === 0) {
                tableBody.appendChild(emptyRow("当前条件下没有消息。"));
                statusText.textContent = "共 0 条消息。";
            } else {
                var unread = 0;
                list.forEach(function (message) {
                    if (!message.read) unread++;
                    tableBody.appendChild(buildRow(message));
                });
                statusText.textContent = "本页 " + list.length + " 条，未读 " + unread + " 条。";
            }
            renderPager(data);
        } catch (error) {
            tableBody.appendChild(emptyRow("加载失败。"));
            statusText.textContent = error.status === 401
                    ? "登录已失效，请重新登录后查看。"
                    : "加载失败：" + error.message;
            pageInfo.textContent = "—";
        } finally {
            loading = false;
            refreshButton.disabled = false;
            prevButton.disabled = currentPage <= 1;
            nextButton.disabled = currentPages === 0 || currentPage >= currentPages;
        }
    }

    async function loadAll() {
        await loadInbox();
        await loadStats();
    }

    function filterSummary() {
        var params = filters();
        var parts = [];
        if (params.keyword) parts.push("关键词：" + params.keyword);
        if (params.messageType) parts.push("类型：" + typeLabel(params.messageType));
        if (params.readState) {
            parts.push("已读状态：" + (params.readState === "unread" ? "未读" : "已读"));
        }
        if (params.startDate) parts.push("起始日期：" + params.startDate);
        if (params.endDate) parts.push("结束日期：" + params.endDate);
        parts.push("排序：" + (sortSelect.value === "sentAt" ? "发送时间" : "创建时间")
                + (dirSelect.value === "desc" ? "降序" : "升序"));
        return parts.join("；");
    }

    function exportCsv() {
        var params = filters();
        var url = (window.GH_CONTEXT_PATH || "") + "/api/messages"
                + "?action=exportInbox"
                + "&keyword=" + encodeURIComponent(params.keyword)
                + "&messageType=" + encodeURIComponent(params.messageType)
                + "&readState=" + encodeURIComponent(params.readState)
                + "&startDate=" + encodeURIComponent(params.startDate)
                + "&endDate=" + encodeURIComponent(params.endDate)
                + "&sortBy=" + encodeURIComponent(sortSelect.value)
                + "&sortDir=" + encodeURIComponent(dirSelect.value);
        // 浏览器直接下载，不能用 GHApi（它把结果当JSON解析）。
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
        // 打印必须覆盖全部筛选结果，而不是当前页；先向后端取报表数据，取不到就不打印。
        var params = filters();
        if (printButton) printButton.disabled = true;
        statusText.textContent = "正在准备打印数据……";
        var reportRows;
        var reportTotal;
        try {
            if (!window.GHApi) {
                throw new Error("公共请求工具未加载，请检查脚本引用顺序");
            }
            var result = await window.GHApi.get("/api/messages", {
                action: "reportInbox",
                keyword: params.keyword,
                messageType: params.messageType,
                readState: params.readState,
                startDate: params.startDate,
                endDate: params.endDate,
                sortBy: sortSelect.value,
                sortDir: dirSelect.value
            });
            var data = result.data || {};
            reportRows = data.list || [];
            reportTotal = Number(data.total == null ? reportRows.length : data.total);
        } catch (error) {
            showFailure(statusText, "打印数据获取失败：" + (error.message || "请稍后重试。"));
            if (printButton) printButton.disabled = false;
            return;
        }
        if (printButton) printButton.disabled = false;
        statusText.textContent = "打印数据共 " + reportTotal + " 条。";

        var now = new Date();
        var pad = function (value) {
            return value < 10 ? "0" + value : String(value);
        };
        var timeText = now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate())
                + " " + pad(now.getHours()) + ":" + pad(now.getMinutes()) + ":" + pad(now.getSeconds());

        var rows = reportRows.map(function (message) {
            return "<tr>"
                    + "<td>" + escapeHtml(message.id) + "</td>"
                    + "<td>" + escapeHtml(message.title) + "</td>"
                    + "<td>" + escapeHtml(typeLabel(message.messageType)) + "</td>"
                    + "<td>" + escapeHtml(message.senderId) + "</td>"
                    + "<td>" + escapeHtml(message.sentAt || "未发送") + "</td>"
                    + "<td>" + escapeHtml(message.read ? "已读" : "未读") + "</td>"
                    + "</tr>";
        }).join("");

        var html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
                + "<title>个人消息 - 我的收件箱</title>"
                + "<style>"
                + "body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;color:#000;}"
                + "h2{margin:0 0 8px;font-size:20px;}"
                + ".meta{font-size:12px;color:#333;margin-bottom:6px;}"
                + "table{border-collapse:collapse;width:100%;font-size:13px;}"
                + "th,td{border:1px solid #444;padding:6px 8px;text-align:left;}"
                + "th{background:#eee;}"
                + "</style></head><body>"
                + "<h2>农业大棚系统 - 我的收件箱</h2>"
                + "<div class=\"meta\">筛选条件：" + escapeHtml(filterSummary()) + "</div>"
                + "<div class=\"meta\">打印时间：" + escapeHtml(timeText)
                + "　实际条数：" + reportTotal + " 条（全部筛选结果，非当前页）</div>"
                + "<table><thead><tr>"
                + "<th>编号</th><th>标题</th><th>类型</th><th>发送人ID</th><th>发送时间</th><th>状态</th>"
                + "</tr></thead><tbody>"
                + (rows === "" ? "<tr><td colspan=\"6\">当前条件下没有消息</td></tr>" : rows)
                + "</tbody></table>"
                + "</body></html>";

        var printWindow = window.open("", "_blank", "width=980,height=720");
        if (!printWindow) {
            showFailure(statusText, "浏览器拦截了打印窗口，请允许弹出窗口后重试。");
            return;
        }
        printWindow.document.open();
        printWindow.document.write(html);
        printWindow.document.close();
        printWindow.focus();
        printWindow.print();
    }

    refreshButton.addEventListener("click", loadAll);

    if (exportButton) {
        exportButton.addEventListener("click", exportCsv);
    }

    if (printButton) {
        printButton.addEventListener("click", printList);
    }

    if (searchButton) {
        searchButton.addEventListener("click", function () {
            currentPage = 1;
            loadAll();
        });
    }

    if (resetButton) {
        resetButton.addEventListener("click", function () {
            keywordInput.value = "";
            typeSelect.value = "";
            readSelect.value = "";
            startInput.value = "";
            endInput.value = "";
            sizeSelect.value = "10";
            sortSelect.value = "sentAt";
            dirSelect.value = "desc";
            currentPage = 1;
            loadAll();
        });
    }

    if (prevButton) {
        prevButton.addEventListener("click", function () {
            if (currentPage > 1) {
                currentPage--;
                loadInbox();
            }
        });
    }

    if (nextButton) {
        nextButton.addEventListener("click", function () {
            if (currentPages > 0 && currentPage < currentPages) {
                currentPage++;
                loadInbox();
            }
        });
    }

    if (sizeSelect) {
        sizeSelect.addEventListener("change", function () {
            currentPage = 1;
            loadInbox();
        });
    }

    loadAll();
})();
