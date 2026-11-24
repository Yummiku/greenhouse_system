/* 个人驾驶舱：登录趋势、操作量与模块分布、本人未读消息；默认最近7天，30秒刷新已应用条件。 */
(function () {
    "use strict";

    var REFRESH_MS = 30000;

    var startInput = document.getElementById("dash_start");
    var endInput = document.getElementById("dash_end");
    var refreshButton = document.getElementById("dash_refresh");
    var updatedText = document.getElementById("dash_updated");
    var rangeText = document.getElementById("dash_range");
    var statusText = document.getElementById("dash_status");
    var loginScope = document.getElementById("login_scope");
    var loginTrendBox = document.getElementById("login_trend");
    var moduleChart = document.getElementById("module_chart");

    var timer = null;
    var loading = false;
    var applied = { startDate: "", endDate: "" };

    function today() {
        var now = new Date();
        var month = String(now.getMonth() + 1).padStart(2, "0");
        var day = String(now.getDate()).padStart(2, "0");
        return now.getFullYear() + "-" + month + "-" + day;
    }

    function daysAgo(count) {
        var date = new Date();
        date.setDate(date.getDate() - count);
        var month = String(date.getMonth() + 1).padStart(2, "0");
        var day = String(date.getDate()).padStart(2, "0");
        return date.getFullYear() + "-" + month + "-" + day;
    }

    startInput.value = daysAgo(6);
    endInput.value = today();

    function escapeHtml(text) {
        return String(text == null ? "" : text)
                .replace(/&/g, "&amp;").replace(/</g, "&lt;")
                .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    function setNumber(id, value) {
        var node = document.getElementById(id);
        node.textContent = value == null ? "—" : value;
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
        loginTrendBox.innerHTML = trend.map(function (item) {
            var successWidth = Math.round(Number(item.success || 0) * 100 / max);
            var failureWidth = Math.round(Number(item.failure || 0) * 100 / max);
            return "<div class=\"dash-bar-wrap\"><div class=\"dash-note\">" + escapeHtml(item.date)
                    + "　成功 " + item.success + " / 失败 " + item.failure + "</div>"
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
        moduleChart.innerHTML = byModule.map(function (item) {
            var width = Math.round(Number(item.count || 0) * 100 / max);
            return "<div class=\"dash-bar-wrap\"><div class=\"dash-note\">"
                    + escapeHtml(item.module || "（未填模块）") + "　" + item.count + " 条</div>"
                    + "<div class=\"dash-bar\"><span style=\"width:" + width + "%\"></span></div></div>";
        }).join("");
    }

    async function load() {
        if (loading) return;
        loading = true;
        refreshButton.disabled = true;
        try {
            var result = await window.GHApi.get("/api/dashboard", {
                action: "my",
                startDate: applied.startDate,
                endDate: applied.endDate
            });
            var data = result.data || {};
            var login = data.login || {};
            var operation = data.operation || {};
            var message = data.message || {};

            setNumber("card_login_success", login.success);
            setNumber("card_login_failure", login.failure);
            setNumber("card_active_users", login.activeUsers);
            setNumber("card_unread", message.unread);
            setNumber("card_op_total", operation.total);
            setNumber("card_op_success", operation.success);
            setNumber("card_op_failed",
                    (Number(operation.failed || 0) + Number(operation.denied || 0)));

            renderTrend(login.trend);
            renderModules(operation.byModule);

            loginScope.textContent = data.scope === "global"
                    ? "范围：全站（系统管理员）" : "范围：本人";
            if (data.range) {
                rangeText.textContent = data.range.label || "";
            }
            updatedText.textContent = "上次成功：" + (data.updatedAt || "");
            statusText.textContent = "读取正常（每30秒按已应用条件刷新）";
        } catch (error) {
            // 刷新失败保留上次数值，只标记失败与上次成功时间。
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
