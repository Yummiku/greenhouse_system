<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ include file="/home/frame/login_check.jsp" %>
<% request.setAttribute("pageTitle", "环境监测"); %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>农业大棚系统 - 环境监测</title>
  <%@ include file="/home/frame/frame_style.jsp" %>
  <link rel="stylesheet" href="<%=request.getContextPath()%>/maintain/environment/environment.css">
</head>
<body class="page-header-fixed page-quick-sidebar-over-content">

<%@ include file="/home/frame/frame_header.jsp" %>
<div class="clearfix"></div>

<div class="page-container">
  <%@ include file="/home/frame/frame_left_sidebar.jsp" %>

  <div class="page-content-wrapper">
    <main class="page-content">
      <%@ include file="/home/frame/frame_page_header.jsp" %>

      <p id="env-message" role="status" aria-live="polite">正在加载环境数据……</p>

      <section class="portlet light bordered" aria-labelledby="env-live-title">
        <div class="portlet-title">
          <div class="caption">
            <i class="icon-bar-chart"></i>
            <span class="caption-subject bold" id="env-live-title">大棚环境监测</span>
            <span class="caption-helper">五类指标最新值与历史采样</span>
          </div>
          <div class="actions">
            <label class="env-inline" for="env-live-greenhouse">大棚
              <select id="env-live-greenhouse" class="form-control"></select>
            </label>
            <button type="button" id="env-refresh" class="btn default btn-sm">立即刷新</button>
          </div>
        </div>
        <div class="portlet-body">
          <div id="env-cards" class="env-cards"></div>
          <p class="env-note">
            空气温度与土壤湿度每 2 秒读取一次最新接口，轮询不会重叠；超过 10 秒未更新标记为过期。
            0 是有效数值，与「无数据」「读取失败」「过期」分别显示。数据来源分实采、模拟、人工三类，不混用。
          </p>
        </div>
      </section>

      <section class="portlet light bordered" aria-labelledby="env-list-title">
        <div class="portlet-title">
          <div class="caption"><span class="caption-subject bold" id="env-list-title">采样记录</span></div>
        </div>
        <div class="portlet-body">
          <form id="env-filter" class="env-filters">
            <label>大棚
              <select class="form-control" name="greenhouse_id"></select>
            </label>
            <label>设备
              <select class="form-control" name="device_pk"></select>
            </label>
            <label>指标
              <select class="form-control" name="metric_type"></select>
            </label>
            <label>来源
              <select class="form-control" name="source">
                <option value="">全部来源</option>
                <option value="real">实采</option>
                <option value="simulated">模拟</option>
                <option value="manual">人工</option>
              </select>
            </label>
            <label>开始时间
              <input class="form-control" type="datetime-local" name="from" step="1">
            </label>
            <label>结束时间
              <input class="form-control" type="datetime-local" name="to" step="1">
            </label>
            <label>排序
              <select class="form-control" name="sort">
                <option value="sample_time">采样时间</option>
                <option value="value">数值</option>
                <option value="received_at">接收时间</option>
                <option value="id">记录ID</option>
              </select>
            </label>
            <label>顺序
              <select class="form-control" name="dir">
                <option value="desc">降序</option>
                <option value="asc">升序</option>
              </select>
            </label>
            <label>&nbsp;
              <button class="btn blue form-control" type="submit">查询</button>
            </label>
            <label>&nbsp;
              <button class="btn default form-control" type="button" id="env-reset">重置</button>
            </label>
          </form>

          <div class="env-actions">
            <button class="btn blue" type="button" id="env-add">新增人工测试记录</button>
            <button class="btn default" type="button" id="env-stats">统计与趋势</button>
            <button class="btn default" type="button" id="env-export">导出全部筛选结果（CSV）</button>
            <button class="btn default" type="button" id="env-print">打印曲线与数据表</button>
          </div>

          <p id="env-summary"></p>
          <div id="env-chart" class="env-chart" hidden></div>

          <div class="table-responsive">
            <table class="table table-bordered">
              <thead>
              <tr>
                <th>采样键</th>
                <th>大棚</th>
                <th>设备</th>
                <th>指标</th>
                <th>数值</th>
                <th>采样时间</th>
                <th>来源</th>
                <th>录入人</th>
                <th>操作</th>
              </tr>
              </thead>
              <tbody id="env-rows"></tbody>
            </table>
          </div>

          <div class="env-actions">
            <button id="env-prev" type="button" class="btn default">上一页</button>
            <span id="env-pages"></span>
            <button id="env-next" type="button" class="btn default">下一页</button>
          </div>
        </div>
      </section>
    </main>
  </div>
</div>

<%@ include file="/home/frame/frame_footer.jsp" %>

<dialog id="env-editor" class="env-dialog">
  <h3 id="env-editor-title">新增人工测试采样</h3>
  <form id="env-form">
    <input type="hidden" name="id">
    <div class="env-form-grid">
      <label>大棚
        <select class="form-control" name="greenhouse_id" required></select>
      </label>
      <label>设备
        <select class="form-control" name="device_pk" required></select>
      </label>
      <label>指标
        <select class="form-control" name="metric_type" required></select>
      </label>
      <label>数值
        <input class="form-control" name="value" type="number" step="0.001" required>
      </label>
      <label>采样时间
        <input class="form-control" name="sample_time" type="datetime-local" step="1" required>
      </label>
      <label>备注（新增时可用）
        <input class="form-control" name="remark" maxlength="255">
      </label>
      <label>更正原因（更正时必填）
        <input class="form-control" name="reason" maxlength="255">
      </label>
    </div>
    <p id="env-editor-hint" class="env-note"></p>
    <p id="env-form-message" role="alert"></p>
    <button id="env-save" class="btn blue" type="submit">保存</button>
    <button class="btn default" type="button" data-close="env-editor">取消</button>
  </form>
</dialog>

<dialog id="env-delete" class="env-dialog">
  <h3>逻辑删除人工记录</h3>
  <form id="env-delete-form">
    <p id="env-delete-target"></p>
    <label>删除原因（必填）
      <input class="form-control" name="reason" maxlength="255" required>
    </label>
    <p class="env-note">删除只对人的误录生效：实采与模拟记录不能删除，避免原始数据被抹除。</p>
    <p id="env-delete-message" role="alert"></p>
    <button id="env-delete-save" class="btn blue" type="submit">确认删除</button>
    <button class="btn default" type="button" data-close="env-delete">取消</button>
  </form>
</dialog>

<dialog id="env-detail" class="env-dialog">
  <h3>采样详情</h3>
  <dl id="env-detail-body" class="env-detail-list"></dl>
  <h4>人工更正留痕</h4>
  <div class="table-responsive">
    <table class="table table-bordered">
      <thead>
      <tr>
        <th>更正时间</th>
        <th>操作人</th>
        <th>原值 → 新值</th>
        <th>原采样时间 → 新采样时间</th>
        <th>原因</th>
      </tr>
      </thead>
      <tbody id="env-detail-revisions"></tbody>
    </table>
  </div>
  <button class="btn default" type="button" data-close="env-detail">关闭</button>
</dialog>

<section id="env-print-area"></section>

<%@ include file="/home/frame/frame_javascript.jsp" %>
<script src="<%= request.getContextPath() %>/maintain/environment/environment.js"></script>
</body>
</html>
