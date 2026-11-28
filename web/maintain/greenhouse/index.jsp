<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "大棚档案管理"); %>
<!DOCTYPE html>
<html lang="zh-CN">

<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>农业大棚系统 - 大棚档案管理</title>
    <%@ include file="/home/frame/frame_style.jsp" %>
    <style>
        .greenhouse-module section.portlet {
            margin-top: 16px;
        }

        .greenhouse-module #filter {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 10px;
            margin-bottom: 12px;
        }

        .greenhouse-module #filter .form-control {
            width: auto;
            min-width: 120px;
        }

        .greenhouse-module #filter input {
            flex: 1;
            min-width: 160px;
        }

        .greenhouse-module .controls>button {
            margin: 4px 6px 4px 0;
        }

        .greenhouse-module #rows button {
            margin: 3px;
        }

        .greenhouse-module #msg {
            white-space: pre-wrap;
            margin: 14px 0;
        }

        .gh-summary-cards {
            display: flex;
            flex-wrap: wrap;
            gap: 12px
        }

        .gh-summary-cards article {
            flex: 1 1 150px;
            padding: 16px;
            background: #edf6f0
        }

        .gh-summary-cards strong {
            display: block;
            font-size: 26px;
            color: #216143
        }

        .greenhouse-module #stats {
            font-size: 16px;
        }

        .greenhouse-module dialog {
            width: min(760px, 94vw);
            max-height: 90vh;
            overflow: auto;
            border: 1px solid #bbb;
            border-radius: 8px;
            padding: 24px;
        }

        .greenhouse-module dialog::backdrop {
            background: #0006;
        }

        .greenhouse-module dialog label {
            display: inline-block;
            width: min(280px, 100%);
            margin: 8px;
            vertical-align: top;
        }

        .greenhouse-module pre {
            white-space: pre-wrap;
            overflow-wrap: anywhere;
        }

        .greenhouse-module [hidden] {
            display: none !important;
        }

        .greenhouse-print-title {
            display: none;
        }

        @media print {

            .page-header,
            .page-sidebar-wrapper,
            .page-footer,
            .scroll-to-top,
            .greenhouse-module .page-title,
            .greenhouse-module .page-bar,
            .greenhouse-module .controls,
            .greenhouse-module dialog,
            .greenhouse-module #msg {
                display: none !important;
            }

            body.page-header-fixed {
                padding-top: 0 !important;
                background: white !important;
            }

            .page-container,
            .page-content-wrapper,
            .page-content.greenhouse-module {
                margin: 0 !important;
                padding: 0 !important;
                min-height: 0 !important;
                background: white !important;
            }

            .page-content-wrapper {
                float: none !important;
            }

            .greenhouse-module .portlet {
                border: 0 !important;
                padding: 0 !important;
                margin: 12px 0 !important;
            }

            .greenhouse-module .table-responsive {
                overflow: visible !important;
                border: 0 !important;
            }

            .greenhouse-module table {
                width: 100%;
                font-size: 11px;
            }

            .greenhouse-module tr {
                break-inside: avoid;
            }

            .greenhouse-module .greenhouse-print-title,
            .greenhouse-module #printinfo {
                display: block !important;
            }

            .greenhouse-module #printinfo {
                overflow-wrap: anywhere;
            }
        }
    </style>
</head>

