/*
 * 第12周 环境采样数据管理（唐昕煜）—— 页面逻辑
 *
 * 三条硬规则写在这里而不是只放在后端注释里：
 * 1) 0 是有效数值：卡片和列表都不允许把 0 渲染成「无数据」。
 * 2) 最新值与列表使用同一组筛选条件，避免「卡片显示 A 棚、表格显示 B 棚」。
 * 3) 自动采集数据只读：更正/删除按钮只对 source=manual 的记录出现，后端也会再校验一次。
 */
(function () {
    "use strict";

    const $ = id => document.getElementById(id);
    const SOURCE_NAMES = {real: "实采", simulated: "模拟", manual: "人工"};
    const SOURCE_BADGE = {real: "env-badge-real", simulated: "env-badge-simulated", manual: "env-badge-manual"};
    const LIVE_INTERVAL = 2000;
    const STALE_SECONDS = 10;

    let context = null;
    let metrics = new Map();
    let devices = [];
    let applied = {};
    let page = 1;
    let pageSize = 10;
    let total = 0;
    let sequence = 0;
    let liveBusy = false;
    let liveTimer = null;
    let liveGreenhouseId = null;
    let deleteTarget = null;

    const get = (action, params = {}) => window.GHApi.get("/api/environment", Object.assign({action}, params));
    const post = (action, params) => window.GHApi.post("/api/environment", Object.assign({action}, params));

    function element(tag, text) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function show(message) {
        $("env-message").textContent = message;
    }

    function metricLabel(code) {
        const metric = metrics.get(code);
        return metric ? metric.label : code;
    }

    function metricUnit(code) {
        const metric = metrics.get(code);
        return metric ? metric.unit : "";
    }

    function valueText(value) {
        if (value === null || value === undefined || value === "") return "—";
        return String(value);
    }

    function staleText(seconds) {
        if (seconds === null || seconds === undefined) return "";
        if (seconds > STALE_SECONDS) return "已过期（" + seconds + " 秒未更新）";
        return seconds + " 秒前更新";
    }

    function sourceBadge(source) {
        const badge = element("span", SOURCE_NAMES[source] || source || "未知");
        badge.className = "env-badge " + (SOURCE_BADGE[source] || "env-badge-manual");
        return badge;
    }

    function timeText(value) {
        return value ? String(value) : "";
    }

    // ---------------------------------------------------------------- 初始化

    async function loadContext() {
        const data = (await get("context")).data;
        context = data;
        metrics = new Map(data.metrics.map(metric => [metric.code, metric]));
        devices = data.devices || [];
        $("env-add").hidden = !data.can_manage;
        fillGreenhouseSelect($("env-live-greenhouse"), data.greenhouses, false);
        fillGreenhouseSelect($("env-filter").elements.namedItem("greenhouse_id"), data.greenhouses, true);
        fillMetricSelect($("env-filter").elements.namedItem("metric_type"), true);
        if (data.greenhouses.length) {
            liveGreenhouseId = data.greenhouses[0].id;
            $("env-live-greenhouse").value = String(liveGreenhouseId);
        }
        if (!data.greenhouses.length) {
            show("当前没有可用大棚档案：请先执行 11_greenhouse.sql 与 12_device_binding_seed.sql，或联系潘雨希确认大棚数据。");
        }
        if (!devices.length) {
            show("设备关联表为空：采样无法归属设备，请先执行 12_device_binding_seed.sql 的关联数据。");
        }
    }

    function fillGreenhouseSelect(select, rows, withAll) {
        select.replaceChildren();
        if (withAll) select.append(new Option("全部大棚", ""));
        rows.forEach(row => select.append(new Option(row.greenhouse_code + " " + row.name, String(row.id))));
    }

    function fillMetricSelect(select, withAll) {
        select.replaceChildren();
        if (withAll) select.append(new Option("全部指标", ""));
        metrics.forEach((metric, code) => select.append(new Option(metric.label + "（" + metric.unit + "）", code)));
    }

    /** 设备下拉始终跟随大棚：跨棚选设备会被后端拒绝，界面也不该提供这种组合。 */
    function fillDeviceSelect(select, greenhouseId, selected, withAll) {
        select.replaceChildren();
        if (withAll) select.append(new Option("全部设备", ""));
        const matched = devices.filter(device => String(device.greenhouse_id) === String(greenhouseId));
        matched.forEach(device => {
            select.append(new Option(device.device_code + " · " + metricLabel(device.metric_type), String(device.device_pk)));
        });
        if (selected && matched.some(device => String(device.device_pk) === String(selected))) {
            select.value = String(selected);
        }
        select.disabled = !matched.length;
    }

    // ---------------------------------------------------------------- 最新值卡片

    async function loadLatest() {
        if (liveBusy || !liveGreenhouseId) return;
        liveBusy = true;
        try {
            const devicePk = $("env-filter").elements.namedItem("device_pk").value;
            const data = (await get("latest", {greenhouse_id: liveGreenhouseId, device_pk: devicePk || ""})).data;
            renderCards(data);
        } catch (error) {
            // 失败时保留上一次数值，只改提示，避免卡片闪成「无数据」。
            show("最新值刷新失败：" + error.message);
        } finally {
            liveBusy = false;
        }
    }

    function renderCards(data) {
        const container = $("env-cards");
        container.replaceChildren();
        (data.cards || []).forEach(card => {
            const box = element("div");
            box.className = "env-card" + (card.has_data ? "" : " is-empty");
            box.append(element("h4", card.label + "（" + card.unit + "）"));
            const value = element("div");
            value.className = "env-value";
            value.append(element("span", card.has_data ? valueText(card.sample.value) : "—"));
            value.append(element("span", card.unit, " " + card.unit));
            box.append(value);

            const meta = element("div");
            meta.className = "env-meta";
            if (!card.has_data) {
                meta.textContent = "暂无该指标的采样记录";
            } else {
                const sample = card.sample;
                meta.append(sourceBadge(sample.source));
                meta.append(element("span", " " + timeText(sample.sample_time)));
                const seconds = typeof card.age_seconds === "number" ? card.age_seconds : null;
                if (card.stale) {
                    const stale = element("span", " " + (seconds === null ? "过期" : staleText(seconds)));
                    stale.className = "env-badge env-badge-stale";
                    meta.append(stale);
                } else if (seconds !== null) {
                    meta.append(element("span", " · " + staleText(seconds)));
                }
            }
            box.append(meta);
            container.append(box);
        });
        if (data.server_time) {
            show("最新值读取于 " + data.server_time + "；每2秒自动刷新，超过10秒未更新标记为过期。");
        }
    }

    function startLive() {
        if (liveTimer) return;
        liveTimer = setInterval(() => {
            if (document.hidden) return;
            loadLatest();
        }, LIVE_INTERVAL);
    }

    // ---------------------------------------------------------------- 列表

    function filtersFromForm() {
        const values = Object.fromEntries(new FormData($("env-filter")));
        Object.keys(values).forEach(key => {
            if (values[key] === "") delete values[key];
            if (key === "device_pk" && values[key] === "") delete values[key];
        });
        return values;
    }

    function updateDeviceFilter() {
        const greenhouseId = $("env-filter").elements.namedItem("greenhouse_id").value;
        const deviceSelect = $("env-filter").elements.namedItem("device_pk");
        const previous = deviceSelect.value;
        fillDeviceSelect(deviceSelect, greenhouseId, previous, true);
    }

    async function load() {
        const request = ++sequence;
        const filters = Object.assign({}, applied);
        const requestedPage = page;
        show("正在查询采样记录……");
        const list = await get("list", Object.assign({}, filters, {page: requestedPage, size: pageSize}));
        if (request !== sequence) return;
        total = Number(list.data.total);
        const pages = Math.max(1, Math.ceil(total / pageSize));
        if (page > pages) {
            page = pages;
            return load();
        }
        renderRows(list.data.items);
        $("env-summary").textContent = total
                ? "当前筛选共 " + total + " 条采样；统计、导出与打印沿用同一组条件。"
                : "当前条件下暂无采样记录，条数 0。0 与无数据在卡片和列表中分别标注。";
        $("env-pages").textContent = "第 " + page + " / " + pages + " 页，共 " + total + " 条";
        $("env-prev").disabled = page <= 1;
        $("env-next").disabled = page >= pages;
        show(total ? "查询成功" : "当前条件下暂无采样记录");
    }

    function renderRows(items) {
        const body = $("env-rows");
        body.replaceChildren();
        if (!items.length) {
            const tr = element("tr");
            const td = element("td", "当前条件下暂无采样记录");
            td.colSpan = 9;
            tr.append(td);
            body.append(tr);
            return;
        }
        items.forEach(row => {
            const tr = element("tr");
            // 首列用稳定的数据库记录编号；长采样键只在管理员详情的技术信息里按需出现。
            tr.append(element("td", valueText(row.id)));
            tr.append(element("td", row.greenhouse_code + " " + row.greenhouse_name));
            tr.append(element("td", row.device_code || String(row.device_pk)));
            tr.append(element("td", metricLabel(row.metric_type)));
            tr.append(element("td", valueText(row.value) + " " + row.unit));
            tr.append(element("td", timeText(row.sample_time)));
            const source = element("td");
            source.append(sourceBadge(row.source));
            tr.append(source);
            tr.append(element("td", String(row.created_by_name || "未记录")));

            const actions = element("td");
            actions.append(button("详情", () => openDetail(row.id)));
            if (row.source === "manual" && context.can_manage) {
                actions.append(button("编辑", () => openEditor(row)));
                actions.append(button("删除", () => deleteRow(row), true));
            }
            tr.append(actions);
            body.append(tr);
        });
    }

    function button(text, action, danger) {
        const node = element("button", text);
        node.type = "button";
        node.className = danger ? "btn btn-danger btn-sm" : "btn btn-default btn-sm";
        node.addEventListener("click", () => {
            node.disabled = true;
            Promise.resolve(action())
                    .catch(error => show(error.message))
                    .finally(() => {
                        node.disabled = false;
                    });
        });
        return node;
    }

    // ---------------------------------------------------------------- 详情

    async function openDetail(id) {
        const data = (await get("detail", {id})).data;
        const sample = data.sample;
        const list = $("env-detail-body");
        list.replaceChildren();
        const fields = [
            ["记录编号", sample.id],
            ["大棚", sample.greenhouse_code + " " + sample.greenhouse_name],
            ["设备", (sample.device_code || sample.device_pk) + (sample.device_name ? " · " + sample.device_name : "")],
            ["指标", metricLabel(sample.metric_type) + "（" + sample.unit + "）"],
            ["数值", valueText(sample.value) + " " + sample.unit],
            ["来源", SOURCE_NAMES[sample.source] || sample.source],
            ["采样时间", timeText(sample.sample_time)],
            ["接收时间", timeText(sample.received_at)],
            ["录入人", sample.created_by_name || "未记录"],
            ["最后更新", timeText(sample.updated_at)]
        ];
        fields.forEach(([label, value]) => {
            const text = value === undefined || value === null || value === "" ? "未记录" : String(value);
            list.append(element("dt", label), element("dd", text));
        });
        if (sample.is_deleted) {
            list.append(element("dt", "删除时间"), element("dd", timeText(sample.deleted_at)));
        }

        // 技术信息只对管理员按需展开：普通用户日常页面不出现长采样键。
        const tech = $("env-tech");
        tech.hidden = !context.can_manage;
        tech.open = false;
        $("env-detail-key").textContent = sample.sample_key || "";

        const body = $("env-detail-revisions");
        body.replaceChildren();
        if (!data.revisions.length) {
            const tr = element("tr");
            const td = element("td", "暂无修改记录");
            td.colSpan = 5;
            tr.append(td);
            body.append(tr);
        } else {
            data.revisions.forEach(revision => {
                const tr = element("tr");
                tr.append(element("td", timeText(revision.changed_at)));
                tr.append(element("td", revision.changed_by_name || String(revision.changed_by)));
                tr.append(element("td", valueText(revision.old_value) + " → " +
                        valueText(revision.new_value) + (sample.unit ? " " + sample.unit : "")));
                tr.append(element("td", timeText(revision.old_sample_time) + " → " + timeText(revision.new_sample_time)));
                tr.append(element("td", revision.reason || "未填写"));
                body.append(tr);
            });
        }
        $("env-detail").showModal();
    }

    // ---------------------------------------------------------------- 人工记录维护

    function openEditor(row) {
        const form = $("env-form");
        form.reset();
        const field = name => form.elements.namedItem(name);
        $("env-editor-title").textContent = row ? "编辑采样记录" : "新增采样记录";
        $("env-editor-note").textContent = row
                ? "来源仍为「人工」，记录编号与采样键不变，保存后列表条数不增加。"
                : "手工录入的数据会计为「人工」来源。";
        $("env-form-message").textContent = "";
        field("id").value = row ? row.id : "";
        field("greenhouse_id").disabled = Boolean(row);
        field("device_pk").disabled = Boolean(row);
        field("metric_type").disabled = Boolean(row);
        $("env-reason-field").hidden = !row;
        field("reason").required = Boolean(row);

        fillGreenhouseSelect(field("greenhouse_id"), context.greenhouses, false);
        const greenhouseId = row ? row.greenhouse_id
                : (applied.greenhouse_id || liveGreenhouseId || context.greenhouses[0].id);
        field("greenhouse_id").value = String(greenhouseId);
        fillDeviceSelect(field("device_pk"), greenhouseId, row ? row.device_pk : null, false);
        fillMetricSelect(field("metric_type"), false);

        if (row) {
            field("metric_type").replaceChildren(new Option(metricLabel(row.metric_type), row.metric_type));
            field("metric_type").value = row.metric_type;
            field("value").value = valueText(row.value);
            field("sample_time").value = String(row.sample_time).replace(" ", "T").slice(0, 16);
        } else {
            field("value").value = "";
            const now = new Date();
            now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
            field("sample_time").value = now.toISOString().slice(0, 16);
            syncEditorMetric();
        }
        updateEditorHint(row);
        $("env-editor").showModal();
    }

    /** 一台采样设备只绑一个指标：选中设备就把指标带出来，避免把温度设备选成湿度。 */
    function syncEditorMetric() {
        const form = $("env-form");
        if (form.elements.namedItem("id").value) return;
        const devicePk = form.elements.namedItem("device_pk").value;
        const device = devices.find(item => String(item.device_pk) === String(devicePk));
        if (!device) return;
        form.elements.namedItem("metric_type").value = device.metric_type;
    }

    function updateEditorHint(row) {
        const form = $("env-form");
        const metric = metrics.get(form.elements.namedItem("metric_type").value);
        const hint = $("env-editor-hint");
        if (!metric) {
            hint.textContent = "";
            return;
        }
        hint.textContent = row
                ? "可修改数值与采样时间，修改原因必填；每次修改都会写入修改记录，原值、操作人与时间都保留。"
                : "保存后来源固定为「人工」；指标随设备绑定自动带出，允许范围 " + metric.range +
                  "，0 与负温度都是有效值。";
    }

    /** 删除只用浏览器原生确认框，不再要求填写删除原因（后台允许省略原因）。 */
    async function deleteRow(row) {
        const label = metricLabel(row.metric_type) + "：" + valueText(row.value) + row.unit;
        if (!window.confirm("确认删除这条人工采样记录？\n" + label)) return;
        show("正在删除……");
        try {
            await post("delete", {id: row.id});
            await load();
            await loadLatest();
            await loadStats(true);
            show("删除成功：记录已从当前列表移除，后台保留原记录与删除人时间。");
        } catch (error) {
            show("删除失败：" + error.message);
        }
    }

    // ---------------------------------------------------------------- 统计与曲线

    async function loadStats(silent) {
        const filters = Object.assign({}, applied);
        const cards = $("env-stat-cards");
        const chart = $("env-chart");
        cards.replaceChildren();
        chart.replaceChildren();
        const data = (await get("stats", filters)).data;
        $("env-stat-scope").textContent = "统计范围：当前已执行查询条件（大棚 " + filterName(filters.greenhouse_id) +
                "，设备 " + deviceName(filters.device_pk) +
                "，指标 " + (filters.metric_type ? metricLabel(filters.metric_type) : "全部") +
                "，来源 " + (SOURCE_NAMES[filters.source] || "全部") + "）。";
        if (data.empty) {
            cards.append(element("p", "暂无可统计数据"));
            if (!silent) show("当前条件下暂无可统计数据。");
            return;
        }
        const rows = filters.metric_type
                ? data.summary.filter(item => item.metric_type === filters.metric_type)
                : data.summary;
        (rows.length ? rows : data.summary).forEach(row => {
            const unit = metricUnit(row.metric_type);
            const box = element("div");
            box.className = "env-card";
            box.append(element("h4", metricLabel(row.metric_type) + "（" + unit + "）"));
            box.append(element("div", "共 " + row.total + " 条"));
            const meta = element("div");
            meta.className = "env-meta";
            meta.textContent = "平均 " + Number(row.avg_value).toFixed(2) + unit +
                    " · 最小 " + valueText(row.min_value) + unit +
                    " · 最大 " + valueText(row.max_value) + unit;
            box.append(meta);
            const range = element("div");
            range.className = "env-meta";
            range.textContent = timeText(row.first_time) + " 至 " + timeText(row.last_time);
            box.append(range);
            cards.append(box);
        });
        const sources = (data.sources || []).map(item => (SOURCE_NAMES[item.source] || item.source) + " " + item.total).join("、");
        if (sources) cards.append(element("p", "来源构成：" + sources));

        if (!filters.metric_type) {
            chart.append(element("p", "五类指标单位不同，不混画在同一坐标里；选择一个指标后可查看趋势曲线。"));
            if (!silent) show("已显示各指标分组统计；选择单一指标可查看趋势。");
            return;
        }
        try {
            const trend = (await get("trend", Object.assign({}, filters, {points: 200}))).data;
            drawChart(chart, trend.points, metricUnit(filters.metric_type),
                    metricLabel(filters.metric_type) + " 历史趋势");
        } catch (error) {
            chart.append(element("p", "趋势读取失败：" + error.message));
        }
        if (!silent) show("统计与趋势沿用当前筛选条件。");
    }

    /** 手写 SVG 曲线：不依赖图表插件，打印时同一段 SVG 可直接放进打印区。 */
    function drawChart(container, points, unit, title) {
        if (!points || !points.length) {
            container.append(element("p", "没有可绘制的数据点。"));
            return;
        }
        const NS = "http://www.w3.org/2000/svg";
        const width = 820;
        const height = 220;
        const pad = {left: 58, right: 16, top: 14, bottom: 28};
        const values = points.map(point => Number(point.value));
        let min = Math.min.apply(null, values);
        let max = Math.max.apply(null, values);
        if (min === max) {
            const delta = Math.abs(min) * 0.1 || 1;
            min -= delta;
            max += delta;
        }
        const span = max - min;
        const x = index => pad.left + (points.length === 1
                ? 0 : (index / (points.length - 1)) * (width - pad.left - pad.right));
        const y = value => pad.top + (1 - (Number(value) - min) / span) * (height - pad.top - pad.bottom);

        const svg = document.createElementNS(NS, "svg");
        svg.setAttribute("viewBox", "0 0 " + width + " " + height);
        svg.setAttribute("role", "img");
        svg.setAttribute("aria-label", title + "，共 " + points.length + " 个数据点");

        [0, 0.5, 1].forEach(ratio => {
            const value = max - span * ratio;
            const line = document.createElementNS(NS, "line");
            line.setAttribute("x1", pad.left);
            line.setAttribute("x2", width - pad.right);
            line.setAttribute("y1", y(value));
            line.setAttribute("y2", y(value));
            line.setAttribute("stroke", "#e7ecf1");
            svg.append(line);
            const label = document.createElementNS(NS, "text");
            label.setAttribute("x", pad.left - 6);
            label.setAttribute("y", y(value) + 4);
            label.setAttribute("text-anchor", "end");
            label.setAttribute("font-size", "11");
            label.setAttribute("fill", "#8494a7");
            label.textContent = Number(value).toFixed(1);
            svg.append(label);
        });

        const path = document.createElementNS(NS, "polyline");
        path.setAttribute("points", points.map((point, index) => x(index) + "," + y(point.value)).join(" "));
        path.setAttribute("fill", "none");
        path.setAttribute("stroke", "#3f8ae0");
        path.setAttribute("stroke-width", "2");
        svg.append(path);

        points.forEach((point, index) => {
            if (points.length > 30 && index % Math.ceil(points.length / 30) !== 0) return;
            const dot = document.createElementNS(NS, "circle");
            dot.setAttribute("cx", x(index));
            dot.setAttribute("cy", y(point.value));
            dot.setAttribute("r", "2.5");
            dot.setAttribute("fill", point.source === "manual" ? "#7c6bd0" : "#3f8ae0");
            const tip = document.createElementNS(NS, "title");
            tip.textContent = timeText(point.sample_time) + " " + valueText(point.value) + unit +
                    "（" + (SOURCE_NAMES[point.source] || point.source) + "）";
            dot.append(tip);
            svg.append(dot);
        });

        const first = document.createElementNS(NS, "text");
        first.setAttribute("x", pad.left);
        first.setAttribute("y", height - 8);
        first.setAttribute("font-size", "11");
        first.setAttribute("fill", "#8494a7");
        first.textContent = timeText(points[0].sample_time);
        svg.append(first);

        const last = document.createElementNS(NS, "text");
        last.setAttribute("x", width - pad.right);
        last.setAttribute("y", height - 8);
        last.setAttribute("text-anchor", "end");
        last.setAttribute("font-size", "11");
        last.setAttribute("fill", "#8494a7");
        last.textContent = timeText(points[points.length - 1].sample_time);
        svg.append(last);
        container.append(svg);
    }

    // ---------------------------------------------------------------- 导出与打印

    function exportCsv() {
        const parameters = new URLSearchParams(Object.assign({action: "export"}, applied));
        window.location.assign(window.GH_CONTEXT_PATH + "/api/environment?" + parameters.toString());
    }

    function filterName(id) {
        if (!id) return "全部";
        const row = context.greenhouses.find(item => String(item.id) === String(id));
        return row ? row.greenhouse_code + " " + row.name : String(id);
    }

    function deviceName(pk) {
        if (!pk) return "全部";
        const row = devices.find(item => String(item.device_pk) === String(pk));
        return row ? row.device_code : String(pk);
    }

    async function printReport() {
        const printButton = $("env-print");
        printButton.disabled = true;
        show("正在准备全部筛选记录与曲线……");
        try {
            const filters = Object.assign({}, applied);
            const report = (await get("report", filters)).data;
            const area = $("env-print-area");
            area.replaceChildren();
            area.append(element("h2", "农业大棚系统环境采样报表"));
            const readable = "大棚：" + filterName(filters.greenhouse_id) +
                    "；设备：" + deviceName(filters.device_pk) +
                    "；指标：" + (filters.metric_type ? metricLabel(filters.metric_type) : "全部指标") +
                    "；来源：" + (SOURCE_NAMES[filters.source] || "全部") +
                    "；时间：" + (filters.from || "不限") + " 至 " + (filters.to || "不限") +
                    "；排序：" + (({sample_time: "采样时间", value: "数值", received_at: "接收时间", id: "记录ID"})[filters.sort] || "采样时间") +
                    (filters.dir === "asc" ? " 升序" : " 降序");
            area.append(element("p", readable));
            area.append(element("p", "打印人：" + context.operator + "；打印时间：" +
                    new Date().toLocaleString("zh-CN", {timeZone: "Asia/Shanghai"}) +
                    "；共 " + report.items.length + " 条" + (report.truncated ? "（超出打印上限，已截断）" : "")));

            if (filters.metric_type) {
                const stats = (await get("stats", filters)).data;
                if (!stats.empty) {
                    const row = stats.summary.find(item => item.metric_type === filters.metric_type) || stats.summary[0];
                    const trends = (await get("trend", Object.assign({}, filters, {points: 200}))).data;
                    const box = element("div");
                    box.className = "env-chart";
                    box.append(element("p",
                        metricLabel(row.metric_type) + "：共 " + row.total + " 条，平均 " +
                        Number(row.avg_value).toFixed(2) + metricUnit(row.metric_type) +
                        "，最小 " + valueText(row.min_value) + "，最大 " + valueText(row.max_value)));
                    drawChart(box, trends.points, metricUnit(row.metric_type), metricLabel(row.metric_type) + " 趋势");
                    area.append(box);
                }
            }

            const table = element("table");
            const head = element("thead");
            const headRow = element("tr");
            ["记录编号", "大棚", "设备", "指标", "数值", "单位", "采样时间", "来源"].forEach(title => headRow.append(element("th", title)));
            head.append(headRow);
            table.append(head);
            const body = element("tbody");
            if (!report.items.length) {
                const tr = element("tr");
                const td = element("td", "当前条件下没有采样记录");
                td.colSpan = 8;
                tr.append(td);
                body.append(tr);
            } else {
                report.items.forEach(row => {
                    const tr = element("tr");
                    tr.append(element("td", valueText(row.id)));
                    tr.append(element("td", row.greenhouse_code));
                    tr.append(element("td", row.device_code || String(row.device_pk)));
                    tr.append(element("td", metricLabel(row.metric_type)));
                    tr.append(element("td", valueText(row.value)));
                    tr.append(element("td", row.unit));
                    tr.append(element("td", timeText(row.sample_time)));
                    tr.append(element("td", SOURCE_NAMES[row.source] || row.source));
                    body.append(tr);
                });
            }
            table.append(body);
            area.append(table);
            window.print();
            show("打印预览已准备：表格与曲线使用同一组筛选条件。");
        } catch (error) {
            show("打印准备失败：" + error.message);
        } finally {
            printButton.disabled = false;
        }
    }

    // ---------------------------------------------------------------- 事件绑定

    function bindEvents() {
        $("env-filter").addEventListener("submit", event => {
            event.preventDefault();
            applied = filtersFromForm();
            page = 1;
            load().catch(error => show(error.message));
            loadLatest();
            loadStats(true).catch(error => show(error.message));
        });

        $("env-filter").elements.namedItem("greenhouse_id").addEventListener("change", updateDeviceFilter);

        $("env-filter").elements.namedItem("metric_type").addEventListener("change", () => {
            loadStats(true).catch(error => show(error.message));
        });

        $("env-reset").addEventListener("click", () => {
            $("env-filter").reset();
            updateDeviceFilter();
            applied = filtersFromForm();
            page = 1;
            load().catch(error => show(error.message));
            loadLatest();
        });

        $("env-prev").addEventListener("click", () => {
            if (page > 1) {
                page--;
                load().catch(error => show(error.message));
            }
        });

        $("env-next").addEventListener("click", () => {
            if (page * 10 < total) {
                page++;
                load().catch(error => show(error.message));
            }
        });

        $("env-refresh").addEventListener("click", () => loadLatest());
        $("env-live-greenhouse").addEventListener("change", event => {
            liveGreenhouseId = event.target.value;
            loadLatest();
        });
        $("env-add").addEventListener("click", () => openEditor(null));
        $("env-export").addEventListener("click", exportCsv);
        $("env-print").addEventListener("click", printReport);
        $("env-list-refresh").addEventListener("click", () => {
            load().catch(error => show(error.message));
            loadLatest();
            loadStats(true).catch(error => show(error.message));
        });

        $("env-page-size").addEventListener("change", event => {
            pageSize = Number(event.target.value) || 10;
            page = 1;
            load().catch(error => show(error.message));
        });

        $("env-copy-key").addEventListener("click", async () => {
            const text = $("env-detail-key").textContent || "";
            try {
                await navigator.clipboard.writeText(text);
                show("采样键已复制。");
            } catch (error) {
                show("复制失败，请手动选中后复制。");
            }
        });

        $("env-form").elements.namedItem("greenhouse_id").addEventListener("change", event => {
            fillDeviceSelect($("env-form").elements.namedItem("device_pk"), event.target.value, null, false);
            syncEditorMetric();
            updateEditorHint(false);
        });
        $("env-form").elements.namedItem("device_pk").addEventListener("change", () => {
            syncEditorMetric();
            updateEditorHint(false);
        });
        $("env-form").elements.namedItem("metric_type").addEventListener("change", () => updateEditorHint(false));

        $("env-form").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.target;
            const data = Object.fromEntries(new FormData(form));
            const editing = Boolean(data.id);
            $("env-save").disabled = true;
            try {
                await post(editing ? "update" : "create", data);
                $("env-editor").close();
                await load();
                await loadLatest();
                await loadStats(true);
                show(editing ? "更正已保存，详情里可以看到原值与新值。" : "人工测试记录已保存（来源：人工）。");
            } catch (error) {
                $("env-form-message").textContent = error.message;
            } finally {
                $("env-save").disabled = false;
            }
        });

        document.querySelectorAll("[data-close]").forEach(node => {
            node.addEventListener("click", () => $(node.dataset.close).close());
        });
    }

    (async function init() {
        try {
            await loadContext();
            bindEvents();
            updateDeviceFilter();
            applied = filtersFromForm();
            await Promise.all([
                load(),
                loadLatest(),
                loadStats(true).catch(error => show(error.message))
            ]);
            startLive();
        } catch (error) {
            show("初始化失败：" + error.message);
        }
    })();
})();
