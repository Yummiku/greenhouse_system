async function loadThresholdRules() {
    const rows = document.getElementById("threshold-rows");
    const status = document.getElementById("threshold-status");

    try {
        const response = await fetch(rows.dataset.url);
        const result = await response.json();
        if (!response.ok || result.result_code !== 0) {
            status.textContent = result.result_msg || "加载规则失败";
            return;
        }

        allRules = result.data;
        renderRules();
    } catch (error) {
        status.textContent = "加载规则失败，请刷新后重试。";
    }
}


const createForm = document.getElementById("threshold-create");
if (createForm) {
    createForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        const status = document.getElementById("threshold-save-status");
        const button = createForm.querySelector("button");
        button.disabled = true;
        status.textContent = "正在保存…";
        try {
            const response = await fetch(createForm.action, {
                method: "POST",
                body: new URLSearchParams(new FormData(createForm))
            });
            const result = await response.json();
            status.textContent = result.result_msg;
            if (response.ok && result.result_code === 0) {
                createForm.reset();
                await loadThresholdRules();
            }
        } catch (error) {
            status.textContent = "无法确认保存结果，请刷新列表核对。";
        } finally {
            button.disabled = false;
        }
    });
}
function addRuleActions(row, rule, blocker) {
    const cell = row.insertCell();
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "btn btn-default btn-sm";
    const detail = document.createElement("button");
    detail.type = "button";
    detail.className = "btn btn-default btn-sm";
    detail.textContent = "详情";
    detail.onclick = () => showRule(rule, false);
    cell.append(detail, " ");
    if (!createForm) return;
    edit.textContent = "编辑";
    edit.onclick = () => showRule(rule, true);
    const toggle = document.createElement("button");
    toggle.type = "button";
    toggle.className = "btn btn-default btn-sm";
    toggle.textContent = rule.enabled ? "停用" : "启用";
    toggle.onclick = () => updateRule(toggle, rule.enabled ? "disable" : "enable", { id: rule.id });
    if (blocker) {
        const hint = document.createElement("span");
        hint.title = "请先停用「" + blocker.name + "」（编号 " + blocker.id + "），再启用这条规则";
        hint.tabIndex = 0;
        hint.setAttribute("aria-label", hint.title);
        hint.style.display = "inline-block";
        hint.style.cursor = "not-allowed";
        toggle.disabled = true;
        toggle.style.pointerEvents = "none";
        hint.append(toggle);
        cell.append(edit, " ", hint);
    } else {
        cell.append(edit, " ", toggle);
    }
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "btn btn-default btn-sm";
    remove.textContent = "删除";
    remove.disabled = rule.enabled;
    if (rule.enabled) cell.title = "请先停用规则，再删除";
    remove.onclick = async () => {
        if (!confirm("确定删除规则「" + rule.name + "」？")) return;
        const deleted = await updateRule(remove, "delete", { id: rule.id });
        if (deleted) panel.hidden = true;
    };
    cell.append(" ", remove);
}

async function updateRule(button, action, values, status = document.getElementById("threshold-save-status")) {
    const url = new URL(createForm.action);
    url.searchParams.set("action", action);
    button.disabled = true;
    try {
        const response = await fetch(url, {
            method: "POST",
            body: new URLSearchParams(values)
        });
        const result = await response.json();
        if (response.ok && result.result_code === 0) await loadThresholdRules();
        status.textContent = result.result_msg;
        return response.ok && result.result_code === 0;
    } catch (error) {
        status.textContent = "无法确认更新结果，请刷新列表核对。";
    } finally {
        button.disabled = false;
    }
}

const panel = document.getElementById("rule-panel");
const editForm = document.getElementById("rule-edit");
const editStatus = document.getElementById("rule-edit-status");
let editingId;
const metricNames = {
    air_temperature: "空气温度", air_humidity: "空气湿度",
    soil_temperature: "土壤温度", soil_moisture: "土壤湿度", illuminance: "光照强度"
};

