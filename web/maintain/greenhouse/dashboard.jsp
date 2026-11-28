<%@ page contentType="text/html;charset=UTF-8" %><%@ include file="/home/frame/login_check.jsp" %><% request.setAttribute("pageTitle","大棚个人驾驶舱"); %>
<!doctype html>
<html lang="zh-CN">

<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>大棚个人驾驶舱</title><%@ include file="/home/frame/frame_style.jsp" %><style>
        .gh-dashboard section {
            padding: 20px;
            background: white;
            margin: 18px 0
        }

        .gh-dashboard .cards {
            display: flex;
            flex-wrap: wrap;
            gap: 18px
        }

        .gh-dashboard .cards article {
            padding: 24px;
            background: #edf6f0;
            flex: 1;
            min-width: 140px
        }

        .gh-dashboard strong {
            font-size: 32px;
            color: #216143;
            display: block
        }

        .gh-dashboard .bar {
            background: #e6eeea;
            height: 26px;
            margin: 8px 0
        }

        .gh-dashboard .bar span {
            display: block;
            height: 100%;
            background: #287a54
        }

        .gh-dashboard input,
        .gh-dashboard select,
        .gh-dashboard button {
            padding: 8px;
            margin: 4px
        }

        .gh-dashboard .plot {
            min-height: 90px
        }

        .gh-dashboard .error {
            color: #a03020
        }
    </style>
</head>

<body class="page-header-fixed page-sidebar-closed-hide-logo"><%@ include file="/home/frame/frame_header.jsp" %><div
        class="page-container"><%@ include file="/home/frame/frame_left_sidebar.jsp" %><div
            class="page-content-wrapper">
            <div class="page-content gh-dashboard"><%@ include file="/home/frame/frame_page_header.jsp" %><a
                    href="index.jsp">返回大棚档案</a>
                <section class="cards">
                    <article>未删除大棚<strong id="dashTotal">—</strong>座</article>
                    <article>总面积<strong id="dashArea">—</strong>平方米</article>
                    <article>当前有效绑定<strong id="dashDevices">—</strong>台</article>
                </section>
                <section>
                    <h3>大棚状态分布</h3>
                    <div id="dashStatuses" class="plot"></div>
                </section>
                <section>
                    <h3>当前绑定设备按大棚分布</h3>
                    <div id="dashDistribution" class="plot"></div>
                </section>
                <section>
                    <h3>当前生长中作物批次比例</h3>
                    <p>分母为当前生长中批次总数，不是产量或面积。</p>
                    <div id="dashCrops" class="plot"></div>
                </section>
                <section>
                    <form id="dashFilter"><label>统一日期范围<input type="date" name="date_from" required>至<input type="date"
                                name="date_to" required></label><select id="dashGreenhouse" name="greenhouse_id">
                            <option value="">全部大棚</option>
                        </select><button class="btn blue">查询</button><button type="button" id="dashRefresh"
                            class="btn default">立即刷新</button></form>
                    <p>默认最近7个自然日，含今天。以下均为当前状态存量，不冒充所选日期的历史快照；每30秒刷新已应用条件。</p>
                    <p id="dashState" role="status">加载中…</p>
                    <p id="dashTime"></p>
                </section>
            </div>
        </div>
    </div><%@ include file="/home/frame/frame_footer.jsp" %><%@ include file="/home/frame/frame_javascript.jsp" %>
    <script>
        window.GH_CONTEXT_PATH = '<%=request.getContextPath()%>';
    </script>
    <script src="../../assets/app/js/app-api.js"></script>
    <script src="dashboard.js"></script>
</body>

</html>
