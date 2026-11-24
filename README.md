# 农业大棚系统（XM06）

六人小组共同开发的农业大棚监测与管理项目，基于课程 DeviceDemo 框架改造。

## 技术环境

- JDK 17
- Tomcat 10.1
- MySQL 8
- Java Servlet / JSP / JDBC / Bootstrap

这是传统 Java Web 项目，直接用 IDEA 打开仓库根目录；当前不是 Maven 或 Spring Boot 工程。

## 配置与启动

完整操作步骤请阅读 [readme.txt](readme.txt)，包括建库、数据库连接、IDEA / Tomcat 配置、创建账号和启动检查。

1. 新成员在自己的 MySQL 中创建空库 `greenhouse_system`，依次执行 [基础初始化](sql/01_greenhouse_framework.sql) 和 [用户管理升级](sql/09_user_management.sql)。
2. 将 `web/WEB-INF/greenhouse.example.properties` 复制为同目录的 `greenhouse.local.properties`，填写自己的连接配置。
3. 配置 JDK 17 和 Tomcat 10.1；IDEA 路径变量 `TOMCAT_HOME` 指向自己的 Tomcat 安装目录。
4. 重新构建项目，运行 `user.tools.AddUserDemo`，按提示设置本机三类账号的密码。
5. 部署 `DeviceDemo:war exploded`，Application context 设为 `/greenhouse_system`，HTTP 端口设为 `9999`。
6. 启动后访问 `http://localhost:9999/greenhouse_system/`。

已有框架数据库不要重新执行新库初始化脚本。需要补菜单时使用 [已有库菜单脚本](sql/02_existing_database_menu.sql)。

## 当前功能

- 登录、退出及三类角色
- 动态菜单和后台维护权限校验
- 待办事项查询、维护、统计、导出和打印
- 兼容的设备记录 CRUD
- 环境监测页面及接口连接检查
- 用户管理、账号状态与旧PC会话失效校验
- 个人消息收发、草稿、已读、撤回、逻辑删除、统计及导出打印
- 大棚档案、状态分布图、设备绑定、种植批次及坐标地图入口

环境监测当前为开发入口；真实采样、历史曲线、阈值报警、Qt 采集和远程设备控制尚未完成。
Java 编译、Tomcat 启动和业务验收需在各成员的开发环境执行，不能用静态检查代替运行验收。

## 小组协作

- 每位成员使用独立数据库，保持表结构一致，通过迁移 SQL 同步表结构变更。
- 新农业模块放在 `src/greenhouse/<模块>` 和 `web/maintain/<模块>`。
- 每个新接口都需要校验登录、操作权限和业务数据范围。
- 本机配置、数据库密码、微信密钥、IDEA 运行历史和编译输出不提交到仓库。
- `.gitignore` 不会过滤手动制作的压缩包；手工分享前同样排除私有配置。
- `week08` 包和 `/api/week08/*` 路由为原小程序兼容代码，后续统一时需同步修改客户端。

## 第十二周：用户管理

新增用户信息入口、系统管理员账号维护、有效负责人列表及PC旧会话失效检查。
已有库先备份并执行 `sql/09_user_management.sql`，再部署代码并重新登录；不要重新执行01脚本。
账号状态为 `active / disabled / deleted`，有效负责人接口为 `/api/users?action=options`。
详细更新与验收步骤见 `readme.txt` 第八部分。隔离集成检查见 `tests/UserModuleCheck.java`。

## 大棚扩展模块合入

已合入潘雨希的三项问题修正版。大棚列表可进入设备关联、种植批次与地图页面。
已有用户管理和大棚表的数据库，在备份后执行 `sql/14_greenhouse_extensions.sql`；已执行过14的环境无需为本次修复重跑SQL。
首次建库仍按01、09、11、14顺序初始化，12号脚本仅用于可选模拟数据，02号脚本用于补充菜单。
两个大棚Servlet使用 `@WebServlet` 注册，无需重复增加XML映射。基础合入见 `readme.txt` 第十部分，三个独立页面和子菜单部署见第十一部分。
地图支持保存和清除坐标、查看所选大棚的在线地图入口；底图加载需联网单独验收。设备绑定不代表真实硬件已接入。