function showRule(rule, editing) {
    editingId = editing ? rule.id : null;
    document.getElementById("rule-panel-title").textContent = editing ? "编辑规则" : "规则详情";
    document.getElementById("rule-details").textContent = [
        "名称：" + rule.name,
        "大棚编号：" + rule.greenhouseId,
        "指标：" + (metricNames[rule.metricType] || rule.metricType) + "（" + rule.unit + "）",
        "下限：" + (rule.lower ?? "未设置") + " / 上限：" + (rule.upper ?? "未设置"),
        "状态：" + (rule.enabled ? "启用" : "停用"),
        "备注：" + (rule.remark || "无"),
        "更新时间：" + (rule.updatedAt || "未知")
    ].join("\n");
    editStatus.textContent = "";
    editForm.hidden = !editing;
    editForm.elements.lower.value = rule.lower ?? "";
    editForm.elements.upper.value = rule.upper ?? "";
    editForm.elements.remark.value = rule.remark ?? "";
    panel.hidden = false;
    panel.scrollIntoView({ block: "nearest" });
}

document.getElementById("rule-close").onclick = () => { panel.hidden = true; };
editForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (!createForm || editingId == null) return;
    const values = Object.fromEntries(new FormData(editForm));
    values.id = editingId;
    const saved = await updateRule(editForm.querySelector("button"), "edit", values, editStatus);
    if (saved && editingId === values.id) {
        panel.hidden = true;
        document.getElementById("threshold-save-status").textContent = "规则已更新";
    }
});


let allRules = [];
let currentPage = 1;
const pageSize = 10;
const filters = {
    name: document.getElementById("filter-name"),
    house: document.getElementById("filter-house"),
    metric: document.getElementById("filter-metric"),
    state: document.getElementById("filter-state"),
    order: document.getElementById("filter-order")
};
for (const [code, label] of Object.entries(metricNames)) {
    filters.metric.add(new Option(label, code));
}

function filteredRules() {
    const name = filters.name.value.trim().toLowerCase();
    const house = filters.house.value;
    const metric = filters.metric.value;
    const state = filters.state.value;
    const [field, direction] = filters.order.value.split("-");
    return allRules.filter(rule =>
        rule.name.toLowerCase().includes(name) &&
        (!house || rule.greenhouseId === house) &&
        (!metric || rule.metricType === metric) &&
        (!state || String(rule.enabled) === state)
    ).sort((a, b) => {
        const order = field === "name"
            ? a.name.localeCompare(b.name, "zh-CN")
            : (a.updatedAt || "").localeCompare(b.updatedAt || "");
        return (direction === "desc" ? -order : order) || b.id - a.id;
    });
}

function renderRules() {
    const rules = filteredRules();
    const pageCount = Math.max(1, Math.ceil(rules.length / pageSize));
    currentPage = Math.min(currentPage, pageCount);
    const rows = document.getElementById("threshold-rows");
    rows.replaceChildren();
    const start = (currentPage - 1) * pageSize;
    for (const rule of rules.slice(start, start + pageSize)) {
        const row = rows.insertRow();
        for (const value of [rule.name, rule.lower ?? "未设置", rule.upper ?? "未设置",
            rule.unit, rule.enabled ? "启用" : "停用"]) {
            row.insertCell().textContent = value;
        }
        const blocker = !rule.enabled && allRules.find(other => other.enabled &&
            other.greenhouseId === rule.greenhouseId && other.metricType === rule.metricType);
        addRuleActions(row, rule, blocker);
    }

    const enabled = rules.filter(rule => rule.enabled).length;
    const counts = new Map();
    for (const rule of rules) {
        const label = metricNames[rule.metricType] || rule.metricType;
        counts.set(label, (counts.get(label) || 0) + 1);
    }
    const distribution = Array.from(counts, ([label, count]) => label + " " + count + " 条").join("、");
    document.getElementById("rule-summary").textContent =
        "启用 " + enabled + " 条，停用 " + (rules.length - enabled) + " 条" +
        (distribution ? "。指标分布：" + distribution : "");
    document.getElementById("threshold-status").textContent =
        rules.length ? "共 " + rules.length + " 条规则" : "没有符合条件的规则";
    document.getElementById("rule-page").textContent = currentPage + " / " + pageCount;
    document.getElementById("rule-prev").disabled = currentPage <= 1;
    document.getElementById("rule-next").disabled = currentPage >= pageCount;
}

