# 第13周 环境采样模块交接说明（唐昕煜）

> 本文件由编程智能体按《第13周工作安排与执行指导》《环境采样模块修改指导书》整理，
> **所有运行结果均为“未执行”或“已做的静态检查”**，没有把未运行的步骤写成通过。
> 成员本人仍需在本机启动 Tomcat + MySQL 后逐项复测（指导书第九节 20 项）。

## 一 代码

| 项目 | 内容 |
| --- | --- |
| 仓库 | https://github.com/Yummiku/greenhouse_system |
| 分支 | `feature/w12-environment`（沿用原 PR #4，按“已有功能分支继续更新原 PR”） |
| 基线 | 第12周提交 `3326aa4`；本周先合并 `main 06fd6fc`（绿色主题 + 用户/消息/大棚设备基线）为 `f556e23` |
| 提交 | `2fab75a` 上传链路整改 · `ac8ae97` 页面整改 · `cf52412` 删除原因与演示 SQL · `4be9182` Qt 单指标 · `4d38f72` 驾驶舱与只读接口 · 本文档提交 |

### 本周改动文件

| 文件 | 改动 | 对应用户可见结果 |
| --- | --- | --- |
| `src/greenhouse/environment/EnvironmentIngestServlet.java` | 逐条核对 `metric_type` 与设备绑定；大棚 `enabled`、`control` 设备拒绝；幂等比较含 `source`；唯一键冲突重读；逐条 `items` 回执 | 错误指标被拒绝并说明“设备只接受哪个指标”，不再静默写成绑定指标 |
| `src/greenhouse/environment/AlarmBridge.java` | 新增：反射挂接报警模块 `AlarmSampleInbox.enqueue` 与 `sampleAlarmProcessor` | 报警模块未部署时 `alarm_status=NOT_READY`，采样照常入库，不谎报判定成功 |
| `src/greenhouse/environment/EnvironmentDao.java` | `Binding` 增加 `device_type` / `device_code` 与 `sensor()` | 上传校验能识别控制设备，提示里给业务编号 |
| `src/greenhouse/environment/EnvironmentServlet.java` | 删除原因可省略；新增 `action=dashboard` | 删除只需确认；驾驶舱有独立只读数据源 |
| `web/maintain/environment/environment.jsp` / `.js` / `.css` | 统计置顶、工具栏改名、记录编号列、原生删除确认、每页 10/20/50、详情技术信息折叠与复制 | 见指导书第一至六节 |
| `web/maintain/dashboard/environment/dashboard.jsp` / `dashboard.js` | 新增个人驾驶舱页面 | 大棚单指标数量/均值/极值/趋势 + 最新采样与过期标记 |
| `client/qt-collector/**` | 单指标配置、`source` 由配置决定、地址上下文校验、界面显示来源 | 五指标不再发给同一台单指标设备 |
| `sql/22_environment_demo_seed.sql` | 固定批次与稳定采样键，重复执行不新增，缺大棚/绑定设备时跳过并说明 | 演示脚本可重复执行 |

未改动：`web.xml`、公共菜单、`LoginFilter`、`Db`、公共样式（按约定由戴锦晨统一合入；`LoginFilter` 中 `/api/environment/ingest` 的放行来自第12周 PR，本次未再改）。

## 二 数据（SQL）

执行顺序（MySQL 8，目标库先 `USE` 对）：

```
01_greenhouse_framework.sql → 09_user_management.sql → 11_greenhouse.sql
→ 12_device_binding_seed.sql（可选，演示大棚与设备绑定）
→ 21_environment_sample.sql（本模块两张表的环境采样表）
→ 22_environment_demo_seed.sql（可选，模拟来源演示数据）
→ 31 阈值规则（李慧颖）→ 33_alarm_event.sql（李慧颖，报警增量）
```

- `21` 依赖 `11` 的 `greenhouse_file` / `greenhouse_device_binding` 与框架的 `device_file`，缺依赖会外键失败，这是有意的。
- `22` 只对 `enabled`、未删除、设备非 `control` 且已绑定的组合写入；重复执行同一批不新增，末尾逐棚输出“可写入 / 停用跳过 / 无绑定设备跳过”。
- 三个脚本都不含 `DROP TABLE`、不清空已有数据、不改历史记录；验收库为 `greenhouse_system_test`，执行前必须核对 `USE`。

## 三 运行

1. JDK 17 编译 `src` 到 `WEB-INF/classes`（涉及 `src` 必须重建，不能只替换 JSP）。
2. 本机私有配置 `WEB-INF/greenhouse.local.properties` 增加 `greenhouse.environment.collectorKey=<至少32位随机串>`，不提交到仓库。未配置时上传接口返回 503，页面查询不受影响。
3. 部署到 Tomcat 10.1，页面地址：
   - 环境监测：`{context}/maintain/environment/environment.jsp`
   - 驾驶舱：`{context}/maintain/dashboard/environment/dashboard.jsp`（菜单入口待戴统一合入）
