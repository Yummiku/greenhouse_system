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

十、大棚扩展与三项修复合入（2026-11-18）

本节为当前部署说明，更新第九部分中“状态统计图尚未实现”等历史状态。
已合入状态分布图、设备交互绑定、种植批次和单棚坐标地图入口。
停用棚已有批次允许按规则收获或取消，保留历史字段；新增及更换目标棚须启用。
坐标可以清除，无业务历史的大棚删除时同事务清理坐标；设备和种植历史仍阻止直接删除。
两个Servlet的维护动作均检查Session中的userCsrfToken；页面自动获取并提交csrf_token。
不得只复制Java而不更新页面，否则旧页面维护请求会因缺少令牌返回403。

主机向已有虚拟机项目复制以下七个文件，目录相对于项目根目录：
src/greenhouse/greenhouse/GreenhouseServlet.java
src/greenhouse/greenhouse/GreenhouseExtensionServlet.java
src/greenhouse/greenhouse/CsrfGuard.java
web/maintain/greenhouse/index.jsp
web/maintain/greenhouse/resources.jsp
web/maintain/greenhouse/resources.js
sql/14_greenhouse_extensions.sql
同时同步README.md、readme.txt以保持说明一致。
不覆盖greenhouse.local.properties、IDEA本机配置及其他组员模块。

数据库先备份：已有09、11的数据库只新增执行14；已执行14的环境无需为本次修复重跑SQL。
11、12号脚本未变化，不重跑01，不重建或清空表。12号模拟数据不是本次部署的必需步骤。
14创建greenhouse_geo和planting_batch两张扩展表，不提供真实硬件数据或真实坐标。
GreenhouseServlet和GreenhouseExtensionServlet均通过@WebServlet注册，web.xml本次不改。

虚拟机更新顺序：停止Tomcat；备份并按需执行14；复制文件；重新构建项目和war exploded；启动Tomcat；重新登录并强制刷新页面。
入口：http://localhost:9999/greenhouse_system/maintain/greenhouse/index.jsp
扩展：http://localhost:9999/greenhouse_system/maintain/greenhouse/resources.jsp
无需新增独立菜单，从大棚页的“设备关联、种植批次与地图”或行内入口进入。

验收重点：状态图随条件更新；现有设备可绑定且重复绑定拒绝；停用棚已有批次可收获或取消且历史不可篡改；
坐标保存与清除、仅坐标档案删除、有业务关联删除拒绝；缺少或错误令牌403、普通用户维护403、正常管理操作成功。
打印预览、重启后数据保留、多成员联合验收和在线底图实际加载，仍须在运行环境分别确认。

本次主机合并验证：32个应用Java源文件合并编译、用户管理58项H2隔离断言、大棚修复51项H2/模拟HTTP断言、
扩展页面25项模拟DOM/API检查、JS语法及XML解析通过。隔离检查替换了数据库连接，不读取真实MySQL凭据；
本次没有执行主机或虚拟机SQL，没有完成真实Tomcat页面、地图底图和系统打印验收。

十一、三个独立业务页面与子菜单（2026-11-18）

大棚位置地图map.jsp、设备归属管理devices.jsp、种植批次管理batches.jsp分别只显示各自业务。
三个页面共用resources.jsp布局模板及resources.js，更新时必须一起复制，不能只复制入口JSP。
地图和种植查询字段已有中文标签、用途说明、无数据提示；种植详情采用中文字段。
resources.jsp旧地址默认显示地图，旧行内入口已改为分别跳转三个页面并传递greenhouse_id。

本次新增复制：web/maintain/greenhouse/map.jsp、devices.jsp、batches.jsp；sql/15_greenhouse_submenus.sql。
本次更新复制：web/maintain/greenhouse/resources.jsp、resources.js、index.jsp；web/home/frame/frame_menu.js。
在已有14号扩展表的数据库执行15号脚本补菜单，三类角色都可见，后台原维护权限不变。
菜单结构：农业管理→大棚档案→大棚位置地图、设备归属管理、种植批次管理。
点击“大棚档案”文字进入档案列表；点击右侧箭头展开或收起三个子菜单。
原菜单渲染仅支持两层，本次改为递归显示，并保留当前页面及祖先菜单高亮。
没有修改Java或web.xml，没有新增业务字段。更新部署后重新登录并Ctrl+F5刷新。

十二、设备新增与解除绑定（2026-11-18）

设备归属管理新增“新增设备档案”；填写唯一编号、名称和类型，保存后设备处于未绑定状态。
已绑定设备可“解除绑定”，解除时将原大棚、指标、单位、解除时间及操作人写入历史归属表；
与删除当前绑定在同一事务完成，归档失败则全部回滚。设备档案和采样记录不删除，可再绑定另一座启用大棚。
列表“历史归属”查看最近100条解除记录。普通用户可查询，角色1/2才可新增、绑定和解除；维护均需有效令牌。
新增POST动作device_create（device_code/device_name/device_type）、unbind（device_pk/greenhouse_id）；
GET动作device_history（device_pk）。编号重复409，归属已变化时解除返回409要求刷新。

