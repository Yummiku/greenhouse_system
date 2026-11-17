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
            .greenhouse-module .controls > button {
                margin: 4px 6px 4px 0;
            }
            .greenhouse-module #rows button {
                margin: 3px;
            }
            .greenhouse-module #msg {
                white-space: pre-wrap;
                margin: 14px 0;
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
                    <section class="controls portlet light bordered">
                        <form id="filter">
                            <input
                                class="form-control"
                                name="keyword"
                                placeholder="名称或编号"
                            /><input
                                class="form-control"
                                name="location"
                                placeholder="位置"
                            /><select class="form-control" name="manager_id" id="managerFilter">
                                <option value="">全部负责人</option></select
                            ><select class="form-control" name="status">
                                <option value="">全部状态</option>
                                <option value="enabled">启用</option>
                                <option value="disabled">停用</option></select
                            ><select class="form-control" name="sort">
                                <option value="created_at">创建时间</option>
                                <option value="greenhouse_code">编号</option>
                                <option value="area">面积</option></select
                            ><select class="form-control" name="dir">
                                <option value="desc">降序</option>
                                <option value="asc">升序</option></select
                            ><button class="btn default">查询</button>
                        </form>
                        <button id="add" class="btn blue" hidden>新增大棚</button
                        ><button id="export" class="btn default">导出全部筛选结果</button
                        ><button id="print" class="btn default">打印预览</button>
                    </section>
                    <p id="msg" role="status"></p>
                    <section id="stats" class="portlet light bordered"></section>
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
                            <button id="prev" class="btn default">上一页</button
                            ><span id="pages"></span
                            ><button id="next" class="btn default">下一页</button>
                        </div>
                    </section>
                    <dialog id="edit">
                        <h2>大棚档案</h2>
                        <form id="editor">
                            <input type="hidden" name="id" /><label
                                >编号<input
                                    class="form-control"
                                    name="greenhouse_code"
                                    required
                                    maxlength="64" /></label
                            ><label
                                >名称<input
                                    class="form-control"
                                    name="name"
                                    required
                                    maxlength="100" /></label
                            ><label
                                >位置<input
                                    class="form-control"
                                    name="location"
                                    required
                                    maxlength="255" /></label
                            ><label
                                >面积(m²)<input
                                    class="form-control"
                                    name="area"
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    required /></label
                            ><label
                                >长度(m)<input
                                    class="form-control"
                                    name="length"
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    required /></label
                            ><label
                                >宽度(m)<input
                                    class="form-control"
                                    name="width"
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    required /></label
                            ><label
                                >负责人<select
                                    class="form-control"
                                    name="manager_id"
                                    id="managerEdit"
                                    required
                                ></select></label
                            ><label
                                >状态<select class="form-control" name="status">
                                    <option value="enabled">启用</option>
                                    <option value="disabled">停用</option>
                                </select></label
                            ><label
                                >备注<input class="form-control" name="remark" maxlength="1000"
                            /></label>
                            <p>面积独立保存，不按长×宽覆盖。</p>
                            <button class="btn default">保存</button
                            ><button
                                class="btn default"
                                type="button"
                                onclick="document.getElementById('edit').close()"
                            >
                                取消
                            </button>
                            <p id="formmsg"></p>
                        </form>
                    </dialog>
                    <dialog id="detail">
                        <h2>档案详情</h2>
                        <pre id="detailtext"></pre>
                        <button
                            class="btn default"
                            onclick="document.getElementById('detail').close()"
                        >
                            关闭
                        </button>
                    </dialog>
                </main>
            </div>
        </div>
        <%@ include file="/home/frame/frame_footer.jsp" %>
        <%@ include file="/home/frame/frame_javascript.jsp" %>
        <script>
            (function () {
                const $ = (id) => document.getElementById(id),
                    api = (a, p = {}) => GHApi.get("/api/greenhouses", { action: a, ...p });
                let applied = {},
                    pageNo = 1,
                    total = 0,
                    canWrite = false;
                function text(id, t) {
                    $(id).textContent = t;
                }
                let operatorName = "";

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
                            b.className = "btn btn-default btn-xs";
                            b.onclick = () => fn().catch((e) => text("msg", e.message));
                            td.append(b);
                        }
                        btn("详情", async () => {
                            const x = await api("detail", { id: r.id });
                            text(
                                "detailtext",
                                Object.entries(x.data)
                                    .map(([k, v]) => k + "：" + v)
                                    .join("\n"),
                            );
                            $("detail").showModal();
                        });
                        if (canWrite) {
                            btn("编辑", async () => {
                                const x = await api("detail", { id: r.id });
                                $("editor").reset();
                                Object.entries(x.data).forEach(([k, v]) => {
                                    const input = $("editor").elements.namedItem(k);
                                    if (input) {
                                        input.value = v ?? "";
                                    }
                                });
                                text("formmsg", "");
                                $("edit").showModal();
                            });
                            btn("删除", async () => {
                                if (!window.confirm("确认逻辑删除？有关联时须使用停用。")) {
                                    return;
                                }
                                try {
                                    await GHApi.post("/api/greenhouses", {
                                        action: "delete",
                                        id: r.id,
                                    });
                                } catch (error) {
                                    const message = error && error.message
                                        ? error.message
                                        : "删除失败，请稍后重试。";
                                    text("msg", message);
                                    window.alert("删除失败\n\n" + message);
                                    return;
                                }
                                await load();
                            });
                        }
                        tr.append(td);
                        $("rows").append(tr);
                    });
                }
                async function load() {
                    text("msg", "加载中…");
                    try {
                        const [r, s] = await Promise.all([
                            api("list", { ...applied, page: pageNo }),
                            api("stats", applied),
                        ]);
                        total = Number(r.data.total);
                        render(r.data.items);
                        text(
                            "stats",
                            "当前筛选：" +
                                s.data.total +
                                " 座，总面积 " +
                                s.data.area +
                                " m²，启用 " +
                                s.data.enabled +
                                " 座，停用 " +
                                s.data.disabled +
                                " 座",
                        );
                        text("pages", "第 " + pageNo + " 页 / 共 " + total + " 条");
                        $("prev").disabled = pageNo === 1;
                        $("next").disabled = pageNo * 10 >= total;
                        text("msg", total ? "查询成功" : "暂无符合条件的大棚");
                    } catch (e) {
                        text("msg", e.message);
                    }
                }
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
                    $("edit").showModal();
                };
                $("editor").onsubmit = async (e) => {
                    e.preventDefault();
                    const p = Object.fromEntries(new FormData(e.target));
                    try {
                        await GHApi.post("/api/greenhouses", {
                            ...p,
                            action: p.id ? "update" : "create",
                        });
                        $("edit").close();
                        await load();
                    } catch (e) {
                        text("formmsg", e.message);
                    }
                };
                $("export").onclick = () => {
                    location.href =
                        GH_CONTEXT_PATH +
                        "/api/greenhouses?" +
                        new URLSearchParams({ ...applied, action: "export" });
                };
                $("print").onclick = async () => {
                    try {
                        text("msg", "正在准备全部筛选记录…");
                        let items = [],
                            p = 1,
                            r;
                        do {
                            r = await api("list", { ...applied, page: p++, size: 100 });
                            items.push(...r.data.items);
                        } while (items.length < Number(r.data.total));
                        render(items);
                        text(
                            "printinfo",
                            "筛选条件：" +
                                conditions() +
                                "；打印时间：" +
                                new Date().toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" }) +
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
                        canWrite = r.data.role !== "3";
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