4. Qt 采集端（可选）：`client/qt-collector`，Qt 6 + CMake；配置 `base_url` 必须含应用上下文（如 `http://localhost:9999/greenhouse_system`），`metrics` 只写该 `device_pk` 绑定的一个指标，`source` 默认 `simulated`。

## 四 接口

详见 `docs/contracts/w13-environment-api.md`。要点：

- 页面/驾驶舱：`GET /api/environment?action=context|list|detail|latest|stats|trend|report|export|dashboard`，登录会话 + 角色校验。
- 维护：`POST /api/environment?action=create|update|delete`，角色 3 返回 403；`delete` 的 `reason` 可省略。
- 采集上传：`POST /api/environment/ingest`，请求头 `X-Collector-Key`，响应含 `accepted/duplicated/rejected` 与逐条 `items`。

## 五 验证（必须如实阅读）

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| `environment.js` / `dashboard.js` 语法 | 通过 | Node `vm.Script` 编译通过 |
| Java 结构自检（括号/引号平衡） | 通过 | 结构检查；编译结果见下一行 |
| C++ 结构自检（括号/引号平衡） | 通过 | **未编译、未运行** |
| 环境模块 `javac` 编译 | **通过** | JDK 17.0.20 + servlet-api(6.0) + `web/WEB-INF/lib/json.jar` + mysql-connector 8.0.33，编译 `environment` 五个类 + `device.dao.Db` + `greenhouse.config.LocalSettings`，`javac exit 0` |
| 全项目 `javac` 编译 | **未执行** | 只验证了本模块与其直接依赖，其它成员模块未一起编译 |
| Tomcat 启动与页面点击 | **未执行** | 本机未启动 Tomcat/MySQL |
| SQL 21/22 执行 | **未执行** | 未连接任何数据库 |
| Qt 真实上传联调 | **未执行** | 未配置 Qt 6 构建环境 |
| 指导书第九节 20 项自测 | **全部未执行** | 需成员本机按清单逐项跑 |

编译过程中实际发现并修复了一个缺陷（提交 `f67d3ef`）：`EnvironmentIngestServlet$Batch.toJson()`
没有声明 `throws JSONException`，而本项目的 org.json 版本 `JSONObject.put` 会抛受检异常，导致编译不通过。
修好后 `javac exit 0`。这说明“结构自检通过”不等于“能编译”，编译仍然必须做。

### 指导书 20 项自测的当前状态

01 三角色登录 · 02 页面与列表 · 03 新增 0 值 · 04 切换大棚与设备 · 05 空字段与越界 · 06 取消新增/编辑 ·
07 编辑两次留痕 · 08 实采/模拟不可维护 · 09 删除取消 · 10 确认删除 · 11 失败与重试 · 12 组合筛选分页 ·
13 无数据与轮询失败 · 14 统计与图表 · 15 打印与 CSV · 16 技术信息 · 17 Qt 指标一致性 · 18 上传重试与冲突 ·
19 重复执行演示脚本 · 20 重启与旧模块回归

> 上表 20 项**没有一项已在本轮实际执行**，不能填“通过”。已做的只有代码层面的对照与静态检查。

## 六 与李慧颖的报警联调（第13周周三晚约定）

采样到报警的接入口已经按李的接口预留，但**报警模块尚未合入本分支**，因此用反射挂接：

1. 采样与报警任务同事务：`EnvironmentIngestServlet` 在事务内调用 `AlarmBridge.enqueue(connection, sampleId)`
   → 反射调用 `greenhouse.alarm.AlarmSampleInbox.enqueue(Connection, long)`；登记失败会回滚整条采样，不会出现“采样已存、报警没登记”。
2. 提交后才判定：调用 `ServletContext` 属性 `sampleAlarmProcessor#processCommittedSample(long)`，回执 `outcome` 写入该条的 `alarm_status`；
   处理器未注册或抛错时返回 `RETRY_SCHEDULED`，由报警侧按 1/2/4/8/16 秒重试，不重传样本。
3. 报警模块合入 `main` 后，可把 `AlarmBridge` 换成直接 `import`，行为不变。
4. 未接通前：`alarm_ready=false`、`alarm_status=NOT_READY`，任何报告都不能写“采样到报警已联调通过”。

联合验收序列（待跑）：规则 0–35，依次 25 → 36 → 37 → 同 `sample_id` 重试 37 → 25 → 36。

## 七 剩余问题与待测项

1. 全部运行级验证（上面第五节）需要成员本机执行。
2. 驾驶舱菜单入口、`web.xml` 无改动，菜单需戴统一合入；小程序侧只读接口仍按现有微信认证对接，本期未做小程序页面。
3. Qt 端逐条 `sample_id` / `alarm_status` 展示尚未实现（服务端 `items` 已返回，客户端仍按 `accepted/duplicated/rejected` 汇总显示）。
4. 采样键在列表中的隐藏已实现，CSV 仍保留 `采样唯一键` 列（如需彻底移除由组长决定）。
5. 打印/导出的条数上限按指导书本轮不处理。
