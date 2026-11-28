<%@ page contentType="text/html;charset=UTF-8" %>
<%@ include file="/home/frame/login_check.jsp" %>
<%
String resourcePage = request.getAttribute("resourcePage") == null ? "map" : String.valueOf(request.getAttribute("resourcePage"));
String resourceTitle = "map".equals(resourcePage) ? "大棚位置地图" : "devices".equals(resourcePage) ? "设备归属管理" : "种植批次管理";
request.setAttribute("pageTitle", resourceTitle);
%>
<!doctype html>
<html lang="zh-CN">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>农业大棚系统 - <%= resourceTitle %></title><%@ include file="/home/frame/frame_style.jsp" %>
    <style>
        .resources section {
            background: white;
            padding: 20px;
            margin: 16px 0
        }

        .resources input,
        .resources select,
        .resources button {
            padding: 7px;
            margin: 4px;
            font-size: 14px
        }

        .resources td,
        .resources th {
            padding: 8px;
            border-bottom: 1px solid #ddd
        }

        .resources table {
            width: 100%
        }

        .resources label {
            display: inline-block
        }

        .resources pre {
            white-space: pre-wrap
        }

        .resources [hidden] {
            display: none !important
        }

        .resources iframe {
            width: 100%;
            height: 420px;
            border: 1px solid #ccc
        }

        .resources dialog {
            max-width: 850px;
            max-height: 90vh;
            overflow: auto
        }

        .resources .scroll {
            overflow: auto
        }

        @media print {

            .page-header,
            .page-sidebar-wrapper,
            .page-footer,
            .resources .controls,
            .resources button,
            .resources dialog,
            .resources iframe {
                display: none !important
            }

            .page-content-wrapper,
            .page-content {
                margin: 0 !important
            }

            .resources section {
                break-inside: avoid
            }
        }

        .summary-grid {
            display: flex;
            flex-wrap: wrap;
            gap: 12px;
            margin: 16px 0
        }

        .summary-grid article {
            background: #edf6f0;
            padding: 14px;
            flex: 1 1 150px
        }

        .summary-grid strong {
            display: block;
            font-size: 26px;
            color: #216143
        }

        .summary-grid meter {
            width: 100%
        }

        .resources .device-panel h2 {
            margin-top: 0;
            color: #24483f
        }

        .resources .device-block {
            border: 1px solid #dfe6e4;
            border-radius: 6px;
            padding: 16px;
            margin: 18px 0;
            background: #fafcfb
        }

        .resources .device-block h3 {
            font-size: 17px;
            font-weight: 600;
            margin: 0 0 12px
        }

        .resources .device-fields {
            display: flex;
            flex-wrap: wrap;
            align-items: flex-end;
            gap: 14px
        }

        .resources .device-fields label {
            display: flex;
            flex-direction: column;
            gap: 6px;
            flex: 1 1 210px;
            margin: 0
        }

        .resources .device-fields input,
        .resources .device-fields select {
            width: 100%;
            margin: 0;
            min-height: 34px;
            border: 1px solid #b9c9c4;
            border-radius: 4px;
            background: #fff;
            padding: 8px
        }

        .resources .device-fields .device-check {
            flex: 0 1 auto;
            display: flex;
            flex-direction: row;
            align-items: center;
            min-height: 34px
        }

        .resources .device-check input {
            width: auto;
            min-height: 0;
            margin: 0 5px 0 0
        }

        .resources .device-fields button {
            margin: 0;
            min-height: 34px
        }

        .resources .device-help {
            color: #596b65;
            line-height: 1.7;
            margin: 10px 0 0
        }

        .resources #bindFields {
            border: 0;
            padding: 0;
            margin: 0;
            min-width: 0
        }

        .resources #bindFields:disabled {
            opacity: .65
        }

        .resources .device-table th {
            background: #f1f6f4;
            white-space: nowrap
        }

        .resources .device-code {
            display: block;
            color: #66766f;
            font-size: 12px;
            overflow-wrap: anywhere;
            max-width: 340px;
            margin-top: 5px
        }

        .resources .device-state {
            display: inline-block;
            padding: 3px 9px;
            border-radius: 4px;
            background: #e8f3ed;
            color: #285c42
        }

        .resources .device-state.unbound {
            background: #fff2d9;
            color: #835713
        }

        .resources .device-empty {
            text-align: center;
            padding: 24px;
            color: #596b65
        }

        .resources .resource-panel h2 {
            margin-top: 0;
            color: #24483f
        }

        .resources .map-info {
            white-space: pre-line;
            line-height: 1.9;
            margin-top: 14px
        }

        .resources .empty-hint {
            padding: 20px;
            background: #f1f6f4;
            color: #52665e;
            border-radius: 6px
        }

        .resources .resource-actions {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: 10px;
            margin: 14px 0
        }

        .resources .resource-actions .btn {
            margin: 0
        }

        .resources .batch-stats {
            border: 0;
            background: #f1f6f4;
            padding: 15px;
            font: inherit;
            white-space: pre-wrap;
            line-height: 1.9
        }

        .resources .batch-editor textarea {
            width: 100%;
            border: 1px solid #b9c9c4;
            border-radius: 4px;
            padding: 8px
        }

        .resources dialog {
            width: min(850px, 95vw);
            border: 1px solid #b9c9c4;
            border-radius: 8px;
            padding: 24px
        }

        .resources dialog::backdrop {
            background: rgba(0, 0, 0, .35)
        }

        .resources .resource-error {
            color: #a42d28;
            white-space: pre-wrap
        }

        .resources .resource-detail {
            display: grid;
            grid-template-columns: 120px 1fr;
            gap: 12px;
            margin: 20px 0
        }

        .resources .resource-detail dd {
            overflow-wrap: anywhere
        }

        @media print {
            .resources .device-table {
                min-width: 0
            }

            .resources .resource-actions {
                display: none !important
            }
        }
    </style>
