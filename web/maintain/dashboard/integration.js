/* 集成驾驶舱：系统管理员专用，聚合已接通真实来源，未接通明确标记未接入。 */
(function () {
    "use strict";

    var REFRESH_MS = 30000;

    var startInput = document.getElementById("dash_start");
    var endInput = document.getElementById("dash_end");
    var refreshButton = document.getElementById("dash_refresh");
    var updatedText = document.getElementById("dash_updated");
    var rangeText = document.getElementById("dash_range");
    var statusText = document.getElementById("dash_status");
    var loginTrendBox = document.getElementById("login_trend");
    var moduleChart = document.getElementById("module_chart");
    var sourceBody = document.getElementById("source_body");

    var timer = null;
    var loading = false;
    var applied = { startDate: "", endDate: "" };

    function pad(text) { return String(text).padStart(2, "0"); }

    function today() {
        var now = new Date();
        return now.getFullYear() + "-" + pad(now.getMonth() + 1) + "-" + pad(now.getDate());
    }

    function daysAgo(count) {
        var date = new Date();
        date.setDate(date.getDate() - count);
        return date.getFullYear() + "-" + pad(date.getMonth() + 1) + "-" + pad(date.getDate());
    }

    startInput.value = daysAgo(6);
    endInput.value = today();

    function escapeHtml(text) {
        return String(text == null ? "" : text)
                .replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function setNumber(id, value) {
        document.getElementById(id).textContent = value == null ? "—" : value;
    }

    function renderTrend(trend) {
        if (!trend || !trend.length) {
            loginTrendBox.innerHTML = "<p class=\"muted\">当前条件下暂无登录数据</p>";
            return;
        }
        var max = 1;
        trend.forEach(function (item) {
            max = Math.max(max, Number(item.success || 0) + Number(item.failure || 0));
        });
        loginTrendBox.innerHTML = "<div class=\"dash-note\">登录趋势（绿=成功，红=失败）</div>"
                + trend.map(function (item) {
                    var successWidth = Math.round(Number(item.success || 0) * 100 / max);
                    var failureWidth = Math.round(Number(item.failure || 0) * 100 / max);
                    return "<div class=\"dash-bar-wrap\"><div class=\"dash-note\">" + escapeHtml(item.date)
                            + "　" + item.success + " / " + item.failure + "</div>"
                            + "<div class=\"dash-bar\"><span style=\"width:" + successWidth + "%\"></span></div>"
                            + "<div class=\"dash-bar fail\" style=\"margin-top:2px;\"><span style=\"width:" + failureWidth + "%\"></span></div></div>";
                }).join("");
    }

    function renderModules(byModule) {
        if (!byModule || !byModule.length) {
            moduleChart.innerHTML = "<p class=\"muted\">当前条件下暂无操作记录</p>";
            return;
        }
        var max = 1;
        byModule.forEach(function (item) { max = Math.max(max, Number(item.count || 0)); });
        moduleChart.innerHTML = "<div class=\"dash-note\">操作模块分布</div>"
                + byModule.map(function (item) {
                    var width = Math.round(Number(item.count || 0) * 100 / max);
                    return "<div class=\"dash-bar-wrap\"><div class=\"dash-note\">"
                            + escapeHtml(item.module || "（未填模块）") + "　" + item.count + " 条</div>"
                            + "<div class=\"dash-bar\"><span style=\"width:" + width + "%\"></span></div></div>";
                }).join("");
    }

    function renderSources(sources) {
        if (!sources || !sources.length) {
            sourceBody.innerHTML = "<tr><td colspan=\"4\" class=\"muted\">没有来源信息</td></tr>";
            return;
        }
        sourceBody.innerHTML = sources.map(function (item) {
            var connected = item.status === "connected";
            return "<tr><td>" + escapeHtml(item.name) + "</td>"
                    + "<td class=\"" + (connected ? "status-connected" : "status-pending") + "\">"
                    + (connected ? "已接通" : "未接入") + "</td>"
                    + "<td>" + escapeHtml(item.owner || "肖泽亮") + "</td>"
                    + "<td>" + escapeHtml(item.note || "") + "</td></tr>";
        }).join("");
    }

    async function load() {
        if (loading) return;
        loading = true;
        refreshButton.disabled = true;
        try {
            var result = await window.GHApi.get("/api/dashboard", {
                action: "integration",
                startDate: applied.startDate,
                endDate: applied.endDate
            });
            var data = result.data || {};
            var login = data.login || {};
            var operation = data.operation || {};
            var accounts = data.accounts || {};
            var messages = data.messageSystem || {};

            setNumber("card_login_success", login.success);
            setNumber("card_login_failure", login.failure);
            setNumber("card_active_users", login.activeUsers);
            setNumber("card_op_total", operation.total);
            setNumber("card_account_total", accounts.total);
            document.getElementById("card_account_detail").textContent =
                    "启用 " + (accounts.active == null ? "—" : accounts.active)
                    + " / 停用 " + (accounts.disabled == null ? "—" : accounts.disabled);
            setNumber("card_message_total", messages.total);
            document.getElementById("card_message_detail").textContent =
                    "今日新增 " + (messages.today == null ? "—" : messages.today)
                    + " / 草稿 " + (messages.draft == null ? "—" : messages.draft)
                    + " / 已撤回 " + (messages.withdrawn == null ? "—" : messages.withdrawn);
            document.getElementById("card_op_detail").textContent =
                    "成功 " + (operation.success == null ? "—" : operation.success)
                    + " / 失败 " + (operation.failed == null ? "—" : operation.failed)
                    + " / 拒绝 " + (operation.denied == null ? "—" : operation.denied);

            renderTrend(login.trend);
            renderModules(operation.byModule);
            renderSources(data.sources);
            if (data.range && data.range.label) {
                rangeText.textContent = "范围：" + data.range.label
                        + "；未接入业务按“未接入”显示，不填固定0。";
            }
            updatedText.textContent = "上次成功：" + (data.updatedAt || "");
            statusText.textContent = "读取正常（每30秒刷新已应用条件）";
        } catch (error) {
            statusText.textContent = "读取失败：" + error.message
                    + (updatedText.textContent ? "（保留上次数据，" + updatedText.textContent + "）" : "");
        } finally {
            loading = false;
            refreshButton.disabled = false;
        }
    }

    function applyAndLoad() {
        applied.startDate = startInput.value;
        applied.endDate = endInput.value;
        if (!applied.startDate || !applied.endDate) {
            statusText.textContent = "请选择起止日期后再刷新。";
            return;
        }
        load();
    }

    startInput.addEventListener("change", applyAndLoad);
    endInput.addEventListener("change", applyAndLoad);
    refreshButton.addEventListener("click", applyAndLoad);

    applied.startDate = startInput.value;
    applied.endDate = endInput.value;
    load();
    timer = window.setInterval(function () {
        if (document.hidden) return;
        load();
    }, REFRESH_MS);
    window.addEventListener("beforeunload", function () {
        if (timer) window.clearInterval(timer);
    });
})();
