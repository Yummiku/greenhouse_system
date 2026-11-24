/* 登录日志页面：查询、统计、趋势、详情、导出、打印。只读，不提供新增/编辑/删除。 */
(function () {
    "use strict";

    var statTotal = document.getElementById("stat_total");
    var statSuccess = document.getElementById("stat_success");
    var statFailure = document.getElementById("stat_failure");
    var statActive = document.getElementById("stat_active");
    var trendBox = document.getElementById("login_trend");
    var rangeLabel = document.getElementById("range_label");
    var statusText = document.getElementById("log_status");
    var rowCount = document.getElementById("row_count");
    var body = document.getElementById("log_body");
    var pageInfo = document.getElementById("page_info");
    var prevButton = document.getElementById("page_prev");
    var nextButton = document.getElementById("page_next");
    var sizeSelect = document.getElementById("page_size");
    var searchButton = document.getElementById("filter_search");
    var resetButton = document.getElementById("filter_reset");
    var exportButton = document.getElementById("log_export");
    var printButton = document.getElementById("log_print");
    var refreshButton = document.getElementById("log_refresh");
    var keywordInput = document.getElementById("filter_keyword");
    var startInput = document.getElementById("filter_start");
    var endInput = document.getElementById("filter_end");
    var resultSelect = document.getElementById("filter_result");
    var reasonSelect = document.getElementById("filter_reason");
    var sourceSelect = document.getElementById("filter_source");
    var keywordBox = document.getElementById("keyword_box");

    var isAdmin = String(window.GH_ROLE_ID || "") === "1";
    var applied = emptyFilters();
    var currentPage = 1;
    var currentPages = 0;
    var currentTotal = 0;
    var currentRows = [];

    if (!isAdmin && keywordBox) {
        keywordBox.style.display = "none";
    }

    function emptyFilters() {
        return { keyword: "", startDate: "", endDate: "", result: "", reason: "", source: "" };
    }

    function readFilters() {
        return {
            keyword: keywordInput ? keywordInput.value.trim() : "",
            startDate: startInput ? startInput.value : "",
            endDate: endInput ? endInput.value : "",
            result: resultSelect ? resultSelect.value : "",
            reason: reasonSelect ? reasonSelect.value : "",
            source: sourceSelect ? sourceSelect.value : ""
        };
    }

    function params() {
        return {
            keyword: applied.keyword,
            startDate: applied.startDate,
            endDate: applied.endDate,
            result: applied.result,
            reason: applied.reason,
            source: applied.source,
            size: sizeSelect.value,
            page: currentPage,
            sortBy: "eventTime",
            sortDir: "desc"
        };
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
                .replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function resultLabel(value) {
        return value === "success" ? "成功" : "失败";
    }

    function reasonLabel(value) {
        switch (value) {
            case "credential_error": return "凭据错误";
            case "account_disabled": return "账号停用";
            case "invalid_input": return "输入不完整";
            case "auth_service_error": return "认证服务失败";
            default: return "";
        }
    }

    function sourceLabel(value) {
        if (value === "wx") return "微信";
        if (value === "mini") return "小程序";
        return "PC";
    }

    async function loadStats() {
        var result = await window.GHApi.get("/api/logs", Object.assign({ action: "loginStats" }, params()));
        var data = result.data || {};
        statTotal.textContent = data.total == null ? "—" : data.total;
        statSuccess.textContent = data.success == null ? "—" : data.success;
        statFailure.textContent = data.failure == null ? "—" : data.failure;
        statActive.textContent = data.activeUsers == null ? "—" : data.activeUsers;
    }

    async function loadTrend() {
        var result = await window.GHApi.get("/api/logs", Object.assign({ action: "loginTrend" }, params()));
        var trend = (result.data || {}).trend || [];
        if (!trend.length) {
            trendBox.innerHTML = "<p class=\"muted\">当前条件下暂无数据</p>";
            return;
        }
        var max = 1;
        trend.forEach(function (item) {
            max = Math.max(max, Number(item.success || 0) + Number(item.failure || 0));
        });
        var html = trend.map(function (item) {
            var total = Number(item.success || 0) + Number(item.failure || 0);
            var successWidth = Math.round(Number(item.success || 0) * 100 / max);
            var failureWidth = Math.round(Number(item.failure || 0) * 100 / max);
            return "<div class=\"log-bar-wrap\">"
                    + "<div class=\"muted\">" + escapeHtml(item.date)
                    + "　成功 " + item.success + " / 失败 " + item.failure
                    + "（共 " + total + "）</div>"
                    + "<div class=\"log-bar\"><span style=\"width:" + successWidth + "%\"></span></div>"
                    + "<div class=\"log-bar fail\" style=\"margin-top:2px;\"><span style=\"width:" + failureWidth + "%\"></span></div>"
                    + "</div>";
        }).join("");
        trendBox.innerHTML = html;
    }

    async function loadList() {
        var result = await window.GHApi.get("/api/logs", Object.assign({ action: "loginList" }, params()));
        var data = result.data || {};
        currentRows = data.list || [];
        currentTotal = Number(data.total || 0);
        currentPages = Number(data.pages || 0);
        rowCount.textContent = "共 " + currentTotal + " 条";
        statusText.textContent = currentTotal === 0
                ? "当前条件下暂无登录事件。"
                : "已按当前条件显示第 " + currentPage + " 页，共 " + currentTotal + " 条。";
        renderRows();
        renderPager();
    }

    function renderRows() {
        if (!currentRows.length) {
            body.innerHTML = "<tr><td colspan=\"8\" class=\"text-center muted\">当前条件下暂无登录事件</td></tr>";
            return;
        }
        body.innerHTML = currentRows.map(function (row, index) {
            return "<tr>"
                    + "<td>" + escapeHtml(row.eventTime) + "</td>"
                    + "<td>" + escapeHtml(row.usernameAttempt || "—") + "</td>"
                    + "<td>" + escapeHtml(row.userId == null ? "—" : row.userId) + "</td>"
                    + "<td>" + escapeHtml(resultLabel(row.result)) + "</td>"
                    + "<td>" + escapeHtml(reasonLabel(row.reasonCode) || "—") + "</td>"
                    + "<td>" + escapeHtml(sourceLabel(row.source)) + "</td>"
                    + "<td>" + escapeHtml(row.ip || "—") + "</td>"
                    + "<td class=\"text-right\"><button type=\"button\" class=\"btn btn-default btn-sm\" data-row=\"" + index + "\">详情</button></td>"
                    + "</tr>";
        }).join("");
        Array.prototype.forEach.call(body.querySelectorAll("[data-row]"), function (button) {
            button.addEventListener("click", function () {
                showDetail(currentRows[Number(button.getAttribute("data-row"))]);
            });
        });
    }

    function renderPager() {
        var page = currentPage;
        pageInfo.textContent = "第 " + (currentPages === 0 ? 0 : page) + " 页 / 共 " + currentPages
                + " 页，共 " + currentTotal + " 条";
        prevButton.disabled = page <= 1;
        nextButton.disabled = currentPages === 0 || page >= currentPages;
    }

    function showDetail(row) {
        if (!row) return;
        document.getElementById("detail_id").textContent = row.id;
        document.getElementById("detail_time").textContent = row.eventTime || "";
        document.getElementById("detail_username").textContent = row.usernameAttempt || "（未记录）";
        document.getElementById("detail_user").textContent = row.userId == null ? "（未识别）" : row.userId;
        document.getElementById("detail_result").textContent = resultLabel(row.result);
        document.getElementById("detail_reason").textContent = reasonLabel(row.reasonCode) || "—";
        document.getElementById("detail_source").textContent = sourceLabel(row.source);
        document.getElementById("detail_ip").textContent = row.ip || "—";
        document.getElementById("detail_client").textContent = row.clientInfo || "—";
        window.jQuery("#log_detail_modal").modal("show");
    }

    function buildQuery(extra) {
        var values = Object.assign({}, params(), extra || {});
        return Object.keys(values).map(function (key) {
            return encodeURIComponent(key) + "=" + encodeURIComponent(values[key] == null ? "" : values[key]);
        }).join("&");
    }

    function exportCsv() {
        window.location.href = (window.GH_CONTEXT_PATH || "") + "/api/logs?action=loginExport&" + buildQuery({ page: 1 });
    }

    async function printReport() {
        printButton.disabled = true;
        statusText.textContent = "正在准备打印数据……";
        var report;
        try {
            report = await window.GHApi.get("/api/logs", Object.assign({ action: "loginReport" }, params()));
        } catch (error) {
            statusText.textContent = "打印数据获取失败：" + error.message;
            printButton.disabled = false;
            return;
        }
        printButton.disabled = false;
        var rows = ((report.data || {}).list) || [];
        var total = Number(((report.data || {}).total) || rows.length);
        var now = new Date();
        var html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>登录日志</title>"
                + "<style>body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;}"
                + "table{border-collapse:collapse;width:100%;font-size:13px;}"
                + "th,td{border:1px solid #444;padding:6px 8px;text-align:left;}th{background:#e9f5ee;}"
                + ".meta{font-size:12px;color:#333;margin-bottom:6px;}</style></head><body>"
                + "<h2>农业大棚系统 - 登录日志</h2>"
                + "<div class=\"meta\">筛选条件：" + escapeHtml(describeFilters()) + "</div>"
                + "<div class=\"meta\">打印时间：" + now.toLocaleString() + "　实际条数：" + total + " 条（全部筛选结果，非当前页）</div>"
                + "<table><thead><tr><th>时间</th><th>尝试账号</th><th>用户ID</th><th>结果</th><th>原因</th><th>来源</th><th>IP</th></tr></thead><tbody>"
                + (rows.length ? rows.map(function (row) {
                    return "<tr><td>" + escapeHtml(row.eventTime) + "</td><td>" + escapeHtml(row.usernameAttempt || "") + "</td>"
                            + "<td>" + escapeHtml(row.userId == null ? "" : row.userId) + "</td><td>" + escapeHtml(resultLabel(row.result)) + "</td>"
                            + "<td>" + escapeHtml(reasonLabel(row.reasonCode) || "") + "</td><td>" + escapeHtml(sourceLabel(row.source)) + "</td>"
                            + "<td>" + escapeHtml(row.ip || "") + "</td></tr>";
                }).join("") : "<tr><td colspan=\"7\">当前条件下没有登录事件</td></tr>")
                + "</tbody></table></body></html>";

        var printWindow = window.open("", "_blank", "width=980,height=720");
        if (!printWindow) {
            statusText.textContent = "浏览器拦截了打印窗口，请允许弹出窗口后重试。";
            return;
        }
        printWindow.document.open();
        printWindow.document.write(html);
        printWindow.document.close();
        printWindow.focus();
        printWindow.print();
    }

    function describeFilters() {
        var parts = [];
        if (applied.startDate) parts.push("起始日期：" + applied.startDate);
        if (applied.endDate) parts.push("结束日期：" + applied.endDate);
        if (applied.result) parts.push("结果：" + resultLabel(applied.result));
        if (applied.reason) parts.push("原因：" + reasonLabel(applied.reason));
        if (applied.source) parts.push("来源：" + sourceLabel(applied.source));
        if (applied.keyword) parts.push("尝试账号：" + applied.keyword);
        return parts.length ? parts.join("；") : "全部登录事件";
    }

    async function loadAll() {
        statusText.textContent = "加载中…";
        try {
            await loadStats();
            await loadTrend();
            await loadList();
            rangeLabel.textContent = describeFilters();
        } catch (error) {
            statusText.textContent = "数据读取失败：" + error.message;
            body.innerHTML = "<tr><td colspan=\"8\" class=\"text-center muted\">读取失败，请稍后重试</td></tr>";
        }
    }

    searchButton.addEventListener("click", function () {
        applied = readFilters();
        currentPage = 1;
        loadAll();
    });

    resetButton.addEventListener("click", function () {
        if (keywordInput) keywordInput.value = "";
        startInput.value = "";
        endInput.value = "";
        resultSelect.value = "";
        reasonSelect.value = "";
        sourceSelect.value = "pc";
        sourceSelect.value = "";
        sizeSelect.value = "10";
        applied = emptyFilters();
        currentPage = 1;
        loadAll();
    });

    sizeSelect.addEventListener("change", function () {
        currentPage = 1;
        loadAll();
    });

    prevButton.addEventListener("click", function () {
        if (currentPage > 1) {
            currentPage--;
            loadList();
        }
    });

    nextButton.addEventListener("click", function () {
        if (currentPages && currentPage < currentPages) {
            currentPage++;
            loadList();
        }
    });

    exportButton.addEventListener("click", exportCsv);
    printButton.addEventListener("click", printReport);
    refreshButton.addEventListener("click", loadAll);

    loadAll();
})();