</head>

<body class="page-header-fixed page-sidebar-closed-hide-logo"><%@ include file="/home/frame/frame_header.jsp" %><div
        class="page-container"><%@ include file="/home/frame/frame_left_sidebar.jsp" %><div
            class="page-content-wrapper">
            <div class="page-content resources"><%@ include file="/home/frame/frame_page_header.jsp" %>

                <p id="notice" role="status"></p>
                <% if ("map".equals(resourcePage)) { %>
                <section class="resource-panel" id="mapPanel">
                    <h2>大棚位置地图</h2>
                    <nav class="controls resource-actions" aria-label="大棚业务页面"><a class="btn default"
                            href="index.jsp">大棚档案</a><a class="btn default" href="map.jsp">位置地图</a><a
                            class="btn default" href="devices.jsp">设备归属</a><a class="btn default"
                            href="batches.jsp">种植批次</a><a class="btn default" href="dashboard.jsp">个人驾驶舱</a></nav>
                    <p class="device-help">选择大棚查看它的位置和档案。管理员可填写坐标、保存定位，也可清除原有定位。</p>
                    <div class="device-block">
                        <div class="device-fields"><label>查看哪座大棚<select id="mapChoice"></select></label></div>
                        <p id="mapInfo" class="map-info"></p>
                    </div>
                    <p id="mapEmpty" class="empty-hint" role="status"></p>
                    <link rel="stylesheet" href="../../assets/vendor/leaflet/leaflet.css">
                    <div id="mapCanvas" style="height:420px;z-index:0" aria-label="在线地图，管理员点击预览选点"></div>
                    <p id="mapSaved"></p>
                    <p id="mapPreview" role="status"></p>
                    <p id="mapNetwork" role="status">正在请求在线底图…</p>
                    <script src="../../assets/vendor/leaflet/leaflet.js"></script>
                    <script src="map-picker.js"></script>
                    <p><a id="mapLink" class="btn default" target="_blank" rel="noopener noreferrer"
                            hidden>打开外部地图查看位置</a></p>
                    <form id="geoForm" class="device-block controls" hidden>
                        <h3>设置大棚位置</h3>
                        <p class="device-help">填写WGS84经纬度，例如纬度30、经度120。请先核对位置再保存。</p>
                        <div class="device-fields">
                            <label>纬度<input name="latitude" type="number" step="0.000001" min="-90" max="90"
                                    placeholder="-90 至 90" required></label>
                            <label>经度<input name="longitude" type="number" step="0.000001" min="-180" max="180"
                                    placeholder="-180 至 180" required></label>
                            <button class="btn blue">保存位置</button><button type="button" class="btn default"
                                id="clearGeo" disabled>清除定位</button>
                        </div>
                        <p class="device-help">清除定位只移除地图位置，大棚档案和业务记录会保留；0,0也是有效坐标。</p>
                    </form>
                    <p class="device-help">地图需要联网。若底图空白，可尝试上方外部地图链接；坐标保存与地图底图加载分别确认。</p>
                </section>
                <% } %>
                <% if ("devices".equals(resourcePage)) { %>
                <section class="device-panel" aria-labelledby="deviceTitle">
                    <h2 id="deviceTitle">大棚设备归属</h2>
                    <div id="deviceStats" class="summary-grid" aria-live="polite"></div>
                    <p class="device-help">查看每台设备属于哪座大棚；将尚未分配的设备绑定到大棚后，后续采样数据才能对应到该大棚。</p>
                    <div class="device-block controls">
                        <h3>查看设备</h3>
                        <form id="deviceFilter" class="device-fields">
                            <label>设备名称或编号<input name="keyword" placeholder="输入名称或编号关键词"></label>
                            <label>所属大棚<select name="greenhouse_id" class="greenhouse">
                                    <option value="">全部大棚</option>
                                </select></label>
                            <label class="device-check"><input type="checkbox" name="unbound"
                                    value="1">只看尚未绑定的设备</label>
                            <button class="btn blue">查询</button><button type="button" class="btn default"
                                id="resetDevice">重置</button>
                        </form>
                    </div>
                    <nav class="controls resource-actions" aria-label="大棚业务页面"><a class="btn default"
                            href="index.jsp">大棚档案</a><a class="btn default" href="map.jsp">位置地图</a><a
                            class="btn default" href="devices.jsp">设备归属</a><a class="btn default"
                            href="batches.jsp">种植批次</a><a class="btn default" href="dashboard.jsp">个人驾驶舱</a></nav>
                    <div class="controls resource-actions"><button id="newDevice" class="btn blue"
                            hidden>新增设备档案</button><button id="refreshDevices" class="btn default">刷新</button></div>
                    <form id="bindForm" class="device-block controls" hidden>
                        <h3>将设备分配到大棚</h3>
                        <p class="device-help">依次选择设备、大棚和指标，再确认绑定。需要更换大棚时，先在列表中解除原绑定。</p>
                        <fieldset id="bindFields" disabled>
                            <legend class="sr-only">设备绑定信息</legend>
                            <div class="device-fields">
                                <label>1 选择未绑定设备<select name="device_pk" id="unboundDevice" required>
                                        <option value="">正在加载设备…</option>
                                    </select></label>
                                <label>2 选择目标大棚<select name="greenhouse_id" class="greenhouse active"
                                        required></select></label>
                                <label>3 选择采集指标或用途<select name="metric_type" id="bindingMetric"
                                        required></select></label>
                                <label id="purposeLabel" hidden>控制用途<select name="control_purpose">
                                        <option value="ventilation">通风</option>
                                        <option value="cooling">降温</option>
                                        <option value="irrigation">灌溉</option>
                                    </select></label><button id="bindDevice" class="btn blue">确认绑定</button>
                            </div>
                        </fieldset>
                        <p id="bindingHint" class="device-help" role="status">正在检查可绑定设备…</p>
                    </form>
                    <p id="deviceNotice" role="status" aria-live="polite">正在加载设备…</p>
                    <div class="scroll">
                        <table class="device-table">
                            <caption class="sr-only">设备归属查询结果</caption>
                            <thead>
                                <tr>
                                    <th>设备名称与编号</th>
                                    <th>设备类型</th>
                                    <th>所属大棚</th>
                                    <th>采集指标或用途</th>
                                    <th>单位</th>
                                    <th>绑定状态</th>
                                    <th class="controls">操作</th>
                                </tr>
                            </thead>
                            <tbody id="devices"></tbody>
                        </table>
                    </div>
                    <div class="controls resource-actions"><label>每页<select id="deviceSize">
                                <option>10</option>
                                <option>20</option>
                                <option>50</option>
                            </select>条</label><button id="devicePrev" class="btn default">上一页</button><span
                            id="devicePages"></span><button id="deviceNext" class="btn default">下一页</button></div>
                    <p class="device-help">解除绑定后设备可重新分配，原归属保存在历史中。名称中标注“模拟”的设备用于演示。</p>
                    <dialog id="deviceDialog">
                        <h2>新增设备档案</h2>
                        <p class="device-help">登记设备后，再选择它所属的大棚和采集指标。新增档案不会连接或启动实体设备。</p>
                        <form id="deviceForm">
                            <div class="device-fields"><label>设备编号<input name="device_code" maxlength="255"
                                        placeholder="例如 QA-TEMP-01" required></label><label>设备名称<input
                                        name="device_name" maxlength="255" placeholder="例如 测试温度传感器"
                                        required></label><label>设备类型<select name="device_type">
                                        <option value="sensor">传感器</option>
                                        <option value="control">控制设备</option>
                                    </select></label></div>
                            <p id="deviceError" class="resource-error" role="status"></p>
                            <div class="resource-actions"><button class="btn blue">保存设备</button><button type="button"
                                    id="closeDevice" class="btn default">取消</button></div>
                        </form>
                    </dialog>
                    <dialog id="deviceHistoryDialog">
                        <h2>设备历史归属</h2>
                        <p id="deviceHistoryTitle"></p>
                        <div class="scroll">
                            <table class="device-table">
                                <thead>
                                    <tr>
                                        <th>原设备编号与名称</th>
                                        <th>原所属大棚</th>
                                        <th>原指标或用途</th>
                                        <th>绑定开始</th>
                                        <th>解除时间</th>
                                        <th>操作人</th>
                                    </tr>
                                </thead>
                                <tbody id="deviceHistoryRows"></tbody>
                            </table>
                        </div>
                        <p class="device-help">展示最近100条解除记录，当前归属请查看设备列表。</p><button type="button"
                            id="closeDeviceHistory" class="btn default">关闭</button>
                    </dialog>
                </section>
                <% } %>
                <% if ("batches".equals(resourcePage)) { %>
                <section class="resource-panel" id="batchPanel">
                    <h2>种植批次管理</h2>
                    <p id="batchSummary" role="status"></p>
                    <div id="batchStats" class="summary-grid"></div>
                    <p class="device-help">一个批次记录一座大棚中的一次种植安排，包括种什么、何时种、预计生长多久及当前状态。</p>
                    <div class="device-block controls">
                        <h3>查询种植记录</h3>
                        <form id="batchFilter" class="device-fields">
                            <label>所属大棚<select name="greenhouse_id" class="greenhouse">
                                    <option value="">全部大棚</option>
                                </select></label>
                            <label>作物名称<input name="crop_name" placeholder="例如番茄"></label><label>作物品种<input
                                    name="variety" placeholder="输入品种关键词"></label>
                            <label>种植状态<select name="status">
                                    <option value="">全部状态</option>
                                    <option value="planned">计划</option>
                                    <option value="growing">生长中</option>
                                    <option value="harvested">已收获</option>
                                    <option value="canceled">已取消</option>
                                </select></label>
                            <label>种植日期从<input name="date_from" type="date"></label><label>种植日期到<input name="date_to"
                                    type="date"></label>
                            <label>排序依据<select name="sort">
                                    <option value="planting_date">种植日期</option>
                                    <option value="batch_code">批次编号</option>
                                    <option value="created_at">创建时间</option>
                                </select></label>
                            <label>排列顺序<select name="dir">
                                    <option value="desc">降序</option>
                                    <option value="asc">升序</option>
                                </select></label><button class="btn blue">查询批次</button><button type="button"
                                class="btn default" id="resetBatch">重置</button>
                        </form>
                    </div>
                    <nav class="controls resource-actions" aria-label="大棚业务页面"><a class="btn default"
                            href="index.jsp">大棚档案</a><a class="btn default" href="map.jsp">位置地图</a><a
                            class="btn default" href="devices.jsp">设备归属</a><a class="btn default"
                            href="batches.jsp">种植批次</a><a class="btn default" href="dashboard.jsp">个人驾驶舱</a></nav>
                    <div class="controls resource-actions"><button class="btn blue" id="newBatch"
                            hidden>新增种植批次</button><button class="btn default" id="batchExport">导出筛选结果</button><button
                            class="btn default" id="batchPrint">打印</button><button class="btn default"
                            id="refreshBatches">刷新</button></div>
                    <p class="device-help">状态流程：计划 → 生长中 → 已收获，也可取消。只有计划批次可以删除，结束后的记录保留查询。</p>
                    <p id="printMeta"></p>
                    <div class="scroll">
                        <table class="device-table">
                            <thead>
                                <tr>
                                    <th>批次编号</th>
                                    <th>所属大棚</th>
                                    <th>作物名称</th>
                                    <th>品种</th>
                                    <th>种植日期</th>
                                    <th>生长周期 天</th>
                                    <th>状态</th>
                                    <th class="controls">操作</th>
                                </tr>
                            </thead>
                            <tbody id="batches"></tbody>
                        </table>
                    </div>
                    <div class="controls resource-actions"><label>每页<select id="batchSize">
                                <option>10</option>
                                <option>20</option>
                                <option>50</option>
                            </select>条</label><button class="btn default" id="previous">上一页</button><span
                            id="pageLabel"></span><button class="btn default" id="following">下一页</button></div>
                </section>
                <dialog id="batchDialog">
                    <h2 id="batchDialogTitle">种植批次</h2>
                    <p id="batchHint" class="device-help"></p>
                    <form id="batchForm" class="batch-editor"><input type="hidden" name="id">
                        <div class="device-fields">
                            <label>所属大棚<select name="greenhouse_id" class="greenhouse active"
                                    required></select></label><label>批次编号<input name="batch_code" maxlength="64"
                                    placeholder="例如 BATCH-202611-01" required></label>
                            <label>作物名称<input name="crop_name" maxlength="100" placeholder="例如番茄"
                                    required></label><label>作物品种<input name="variety" maxlength="100"
                                    placeholder="品种可选"></label>
                            <label>种植日期<input name="planting_date" type="date" required></label><label>生长周期 天<input
                                    name="growth_days" type="number" min="1" max="3650" placeholder="例如60"
                                    required></label>
                            <label>种植状态<select name="status">
                                    <option value="planned">计划</option>
                                    <option value="growing">生长中</option>
                                    <option value="harvested">已收获</option>
                                    <option value="canceled">已取消</option>
                                </select></label>
                            <label>备注<textarea name="remark" maxlength="1000" rows="3"
                                    placeholder="可填写种植说明"></textarea></label>
                        </div>
                        <p id="batchError" class="resource-error" role="status"></p>
                        <div class="resource-actions"><button class="btn blue">保存批次</button><button class="btn default"
                                type="button" id="closeBatch">取消</button></div>
                    </form>
                </dialog>
                <dialog id="resourceDetail">
                    <h2>种植批次详情</h2>
                    <dl id="resourceDetailText" class="resource-detail"></dl><button class="btn default"
                        id="closeDetail">关闭</button>
                </dialog>
                <% } %>
            </div>
        </div>
    </div><%@ include file="/home/frame/frame_footer.jsp" %><%@ include file="/home/frame/frame_javascript.jsp" %>
    <script>
        window.GH_CONTEXT_PATH = '<%=request.getContextPath()%>';
    </script>
    <script src="../../assets/app/js/app-api.js"></script>
    <script src="greenhouse-modals.js"></script>
    <script src="resources.js"></script>
</body>

</html>
