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
