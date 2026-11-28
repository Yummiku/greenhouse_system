(function () {
    "use strict";
    const $id = id => document.getElementById(id);
    function node(tag, text) {const n = document.createElement(tag);if (text != null) n.textContent = text;return n;}
    function chart(root, title, counts) {
        const section = node("section");section.append(node("h4", title));
        const total = counts.reduce((sum, pair) => sum + pair[1], 0);
        if (!total) section.append(node("p", "暂无数据（0条）"));
        counts.forEach(pair => {
            const row = node("div", pair[0] + "：" + pair[1] + "（" + (total ? (100 * pair[1] / total).toFixed(1) : "0.0") + "%）");row.className = "permission-distribution";
            const progress = node("progress");progress.max = Math.max(1, total);progress.value = pair[1];progress.setAttribute("aria-label", pair[0] + " " + pair[1]);row.append(progress);section.append(row);
        });root.append(section);
    }
    async function load() {
        $id("dashboard-refresh").disabled = true;
        try {
            const data = (await GHApi.get("/api/permission-requests", {action: "dashboard"})).data;
            const requestCounts = {pending: 0, approved: 0, rejected: 0, withdrawn: 0};
            data.requests.forEach(g => {requestCounts[g.status] = Number(g.count);});
            const users = {active: 0, disabled: 0, deleted: 0}, roles = {1: 0, 2: 0, 3: 0};
            data.users.forEach(g => {users[g.status] += Number(g.count);if (g.status !== "deleted") roles[g.role_id] += Number(g.count);});
            const profile = data.current;
            $id("dashboard-scope").textContent = profile.display_name + "（" + profile.username + "）｜" + (data.global ? "全体账号和全体权限申请" : "仅本人资料和本人权限申请");
            const cards = $id("dashboard-cards"); cards.replaceChildren();
            const pairs = data.global ? [["未删除账号", users.active + users.disabled], ["启用账号", users.active], ["停用账号", users.disabled], ["待审批申请", requestCounts.pending], ["活跃用户数", "未接入"]]
                : [["当前角色", {1: "系统管理员", 2: "普通管理员", 3: "普通用户"}[profile.role_id]], ["本人待审批", requestCounts.pending], ["本人已批准", requestCounts.approved], ["本人已驳回", requestCounts.rejected], ["活跃用户数", "未接入"]];
            pairs.forEach(pair => {const c = node("div", pair[0]);c.className = "permission-card";c.append(node("strong", pair[1]));cards.append(c);});
            $id("dashboard-users").hidden = !data.global;$id("dashboard-review").hidden = !data.global;
            const userCharts = $id("dashboard-user-charts"); userCharts.replaceChildren();
            if (data.global) {chart(userCharts, "未删除账号角色分布", [["系统管理员", roles[1]], ["普通管理员", roles[2]], ["普通用户", roles[3]]]);chart(userCharts, "全部账号状态分布", [["启用", users.active], ["停用", users.disabled], ["已删除", users.deleted]]);}
            const requests = $id("dashboard-request-charts");requests.replaceChildren();
            chart(requests, data.global ? "全部申请" : "本人申请", [["待审批", requestCounts.pending], ["已批准", requestCounts.approved], ["已驳回", requestCounts.rejected], ["已撤回", requestCounts.withdrawn]]);
            $id("dashboard-message").textContent = "统计已更新：" + new Date().toLocaleString();
        } catch (e) {
            $id("dashboard-message").textContent = e.message + "；已有数据不代表最新状态，请刷新重试。";window.alert(e.message);
        } finally {$id("dashboard-refresh").disabled = false;}
    }
    $id("dashboard-refresh").onclick = load;load();
})();
