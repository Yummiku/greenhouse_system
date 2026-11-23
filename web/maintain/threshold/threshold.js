(() => {
    "use strict";
    const app = document.getElementById("threshold-app");
    const base = app.dataset.base;
    const api = base + "/api/threshold";
    const filters = document.getElementById("threshold-query");
    const dialog = document.getElementById("threshold-dialog");
    const form = document.getElementById("threshold-form");
    const save = document.getElementById("threshold-save");
    const close = document.getElementById("threshold-close");
    const message = document.getElementById("threshold-status");
    const dialogMessage = document.getElementById("threshold-dialog-status");
    const retry = document.getElementById("threshold-house-retry");
    const detail = document.getElementById("threshold-detail");
    const canManage = !!document.getElementById("threshold-new");
    const token = document.getElementById("threshold-token").value;
    const actor = document.getElementById("threshold-actor").textContent;
    const metrics = {
        air_temperature: ["空气温度", "℃"], air_humidity: ["空气湿度", "%"],
        soil_temperature: ["土壤温度", "℃"], soil_moisture: ["土壤湿度", "%"],
        illuminance: ["光照强度", "lx"]
    };
    let rules = [];
    let houses = [];
    let applied = { name: "", greenhouseId: "", metric: "", state: "", sort: "updatedAt", dir: "desc" };
    let page = 1;
    let size = 10;
    let loaded = false;
    let refreshing = false;
    let editing = null;
    let dialogGeneration = 0;
    let writing = false;
    let optionsLoaded = false;

    function element(tag, text, className) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        if (className) node.className = className;
        return node;
    }
    function fail(target, text) {
        target.textContent = text;
        alert(text);
    }
    async function request(url, options) {
        const response = await fetch(url, { cache: "no-store", ...options });
        let result;
        try { result = await response.json(); }
        catch { throw new Error("服务器响应异常，请稍后重试"); }
        if (!response.ok || result.result_code !== 0) throw new Error(result.result_msg || "请求失败");
        return result.data;
    }
    const metricName = rule => (metrics[rule.metricType] || [rule.metricType])[0];
    function houseName(rule) {
        if (!rule.greenhouseName) return "大棚记录已失效（技术编号 " + rule.greenhouseId + "）";
        const suffix = Number(rule.greenhouseDeleted) === 1 ? "（已删除）"
            : rule.greenhouseStatus !== "enabled" ? "（已停用）" : "";
        return rule.greenhouseName + "（" + (rule.greenhouseCode || "编号未记录") + "）" + suffix;
    }
    const bound = value => value == null ? "未设置" : String(value);
    const valid = rule => rule.greenhouseValid === true;
    const selectionKey = rule => rule.greenhouseId + "/" + rule.metricType;

    function selected() {
        return rules.filter(rule =>
            rule.name.toLowerCase().includes(applied.name.toLowerCase()) &&
            (!applied.greenhouseId || rule.greenhouseId === applied.greenhouseId) &&
            (!applied.metric || rule.metricType === applied.metric) &&
            (!applied.state || String(rule.enabled) === applied.state)
        ).sort((a, b) => {
            const order = String(a[applied.sort] || "").localeCompare(String(b[applied.sort] || ""), "zh-CN");
            const tie = BigInt(a.id) > BigInt(b.id) ? -1 : BigInt(a.id) < BigInt(b.id) ? 1 : 0;
            return (applied.dir === "desc" ? -order : order) || tie;
        });
    }

    function houseOptions() {
        const input = filters.elements.greenhouseId;
        const previous = input.value;
        const options = new Map(houses.map(house => [String(house.id), house.name + "（" + house.greenhouse_code + "）"]));
        for (const rule of rules) options.set(rule.greenhouseId, houseName(rule));
        input.replaceChildren(new Option("全部大棚", ""));
        for (const [id, label] of options) input.add(new Option(label, id));
        if (previous && !options.has(previous)) input.add(new Option("大棚记录已失效（" + previous + "）", previous));
        input.value = previous;
    }

    function chartSection(title, groups, total) {
        const section = element("section");
        section.append(element("h3", title));
        for (const [label, count] of groups) {
            const row = element("div", undefined, "threshold-bar");
            const track = element("div", undefined, "threshold-track");
            const fill = element("div", undefined, "threshold-fill");
            fill.style.width = (total ? count / total * 100 : 0) + "%";
            track.append(fill);
            row.title = label + " " + count + "条";
            row.append(element("span", label, "threshold-bar-label"), track, element("span", count + "条"));
            section.append(row);
        }
        return section;
    }

    function statistics(data, summary, charts) {
        charts.replaceChildren();
        if (!data.length) { summary.textContent = "暂无可统计数据"; return; }
        const enabled = data.filter(rule => rule.enabled).length;
        const invalid = data.filter(rule => rule.enabled && !valid(rule)).length;
        summary.textContent = "共" + data.length + "条，启用" + enabled + "条，停用" +
            (data.length - enabled) + "条。其中" + invalid + "条因大棚无效暂不生效。";
        charts.append(chartSection("启停状态分布", [["启用", enabled], ["停用", data.length - enabled]], data.length),
            chartSection("监测指标分布", Object.values(metrics).map(([label]) =>
                [label, data.filter(rule => metricName(rule) === label).length]), data.length));
    }

    function button(text, action) {
        const node = element("button", text, "btn default btn-sm");
        node.type = "button";
        node.onclick = action;
        return node;
    }

    function actions(cell, rule) {
        cell.append(button("详情", () => openRule(rule.id, false)));
        if (!canManage) return;
        cell.append(button("编辑", () => openRule(rule.id, true)));
        const conflict = rules.find(other => other.id !== rule.id && other.enabled &&
            selectionKey(other) === selectionKey(rule));
        const toggle = button(rule.enabled ? "停用" : "启用",
            () => write(rule.enabled ? "disable" : "enable", { id: rule.id }, toggle));
        const reason = !rule.enabled && (!valid(rule) ? "所属大棚已失效，不能启用"
            : conflict ? "请先停用规则【" + conflict.name + "】（编号" + conflict.id + "）" : "");
        if (reason) {
            toggle.disabled = true;
            const wrapper = element("span");
            wrapper.title = reason;
            wrapper.tabIndex = 0;
            wrapper.setAttribute("aria-label", reason);
            toggle.style.pointerEvents = "none";
            wrapper.append(toggle);
            cell.append(wrapper, element("small", reason));
        } else cell.append(toggle);
        const remove = button("删除", async () => {
            if (confirm("确认删除规则【" + rule.name + "】吗？\n" + houseName(rule) + "／" + metricName(rule))) {
                await write("delete", { id: rule.id }, remove);
            }
        });
        remove.disabled = rule.enabled;
        cell.append(remove);
        if (rule.enabled) cell.append(element("small", "请先停用规则再删除"));
    }

    function render() {
        const data = selected();
        const pages = Math.max(1, Math.ceil(data.length / size));
        page = Math.max(1, Math.min(page, pages));
        const body = document.getElementById("threshold-rows");
        body.replaceChildren();
        for (const rule of data.slice((page - 1) * size, page * size)) {
            const row = body.insertRow();
            for (const value of [rule.id, rule.name, houseName(rule), metricName(rule),
                bound(rule.lowerBound), bound(rule.upperBound), rule.unit,
                (rule.enabled ? "启用" : "停用") + (rule.enabled && !valid(rule) ? "（暂不生效）" : ""),
                rule.updatedAt || "未记录"]) row.insertCell().textContent = value;
            actions(row.insertCell(), rule);
        }
        if (!data.length) {
            const cell = body.insertRow().insertCell();
            cell.colSpan = 10; cell.textContent = "当前条件下暂无规则";
        }
        document.getElementById("threshold-page").textContent =
            "第" + page + "页／共" + pages + "页，共" + data.length + "条";
        document.getElementById("threshold-prev").disabled = page === 1;
        document.getElementById("threshold-next").disabled = page === pages;
        statistics(data, document.getElementById("threshold-summary"), document.getElementById("threshold-charts"));
    }

    async function reload() {
        refreshing = true;
        document.getElementById("threshold-export").disabled = true;
        document.getElementById("threshold-print").disabled = true;
        try {
            const data = await request(api + "?action=list");
            rules = data;
            loaded = true;
            houseOptions();
            render();
            return true;
        } catch (error) {
            loaded = false;
            document.getElementById("threshold-rows").replaceChildren();
            document.getElementById("threshold-charts").replaceChildren();
            document.getElementById("threshold-summary").textContent = "读取失败，统计不可用";
            document.getElementById("threshold-page").textContent = "读取失败";
            message.textContent = error.message;
            return false;
        } finally {
            refreshing = false;
            document.getElementById("threshold-export").disabled = !loaded;
            document.getElementById("threshold-print").disabled = !loaded;
        }
    }

    async function loadHouses() {
        const generation = dialogGeneration;
        optionsLoaded = false;
        retry.hidden = true;
        save.disabled = true;
        form.elements.greenhouseId.disabled = true;
        try {
            const list = [];
            let p = 1;
            let total;
            do {
                const data = await request(base + "/api/greenhouses?action=list&status=enabled&page=" + p + "&size=100");
                list.push(...data.items);
                total = Number(data.total);
                if (!data.items.length) break;
                p++;
            } while (list.length < total);
            if (generation !== dialogGeneration || !dialog.open) return;
            houses = list.filter(house => house.status === "enabled");
            houseOptions();
            const select = form.elements.greenhouseId;
            select.replaceChildren(new Option(houses.length ? "请选择大棚" : "暂无启用大棚，请先完成大棚档案", ""));
            for (const house of houses) select.add(new Option(house.name + "（" + house.greenhouse_code + "）", house.id));
            optionsLoaded = houses.length > 0;
            select.disabled = !optionsLoaded;
            save.disabled = !optionsLoaded;
            dialogMessage.textContent = optionsLoaded ? "" : "无可选大棚，请先完成大棚档案。";
        } catch (error) {
            if (generation !== dialogGeneration || !dialog.open) return;
            form.elements.greenhouseId.replaceChildren(new Option("加载失败，请重试", ""));
            retry.hidden = false;
            dialogMessage.textContent = "大棚选项加载失败，请重试。";
        }
    }

    function openPanel(title) {
        dialogGeneration++;
        dialogMessage.textContent = "";
        detail.replaceChildren();
        detail.hidden = true;
        form.hidden = true;
        document.getElementById("threshold-dialog-title").textContent = title;
        close.textContent = "关闭";
        if (!dialog.open) dialog.showModal();
    }
    function addDetail(label, value) {
        detail.append(element("dt", label), element("dd", value == null || value === "" ? "未记录" : String(value)));
    }
    function explanation(rule) {
        const parts = [];
        if (rule.lowerBound != null) parts.push("小于" + rule.lowerBound + rule.unit + "为低于下限");
        else parts.push("未设置下限，该侧不限制");
        if (rule.upperBound != null) parts.push("大于" + rule.upperBound + rule.unit + "为高于上限");
        else parts.push("未设置上限，该侧不限制");
        return parts.join("；") + "。等于已设置边界视为正常。规则启用仅供后续读取，自动报警尚待对接。";
    }

    async function openRule(id, edit) {
        openPanel(edit ? "编辑阈值规则" : "规则详情");
        const generation = dialogGeneration;
        dialogMessage.textContent = "正在读取…";
        try {
            const rule = await request(api + "?action=detail&id=" + encodeURIComponent(id));
            if (generation !== dialogGeneration || !dialog.open) return;
            dialogMessage.textContent = "";
            if (edit) {
                editing = rule;
                form.reset(); form.hidden = false;
                form.querySelector("p").textContent = "所属大棚与监测指标保持不变。";
                form.elements.name.value = rule.name;
                form.elements.greenhouseId.replaceChildren(new Option(houseName(rule), rule.greenhouseId));
                form.elements.greenhouseId.disabled = true;
                form.elements.metric.value = rule.metricType; form.elements.metric.disabled = true;
                form.elements.unit.value = rule.unit;
                form.elements.lower.value = rule.lowerBound ?? "";
                form.elements.upper.value = rule.upperBound ?? "";
                form.elements.remark.value = rule.remark || "";
                retry.hidden = true;
                save.disabled = false; close.textContent = "取消";
                form.elements.name.focus();
            } else {
                detail.hidden = false;
                for (const [label, value] of [
                    ["规则编号", rule.id], ["规则名称", rule.name], ["所属大棚", houseName(rule)],
                    ["监测指标", metricName(rule)], ["下限", bound(rule.lowerBound)], ["上限", bound(rule.upperBound)],
                    ["单位", rule.unit], ["状态", rule.enabled ? "启用" : "停用"],
                    ["大棚有效性", valid(rule) ? "有效" : "无效，规则暂不生效"],
                    ["备注", rule.remark || "无"], ["创建人", rule.createdBy], ["创建时间", rule.createdAt],
                    ["最近修改人", rule.updatedBy], ["最近修改时间", rule.updatedAt], ["边界说明", explanation(rule)]
                ]) addDetail(label, value);
            }
        } catch (error) {
            if (generation === dialogGeneration && dialog.open) fail(dialogMessage, error.message);
        }
    }

    async function write(action, values, trigger) {
        if (writing) return null;
        writing = true; trigger.disabled = true; close.disabled = true;
        const target = action === "create" || action === "edit" ? dialogMessage : message;
        target.textContent = "正在保存";
        try {
            let result;
            try {
                result = await request(api + "?action=" + action, {
                    method: "POST", body: new URLSearchParams({ ...values, csrf_token: token })
                });
            } catch (error) { fail(target, error.message); return null; }
            if (action === "create" || action === "edit") dialog.close();
            const success = { create: "新增成功，当前为停用状态", edit: "修改成功",
                enable: "启用成功", disable: "停用成功", delete: "删除成功" }[action];
            const refreshed = await reload();
            message.textContent = refreshed ? success : success + "，列表刷新失败，请刷新核对";
            if (refreshed && action === "create" && !selected().some(rule => rule.id === result.id)) {
                message.textContent = "新增成功，记录不在当前筛选结果中";
            }
            if (action === "create" || action === "edit") {
                alert(refreshed ? "保存成功" : "保存成功，列表刷新失败，请刷新核对");
            }
            return result;
        } finally {
            writing = false; trigger.disabled = false; close.disabled = false;
            save.textContent = "保存";
        }
    }

    // 用整数定点数比较边界，避免浮点数把很接近的六位小数视为相等。
    function decimal(text, field) {
        const value = text.trim();
        if (!value) return null;
        if (value.length > 100) throw { field, message: "边界最多支持12位整数和6位小数" };
        const match = /^([+-]?)(?:(\d+)(?:\.(\d*))?|\.(\d+))(?:[eE]([+-]?\d+))?$/.exec(value);
        if (!match) throw { field, message: "上下限必须是有限数字" };
        const fraction = match[3] || match[4] || "";
        const raw = (match[2] || "0") + fraction;
        let digits = raw.replace(/^0+/, "").replace(/0+$/, "");
        if (!digits) return 0n;
        const trailing = raw.length - raw.replace(/0+$/, "").length;
        const scale = fraction.length - Number(match[5] || 0) - trailing;
        if (!Number.isFinite(scale) || scale > 6 || digits.length - scale > 12)
            throw { field, message: "边界最多支持12位整数和6位小数" };
        return BigInt((match[1] === "-" ? "-" : "") + digits) * 10n ** BigInt(6 - scale);
    }

    form.addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const name = form.elements.name.value.trim();
            if (!name || name.length > 100) throw { field: "name", message: "规则名称须为1至100字" };
            if (!editing && (!optionsLoaded || !form.elements.greenhouseId.value))
                throw { field: "greenhouseId", message: "请选择有效大棚" };
            if (form.elements.remark.value.length > 500) throw { field: "remark", message: "备注最多500字" };
            const lower = decimal(form.elements.lower.value, "lower");
            const upper = decimal(form.elements.upper.value, "upper");
            if (lower === null && upper === null) throw { field: "lower", message: "至少填写一个边界" };
            if (lower !== null && upper !== null && lower >= upper) throw { field: "lower", message: "下限必须小于上限" };
            if (editing && editing.enabled &&
                (lower !== decimal(String(editing.lowerBound ?? ""), "lower") ||
                 upper !== decimal(String(editing.upperBound ?? ""), "upper")) &&
                !confirm("该规则正在启用，修改边界将影响后续判定，是否保存？")) return;
            const values = Object.fromEntries(new FormData(form));
            values.name = name;
            if (editing) values.id = editing.id;
            save.textContent = "正在保存";
            await write(editing ? "edit" : "create", values, save);
        } catch (error) {
            fail(dialogMessage, error.message || "表单校验失败");
            if (error.field) form.elements[error.field].focus();
        }
    });
    document.getElementById("threshold-dialog-x").onclick = () => { if (!writing) close.click(); };
    close.onclick = () => { dialogGeneration++; dialog.close(); };
    dialog.addEventListener("cancel", event => {
        if (writing) event.preventDefault();
        else dialogGeneration++;
    });
    if (canManage) {
        document.getElementById("threshold-new").onclick = async () => {
            openPanel("新增阈值规则");
            editing = null; form.reset(); form.hidden = false;
            form.querySelector("p").textContent = "保存后为停用状态，需在列表中启用后供报警流程使用。";
            form.elements.metric.disabled = false; form.elements.unit.value = "℃";
            close.textContent = "取消";
            await loadHouses();
        };
    }
    retry.onclick = loadHouses;
    for (const [code, [name, unit]] of Object.entries(metrics)) {
        form.elements.metric.add(new Option(name, code));
        filters.elements.metric.add(new Option(name, code));
    }
    form.elements.metric.onchange = () => { form.elements.unit.value = metrics[form.elements.metric.value][1]; };
    filters.addEventListener("submit", async event => {
        event.preventDefault();
        applied = Object.fromEntries(new FormData(filters));
        applied.name = applied.name.trim();
        page = 1;
        if (await reload()) message.textContent = selected().length ? "查询成功" : "当前条件下暂无规则";
        else alert(message.textContent);
    });
    filters.addEventListener("reset", () => {
        applied = { name: "", greenhouseId: "", metric: "", state: "", sort: "updatedAt", dir: "desc" };
        page = 1; size = 10; document.getElementById("threshold-size").value = "10";
        reload().then(ok => { if (ok) message.textContent = "已重置查询"; else alert(message.textContent); });
    });
    document.getElementById("threshold-size").onchange = event => { size = Number(event.target.value); page = 1; render(); };
    document.getElementById("threshold-prev").onclick = () => { page--; render(); };
    document.getElementById("threshold-next").onclick = () => { page++; render(); };
    document.getElementById("threshold-stats").onclick = () => {
        if (!loaded) { fail(message, "列表尚未成功加载，请查询后重试"); return; }
        const region = document.getElementById("threshold-statistics");
        region.hidden = !region.hidden;
    };

    function criteria() {
        const house = rules.find(rule => rule.greenhouseId === applied.greenhouseId);
        const option = houses.find(item => String(item.id) === applied.greenhouseId);
        const houseText = house ? houseName(house) : option ? option.name + "（" + option.greenhouse_code + "）"
            : applied.greenhouseId ? "大棚记录已失效（" + applied.greenhouseId + "）" : "全部";
        return "名称：" + (applied.name || "全部") + "　大棚：" + houseText +
            "　指标：" + (metrics[applied.metric]?.[0] || "全部") +
            "　状态：" + ({ true: "启用", false: "停用" }[applied.state] || "全部") +
            "　排序：" + (applied.sort === "name" ? "规则名称" : "更新时间") + (applied.dir === "desc" ? "降序" : "升序");
    }
    const headers = ["规则编号", "规则名称", "大棚名称", "业务编号", "监测指标", "下限", "上限", "单位", "启停状态", "大棚有效性", "备注", "更新时间"];
    function outputValues(rule) {
        return [rule.id, rule.name, rule.greenhouseName || "大棚记录已失效", rule.greenhouseCode || "未记录", metricName(rule),
            bound(rule.lowerBound), bound(rule.upperBound), rule.unit, rule.enabled ? "启用" : "停用",
            valid(rule) ? "有效" : "无效", rule.remark || "无", rule.updatedAt || "未记录"];
    }
    function csvCell(value) {
        let text = String(value);
        if (/^[\s]*[=+@-]|^[\t\r\n]/.test(text)) text = "'" + text;
        return '"' + text.replaceAll('"', '""') + '"';
    }
    document.getElementById("threshold-export").onclick = event => {
        const button = event.currentTarget;
        if (!loaded || refreshing) return;
        button.disabled = true;
        try {
            const csv = [headers, ...selected().map(outputValues)].map(row => row.map(csvCell).join(",")).join("\r\n");
            const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
            const link = element("a"); link.href = url; link.download = "阈值规则.csv";
            link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
            message.textContent = "已导出当前查询结果，共" + selected().length + "条，未设置边界使用“未设置”表示";
        } catch (error) { fail(message, "导出失败，请重试"); }
        finally { button.disabled = false; }
    };
    document.getElementById("threshold-print").onclick = event => {
        const button = event.currentTarget;
        if (!loaded || refreshing) return;
        button.disabled = true;
        try {
            const preview = window.open("", "_blank");
            if (!preview) throw new Error("打印窗口被阻止，请允许本站弹出窗口后重试");
            const doc = preview.document;
            doc.title = "农业大棚系统 阈值规则报表";
            const style = doc.createElement("style");
            style.textContent = "body{font:11px sans-serif;color:#000;margin:0;padding:8px}h1{font-size:20px;margin:0 0 12px}p{margin:7px 0;line-height:1.5}table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:10px}th,td{border:1px solid #888;padding:4px;overflow-wrap:anywhere;line-height:1.4}thead{display:table-header-group}tr{break-inside:avoid;page-break-inside:avoid}td:nth-child(12){font-size:9px}.threshold-charts{display:flex;gap:20px;break-inside:avoid;margin:10px 0}.threshold-charts section{flex:1;min-width:0}.threshold-charts h2{font-size:14px;margin:4px 0 8px}.threshold-bar{display:flex;gap:6px;margin:5px 0;align-items:center}.threshold-bar-label{width:65px}.threshold-track{flex:1;background:#eee;height:10px;print-color-adjust:exact;-webkit-print-color-adjust:exact}.threshold-fill{background:#159c89;height:10px;print-color-adjust:exact;-webkit-print-color-adjust:exact}@page{size:A4 landscape;margin:10mm}";
            doc.head.append(style);
            doc.body.append(element("h1", doc.title), element("p", criteria()),
                element("p", "打印人：" + actor + "　打印时间：" + new Date().toLocaleString("zh-CN")));
            const summary = element("p"), charts = element("div", undefined, "threshold-charts");
            statistics(selected(), summary, charts); doc.body.append(summary, charts);
            const table = doc.createElement("table");
            const columns = doc.createElement("colgroup");
            // Give names, remarks and timestamps more space than numeric columns.
            for (const width of [5, 13, 10, 10, 8, 6, 6, 4, 6, 7, 12, 13]) {
                const column = doc.createElement("col");
                column.style.width = width + "%"; columns.append(column);
            }
            table.append(columns);
            const header = table.createTHead().insertRow();
            for (const text of headers) header.append(element("th", text));
            const body = table.createTBody();
            for (const rule of selected()) {
                const row = body.insertRow();
                for (const value of outputValues(rule)) row.insertCell().textContent = value;
            }
            doc.body.append(table);
            preview.focus(); preview.print();
        } catch (error) { fail(message, error.message || "报表准备失败，请重试"); }
        finally { button.disabled = false; }
    };
    reload().then(ok => { if (ok) message.textContent = "查询成功"; });
})();
