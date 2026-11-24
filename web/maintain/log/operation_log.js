/* 操作日志页面：系统管理员看全局，其他角色只看本人操作；只读，无新增/编辑/删除。 */
(function () {
    "use strict";

    var statTotal = document.getElementById("stat_total");
    var statSuccess = document.getElementById("stat_success");
    var statFailed = document.getElementById("stat_failed");
    var statDenied = document.getElementById("stat_denied");
    var moduleChart = document.getElementById("module_chart");
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
    var startInput = document.getElementById("filter_start");
    var endInput = document.getElementById("filter_end");
    var moduleSelect = document.getElementById("filter_module");
    var resultSelect = document.getElementById("filter_result");
    var keywordInput = document.getElementById("filter_keyword");

    var isAdmin = String(window.GH_ROLE_ID || "") === "1";
    var applied = emptyFilters();
    var currentPage = 1;
    var currentPages = 0;
    var currentTotal = 0;
    var currentRows = [];
    var moduleOptionsLoaded = false;

    if (!isAdmin && keywordInput) {
        keywordInput.placeholder = "仅按本人操作筛选";
    }

    function emptyFilters() {
        return { keyword: "", startDate: "", endDate: "", module: "", result: "" };
    }

    function readFilters() {
        return {
            keyword: keywordInput.value.trim(),
            startDate: startInput.value,
            endDate: endInput.value,
            module: moduleSelect.value,
            result: resultSelect.value
        };
    }

    function params() {
        return {
            keyword: applied.keyword,
            startDate: applied.startDate,
            endDate: applied.endDate,
            module: applied.module,
            result: applied.result,
            size: sizeSelect.value,
            page: currentPage,
            sortBy: "operateTime",
            sortDir: "desc"
        };
    }

    function escapeHtml(text) {
        return String(text == null ? "" : text)
                .replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function resultLabel(value) {
        if (value === "success") return "成功";
        if (value === "denied") return "拒绝";
        return "失败";
    }

    function roleLabel(value) {
        if (String(value) === "1") return "系统管理员";
        if (String(value) === "2") return "普通管理员";
        if (String(value) === "3") return "普通用户";
        return "—";
    }

    function reasonLabel(value) {
        switch (value) {
            case "no_permission": return "无权限";
            case "validation_failed": return "校验未通过";
            case "conflict": return "状态冲突";
            case "db_error": return "数据库异常";
            default: return value || "—";
        }
    }

    async function loadStats() {
        var result = await window.GHApi.get("/api/logs", Object.assign({ action: "opStats" }, params()));
        var data = result.data || {};
        statTotal.textContent = data.total == null ? "—" : data.total;
        statSuccess.textContent = data.success == null ? "—" : data.success;
        statFailed.textContent = data.failed == null ? "—" : data.failed;
        statDenied.textContent = data.denied == null ? "—" : data.denied;

        var byModule = data.byModule || [];
        fillModuleOptions(byModule);
        if (!byModule.length) {
            moduleChart.innerHTML = "<p class=\"muted\">当前条件下暂无操作记录</p>";
            return;
        }
        var max = 1;
        byModule.forEach(function (item) { max = Math.max(max, Number(item.count || 0)); });
        moduleChart.innerHTML = byModule.map(function (item) {
            var width = Math.round(Number(item.count || 0) * 100 / max);
            return "<div class=\"op-bar-wrap\"><div class=\"muted\">"
                    + escapeHtml(item.module || "（未填模块）") + "　" + item.count + " 条</div>"
                    + "<div class=\"op-bar\"><span style=\"width:" + width + "%\"></span></div></div>";
        }).join("");
    }

    function fillModuleOptions(byModule) {
        if (moduleOptionsLoaded) return;
        moduleOptionsLoaded = true;
        byModule.forEach(function (item) {
            if (!item.module) return;
            var option = document.createElement("option");
            option.value = item.module;
            option.textContent = item.module;
            moduleSelect.appendChild(option);
        });
    }

    async function loadList() {
        var result = await window.GHApi.get("/api/logs", Object.assign({ action: "opList" }, params()));
        var data = result.data || {};
        currentRows = data.list || [];
        currentTotal = Number(data.total || 0);
        currentPages = Number(data.pages || 0);
        rowCount.textContent = "共 " + currentTotal + " 条";
        statusText.textContent = currentTotal === 0
                ? "当前条件下暂无操作记录。"
                : "已按当前条件显示第 " + currentPage + " 页，共 " + currentTotal + " 条。";
        renderRows();
        renderPager();
    }

    function renderRows() {
        if (!currentRows.length) {
            body.innerHTML = "<tr><td colspan=\"8\" class=\"text-center muted\">当前条件下暂无操作记录</td></tr>";
            return;
        }
        body.innerHTML = currentRows.map(function (row, index) {
            return "<tr>"
                    + "<td>" + escapeHtml(row.operateTime) + "</td>"
                    + "<td>" + escapeHtml(row.operatorName || "—") + "</td>"
                    + "<td>" + escapeHtml(roleLabel(row.roleId)) + "</td>"
                    + "<td>" + escapeHtml(row.module || "—") + "</td>"
                    + "<td>" + escapeHtml(row.action || "—") + "</td>"
                    + "<td>" + escapeHtml(row.targetName || row.targetId || "—") + "</td>"
                    + "<td>" + escapeHtml(resultLabel(row.result)) + "</td>"
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
        pageInfo.textContent = "第 " + (currentPages === 0 ? 0 : currentPage) + " 页 / 共 " + currentPages
                + " 页，共 " + currentTotal + " 条";
        prevButton.disabled = currentPage <= 1;
        nextButton.disabled = currentPages === 0 || currentPage >= currentPages;
    }

    function showDetail(row) {
        if (!row) return;
        document.getElementById("detail_id").textContent = row.id;
        document.getElementById("detail_time").textContent = row.operateTime || "";
        document.getElementById("detail_operator").textContent =
                (row.operatorName || "—") + (row.operatorId == null ? "" : "（ID " + row.operatorId + "）");
        document.getElementById("detail_role").textContent = roleLabel(row.roleId);
        document.getElementById("detail_action").textContent = (row.module || "") + " / " + (row.action || "");
        document.getElementById("detail_target").textContent = row.targetId || "—";
        document.getElementById("detail_target_name").textContent = row.targetName || "—";
        document.getElementById("detail_result").textContent = resultLabel(row.result);
        document.getElementById("detail_reason").textContent = reasonLabel(row.reasonCode);
        document.getElementById("detail_summary").textContent = row.summary || "—";
        document.getElementById("detail_trace").textContent = row.traceId || "—";
        window.jQuery("#log_detail_modal").modal("show");
    }

    function buildQuery(extra) {
        var values = Object.assign({}, params(), extra || {});
        return Object.keys(values).map(function (key) {
            return encodeURIComponent(key) + "=" + encodeURIComponent(values[key] == null ? "" : values[key]);
        }).join("&");
    }

    function exportCsv() {
        window.location.href = (window.GH_CONTEXT_PATH || "") + "/api/logs?action=opExport&" + buildQuery({ page: 1 });
    }

    async function printReport() {
        printButton.disabled = true;
        statusText.textContent = "正在准备打印数据……";
        var report;
        try {
            report = await window.GHApi.get("/api/logs", Object.assign({ action: "opReport" }, params()));
        } catch (error) {
            statusText.textContent = "打印数据获取失败：" + error.message;
            printButton.disabled = false;
            return;
        }
        printButton.disabled = false;
        var rows = ((report.data || {}).list) || [];
        var total = Number(((report.data || {}).total) || rows.length);
        var html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>操作日志</title>"
                + "<style>body{font-family:'Microsoft YaHei',Arial,sans-serif;margin:24px;}"
                + "table{border-collapse:collapse;width:100%;font-size:12px;}"
                + "th,td{border:1px solid #444;padding:6px 8px;text-align:left;}th{background:#e9f5ee;}"
                + ".meta{font-size:12px;color:#333;margin-bottom:6px;}</style></head><body>"
                + "<h2>农业大棚系统 - 操作日志</h2>"
                + "<div class=\"meta\">筛选条件：" + escapeHtml(describeFilters()) + "</div>"
                + "<div class=\"meta\">打印时间：" + new Date().toLocaleString()
                + "　实际条数：" + total + " 条（全部筛选结果，非当前页）</div>"
                + "<table><thead><tr><th>时间</th><th>操作人</th><th>角色</th><th>模块</th><th>动作</th>"
                + "<th>目标</th><th>结果</th><th>摘要</th></tr></thead><tbody>"
                + (rows.length ? rows.map(function (row) {
                    return "<tr><td>" + escapeHtml(row.operateTime) + "</td><td>" + escapeHtml(row.operatorName) + "</td>"
                            + "<td>" + escapeHtml(roleLabel(row.roleId)) + "</td><td>" + escapeHtml(row.module) + "</td>"
                            + "<td>" + escapeHtml(row.action) + "</td><td>" + escapeHtml(row.targetName || row.targetId) + "</td>"
                            + "<td>" + escapeHtml(resultLabel(row.result)) + "</td><td>" + escapeHtml(row.summary) + "</td></tr>";
                }).join("") : "<tr><td colspan=\"8\">当前条件下没有操作记录</td></tr>")
                + "</tbody></table></body></html>";

        var printWindow = window.open("", "_blank", "width=1100,height=760");
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
        if (applied.module) parts.push("模块：" + applied.module);
        if (applied.result) parts.push("结果：" + resultLabel(applied.result));
        if (applied.keyword) parts.push("关键词：" + applied.keyword);
        if (!isAdmin) parts.push("范围：本人操作");
        return parts.length ? parts.join("；") : (isAdmin ? "全部操作" : "本人操作");
    }

    async function loadAll() {
        statusText.textContent = "加载中…";
        try {
            await loadStats();
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
        startInput.value = "";
        endInput.value = "";
        moduleSelect.value = "";
        resultSelect.value = "";
        keywordInput.value = "";
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
