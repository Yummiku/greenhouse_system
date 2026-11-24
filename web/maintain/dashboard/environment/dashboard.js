/*
 * 第13周 环境监测个人驾驶舱（唐昕煜）—— 页面逻辑
 *
 * 口径（与《第13周工作安排与执行指导》附十六一致，必须和交接清单里的说明一致）：
 * - 所选大棚 + 单一指标的采样数量、均值、最小、最大与趋势；
 * - 时间轴为 sample_time，默认最近 7 个自然日（含今天），起日 00:00 到结束日次日 00:00 之前；
 * - 单位随指标，来源分实采 / 模拟 / 人工，不混算不同单位；
 * - 页面每 30 秒刷新已应用条件，不与上一请求重叠；失败保留上次数值并标明读取失败与上次成功时间。
 */
(function () {
    "use strict";

    const $ = id => document.getElementById(id);
    const REFRESH_MS = 30000;
    const SOURCE_NAMES = {real: "实采", simulated: "模拟", manual: "人工"};

    let context = null;
    let metrics = new Map();
    let applied = {greenhouse_id: "", metric_type: "air_temperature", days: "7"};
    let busy = false;
    let timer = null;
    let lastSuccess = "";

    const api = params => window.GHApi.get("/api/environment", params);

    function element(tag, text) {
        const node = document.createElement(tag);
        if (text !== undefined) node.textContent = text;
        return node;
    }

    function show(message) {
        $("dash-message").textContent = message;
    }

    function metricLabel(code) {
        const metric = metrics.get(code);
        return metric ? metric.label : code;
    }

    function valueText(value) {
        if (value === null || value === undefined || value === "") return "—";
        return String(value);
    }

    async function loadContext() {
        const data = (await api({action: "context"})).data;
        context = data;
        metrics = new Map(data.metrics.map(metric => [metric.code, metric]));

        const greenhouse = $("dash-greenhouse");
        greenhouse.replaceChildren();
        data.greenhouses.forEach(row => greenhouse.append(
                new Option(row.greenhouse_code + " " + row.name, String(row.id))));

        const metric = $("dash-metric");
        metric.replaceChildren();
        data.metrics.forEach(item => metric.append(new Option(item.label + "（" + item.unit + "）", item.code)));

        if (!data.greenhouses.length) {
            show("当前没有可用大棚档案：请先执行 11/12 脚本，或联系潘雨希确认大棚数据。");
            return;
        }
        greenhouse.value = String(data.greenhouses[0].id);
        metric.value = "air_temperature";
        applied.greenhouse_id = greenhouse.value;
        applied.metric_type = metric.value;
    }

    function renderCards(data) {
        const container = $("dash-cards");
        container.replaceChildren();
        const unit = data.unit || "";
        const cards = [
            ["有效采样数量", data.empty ? "0" : valueText(data.total), "条"],
            ["平均值", data.empty ? "—" : Number(data.avg_value).toFixed(2), unit],
            ["最小值", data.empty ? "—" : valueText(data.min_value), unit],
            ["最大值", data.empty ? "—" : valueText(data.max_value), unit]
        ];
        cards.forEach(([label, value, suffix]) => {
            const box = element("div");
            box.className = "env-card" + (data.empty ? " is-empty" : "");
            box.append(element("h4", label));
            const line = element("div");
            line.className = "env-value";
            line.append(element("span", value));
            line.append(element("span", " " + suffix));
            box.append(line);
            container.append(box);
        });
    }

    function renderLatest(data) {
        const container = $("dash-latest");
        container.replaceChildren();
        const card = data.latest;
        if (!card || !card.has_data) {
            container.append(element("p", "该指标暂无采样数据。"));
            return;
        }
        const sample = card.sample;
        const seconds = typeof card.age_seconds === "number" ? card.age_seconds : null;
        const line = element("p");
        line.append(element("span", "最新采样：" + valueText(sample.value) + " " + sample.unit));
        line.append(element("span", " · 来源 " + (SOURCE_NAMES[sample.source] || sample.source)));
        line.append(element("span", " · 采样时间 " + valueText(sample.sample_time)));
        if (card.stale) {
            const badge = element("span", seconds === null ? " 已过期" : " 已过期（" + seconds + " 秒未更新）");
            badge.className = "env-badge env-badge-stale";
            line.append(badge);
        } else if (seconds !== null) {
            line.append(element("span", " · " + seconds + " 秒前更新"));
        }
        container.append(line);
        container.append(element("p", "过期表示这条数据没有更新，不等于传感器故障。"));
    }

    function renderSources(data) {
        const sources = (data.sources || [])
                .map(item => (SOURCE_NAMES[item.source] || item.source) + " " + item.total)
                .join("、");
        $("dash-sources").textContent = sources ? "来源构成：" + sources : "";
    }

    function renderChart(data) {
        const container = $("dash-chart");
        container.replaceChildren();
        const points = data.trend || [];
        if (!points.length) {
            container.append(element("p", "当前条件下没有可绘制的数据点。"));
            return;
        }
        const values = points.map(point => Number(point.value));
        const min = Math.min(...values);
        const max = Math.max(...values);
        const span = max - min || 1;
        const width = 900;
        const height = 240;
        const pad = 36;
        const step = points.length > 1 ? (width - pad * 2) / (points.length - 1) : 0;
        const coords = points.map((point, index) => {
            const x = pad + step * index;
            const y = height - pad - ((Number(point.value) - min) / span) * (height - pad * 2);
            return [x, y, point];
        });

        const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
        svg.setAttribute("viewBox", "0 0 " + width + " " + height);
        svg.setAttribute("class", "env-chart-svg");
        svg.setAttribute("role", "img");
        svg.setAttribute("aria-label", data.metric_label + " 趋势图");

        const axis = document.createElementNS("http://www.w3.org/2000/svg", "path");
        axis.setAttribute("d", "M" + pad + " " + (height - pad) + "H" + (width - pad));
        axis.setAttribute("stroke", "#cfd8dc");
        axis.setAttribute("fill", "none");
        svg.append(axis);

        const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
        path.setAttribute("d", coords.map(([x, y], index) => (index === 0 ? "M" : "L") + x + " " + y).join(" "));
        path.setAttribute("stroke", "#1b5e20");
        path.setAttribute("stroke-width", "2");
        path.setAttribute("fill", "none");
        svg.append(path);

        coords.forEach(([x, y, point]) => {
            const dot = document.createElementNS("http://www.w3.org/2000/svg", "circle");
            dot.setAttribute("cx", x);
            dot.setAttribute("cy", y);
            dot.setAttribute("r", "3");
            dot.setAttribute("fill", "#1b5e20");
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent = point.sample_time + " " + point.value + data.unit +
                    "（" + (SOURCE_NAMES[point.source] || point.source) + "）";
            dot.append(title);
            svg.append(dot);
        });

        const caption = element("p");
        caption.className = "env-note";
        caption.textContent = data.metric_label + "（" + data.unit + "）：" +
                valueText(data.from) + " 至 " + valueText(data.to) + " 共 " + points.length + " 个点，" +
                "纵轴范围 " + min + " ~ " + max + data.unit + "。";
        container.append(caption);
        container.append(svg);
    }

    async function load(manual) {
        if (busy || !applied.greenhouse_id) return;
        busy = true;
        try {
            const data = (await api(Object.assign({action: "dashboard"}, applied))).data;
            renderCards(data);
            renderSources(data);
            renderLatest(data);
            renderChart(data);
            lastSuccess = data.server_time || new Date().toLocaleString("zh-CN");
            $("dash-updated").textContent = "统计范围：" + data.from + " 至 " + data.to +
                    "（" + data.days + " 天）；大棚 " + data.greenhouse_code + " " + data.greenhouse_name +
                    "；指标 " + data.metric_label + "；读取于 " + lastSuccess;
            show(data.empty ? "当前条件下没有采样数据（不是读取失败）。" : "读取成功。");
        } catch (error) {
            // 失败时保留上一次数值，只改提示，避免把旧数据伪装成刚更新。
            show("读取失败：" + error.message + (lastSuccess ? "；上次成功时间 " + lastSuccess : ""));
        } finally {
            busy = false;
        }
        if (manual) show($("dash-message").textContent);
    }

    function bindEvents() {
        $("dash-query").addEventListener("submit", event => {
            event.preventDefault();
            applied.greenhouse_id = $("dash-greenhouse").value;
            applied.metric_type = $("dash-metric").value;
            applied.days = $("dash-days").value;
            load(true);
        });
        $("dash-refresh").addEventListener("click", () => load(true));
    }

    (async function init() {
        try {
            await loadContext();
            bindEvents();
            await load(false);
            timer = setInterval(() => {
                if (document.hidden) return;
                load(false);
            }, REFRESH_MS);
        } catch (error) {
            show("初始化失败：" + error.message);
        }
    })();
})();