地图、设备归属和种植批次分别使用 `map.jsp`、`devices.jsp`、`batches.jsp`，共用 `resources.jsp` 布局与 `resources.js`。
在已有数据库执行 `sql/15_greenhouse_submenus.sql` 后，左侧“大棚档案”下显示档案列表、地图、设备归属和种植批次四个子菜单；必须同时更新 `web/home/frame/frame_menu.js` 以支持第三层菜单。

设备归属页面已支持新增设备、解除绑定和历史归属查询。部署前执行 `sql/16_device_binding_actions.sql`，建立设备编号唯一索引及历史归属表；解除后可重新绑定，原归属快照保留。具体部署和测试见 `readme.txt` 第十二部分。

父级“大棚档案”只展开或收起导航，点击“档案列表”进入档案页面。已运行旧15号脚本的数据库，执行 `sql/17_greenhouse_menu_index.sql` 更新这一导航结构。

大棚新增和编辑时，面积只读，按长×宽自动计算并保留两位小数；后台保存时重新核算。原有记录在编辑保存后按新规则更新，无需运行新的SQL。

## 当前待修复事项

2026年11月20日，地图功能仍有问题，本次为阶段性提交，地图尚未通过完整运行验收。保留地图页面和坐标接口，后续继续复现并修复；不能将当前提交视为地图功能验收完成。

## 个人消息模块合入

2026年11月22日合入肖泽亮的个人消息模块及整改版本，并保留消息操作失败弹窗和后台具体错误原因。
已有用户管理数据库先备份，再依次执行 `sql/51_personal_message.sql`（建消息表）和 `sql/52_personal_message_menu.sql`（增量补消息菜单）；已有消息表不需要重建。
`sql/53_personal_message_demo.sql` 为可选演示数据，按用户名和有效状态查找实际账号编号，缺少有效账号时跳过；不要求成员使用固定账号编号。
不要重新执行01或清空原有业务数据。执行SQL前核对脚本中的 `USE` 与目标数据库一致，不能只切换Navicat的数据库下拉框。

消息页面为 `/maintain/message/message_inbox.jsp` 和 `/maintain/message/message_compose.jsp`，接口为 `/api/messages`。
三类角色都有本人收件箱；系统管理员可查看全部发件，普通管理员只查看本人发件，普通用户无发件权限。
草稿可编辑和删除，已发送消息不可编辑；收件人仅可删除自己的已读消息，撤回后不再显示在收件箱。
保存及发送均检查有效账号；停用接收人后，旧草稿发送返回409并保持草稿状态。打印通过报表接口获取跨页结果，操作失败弹窗保留后台原因。

副本可在私有配置中设置 `greenhouse.db.name=greenhouse_system_test`，统一选择验收库；未配置时沿用源码库名。不要提交本机密码或 `greenhouse.local.properties`。
本轮组长确认通过的整改范围为账号状态限制、跨页打印、演示账号解析与重复执行、三角色消息菜单。大批量报表目前有5000条读取上限，完整模块回归和其他边界按实际运行另行确认；地图待修复状态保持不变。详细步骤见 `readme.txt` 第十六部分。

## 默认绿色主题

2026年11月23日起，公共业务页面和登录页默认加载 `web/assets/admin/layout/css/themes/green.css`。
该文件沿用原 Metronic 主题结构，统一墨绿色导航、深绿色主按钮、浅绿色统计区域和表头；删除及错误保留红色，警告和多类别图表保留各自含义。
已有 `btn blue`、`btn green` 等类名可以继续使用，由当前主题统一配色，无须修改按钮事件或业务接口。

主题入口是 `web/home/frame/frame_style.jsp` 和 `web/home/main/login.jsp` 中的 `style_color` 样式引用。
原 `darkblue.css` 保留备用；恢复原主题时，将两处引用的 `green.css?v=20261123` 改回 `darkblue.css`。
无需加载额外的 `greenhouse-green.css` 覆盖文件，也不要修改 Bootstrap 或 Metronic 组件基础文件。

本次只同步主题和加载配置，不包含验收副本中的环境采样、阈值业务代码，也没有调整模块统计与表单的位置。
部署时同步主题 CSS 和两份 JSP，更新资源或重启 Tomcat 后按 Ctrl+F5 刷新，无需运行 SQL。
浏览器检查使用现有 JSP 页面结构和实际样式文件；实际 Tomcat 登录、权限及数据库业务仍按各环境运行验收。
