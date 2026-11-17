XM06 农业大棚系统：小组开发与启动说明

一、当前版本
项目使用 JDK 17、Tomcat 10.1、MySQL 8；不是 Spring Boot 或 Maven 工程。
打开包含 src、web、sql 和 DeviceDemo.iml 的这一层目录。
保留 DeviceDemo 模块/Artifact 名称可以正常运行；浏览器访问路径统一为 /greenhouse_system。
现有功能：登录/退出、三类角色、动态菜单、待办查询维护及报表、环境监测页面和连接检查。
环境监测尚未包含真实采样、历史曲线、阈值报警、Qt采集和设备控制。
旧设备CRUD已修正为参数化查询，它是兼容接口，还不等于完整农业设备业务模块。
旧教学SQL执行入口禁止访问；未完成的旧教学上传入口返回501。

二、每位组员独立初始化数据库
1. 在自己的MySQL中创建空库：
   CREATE DATABASE greenhouse_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
2. 在这个空库先执行 sql/01_greenhouse_framework.sql，再执行 sql/09_user_management.sql。
   结果应为13张表、5条菜单、15条角色菜单关系；初始没有账号或业务记录。
3. 你已经建立13张表的数据库不重复执行01脚本，也不要删表重建。
   已有库如需补齐菜单，执行 sql/02_existing_database_menu.sql。
   原来的四个教学单表脚本已停用，避免误删数据和导入旧作业记录。
4. 修改表结构时提供新的迁移SQL给全组，不向组员发送个人数据库备份。

三、本机配置
1. 将 web/WEB-INF/greenhouse.example.properties 复制为同目录的 greenhouse.local.properties。
2. 填写自己的MySQL地址、端口、账号和密码，使用UTF-8编码。
   示例端口为3366；如果自己的MySQL使用3306，请改为3306。
   这是MySQL密码，不是网页登录密码。主机这份local文件已保留原有连接配置。
3. local文件位于WEB-INF，浏览器不能直接下载，且已加入.gitignore。
   手工打包/复制时.gitignore不会自动过滤文件，必须排除该文件和.idea/workspace.xml。
4. 也可以使用环境变量覆盖配置：
   GH_DB_HOST / GH_DB_PORT / GH_DB_USER / GH_DB_PASSWORD。
   或使用 -Dgreenhouse.db.host、port、user、password 系列JVM参数。
   可用 GH_CONFIG_FILE 或 -Dgreenhouse.configFile 指定外部私有配置文件绝对路径。
5. 默认从当前模块输出或部署输出的 WEB-INF/greenhouse.local.properties 读取配置。
   IDEA模块输出应保持为 web/WEB-INF/classes。配置变更后重新部署/重启。
