(function () {
    "use strict";
    const api = "/api/permission-requests";
    const $id = id => document.getElementById(id);
    const scope = $id("permission-page").dataset.scope;
    const filter = $id("permission-filter");
    const states = {pending: "待审批", approved: "已批准", rejected: "已驳回", withdrawn: "已撤回"};
    const roles = {1: "系统管理员", 2: "普通管理员", 3: "普通用户"};
    const statuses = {active: "启用", disabled: "停用", deleted: "已删除"};
    let context, applied, page = 1, total = 0, selected, busy = false, sequence = 0;
    function text(id, value) { $id(id).textContent = value; }
    function node(tag, value) { const n = document.createElement(tag); if (value != null) n.textContent = value; return n; }
    function failure(error, target) {
        const message = error.message || "操作失败";
        text(target || "permission-message", message);
        window.alert(message);
    }
    function parameters() { return Object.fromEntries(new FormData(filter)); }
    function description(q) {
        return (scope === "review" ? "全部申请人" : "仅本人申请") + "；" + (states[q.status] || "全部状态")
            + "；关键词：" + (q.keyword || "不限") + "；申请日期：" + (q.start || "不限") + " 至 " + (q.end || "不限");
    }
    function groups(data) {
        const counts = {pending: 0, approved: 0, rejected: 0, withdrawn: 0};
        data.groups.forEach(g => { counts[g.status] = Number(g.count); });
        const root = $id("permission-statistics"); root.replaceChildren();
        [["合计", data.total], ...Object.keys(states).map(k => [states[k], counts[k]])].forEach(pair => {
            const card = node("div", pair[0]); card.className = "permission-card";
            card.append(node("strong", pair[1] + " 条")); root.append(card);
        });
    }
    function button(label, action, danger) {
        const b = node("button", label); b.type = "button"; b.className = "btn btn-sm " + (danger ? "btn-danger" : "default");
        b.addEventListener("click", action); return b;
    }
    async function load(q, requestedPage) {
        const current = ++sequence;
        const result = await GHApi.get(api, Object.assign({}, q, {action: "list", scope, page: requestedPage}));
        if (current !== sequence) return;
        const data = result.data; applied = Object.assign({}, q); page = data.page; total = data.total;
        groups(data); text("permission-range", "统计范围：" + description(applied));
        text("permission-summary", "当前条件共 " + total + " 条申请；待审批优先显示。统计、列表和输出沿用已查询条件。");
        const rows = $id("permission-rows"); rows.replaceChildren();
        data.items.forEach(r => {
            const tr = node("tr");
            [r.display_name + "（" + r.username + "）", "普通管理员", r.reason, states[r.status], r.created_at, r.reviewer_name || "—"].forEach(v => tr.append(node("td", v)));
            const actions = node("td"); actions.append(button(scope === "review" && r.status === "pending" ? "查看并审批" : "详情", () => detail(r.id)));
            if (scope === "mine" && r.status === "pending") actions.append(button("撤回", () => withdraw(r), true));
            tr.append(actions); rows.append(tr);
        });
        if (!data.items.length) { const td = node("td", "当前条件下没有申请"); td.colSpan = 7; const tr = node("tr"); tr.append(td); rows.append(tr); }
        text("permission-pages", "第 " + page + " / " + Math.max(1, Math.ceil(total / Number(applied.size))) + " 页，共 " + total + " 条");
        $id("permission-prev").disabled = page <= 1; $id("permission-next").disabled = page * Number(applied.size) >= total;
        $id("permission-add").disabled = !context.can_apply;
    }
    async function refresh(q, requestedPage) {
        if (busy) return;
        try { await load(q || applied, requestedPage || 1); text("permission-message", "查询成功"); }
        catch (e) { failure(e); }
    }
    async function detail(id) {
        if (busy) return;
        try {
            const result = await GHApi.get(api, {action: "detail", scope, id}); selected = result.data;
            const r = selected, body = $id("permission-detail-body"); body.replaceChildren();
            [["申请人", r.display_name + "（" + r.username + "）"], ["申请时角色", roles[r.source_role]], ["当前角色", roles[r.applicant_role]], ["当前账号状态", statuses[r.account_status]], ["申请角色", "普通管理员"], ["申请理由", r.reason], ["状态", states[r.status]], ["申请时间", r.created_at], ["审批人", r.reviewer_name], ["审批时间", r.reviewed_at], ["审批意见", r.review_opinion], ["撤回时间", r.withdrawn_at]].forEach(pair => {body.append(node("dt", pair[0]), node("dd", pair[1] || "—"));});
            const review = scope === "review" && context.can_review && r.status === "pending" && r.applicant_id !== context.current.id;
            $id("permission-review-controls").hidden = !review; $id("permission-review-actions").hidden = !review;
            $id("permission-approve").disabled = !(Number(r.applicant_role) === 3 && r.account_status === "active");
            $id("permission-opinion").value = ""; text("permission-detail-error", "");
            window.jQuery("#permission-detail").modal("show");
        } catch (e) { failure(e); }
    }
    async function write(action, values, modal, errorTarget) {
        if (busy) return; busy = true;
        const box = modal ? $id(modal) : null;
        const controls = box ? Array.from(box.querySelectorAll("button,textarea")) : [];
        const disabled = controls.map(c => c.disabled); controls.forEach(c => { c.disabled = true; });
        let saved = false;
        try {
            await GHApi.post(api, Object.assign({}, values, {action, csrf_token: context.csrf_token}));
            saved = true;
            text("permission-message", action === "create" ? "申请已提交" : "操作成功");
        } catch (e) { failure(e, errorTarget); }
        finally { controls.forEach((c, i) => {c.disabled = disabled[i];}); busy = false; }
        if (saved) {
            if (modal) window.jQuery("#" + modal).modal("hide");
            try { await load(applied, page); }
            catch (e) { failure(new Error("操作已成功，但列表刷新失败。请点击刷新，不要重复提交。" + e.message)); }
        }
    }
    function withdraw(r) {
        if (busy || !window.confirm("确定撤回这条权限申请吗？撤回后将保留申请记录。")) return;
        write("withdraw", {id: r.id, version: r.version}, null, "permission-message");
    }
    function review(action) {
        if (busy || !selected) return;
        const opinion = $id("permission-opinion").value.trim();
        if (action === "reject" && !opinion) {failure(new Error("请填写驳回意见"), "permission-detail-error"); return;}
        if (!window.confirm(action === "approve" ? "确定批准此申请吗？申请人将升级为普通管理员，旧登录会话会失效。" : "确定驳回此申请吗？")) return;
        write(action, {id: selected.id, version: selected.version, review_opinion: opinion}, "permission-detail", "permission-detail-error");
    }
    async function output(print) {
        if (busy || !applied) return;
        busy = true;
        try {
            const params = Object.assign({}, applied, {scope, action: print ? "report" : "export"});
            if (print) {
                const result = await GHApi.get(api, params), data = result.data, area = $id("permission-print-area");
                area.replaceChildren(node("h1", scope === "review" ? "农业大棚系统 - 权限审批" : "农业大棚系统 - 我的权限申请"), node("p", description(applied)), node("p", "实际条数：" + data.total + "；打印时间：" + new Date().toLocaleString()));
                const table = node("table"), head = node("thead"), hr = node("tr"), body = node("tbody");
                ["申请人", "申请角色", "理由", "状态", "申请时间", "审批人", "审批时间", "审批意见", "撤回时间"].forEach(v => hr.append(node("th", v))); head.append(hr);
                data.items.forEach(r => {const tr = node("tr");[r.display_name + "（" + r.username + "）", "普通管理员", r.reason, states[r.status], r.created_at, r.reviewer_name || "—", r.reviewed_at || "—", r.review_opinion || "—", r.withdrawn_at || "—"].forEach(v => tr.append(node("td", v)));body.append(tr);});
                table.append(head, body);area.append(table);window.print();
            } else {
                const url = new URL(window.GH_CONTEXT_PATH + api, window.location.origin);url.search = new URLSearchParams(params).toString();
                const response = await fetch(url, {credentials: "same-origin", cache: "no-store"});
                if (!response.ok) { let reason = "导出失败";try {reason = (await response.json()).result_msg || reason;} catch (_) {} throw new Error(reason); }
                if (!(response.headers.get("Content-Type") || "").startsWith("text/csv")) throw new Error("导出未返回CSV，请检查登录状态");
                const href = URL.createObjectURL(await response.blob()), a = node("a");a.href = href;a.download = "权限申请.csv";document.body.append(a);a.click();a.remove();setTimeout(() => URL.revokeObjectURL(href), 1000);
            }
        } catch (e) {failure(e);} finally {busy = false;}
    }
    filter.addEventListener("submit", e => {e.preventDefault();refresh(parameters(), 1);});
    $id("permission-reset").onclick = () => {filter.reset();refresh(parameters(), 1);};
    $id("permission-refresh").onclick = () => refresh(applied, page);
    $id("permission-prev").onclick = () => refresh(applied, page - 1);
    $id("permission-next").onclick = () => refresh(applied, page + 1);
    $id("permission-add").onclick = () => {if (busy) return; $id("permission-form").reset();text("permission-form-error", "");window.jQuery("#permission-editor").modal("show");};
    $id("permission-form").onsubmit = e => {e.preventDefault();const reason = e.target.elements.reason.value.trim();if (!reason) {failure(new Error("请填写申请理由"), "permission-form-error");return;}write("create", {target_role: "2", reason}, "permission-editor", "permission-form-error");};
    $id("permission-approve").onclick = () => review("approve"); $id("permission-reject").onclick = () => review("reject");
    $id("permission-export").onclick = () => output(false); $id("permission-print").onclick = () => output(true);
    window.jQuery("#permission-editor,#permission-detail").modal({show: false, backdrop: "static", keyboard: false}).on("hide.bs.modal", e => {if (busy) e.preventDefault();});
    // 写入成功后主动关闭；请求期间手动关闭仍被阻止。
    (async () => {
        try {
            context = (await GHApi.get(api, {action: "context"})).data;
            if (scope === "review" && !context.can_review) throw new Error("仅系统管理员可以访问权限审批");
            $id("permission-app").hidden = false; $id("permission-add").hidden = scope !== "mine" || !context.can_apply;
            text("permission-help", scope === "review" ? "待审批申请可批准或驳回；已处理记录只读。账号已停用、删除或角色变化时不可批准。" : context.can_apply ? "只能申请普通管理员。同一时间只能有一条待审批申请，提交后可查看或撤回。" : "当前角色无需申请普通管理员；此处保留本人历史申请。");
            if (scope === "review") filter.elements.status.value = "pending";
            await refresh(parameters(), 1);
        } catch (e) { $id("permission-app").hidden = true;failure(e); }
    })();
})();