<body class="page-header-fixed page-quick-sidebar-over-content">
    <%@ include file="/home/frame/frame_header.jsp" %>
    <div class="clearfix"></div>
    <div class="page-container">
        <%@ include file="/home/frame/frame_left_sidebar.jsp" %>
        <div class="page-content-wrapper">
            <main class="page-content greenhouse-module">
                <%@ include file="/home/frame/frame_page_header.jsp" %>
                <h2 class="greenhouse-print-title">农业大棚系统大棚档案清单</h2>
                <section class="portlet light bordered" aria-label="当前筛选统计">
                    <div id="stats" class="gh-summary-cards" aria-live="polite"></div>
                    <h3>大棚状态分布</h3>
                    <svg id="statusChart" viewBox="0 0 600 160" role="img" aria-label="等待查询状态分布"
                        style="width:100%;max-width:600px;height:auto">
                        <title id="statusChartTitle">等待查询状态分布</title>
                        <text x="0" y="35">启用</text>
                        <rect id="enabledBar" x="65" y="12" width="0" height="30" fill="#16805d" />
                        <text id="enabledLabel" x="65" y="65"></text>
                        <text x="0" y="105">停用</text>
                        <rect id="disabledBar" x="65" y="82" width="0" height="30" fill="#9b590d" />
                        <text id="disabledLabel" x="65" y="135"></text>
                    </svg>
                    <p id="chartNote">按当前已应用条件统计；逻辑删除档案不参与。</p>
                </section>
                <section class="controls portlet light bordered">
                    <form id="filter">
                        <input class="form-control" name="keyword" placeholder="名称或编号" /><input class="form-control"
                            name="location" placeholder="位置" /><select class="form-control" name="manager_id"
                            id="managerFilter">
                            <option value="">全部负责人</option>
                        </select><select class="form-control" name="status">
                            <option value="">全部状态</option>
                            <option value="enabled">启用</option>
                            <option value="disabled">停用</option>
                        </select><select class="form-control" name="sort">
                            <option value="created_at">创建时间</option>
                            <option value="greenhouse_code">编号</option>
                            <option value="area">面积</option>
                        </select><select class="form-control" name="dir">
                            <option value="desc">降序</option>
                            <option value="asc">升序</option>
                        </select><button class="btn blue">查询</button><button type="button" id="resetFilter"
                            class="btn default">重置</button>
                    </form>
                    <a href="map.jsp" class="btn default">位置地图</a>
                    <a href="devices.jsp" class="btn default">设备归属</a>
                    <a href="batches.jsp" class="btn default">种植批次</a>
                    <button id="add" class="btn blue" hidden>新增大棚</button><button id="export"
                        class="btn default">导出全部筛选结果</button><button id="print" class="btn default">打印预览</button><button
                        id="refresh" class="btn default">刷新</button>
                </section>
                <a class="btn default controls" href="dashboard.jsp">个人驾驶舱</a>
                <p id="msg" role="status"></p>

                <p id="printinfo" style="display: none"></p>
                <section class="portlet light bordered">
                    <div class="table-responsive">
                        <table class="table table-bordered table-hover">
                            <thead>
                                <tr>
                                    <th>编号</th>
                                    <th>名称</th>
                                    <th>位置</th>
                                    <th>面积 m²</th>
                                    <th>负责人</th>
                                    <th>状态</th>
                                    <th class="controls">操作</th>
                                </tr>
                            </thead>
                            <tbody id="rows"></tbody>
                        </table>
                    </div>
                    <div class="controls">
                        <label>每页<select id="greenhouseSize">
                                <option>10</option>
                                <option>20</option>
                                <option>50</option>
                            </select>条</label><button id="prev" class="btn default">上一页</button><span
                            id="pages"></span><button id="next" class="btn default">下一页</button>
                    </div>
                </section>
                <dialog id="edit">
                    <h2>大棚档案</h2>
                    <form id="editor">
                        <input type="hidden" name="id" /><label>编号<input class="form-control" name="greenhouse_code"
                                required maxlength="64" /></label><label>名称<input class="form-control" name="name"
                                required maxlength="100" /></label><label>位置<input class="form-control" name="location"
                                required maxlength="255" /></label><label>面积(m² 自动计算)<input class="form-control"
                                name="area" readonly placeholder="填写长、宽后自动计算" type="number" min="0.01" step="0.01"
                                required /></label><label>长度(m)<input class="form-control" name="length" type="number"
                                min="0.01" step="0.01" required /></label><label>宽度(m)<input class="form-control"
                                name="width" type="number" min="0.01" step="0.01" required /></label><label>负责人<select
                                class="form-control" name="manager_id" id="managerEdit"
                                required></select></label><label>状态<select class="form-control" name="status">
                                <option value="enabled">启用</option>
                                <option value="disabled">停用</option>
                            </select></label><label>备注<input class="form-control" name="remark"
                                maxlength="1000" /></label>
                        <p id="areaHint">面积按长度×宽度计算，四舍五入保留两位小数。</p>
                        <button class="btn default">保存</button><button class="btn default" type="button"
                            onclick="document.getElementById('edit').close()">
                            取消
                        </button>
                        <p id="formmsg"></p>
                    </form>
                </dialog>
                <dialog id="detail">
                    <h2>档案详情</h2>
                    <pre id="detailtext"></pre>
                    <button class="btn default" onclick="document.getElementById('detail').close()">
                        关闭
                    </button>
                </dialog>
            </main>
        </div>
    </div>
    <%@ include file="/home/frame/frame_footer.jsp" %>
    <%@ include file="/home/frame/frame_javascript.jsp" %>
    <script src="greenhouse-modals.js"></script>
    <script>
        (function() {
            const $ = (id) => document.getElementById(id),
                api = (a, p = {}) => GHApi.get("/api/greenhouses", {
                    action: a,
                    ...p
                });
            let applied = {},
                pageNo = 1,
                total = 0,
                canWrite = false;

            function text(id, t) {
                $(id).textContent = t;
            }
            let operatorName = "";

            function updateArea() {
                const form = $("editor");

                function centimeters(name) {
                    const value = form.elements.namedItem(name).value.trim();
                    if (!/^(?:[0-9]+|[0-9]*\.[0-9]{1,2})$/.test(value)) return null;
                    const parts = value.split(".");
                    const amount = BigInt(parts[0] || "0") * 100n + BigInt((parts[1] || "").padEnd(2, "0"));
                    return amount > 0n && amount <= 999999999999n ? amount : null;
                }
                const length = centimeters("length"),
                    width = centimeters("width");
                let message = "请填写有效的长、宽，最多两位小数。";
                if (length !== null && width !== null) {
                    const cents = (length * width + 50n) / 100n;
                    if (cents > 0n && cents <= 999999999999n) {
                        form.elements.namedItem("area").value = String(cents / 100n) + "." + String(cents % 100n)
                            .padStart(2, "0");
                        text("areaHint", "面积按长度×宽度计算，四舍五入保留两位小数。");
                        return true;
                    }
                    message = "计算面积需在0.01至9999999999.99平方米之间，请调整长、宽。";
                }
                form.elements.namedItem("area").value = "";
                text("areaHint", message);
                return false;
            }
            ["length", "width"].forEach(name => {
                $("editor").elements.namedItem(name).addEventListener("input", updateArea);
            });

            function conditions() {
                return JSON.stringify(applied);
            }

            function render(items) {
                $("rows").replaceChildren();
                items.forEach((r) => {
                    const tr = document.createElement("tr");
                    [
                        "greenhouse_code",
                        "name",
                        "location",
                        "area",
                        "manager_name",
                        "status",
                    ].forEach((k) => {
                        let td = document.createElement("td");
                        td.textContent =
                            k === "status" ? (r[k] === "enabled" ? "启用" : "停用") : r[k];
                        tr.append(td);
                    });
                    let td = document.createElement("td");
                    td.className = "controls";

                    function btn(t, fn) {
                        let b = document.createElement("button");
                        b.textContent = t;
                        b.type = "button";
                        b.className = "btn " + (t === "删除" ? "red" : "default") + " btn-xs";
                        b.onclick = () => fn().catch((e) => text("msg", e.message));
                        td.append(b);
                    }
                    [
                        ["设备", "devices.jsp"],
                        ["种植", "batches.jsp"],
                        ["地图", "map.jsp"]
                    ].forEach(([label, page]) => {
                        btn(label, async () => {
                            location.href = page + "?greenhouse_id=" +
                                encodeURIComponent(r.id);
                        });
                    });
                    btn("详情", async () => {
                        const x = await api("detail", {
                            id: r.id
                        });
                        text(
                            "detailtext",
                            Object.entries({
                                greenhouse_code: "编号",
                                name: "名称",
                                location: "位置",
                                length: "长（米）",
                                width: "宽（米）",
                                area: "面积（平方米）",
                                manager_name: "负责人",
                                status: "状态",
                                remark: "备注",
                                created_at: "创建时间",
                                updated_at: "更新时间"
                            })
                            .map(([k, label]) => label + "：" + (k === "status" ? (x.data[
                                k] === "enabled" ? "启用" : "停用") : (x.data[k] ===
                                null || x.data[k] === undefined || x.data[k] ===
                                "" ? "未填写" : x.data[k])))
                            .join("\n"),
                        );
                        $("detail").showModal();
                    });
                    if (canWrite) {
                        btn("编辑", async () => {
                            const x = await api("detail", {
                                id: r.id
                            });
                            $("editor").reset();
                            Object.entries(x.data).forEach(([k, v]) => {
                                const input = $("editor").elements.namedItem(k);
                                if (input) {
                                    input.value = v ?? "";
                                }
                            });
                            text("formmsg", "");
                            updateArea();
                            $("edit").showModal();
                        });
                        btn("删除", async () => {
                            if (!window.confirm("确认删除大棚“" + r.name + "”？有关联时须使用停用。")) {
                                return;
                            }
                            try {
                                await GHApi.post("/api/greenhouses", {
                                    action: "delete",
                                    csrf_token: csrfToken,
                                    id: r.id,
                                });
                            } catch (error) {
                                const message = error && error.message ?
                                    error.message :
                                    "删除失败，请稍后重试。";
                                text("msg", message);
                                window.alert("删除失败\n\n" + message);
                                return;
                            }
                            text("msg", await load() ? "已删除" : "已删除，列表刷新失败，请刷新查看");
                        });
                    }
                    tr.append(td);
                    $("rows").append(tr);
                });
            }

            function renderStatusChart(data) {
                const enabled = Number(data.enabled),
                    disabled = Number(data.disabled);
                const sum = enabled + disabled;
                ["enabled", "disabled"].forEach((key) => {
                    const n = Number(data[key]);
                    $(key + "Bar").setAttribute("width", sum ? String(480 * n / sum) : "0");
                    text(key + "Label", n + " 座（" + (sum ? (100 * n / sum).toFixed(1) : "0.0") + "%）");
                });
                const label = "启用 " + enabled + " 座，停用 " + disabled + " 座";
                $("statusChart").setAttribute("aria-label", label);
                text("statusChartTitle", label);
                text("chartNote", sum ? "按当前已应用条件统计；逻辑删除档案不参与。" : "当前条件无可统计档案。");
                $("statusChart").hidden = false;
            }
            let csrfToken = "";
            let queryVersion = 0,
                pageSize = 10;
            async function load() {
                const version = ++queryVersion;
                text("msg", "加载中…");
                text("stats", "统计加载中…");
                $("statusChart").hidden = true;
                try {
                    const [r, s] = await Promise.all([
                        api("list", {
                            ...applied,
                            page: pageNo,
                            size: pageSize
                        }),
                        api("stats", applied),
                    ]);
                    if (version !== queryVersion) return;
                    total = Number(r.data.total);
                    renderStatusChart(s.data);
                    render(r.data.items);
                    $("stats").replaceChildren();
                    [
                        ["大棚总数", s.data.total],
                        ["总面积（平方米）", s.data.area],
                        ["启用数", s.data.enabled],
                        ["停用数", s.data.disabled]
                    ].forEach(([label, count]) => {
                        const card = document.createElement('article'),
                            title = document.createElement('span'),
                            value = document.createElement('strong');
                        title.textContent = label;
                        value.textContent = count;
                        card.append(title, value);
                        $("stats").append(card);
                    });
                    text("pages", "第 " + pageNo + " / " + Math.max(1, Math.ceil(total / pageSize)) + " 页，共 " +
                        total + " 条");
                    $("prev").disabled = pageNo === 1;
                    $("next").disabled = pageNo * pageSize >= total;
                    text("msg", total ? "查询成功" : "暂无符合条件的大棚");
                    return true;
                } catch (e) {
                    if (version !== queryVersion) return;
                    text("stats", "统计暂不可用");
                    text("chartNote", "查询失败，请重新查询后查看状态分布。");
                    text("msg", e.message);
                    return false;
                }
            }
            $("refresh").onclick = load;
            $("resetFilter").onclick = () => {
                $("filter").reset();
                applied = {};
                pageNo = 1;
                load();
            };
            $("greenhouseSize").onchange = () => {
                pageSize = Number($("greenhouseSize").value);
                pageNo = 1;
                load();
            };
            const fieldLabels = {
                keyword: '名称或编号',
                location: '位置',
                manager_id: '负责人',
                status: '状态',
                sort: '排序依据',
                dir: '排列顺序'
            };
            Object.entries(fieldLabels).forEach(([name, label]) => {
                const input = $("filter").elements.namedItem(name),
                    wrapper = document.createElement('label');
                wrapper.textContent = label;
                input.before(wrapper);
                wrapper.append(input);
            });
            $("filter").onsubmit = (e) => {
                e.preventDefault();
                applied = Object.fromEntries(new FormData(e.target));
                pageNo = 1;
                load();
            };
            $("prev").onclick = () => {
                pageNo--;
                load();
            };
            $("next").onclick = () => {
                pageNo++;
                load();
            };
            $("add").onclick = () => {
                $("editor").reset();
                $("editor").elements.id.value = "";
                text("formmsg", "");
                updateArea();
                $("edit").showModal();
            };
            $("editor").onsubmit = async (e) => {
                e.preventDefault();
                if (!updateArea()) {
                    text("formmsg", $("areaHint").textContent);
                    return;
                }
                const p = Object.fromEntries(new FormData(e.target));
                const saveButton = e.target.querySelector('button[type="submit"]');
                if (saveButton.disabled) return;
                saveButton.disabled = true;
                text("msg", "");
                text("formmsg", "");
                try {
                    await GHApi.post("/api/greenhouses", {
                        ...p,
                        action: p.id ? "update" : "create",
                        csrf_token: csrfToken,
                    });
                    $("edit").close();
                    text("msg", await load() ? "已保存" : "已保存，列表刷新失败，请刷新查看");
                } catch (e) {
                    text("formmsg", e.message);
                } finally {
                    saveButton.disabled = false;
                }
            };
            $("export").onclick = () => {
                location.href =
                    GH_CONTEXT_PATH +
                    "/api/greenhouses?" +
                    new URLSearchParams({
                        ...applied,
                        action: "export"
                    });
            };
            $("print").onclick = async () => {
                try {
                    text("msg", "正在准备全部筛选记录…");
                    let items = [],
                        p = 1,
                        r;
                    do {
                        r = await api("list", {
                            ...applied,
                            page: p++,
                            size: 100
                        });
                        items.push(...r.data.items);
                    } while (items.length < Number(r.data.total));
                    render(items);
                    text(
                        "printinfo",
                        "筛选条件：" +
                        conditions() +
                        "；打印时间：" +
                        new Date().toLocaleString("zh-CN", {
                            timeZone: "Asia/Shanghai"
                        }) +
                        "；操作人：" +
                        operatorName,
                    );
                    window.print();
                    await load();
                } catch (e) {
                    text("msg", e.message);
                }
            };
            (async () => {
                try {
                    const r = await api("context");
                    operatorName = r.data.operator || "";
                    csrfToken = r.data.csrf_token || "";
                    canWrite = r.data.role !== "3" && !!csrfToken;
                    $("add").hidden = !canWrite;
                    r.data.users.forEach((u) => {
                        for (const id of ["managerFilter", "managerEdit"]) {
                            const o = document.createElement("option");
                            o.value = u.id;
                            o.textContent = u.display_name;
                            $(id).append(o);
                        }
                    });
                    await load();
                } catch (e) {
                    text("msg", e.message);
                }
            })();
        })();
    </script>
</body>

</html>
