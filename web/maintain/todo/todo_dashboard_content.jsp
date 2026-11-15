<%@ page pageEncoding="UTF-8" %>
<div id="todo_dashboard" data-context="<%= request.getContextPath() %>">
            <h4>待办事项统计概览</h4>
            <p id="todo_report_error" class="text-danger" role="alert"></p>
            <!-- 使用参考视频8中的 Metronic dashboard-stat 统计卡片结构。 -->
            <div class="row">
                <div class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
                    <div class="dashboard-stat blue-madison">
                        <div class="visual"><i class="fa fa-list"></i></div>
                        <div class="details">
                            <div class="number" id="todo_total_count">—</div>
                            <div class="desc">待办事项总数</div>
                        </div>
                        <a class="more" href="<%= request.getContextPath() %>/maintain/todo/todo_crud.jsp">
                            查看待办列表 <i class="m-icon-swapright m-icon-white"></i>
                        </a>
                    </div>
                </div>
                <div class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
                    <div class="dashboard-stat red-intense">
                        <div class="visual"><i class="fa fa-calendar"></i></div>
                        <div class="details">
                            <div class="number" id="todo_today_count">—</div>
                            <div class="desc">今日创建数量</div>
                        </div>
                        <div class="more">按当天创建日期统计</div>
                    </div>
                </div>
                <div class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
                    <div class="dashboard-stat green-haze">
                        <div class="visual"><i class="fa fa-bar-chart-o"></i></div>
                        <div class="details">
                            <div class="number" id="todo_month_count">—</div>
                            <div class="desc">本月创建数量</div>
                        </div>
                        <div class="more">按当月创建日期统计</div>
                    </div>
                </div>
                <div class="col-lg-3 col-md-6 col-sm-6 col-xs-12">
                    <div class="dashboard-stat purple-plum">
                        <div class="visual"><i class="fa fa-clock-o"></i></div>
                        <div class="details">
                            <div class="number todo-latest-time" id="todo_latest_time">—</div>
                            <div class="desc">最新创建时间</div>
                        </div>
                        <div class="more">最近一次创建待办事项的时间</div>
                    </div>
                </div>
            </div>
            <div class="portlet light bordered">
                <div class="portlet-title">
                    <div class="caption"><i class="fa fa-bar-chart-o"></i>每月创建数量</div>
                    <div class="actions"><button type="button" class="btn blue btn-sm" id="todo_dashboard_refresh">刷新统计</button></div>
                </div>
                <div class="portlet-body">
                    <p>显示最近12个有记录的月份；历史记录会按实际创建月份统计。</p>
                    <table class="table table-striped table-bordered">
                        <thead><tr><th>创建月份</th><th>数量</th><th>数量对比</th></tr></thead>
                        <tbody id="todo_month_rows"></tbody>
                    </table>
                </div>
            </div>
</div>