6. PC开发不要求填写微信配置；原小程序接口保留 /api/week08/* 兼容路径。
   需要小程序时在local文件填写自己的 week08.apiKey、week08.wxAppId、week08.wxAppSecret。
   原有 W08_API_KEY、WEEK08_WX_APPID、WEEK08_WX_APPSECRET 环境变量仍然可用。
   这些旧小程序接口目前使用独立认证，不能把PC角色校验直接视为小程序权限实现。

四、IDEA配置与启动
1. Project SDK选择JDK 17。
2. Settings -> Appearance & Behavior -> Path Variables 新增 TOMCAT_HOME，
   值为自己安装的Tomcat 10.1目录（包含bin、lib、conf的目录）。
   项目lib库使用 $TOMCAT_HOME$/lib，不再绑定某个人的安装路径。
   如果IDEA提示未定义路径变量，按提示填写同一个目录。
3. Project Structure检查模块源目录src、Web目录web，以及WEB-INF/lib中的依赖。
4. Build -> Rebuild Project，重新生成类文件。不要继续使用旧的out或classes副本。
5. 创建Tomcat Local运行配置：
   Application Server：自己的Tomcat 10.1。
   JRE：JDK 17；HTTP port：9999（占用时可换端口）。
   Deployment：DeviceDemo:war exploded；Application context：/greenhouse_system。
   不需要部署旧教学 /upload 目录。
6. 在IDEA中运行 src/user/tools/AddUserDemo.java 的main方法，
   按提示为 admin（系统管理员）、manager（普通管理员）、worker（普通用户）设置密码。
   至少8个字符，UTF-8不超过72字节；保存BCrypt哈希。
   已存在的同名同角色账号保留原密码，不重置；同名不同角色会停止提示。
   工具与Tomcat使用同一套本机配置。
7. 运行Tomcat，打开：
   http://localhost:9999/greenhouse_system/
   若修改端口或项目访问路径，请同步调整浏览器地址。

五、交接后的启动检查
1. 未登录打开主页应进入登录页。
2. 未登录访问 /api/environment?action=ping 应返回HTTP401及JSON“请先登录”。
3. admin/manager登录后可以添加、查询、修改和删除自己的测试待办。
4. worker能查询，页面不显示维护按钮；绕过页面提交维护请求仍返回HTTP403。
5. 登录后进入农业管理 -> 环境监测，点击检查接口连接，应提示业务功能待接入。
   这个检查只证明接口可达，不表示已连接传感器或已实现采样数据库。
6. 测试待办筛选、导出、打印；新库为空时应显示无数据。
7. SQL通用入口query_record/update_record所有角色均返回403。
8. 旧设备接口 get_device_record 查询device_file；维护必须POST且只允许角色1/2。

六、小组开发约定
1. 新农业模块后端使用 src/greenhouse/<模块>，页面使用 web/maintain/<模块>。
2. 新接口在web.xml注册；LoginFilter需要识别 /api/ 接口以返回JSON401。
3. 当前BusinessPermissionFilter只覆盖两个兼容入口。
   新Servlet必须在后台校验登录、角色和数据范围，不能只隐藏页面按钮。
4. 新模块采用Servlet -> 业务处理/DAO -> MySQL流程，DAO使用PreparedStatement。
   不在Servlet成员变量中保存请求参数、当前用户或单次操作数据。
5. 公共页面引用 /home/frame/ 中的组件；前端使用GHApi.get/post。
   返回格式：{"result_code":0,"result_msg":"ok","data":{}}；错误配合正确HTTP状态码。
6. 数据库名称和表结构一致，连接配置、登录密码各自设置。
7. 页面不固定显示某个成员的学号、姓名或个人账号。
   顶部和业务记录中的当前登录用户姓名由真实账号数据生成。
8. 修改后至少由另一位组员在独立数据库中启动一次，再认定交接完成。

七、复制与发布
共享：src、web（排除WEB-INF/classes及greenhouse.local.properties）、sql、
DeviceDemo.iml、必要.idea项目配置、readme.txt和.gitignore。
排除：out、web/WEB-INF/classes、.idea/workspace.xml、私有配置和个人上传数据。
本次主机修改未连接或改动虚拟机数据库；复制回虚拟机后需要重新构建并部署。
主机已完成Java17编译与用户模块隔离检查；MySQL升级、Tomcat启动和浏览器验收仍须在虚拟机完成。

八、第十二周用户管理升级与验收（2026-11-16）

1. 用户管理不包含完整角色菜单编辑或权限审批。潘雨希的大棚模块现已另行合入，见第九部分。
2. 先备份虚拟机数据库，在现有greenhouse_system中执行 sql/09_user_management.sql。
   该脚本保留账号和业务数据，添加status、created_at、updated_at、auth_version、version和用户信息菜单；可重复执行。
   原有账号默认active；已有非active/disabled/deleted状态值的数据库必须先核对，不能直接混用。
   原账号新增的创建时间为升级补录时间，不应当作真实历史注册日期。
3. 再将主机的src、web、sql及运行说明覆盖到自己的虚拟机项目，保留自己的.idea、DeviceDemo.iml和私有配置。
   主机与虚拟机连接配置不同的情况下，复制web时排除greenhouse.local.properties。
4. IDEA重新构建项目和DeviceDemo:war exploded后启动Tomcat，旧会话需要重新登录。
   用户页：http://localhost:9999/greenhouse_system/maintain/user/index.jsp
5. admin可管理账号；manager、worker只看到本人资料。密码不进入列表、详情、CSV和打印。
6. 管理动作：新增、编辑、启停、逻辑删除、重置密码；账号创建后不改名。
   查询支持账号/名称关键词、角色、状态、分页、明确排序；统计按角色和状态展示。
   CSV导出全部筛选记录，打印清单和图表基于同一完整记录快照。
7. 不允许改变自己的角色、停用或删除自己；保留至少一个有效系统管理员。
   停用、删除、角色变更和重置密码会提升auth_version，旧PC会话在下一次受保护请求失效。
   重新启用也不会恢复原有旧会话；需要重新登录。
   当前会话校验覆盖PC登录；原week08独立小程序认证没有因本次修改自动获得同样的状态校验。
8. 前端维护请求需要context提供的csrf_token；旧版本记录更新返回409，刷新后重试。
9. 与潘雨希对接：平台账号状态是active/disabled/deleted，大棚自身enabled/disabled保持不变。
   GET /api/users?action=options 返回data.items，每项只有id与display_name，适用于已登录角色的负责人下拉。
   新增/更换负责人仍需后台验证 todo_login_user.status='active'；不能使用status='enabled'判断平台账号。
10. API概览：
    GET /api/users?action=context：当前安全资料、管理能力、CSRF令牌。
    GET action=me：本人资料；GET action=options：有效负责人。
    GET action=list/detail/stats/export/report：管理员查询，detail允许普通用户查自己。
    POST action=create/update/set_status/delete/reset_password：系统管理员维护。
    通用查询参数：keyword、role_id、status、sort、dir、page、size。
    update/set_status/delete/reset_password需id及最近详情中的version；密码仅create/reset_password提交。
11. 验收建议：
    创建临时worker账号并登录；管理员停用，检查已有会话下一次访问401和重新登录失败。
    重新启用后旧会话仍失败，新登录成功；修改角色后旧会话失效。
    普通角色不能访问管理列表、统计、导出和他人详情；手工提交管理请求返回403。
    重复账号409、非法字段400、缺CSRF403、过期version409。
    删除账号保留数据库行与业务关联；对已删除账号不能再编辑或启用。
    CSV与打印不含密码/哈希，统计口径和当前查询一致，打印显示实际登录人。
12. 自动检查源文件：tests/UserModuleCheck.java。
    以临时Java17、Servlet API 6、现有项目JAR及临时H2运行。H2只用于隔离DAO/API/PC过滤器检查，不是生产依赖。
    编译命令示意：java -jar <ecj.jar> -17 -encoding UTF-8 -proc:none -cp "web/WEB-INF/lib/*;<servlet-api.jar>;<h2.jar>" -d <临时classes> <src下全部java> tests/UserModuleCheck.java
    执行：java -cp "<临时classes>;web/WEB-INF/lib/*;<servlet-api.jar>;<h2.jar>" greenhouse.identity.UserModuleCheck
    此检查不读取生产凭据，不连接MySQL；MySQL升级、JSP部署与浏览器交互仍需虚拟机验收。

验证记录：2026-11-16已通过29个应用Java源文件及隔离检查代码的Java17编译，用户模块58项断言、JavaScript语法、web.xml及页面引用核对。H2结果不替代MySQL迁移与浏览器验收。

九、大棚档案入口与模块合入（2026-11-16）

已合入修正后的GreenhouseServlet.java、maintain/greenhouse/index.jsp及11、12脚本。
原框架不需要增加大棚Servlet的XML映射，该类使用@WebServlet注册。
数据库先执行09，再执行11；需要演示数据时执行12；最后运行02补菜单。
已有库已完成的迁移不用重跑，只补需要的菜单。
农业管理下新增202号“大棚档案”，三类角色可见，维护权限仍由后台校验。
首页增加大棚档案快捷入口，设备管理仍单独标为待接入。
虚拟机已经有大棚模块的情况下，只需补菜单SQL并更新web/home/main/index.jsp。
当前大棚页已接入公共页头、侧栏、面包屑、页脚与公共脚本；打印预览隐藏导航，状态统计图尚未实现。

大棚页公共布局更新：重新复制web/maintain/greenhouse/index.jsp并更新部署即可，不修改Java和SQL。该页业务脚本已置于闭包中，避免局部$影响公共jQuery；公共组件只加载一次。29项模拟DOM检查通过，实际Tomcat及浏览器仍需虚拟机复核。

2026-11-17 删除反馈更新：大棚删除请求失败时显示浏览器弹窗，并保留页面错误文字；取消确认不发送请求，成功删除继续刷新列表。仅更新maintain/greenhouse/index.jsp。37项模拟DOM检查通过，含关联拒绝、取消及成功刷新；虚拟机仍需更新页面后确认。