for (const input of Object.values(filters)) {
    input.addEventListener("input", () => {
        currentPage = 1;
        renderRules();
    });
}
document.getElementById("rule-prev").onclick = () => { currentPage--; renderRules(); };
document.getElementById("rule-next").onclick = () => { currentPage++; renderRules(); };

const outputHeaders = ["规则名称", "大棚编号", "指标", "下限", "上限", "单位", "状态", "备注", "更新时间"];
function outputValues(rule) {
    return [rule.name, rule.greenhouseId, metricNames[rule.metricType] || rule.metricType,
        rule.lower ?? "", rule.upper ?? "", rule.unit, rule.enabled ? "启用" : "停用",
        rule.remark ?? "", rule.updatedAt ?? ""];
}

function csvCell(value) {
    let text = String(value);
    if (typeof value === "string" && /^[\s]*[=+@-]|^[\t\r\n]/.test(text)) text = "'" + text;
    return '"' + text.replaceAll('"', '""') + '"';
}
document.getElementById("rule-export").onclick = () => {
    const rows = [outputHeaders, ...filteredRules().map(outputValues)];
    const csv = rows.map(row => row.map(csvCell).join(",")).join("\r\n");
    const url = URL.createObjectURL(new Blob(["\uFEFF", csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a");
    link.href = url;
    link.download = "阈值规则.csv";
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
};

document.getElementById("rule-print").onclick = () => {
    const preview = window.open("", "_blank");
    if (!preview) {
        document.getElementById("threshold-status").textContent = "请允许浏览器打开打印窗口。";
        return;
    }
    const doc = preview.document;
    doc.title = "阈值规则清单";
    const heading = doc.createElement("h1");
    heading.textContent = doc.title;
    const criteria = doc.createElement("p");
    criteria.textContent = [
        "名称：" + (filters.name.value.trim() || "全部"),
        "大棚：" + (filters.house.value || "全部"),
        "指标：" + filters.metric.selectedOptions[0].text,
        "状态：" + filters.state.selectedOptions[0].text,
        "排序：" + filters.order.selectedOptions[0].text
    ].join("　");
    const table = doc.createElement("table");
    const style = doc.createElement("style");
    style.textContent = "table{border-collapse:collapse;width:100%}th,td{border:1px solid #888;padding:6px;overflow-wrap:anywhere}thead{display:table-header-group}";
    doc.head.append(style);
    const header = table.createTHead().insertRow();
    for (const value of outputHeaders) {
        const cell = doc.createElement("th");
        cell.textContent = value;
        header.append(cell);
    }
    const body = table.createTBody();
    for (const rule of filteredRules()) {
        const row = body.insertRow();
        for (const value of outputValues(rule)) row.insertCell().textContent = value;
    }
    doc.body.append(heading, criteria, table);
    preview.focus();
    preview.print();
};

loadThresholdRules();

async function loadGreenhouses() {
    const select = document.getElementById("rule-house");
    const button = createForm.querySelector("button");
    try {
        let page = 1;
        let total;
        const houses = [];
        do {
            const url = new URL(select.dataset.url, location.origin);
            url.search = new URLSearchParams({ action: "list", status: "enabled", page, size: 100 });
            const response = await fetch(url);
            const result = await response.json();
            if (!response.ok || result.result_code !== 0) throw new Error("大棚查询失败");
            const { items } = result.data;
            total = Number(result.data.total);
            houses.push(...items);
            if (!items.length) break;
            page++;
        } while (houses.length < total);

        select.replaceChildren(new Option(houses.length ? "请选择大棚" : "暂无启用大棚", ""));
        for (const house of houses) {
            if (house.status === "enabled") {
                select.add(new Option(house.name + "（" + house.greenhouse_code + "）", house.id));
            }
        }
        select.disabled = button.disabled = select.options.length <= 1;
    } catch (error) {
        select.replaceChildren(new Option("大棚加载失败，请刷新重试", ""));
    }
}

if (createForm) loadGreenhouses();