虚拟机先备份数据库，再执行sql/16_device_binding_actions.sql。
16号脚本给device_file.device_id增加唯一索引，并新建greenhouse_device_binding_history；可重复执行。
如重复编号核对查询有结果或添加索引报1062，先核对原设备编号，不要直接删除设备；处理后重试脚本。
本轮更新文件：src/greenhouse/greenhouse/GreenhouseExtensionServlet.java、
web/maintain/greenhouse/resources.jsp、resources.js、sql/16_device_binding_actions.sql及本说明。
Java已变化，需要停止Tomcat、复制文件、重新构建项目与war exploded、启动并重新登录刷新。
已完成14和15的库只补16；首次部署按09、11、14、15、16及原初始化说明进行，12仅可选演示数据。
主机验证：32个应用Java编译、79项H2/模拟HTTP断言、16项设备页面模拟断言和42项独立页面/菜单回归通过。
本轮未执行真实MySQL迁移，也未修改虚拟机数据库。
采样模块后续应保存采样当时的大棚归属，不能用当前绑定倒推历史；本历史表记录解除时快照，不伪造旧绑定开始时间。

十三、大棚面积自动计算（2026-11-18）

本节更新旧版面积独立填报规则：新增及编辑时，面积=长度×宽度，四舍五入保留两位小数。
页面面积只读，清空或填写无效尺寸时清空面积并阻止提交；后台重新计算，忽略客户端area值。
长宽最多两位小数；计算结果必须为0.01至9999999999.99平方米，避免超出DECIMAL(12,2)。
没有数据库迁移，不批量修改旧记录；打开旧记录编辑时预览重算，实际保存后才更新数据库面积。
更新文件：src/greenhouse/greenhouse/GreenhouseServlet.java、web/maintain/greenhouse/index.jsp。
复制后须重新构建项目及war exploded，重启Tomcat并强制刷新。示例30×20=600.00，1.15×0.10=0.12。

十四、大棚档案父菜单只负责导航（2026-11-18）

最新菜单结构：农业管理→大棚档案→档案列表、大棚位置地图、设备归属管理、种植批次管理。
父级“大棚档案”没有页面地址，点击文字或箭头只展开/收起；点击“档案列表”进入index.jsp。
已执行旧15号菜单脚本的环境，只需执行sql/17_greenhouse_menu_index.sql，再刷新页面。
15号脚本也已同步为此结构，首次部署使用新版15即可，无需重复补17。
本轮无Java和页面改动；继续使用上一轮支持多层菜单的web/home/frame/frame_menu.js。
覆盖第十一部分中“父菜单文字进入档案列表”这一旧操作说明。首页档案快捷入口仍可直达列表。

十五、阶段性提交说明（2026-11-20）

本次提交包含大棚档案面积计算、设备新增与解绑历史、种植批次、三个独立页面及导航调整。
用户已反馈地图功能仍有问题；本轮保留相关实现并标记待修复，不认定地图完整运行验收通过。
问题的具体表现和原因须后续复现，不将其预先归因为网络、坐标或地图服务配置。
本机数据库连接配置和个人运行配置继续排除，不随提交上传。

十六、个人消息模块合入（2026-11-22）

本次将已整改的消息模块并入主机项目，保留既有大棚档案、设备归属、种植批次及菜单功能。
原消息提交为efa8524，本地验收分支另增加操作失败弹窗及独立数据库名称配置。
web.xml同时保留/api/users、/api/messages和/api/environment，不重复注册Servlet。

数据库准备：
1. 已有用户管理表及status字段的数据库，先备份，再执行sql/51_personal_message.sql和sql/52_personal_message_menu.sql。
   51只建personal_message表；表已存在时不删除、不清空记录。52补消息中心及角色菜单授权。
2. sql/53_personal_message_demo.sql仅为可选演示数据；按admin、worker用户名查有效账号实际id。
   缺少有效账号会提示并跳过，重复执行不重复生成同名演示消息。需要更换测试账号时修改查询中的用户名。
3. 不重跑01，不重建账号或业务表。原SQL默认USE greenhouse_system；验收库执行时改为USE greenhouse_system_test。
   Navicat数据库下拉框不能覆盖脚本内的USE指令。

连接配置与部署：
1. 私有配置web/WEB-INF/greenhouse.local.properties可以增加greenhouse.db.name=greenhouse_system_test。
   Db统一读取该库名；未配置时沿用调用方的库名。JVM属性、环境变量GH_DB_NAME优先于文件。
2. 保留本机host/port/user/password，私有配置不提交。停止Tomcat后重新构建项目与war exploded，再启动并重新登录。
3. 正常项目默认上下文/greenhouse_system、端口9999；验收副本使用/greenhouse_system_test、端口10000。
4. 浏览器Ctrl+F5刷新消息JS；收件箱路径/maintain/message/message_inbox.jsp，发件路径/maintain/message/message_compose.jsp。

权限及操作：
角色1/2/3均有本人收件箱，角色1查看全部发件，角色2只查看本人创建的发件，角色3发件接口返回403。
创建和修改草稿、最终发送均检查有效账号；接收人停用后发送返回409，草稿和发送时间保持不变。
收件人只可删除自己的已读消息；草稿可编辑、删除；已发送消息正文不可改，可按权限撤回。
CSV和报表接口按权限及条件分批取数；打印覆盖跨页记录，当前读取上限5000条，大数据场景需另行核验。
发送、保存、撤回、删除、已读及打印等主动操作失败时弹窗，并保留页面错误文字与后台具体原因。

本轮复测范围：组长在greenhouse_system_test中确认四项初审整改通过。
发件箱打印截图显示12条，收件箱跨页打印按用户反馈通过；演示消息24/25关联实际接收人id5，重复执行未增加记录，
缺失账号测试副本不插入消息；三角色菜单及跳转由用户确认正常。完整消息模块及地图等其他功能不据此自动认定通过。
原有地图待修复说明仍然有效，本次不包含地图修复。
